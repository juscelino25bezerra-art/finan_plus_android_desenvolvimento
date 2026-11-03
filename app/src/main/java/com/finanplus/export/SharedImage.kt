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
import android.os.Build
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
 *
 * Fonte: a mesma do app (a fonte padrão do sistema, `FontFamily.Default` no Compose), nos mesmos pesos
 * dos títulos e textos (800 nos títulos, 700 nos destaques, 600 nos rótulos, 400 no texto).
 * Cada texto e cada forma usa um Paint novo: reaproveitar um só deixava o alinhamento de um texto
 * "vazar" para o seguinte (nomes e rodapé saíam alinhados à direita).
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
    private const val GREEN = 0xFF1B7351.toInt()
    private const val RED = 0xFFB03A4F.toInt()
    private const val TRACK = 0xFFE3E9F5.toInt()
    private const val LINE = 0xFFE6EBF5.toInt()
    private val AVATARS = intArrayOf(0xFF3A5FC8.toInt(), 0xFF7B61C9.toInt(), 0xFF1B9E77.toInt(), 0xFFE07A2F.toInt(), 0xFF2A9DB5.toInt(), 0xFFB03A4F.toInt())

    private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    const val SITE = "finanplus-web.github.io"

    private const val REGULAR = 400
    private const val SEMIBOLD = 600
    private const val BOLD = 700
    private const val EXTRABOLD = 800

    /** A fonte do app no peso pedido (Android 9+ tem pesos intermediários; antes, negrito ou normal). */
    private fun face(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else if (weight >= SEMIBOLD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    /** Paint novo para texto, sempre com alinhamento explícito. */
    private fun text(size: Float, weight: Int, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size; typeface = face(weight); this.color = color; textAlign = align
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

    private fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

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
        val cardW = W - 2 * PAD
        val inner = cardW - 2 * CARD_PAD

        // título em até 2 linhas, medido com o mesmo Paint que o desenha
        val titlePaint = text(60f, EXTRABOLD, INK)
        val title = wrap(titlePaint, e.name, inner, 2)

        val cardTop = 300f
        // alturas na mesma ordem em que o cartão é desenhado abaixo
        val headH = 570f + title.size * 70f
        val listH = st.people.size * ROW_H
        val cardH = headH + listH + CARD_PAD
        val footerH = 340f
        val h = (cardTop + cardH + footerH).toInt()

        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // ---- fundo: azul-claro com brilhos suaves (o "liquid glass lite" do app)
        c.drawColor(BG)
        fun glow(cx: Float, cy: Float, r: Float, color: Int, a: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(cx, cy, r, intArrayOf(alpha(color, a), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        glow(W * 0.95f, 40f, 700f, ACCENT, 90)
        glow(0f, h * 0.35f, 620f, 0xFFC9BBFF.toInt(), 110)
        glow(W * 0.9f, h * 0.75f, 600f, 0xFFA9E5CF.toInt(), 90)
        glow(W * 0.1f, h.toFloat(), 560f, ACCENT, 60)

        // ---- topo: ícone do app + nome
        val iconSize = 136f
        drawIcon(ctx, c, RectF(PAD, 78f, PAD + iconSize, 78f + iconSize))
        c.drawText("Finan+", PAD + iconSize + 32f, 150f, text(64f, EXTRABOLD, INK))
        c.drawText("Despesa compartilhada", PAD + iconSize + 34f, 198f, text(32f, REGULAR, MUTED))

        // ---- cartão branco
        val card = RectF(PAD, cardTop, PAD + cardW, cardTop + cardH)
        c.drawRoundRect(RectF(card.left, card.top + 14f, card.right, card.bottom + 14f), 56f, 56f, fill(Color.argb(36, 38, 62, 140))) // sombra suave
        c.drawRoundRect(card, 56f, 56f, fill(Color.argb(245, 255, 255, 255)))
        c.drawRoundRect(card, 56f, 56f, stroke(Color.WHITE, 3f))

        val x0 = card.left + CARD_PAD
        val x1 = card.right - CARD_PAD
        var y = card.top + CARD_PAD + 20f

        for (line in title) { y += 52f; c.drawText(line, x0, y, titlePaint); y += 18f }
        y += 44f
        val split = if (e.split == SplitMode.EQUAL) "dividida igualmente" else "valor de cada um"
        val meta = text(32f, REGULAR, MUTED)
        c.drawText(fit(meta, "${e.date.format(DMY)} · pago por ${e.payer?.name ?: "—"} · $split", inner), x0, y, meta)

        // total
        y += 40f
        y += 34f; c.drawText("TOTAL", x0, y, text(28f, BOLD, MUTED).apply { letterSpacing = 0.06f })
        y += 92f; c.drawText(Money.format(e.total), x0, y, text(92f, EXTRABOLD, ACCENT))

        // recebido / pendente / situação
        y += 40f
        val colW = inner / 3f
        fun stat(i: Int, label: String, value: String, color: Int, size: Float = 40f) {
            val x = x0 + i * colW
            c.drawText(label, x, y + 30f, text(28f, REGULAR, MUTED))
            val v = text(size, BOLD, color)
            c.drawText(fit(v, value, colW - 8f), x, y + 82f, v)
        }
        stat(0, "Recebido", Money.format(st.received), GREEN)
        stat(1, "Pendente", Money.format(st.pending), if (st.pending > 0) RED else GREEN)
        stat(2, "Situação", st.state.label, stateColor(st.state), 32f)
        y += 110f
        // barra do que já voltou
        c.drawRoundRect(RectF(x0, y, x1, y + 18f), 9f, 9f, fill(TRACK))
        if (st.toReceive > 0 && st.received > 0) {
            val f = (st.received.toFloat() / st.toReceive).coerceIn(0f, 1f)
            c.drawRoundRect(RectF(x0, y, x0 + (x1 - x0) * f, y + 18f), 9f, 9f, fill(GREEN))
        }
        y += 18f + 46f

        // ---- quem deve e quanto
        c.drawLine(x0, y, x1, y, stroke(LINE, 2f))
        y += 70f
        c.drawText("Quem deve e quanto", x0, y - 16f, text(40f, EXTRABOLD, INK))
        st.people.forEachIndexed { i, ps -> row(c, ps, i, x0, x1, y, st.toReceive, last = i == st.people.lastIndex); y += ROW_H }

        // ---- rodapé: marca, tudo centralizado (ícone, nome, frase e site, um embaixo do outro)
        val cx = W / 2f
        val small = 72f
        val iconTop = card.bottom + 60f
        drawIcon(ctx, c, RectF(cx - small / 2, iconTop, cx + small / 2, iconTop + small))
        c.drawText("Feito com Finan+", cx, iconTop + small + 50f, text(34f, EXTRABOLD, INK, Paint.Align.CENTER))
        c.drawText("Finanças pessoais livres e privadas", cx, iconTop + small + 90f, text(26f, REGULAR, MUTED, Paint.Align.CENTER))
        c.drawText(SITE, cx, iconTop + small + 142f, text(28f, BOLD, ACCENT, Paint.Align.CENTER))
        return bmp
    }

    /** Uma pessoa: inicial, nome, parte e quanto devolveu; à direita a situação e o que falta. [toReceive]: o que quem pagou recebe. */
    private fun row(c: Canvas, ps: PersonStatus, i: Int, x0: Float, x1: Float, top: Float, toReceive: Cents, last: Boolean) {
        val cy = top + ROW_H / 2f
        val nameX = x0 + 104f

        // avatar: círculo claro com a inicial centralizada (vertical pela altura real da letra)
        val color = AVATARS[i % AVATARS.size]
        c.drawCircle(x0 + 40f, cy, 40f, fill(alpha(color, 40)))
        val initial = ps.person.name.trim().take(1).uppercase().ifEmpty { "?" }
        val ip = text(36f, EXTRABOLD, color, Paint.Align.CENTER)
        val b = Rect().also { ip.getTextBounds(initial, 0, initial.length, it) }
        c.drawText(initial, x0 + 40f, cy + b.height() / 2f, ip)

        // situação (pílula) em cima e o valor embaixo, alinhados à direita
        val (label, fg) = when (ps.state) {
            PersonStatus.State.NONE -> "Pagou" to ACCENT
            PersonStatus.State.PAID -> "Quitado" to GREEN
            PersonStatus.State.PARTIAL -> "Pagou parte" to ACCENT
            PersonStatus.State.PENDING -> "Pendente" to RED
        }
        val pillText = text(26f, BOLD, fg, Paint.Align.CENTER)
        val pw = pillText.measureText(label) + 44f
        val pill = RectF(x1 - pw, cy - 52f, x1, cy - 8f)
        c.drawRoundRect(pill, 22f, 22f, fill(alpha(fg, 30)))
        val pb = Rect().also { pillText.getTextBounds(label, 0, label.length, it) }
        c.drawText(label, pill.centerX(), pill.centerY() + pb.height() / 2f, pillText)

        val amount = when {
            ps.isPayer -> "recebe ${Money.format(toReceive)}"
            ps.remaining > 0 -> "falta ${Money.format(ps.remaining)}"
            else -> Money.format(ps.owed)
        }
        val ap = text(32f, BOLD, if (!ps.isPayer && ps.remaining > 0) RED else INK, Paint.Align.RIGHT)
        c.drawText(amount, x1, cy + 36f, ap)
        val amountW = ap.measureText(amount)

        // nome (divide a linha com a pílula) e parte (divide com o valor)
        val np = text(36f, BOLD, INK)
        c.drawText(fit(np, ps.person.name, x1 - pw - 24f - nameX), nameX, cy - 10f, np)
        val sp = text(28f, REGULAR, MUTED)
        val sub = if (ps.isPayer) "Pagou a despesa · parte ${Money.format(ps.person.share)}" else "Parte ${Money.format(ps.owed)} · devolveu ${Money.format(ps.paid)}"
        c.drawText(fit(sp, sub, x1 - amountW - 28f - nameX), nameX, cy + 34f, sp)

        if (!last) c.drawLine(nameX, top + ROW_H - 1f, x1, top + ROW_H - 1f, stroke(LINE, 2f))
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
