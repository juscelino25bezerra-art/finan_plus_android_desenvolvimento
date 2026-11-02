// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.finanplus.R
import com.finanplus.core.Cents
import com.finanplus.core.Money
import com.finanplus.core.PersonStatus
import com.finanplus.core.Shared
import com.finanplus.core.SharedExpense
import com.finanplus.core.SharedSummary
import com.finanplus.core.SplitMode
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

/**
 * Resumo de uma despesa compartilhada como imagem (PNG, 1080 px de largura), para mandar no WhatsApp,
 * Telegram etc. Na identidade do Finan+: ícone e nome do app no topo, fundo azul-claro com brilhos
 * suaves, cartão branco com o total, quem pagou e quem deve e quanto, e o endereço do site no rodapé.
 *
 * Desenhado com o Canvas do Android (sem bibliotecas). A altura cresce com o número de participantes.
 */
object SharedImage {
    private const val W = 1080
    private const val PAD = 64f
    private const val CARD_PAD = 56f
    private const val ROW_H = 132f

    // paleta clara do app (Theme.kt), fixa: a imagem é a mesma em qualquer tema
    private const val BG = 0xFFEEF4FF.toInt()
    private const val INK = 0xFF182238.toInt()
    private const val MUTED = 0xFF5B6579.toInt()
    private const val ACCENT = 0xFF3A5FC8.toInt()
    private const val ACCENT2 = 0xFFDFE8FF.toInt()
    private const val GREEN = 0xFF1B7351.toInt()
    private const val RED = 0xFFB03A4F.toInt()
    private const val TRACK = 0xFFE3E9F5.toInt()
    private const val LINE = 0xFFE6EBF5.toInt()
    private val AVATARS = intArrayOf(0xFF3A5FC8.toInt(), 0xFF7B61C9.toInt(), 0xFF1B9E77.toInt(), 0xFFE07A2F.toInt(), 0xFF2A9DB5.toInt(), 0xFFB03A4F.toInt())

    private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    const val SITE = "finanplus-web.github.io"

    /** PNG pronto para compartilhar. */
    fun png(ctx: Context, e: SharedExpense): ByteArray {
        val bmp = render(ctx, e)
        return try {
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bmp.recycle()
        }
    }

