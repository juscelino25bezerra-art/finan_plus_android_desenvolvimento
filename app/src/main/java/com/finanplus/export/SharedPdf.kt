// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.export

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.finanplus.core.Cents
import com.finanplus.core.Money
import com.finanplus.core.PersonStatus
import com.finanplus.core.Shared
import com.finanplus.core.SharedExpense
import com.finanplus.core.SharedSummary
import com.finanplus.core.SplitMode
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Comprovante de uma despesa compartilhada em PDF (A4 retrato), com o PdfDocument do Android,
 * nas mesmas cores do relatório ([PdfReport]): dados da despesa, quem pagou, a parte de cada
 * participante, o que cada um já devolveu, a situação e os pagamentos registrados.
 */
object SharedPdf {
    private const val W = 595f
    private const val H = 842f
    private const val M = 40f
    private const val CW = W - 2 * M
    private const val BOTTOM = H - 48f

    private const val INK = 0xFF182238.toInt()
    private const val MUTED = 0xFF5B6579.toInt()
    private const val ACCENT = 0xFF3A5FC8.toInt()
    private const val GREEN = 0xFF1B7351.toInt()
    private const val RED = 0xFFB03A4F.toInt()
    private const val CARD = 0xFFF3F6FD.toInt()
    private const val LINE = 0xFFDDE3EE.toInt()

    private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
    private fun LocalDate.br() = format(DMY)
    private fun m(c: Cents) = Money.format(c)

