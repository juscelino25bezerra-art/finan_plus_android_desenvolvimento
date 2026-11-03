// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import com.finanplus.core.assist.Text
import java.time.LocalDate

/**
 * Dívidas (Meu dinheiro › Dívidas): empréstimos e financiamentos são lançamentos da categoria "Dívidas"
 * (o antigo "Empréstimo"), normalmente em parcelas. Nada novo é gravado: as dívidas são montadas a partir
 * dos lançamentos que já existem, agrupando as parcelas do mesmo parcelamento (ou a mesma descrição).
 */
object Debts {
    const val CATEGORY = "Dívidas"
    private val names = setOf("dividas", "divida", "emprestimo", "emprestimos", "financiamento", "financiamentos")

    fun isDebtCategory(c: String): Boolean = Text.fold(c).trim() in names

    data class Debt(
        val name: String,
        val parcels: List<Tx>,
        /** próxima parcela a pagar (a mais antiga pendente) */
        val next: Tx?,
        val pendingCount: Int,
        val pendingTotal: Cents,
        val paidCount: Int,
        /** total de parcelas quando o lançamento diz (ex.: 3/10), senão o número de lançamentos */
        val totalCount: Int,
    ) { val done: Boolean get() = pendingCount == 0 }

    private val suffix = Regex("\\s*\\((\\d+)/(\\d+)\\)\\s*$")

    fun baseName(desc: String): String = desc.replace(suffix, "").trim()

    fun list(s: AppState): List<Debt> {
        val txs = s.txs.filter { it.kind == Kind.EXPENSE && it.isFlow && !it.isCard && isDebtCategory(it.category) }
        val groups = txs.groupBy { if (it.groupId.isNotEmpty()) "g:" + it.groupId else "d:" + Text.fold(baseName(it.desc)) }
        return groups.values.map { l ->
            val sorted = l.sortedBy { it.date }
            val pending = sorted.filter { !it.paid }
            Debt(
                name = baseName(sorted.last().desc).ifBlank { CATEGORY },
                parcels = sorted,
                next = pending.firstOrNull(),
                pendingCount = pending.size,
                pendingTotal = pending.sumOf { it.value },
                paidCount = sorted.count { it.paid },
                totalCount = maxOf(sorted.size, sorted.maxOfOrNull { it.parcelTotal } ?: 0),
            )
        }.sortedWith(compareBy<Debt> { it.done }.thenBy { it.next?.date ?: LocalDate.MAX }.thenBy { it.name })
    }

    /** "1 dívida · parcela de R$ 121,40 em 13/11"; sem dívidas abertas: null. */
    fun summary(s: AppState, hide: Boolean): String? {
        val open = list(s).filter { !it.done }
        if (open.isEmpty()) return null
        val n = Words.count(open.size, "dívida", "dívidas")
        val next = open.mapNotNull { it.next }.minByOrNull { it.date } ?: return n
        val v = if (hide) "R$ ••••" else Money.format(next.value)
        val d = String.format(java.util.Locale.ROOT, "%02d/%02d", next.date.dayOfMonth, next.date.monthValue)
        return if (open.size == 1) "$n · parcela de $v em $d" else "$n · próxima parcela de $v em $d"
    }
}