    fun render(ctx: Context, e: SharedExpense): Bitmap {
        val st = Shared.summary(e)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val black = Typeface.create("sans-serif-black", Typeface.NORMAL)

        fun font(size: Float, tf: Typeface = Typeface.DEFAULT, color: Int = INK) {
            p.reset(); p.isAntiAlias = true; p.textSize = size; p.typeface = tf; p.color = color
        }
        val cardW = W - 2 * PAD
        val inner = cardW - 2 * CARD_PAD

        // título em até 2 linhas
        font(60f, black)
        val title = wrap(p, e.name, inner, 2)

        val cardTop = 300f
        // alturas na mesma ordem em que o cartão é desenhado abaixo
        val headH = 570f + title.size * 70f
        val listH = st.people.size * ROW_H
        val cardH = headH + listH + CARD_PAD
        val H = (cardTop + cardH + 260f).toInt()

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // ---- fundo: azul-claro com brilhos suaves (o "liquid glass lite" do app)
        c.drawColor(BG)
        fun glow(cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
        }
        glow(W * 0.95f, 40f, 700f, ACCENT, 90)
        glow(0f, H * 0.35f, 620f, 0xFFC9BBFF.toInt(), 110)
        glow(W * 0.9f, H * 0.75f, 600f, 0xFFA9E5CF.toInt(), 90)
        glow(W * 0.1f, H.toFloat(), 560f, ACCENT, 60)

        // ---- topo: ícone do app + nome
        val iconSize = 136f
        drawIcon(ctx, c, RectF(PAD, 78f, PAD + iconSize, 78f + iconSize))
        font(64f, black); c.drawText("Finan+", PAD + iconSize + 32f, 148f, p)
        font(32f, Typeface.DEFAULT, MUTED); c.drawText("Despesa compartilhada", PAD + iconSize + 34f, 196f, p)

        // ---- cartão branco translúcido
        val card = RectF(PAD, cardTop, PAD + cardW, cardTop + cardH)
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(40, 38, 62, 140)
        c.drawRoundRect(RectF(card.left, card.top + 14f, card.right, card.bottom + 14f), 56f, 56f, p) // sombra suave
        p.color = Color.argb(240, 255, 255, 255); c.drawRoundRect(card, 56f, 56f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = Color.WHITE; c.drawRoundRect(card, 56f, 56f, p); p.style = Paint.Style.FILL

        val x0 = card.left + CARD_PAD
        val x1 = card.right - CARD_PAD
        var y = card.top + CARD_PAD + 20f

        font(60f, black)
        for (line in title) { y += 52f; c.drawText(line, x0, y, p); y += 18f }
        y += 44f
        font(32f, Typeface.DEFAULT, MUTED)
        val split = if (e.split == SplitMode.EQUAL) "dividida igualmente" else "valor de cada um"
        c.drawText(fit(p, "${e.date.format(DMY)} · pago por ${e.payer?.name ?: "—"} · $split", inner), x0, y, p)

        // total
        y += 40f
        font(30f, bold, MUTED); y += 34f; c.drawText("TOTAL", x0, y, p)
        font(92f, black, ACCENT); y += 92f; c.drawText(Money.format(e.total), x0, y, p)

        // recebido / pendente / situação
        y += 40f
        val colW = inner / 3f
        fun stat(i: Int, label: String, value: String, color: Int, size: Float = 40f) {
            val x = x0 + i * colW
            font(28f, Typeface.DEFAULT, MUTED); c.drawText(label, x, y + 30f, p)
            font(size, bold, color); c.drawText(fit(p, value, colW - 8f), x, y + 82f, p)
        }
        stat(0, "Recebido", Money.format(st.received), GREEN)
        stat(1, "Pendente", Money.format(st.pending), if (st.pending > 0) RED else GREEN)
        stat(2, "Situação", st.state.label, stateColor(st.state), 32f)
        y += 110f
        // barra de progresso do que já voltou
        val bar = RectF(x0, y, x1, y + 18f)
        p.reset(); p.isAntiAlias = true; p.color = TRACK; c.drawRoundRect(bar, 9f, 9f, p)
        if (st.toReceive > 0 && st.received > 0) {
            p.color = GREEN
            c.drawRoundRect(RectF(x0, y, x0 + (x1 - x0) * (st.received.toFloat() / st.toReceive).coerceIn(0f, 1f), y + 18f), 9f, 9f, p)
        }
        y += 18f + 46f

        // ---- quem deve e quanto
        p.reset(); p.color = LINE; p.strokeWidth = 2f; c.drawLine(x0, y, x1, y, p)
        y += 70f
        font(40f, bold); c.drawText("Quem deve e quanto", x0, y - 16f, p)
        st.people.forEachIndexed { i, ps -> row(c, p, ps, i, x0, x1, y, bold, black, st.toReceive); y += ROW_H }

        // ---- rodapé: marca
        var fy = card.bottom + 96f
        val small = 64f
        drawIcon(ctx, c, RectF(W / 2f - 250f, fy - 46f, W / 2f - 250f + small, fy - 46f + small))
        font(34f, bold); c.drawText("Feito com Finan+", W / 2f - 166f, fy - 4f, p)
        font(26f, Typeface.DEFAULT, MUTED); c.drawText("Finanças pessoais livres e privadas", W / 2f - 166f, fy + 34f, p)
        fy += 110f
        font(28f, bold, ACCENT); p.textAlign = Paint.Align.CENTER; c.drawText(SITE, W / 2f, fy, p)
        return bmp
    }

    /** Uma pessoa: inicial, nome, parte e quanto devolveu; à direita a situação e o que falta. [toReceive]: o que quem pagou recebe. */
    private fun row(c: Canvas, p: Paint, ps: PersonStatus, i: Int, x0: Float, x1: Float, top: Float, bold: Typeface, black: Typeface, toReceive: Cents) {
        val cy = top + ROW_H / 2f
        // avatar com a inicial
        val color = AVATARS[i % AVATARS.size]
        p.reset(); p.isAntiAlias = true; p.color = color; p.alpha = 40; c.drawCircle(x0 + 40f, cy, 40f, p)
        p.alpha = 255; p.typeface = black; p.textSize = 36f; p.textAlign = Paint.Align.CENTER
        c.drawText(ps.person.name.trim().take(1).uppercase(), x0 + 40f, cy + 13f, p)

        // situação à direita (pílula) e o que falta
        val (label, fg) = when (ps.state) {
            PersonStatus.State.NONE -> "Pagou" to ACCENT
            PersonStatus.State.PAID -> "Quitado" to GREEN
            PersonStatus.State.PARTIAL -> "Pagou parte" to ACCENT
            PersonStatus.State.PENDING -> "Pendente" to RED
        }
        p.reset(); p.isAntiAlias = true; p.typeface = bold; p.textSize = 26f
        val pw = p.measureText(label) + 44f
        val pill = RectF(x1 - pw, cy - 50f, x1, cy - 6f)
        p.color = fg; p.alpha = 30; c.drawRoundRect(pill, 22f, 22f, p)
        p.alpha = 255; p.textAlign = Paint.Align.CENTER; c.drawText(label, pill.centerX(), pill.centerY() + 9f, p)
        val amount = when {
            ps.isPayer -> "recebe ${Money.format(toReceive)}"
            ps.remaining > 0 -> "falta ${Money.format(ps.remaining)}"
            else -> Money.format(ps.owed)
        }
        p.textAlign = Paint.Align.RIGHT; p.textSize = 32f; p.color = if (!ps.isPayer && ps.remaining > 0) RED else INK
        c.drawText(amount, x1, cy + 38f, p)
        val amountW = p.measureText(amount)

        // nome e parte
        val textMax = x1 - pw - 24f - (x0 + 104f)
        p.reset(); p.isAntiAlias = true; p.typeface = bold; p.textSize = 36f; p.color = INK
        c.drawText(fit(p, ps.person.name, textMax), x0 + 104f, cy - 8f, p)
        p.typeface = Typeface.DEFAULT; p.textSize = 28f; p.color = MUTED
        val sub = if (ps.isPayer) "Pagou a despesa · parte ${Money.format(ps.person.share)}" else "Parte ${Money.format(ps.owed)} · devolveu ${Money.format(ps.paid)}"
        // a linha de baixo divide o espaço com o valor à direita (que é mais largo que a pílula)
        c.drawText(fit(p, sub, x1 - amountW - 28f - (x0 + 104f)), x0 + 104f, cy + 34f, p)

        p.reset(); p.color = LINE; p.strokeWidth = 2f
        c.drawLine(x0 + 104f, top + ROW_H - 1f, x1, top + ROW_H - 1f, p)
    }

    private fun stateColor(s: SharedSummary.State) = when (s) {
        SharedSummary.State.SETTLED -> GREEN
        SharedSummary.State.PARTIAL -> ACCENT
        SharedSummary.State.OPEN -> RED
    }

    /** Ícone do app (camadas de fundo e frente do ícone adaptável), com cantos arredondados. */
    private fun drawIcon(ctx: Context, c: Canvas, dst: RectF) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bg = BitmapFactory.decodeResource(ctx.resources, R.mipmap.ic_launcher_background, opts)
        val fg = BitmapFactory.decodeResource(ctx.resources, R.mipmap.ic_launcher_foreground, opts)
        val r = dst.width() * 0.26f
        c.save()
        c.clipPath(Path().apply { addRoundRect(dst, r, r, Path.Direction.CW) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        // as camadas do ícone adaptável têm 108 dp, com a área visível nos 72 dp do meio (1/6 de margem em cada lado)
        if (bg != null) { val m = bg.width / 6; c.drawBitmap(bg, Rect(m, m, bg.width - m, bg.height - m), dst, p); bg.recycle() }
        if (fg != null) { val m = fg.width / 6; c.drawBitmap(fg, Rect(m, m, fg.width - m, fg.height - m), dst, p); fg.recycle() }
        c.restore()
    }

    private fun fit(p: Paint, s: String, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun wrap(p: Paint, s: String, max: Float, maxLines: Int): List<String> {
        val lines = ArrayList<String>()
        var cur = ""
        for (w in s.split(' ').filter { it.isNotEmpty() }) {
            val next = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(next) <= max || cur.isEmpty()) cur = next else { lines.add(cur); cur = w }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        if (lines.size <= maxLines) return lines.ifEmpty { listOf("") }
        val keep = lines.take(maxLines).toMutableList()
        keep[maxLines - 1] = fit(p, lines.drop(maxLines - 1).joinToString(" "), max)
        return keep
    }
}