    fun write(out: OutputStream, e: SharedExpense, appVersion: String = "") {
        val doc = PdfDocument()
        try {
            Pen(doc, appVersion).apply { layout(this, e); finish() }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Pen(val doc: PdfDocument, val version: String) {
        var pageNo = 0
        var y = 0f
        var page: PdfDocument.Page? = null
        val c get() = page!!.canvas
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            y = M
        }

        fun finishPage() {
            val pg = page ?: return
            text("Gerado pelo Finan+${if (version.isNotEmpty()) " $version" else ""} em ${LocalDateTime.now().format(STAMP)} · página $pageNo", M, H - 24f, 8f, MUTED)
            doc.finishPage(pg)
            page = null
        }

        fun finish() = finishPage()

        /** Garante [h] pontos livres na página (senão começa outra). */
        fun need(h: Float) { if (page == null || y + h > BOTTOM) newPage() }

        fun text(s: String, x: Float, baseline: Float, size: Float, color: Int, isBold: Boolean = false, right: Boolean = false) {
            p.reset(); p.isAntiAlias = true; p.textSize = size; p.color = color
            p.typeface = if (isBold) bold else Typeface.DEFAULT
            val w = p.measureText(s)
            c.drawText(s, if (right) x - w else x, baseline, p)
        }

        fun width(s: String, size: Float, isBold: Boolean = false): Float {
            p.reset(); p.textSize = size; p.typeface = if (isBold) bold else Typeface.DEFAULT
            return p.measureText(s)
        }

        /** Corta [s] com "…" para caber em [max] pontos. */
        fun fit(s: String, max: Float, size: Float, isBold: Boolean = false): String {
            if (width(s, size, isBold) <= max) return s
            var t = s
            while (t.isNotEmpty() && width("$t…", size, isBold) > max) t = t.dropLast(1)
            return "$t…"
        }

        /** Quebra [s] em linhas de até [max] pontos. */
        fun wrap(s: String, max: Float, size: Float): List<String> {
            val lines = ArrayList<String>()
            for (para in s.split('\n')) {
                var cur = ""
                for (word in para.split(' ')) {
                    val next = if (cur.isEmpty()) word else "$cur $word"
                    if (width(next, size) <= max || cur.isEmpty()) cur = next else { lines.add(cur); cur = word }
                }
                lines.add(cur)
            }
            return lines
        }

        fun box(top: Float, h: Float, color: Int) {
            p.reset(); p.isAntiAlias = true; p.color = color
            c.drawRoundRect(RectF(M, top, W - M, top + h), 10f, 10f, p)
        }

        fun rule(at: Float) {
            p.reset(); p.color = LINE; p.strokeWidth = 0.8f
            c.drawLine(M, at, W - M, at, p)
        }
    }

    private fun layout(pen: Pen, e: SharedExpense) = with(pen) {
        val st = Shared.summary(e)
        newPage()

        // ---- título
        text("COMPROVANTE DE DESPESA COMPARTILHADA", M, y + 10f, 9f, ACCENT, isBold = true)
        y += 34f
        for (line in wrap(e.name, CW, 22f)) { text(line, M, y, 22f, INK, isBold = true); y += 26f }
        text("Data da despesa: ${e.date.br()}", M, y, 11f, MUTED)
        y += 22f

        // ---- dados principais
        val boxTop = y
        box(boxTop, 92f, CARD)
        val col = CW / 3f
        fun stat(i: Int, label: String, value: String, color: Int) {
            val x = M + 14f + i * col
            text(label, x, boxTop + 24f, 9f, MUTED)
            text(fit(value, col - 20f, 15f, true), x, boxTop + 44f, 15f, color, isBold = true)
        }
        stat(0, "Valor total", m(e.total), INK)
        stat(1, "Pago por", e.payer?.name ?: "—", INK)
        stat(2, "Divisão", if (e.split == SplitMode.EQUAL) "Igual (${e.people.size})" else "Personalizada", INK)
        text("Recebido: ${m(st.received)}", M + 14f, boxTop + 72f, 11f, GREEN, isBold = true)
        text("Pendente: ${m(st.pending)}", M + 14f + col, boxTop + 72f, 11f, if (st.pending > 0) RED else GREEN, isBold = true)
        text("Situação: ${st.state.label}", M + 14f + 2 * col, boxTop + 72f, 11f, INK, isBold = true)
        y = boxTop + 92f + 24f

        // ---- participantes
        text("Participantes", M, y, 13f, INK, isBold = true); y += 16f
        val cols = floatArrayOf(M, M + 200f, M + 290f, M + 380f, W - M)
        fun header() {
            text("Nome", cols[0], y, 9f, MUTED, isBold = true)
            text("Parte", cols[2] - 8f, y, 9f, MUTED, isBold = true, right = true)
            text("Devolveu", cols[3] - 8f, y, 9f, MUTED, isBold = true, right = true)
            text("Falta", cols[4] - 92f, y, 9f, MUTED, isBold = true, right = true)
            text("Situação", cols[4], y, 9f, MUTED, isBold = true, right = true)
            y += 6f; rule(y); y += 14f
        }
        header()
        for (ps in st.people) {
            if (y + 20f > BOTTOM) { newPage(); header() }
            text(fit(ps.person.name + if (ps.isPayer) " (pagou)" else "", cols[1] - cols[0] - 8f, 10.5f, ps.isPayer), cols[0], y, 10.5f, INK, isBold = ps.isPayer)
            text(m(ps.person.share), cols[2] - 8f, y, 10.5f, INK, right = true)
            text(if (ps.isPayer) "—" else m(ps.paid), cols[3] - 8f, y, 10.5f, GREEN, right = true)
            text(if (ps.isPayer) "—" else m(ps.remaining), cols[4] - 92f, y, 10.5f, if (ps.remaining > 0) RED else INK, right = true)
            val stColor = when (ps.state) { PersonStatus.State.PAID -> GREEN; PersonStatus.State.PENDING -> RED; else -> MUTED }
            text(ps.state.label, cols[4], y, 9.5f, stColor, isBold = true, right = true)
            y += 6f; rule(y); y += 14f
        }
        y += 8f

        // ---- quem deve a quem
        val payer = e.payer?.name ?: "quem pagou"
        val debtors = st.people.filter { it.remaining > 0 }
        need(30f + 16f * maxOf(1, debtors.size))
        text("Quem deve e quanto", M, y, 13f, INK, isBold = true); y += 18f
        if (debtors.isEmpty()) { text("Ninguém deve nada: a despesa está quitada.", M, y, 10.5f, GREEN); y += 16f }
        for (d in debtors) {
            need(16f)
            text("${d.person.name} deve ${m(d.remaining)} a $payer", M, y, 10.5f, INK); y += 16f
        }
        y += 6f
        need(20f)
        text("Total a receber: ${m(st.toReceive)}   ·   Recebido: ${m(st.received)}   ·   Pendente: ${m(st.pending)}", M, y, 10.5f, INK, isBold = true)
        y += 26f

        // ---- pagamentos registrados
        if (e.payments.isNotEmpty()) {
            need(40f)
            text("Pagamentos registrados", M, y, 13f, INK, isBold = true); y += 18f
            for (pay in e.payments.sortedWith(compareBy({ it.date }, { it.id }))) {
                need(16f)
                text("${pay.date.br()}  ·  ${e.person(pay.personId)?.name ?: "Participante"}", M, y, 10.5f, INK)
                text(m(pay.value), W - M, y, 10.5f, GREEN, isBold = true, right = true)
                y += 16f
            }
            y += 10f
        }

        // ---- observação
        if (e.note.isNotBlank()) {
            need(40f)
            text("Observação", M, y, 13f, INK, isBold = true); y += 18f
            for (line in wrap(e.note, CW, 10.5f)) { need(15f); text(line, M, y, 10.5f, INK); y += 15f }
            y += 10f
        }
        if (e.receipt.isNotEmpty()) { need(16f); text("Há uma nota fiscal ou comprovante anexado a esta despesa no Finan+.", M, y, 9.5f, MUTED); y += 16f }
    }
}
