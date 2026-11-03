// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth

/** Algo que entra ou sai das contas numa data (conta a pagar, fatura ou valor a receber), ainda pendente. */
data class Due(val title: String, val amount: Cents, val date: LocalDate, val category: String, val refId: String, val invoice: Boolean = false)

/**
 * Aviso de risco do Assistente no Início: no próximo dia com contas a pagar, o saldo de hoje não cobre
 * as contas pendentes até esse dia.
 * [bills] = contas e faturas pendentes até [day] (inclui as atrasadas); [missing] = [bills] − [balance];
 * [income] = o maior valor a receber pendente até [day], se houver (o que "salvaria" o dia, se cair a tempo).
 */
data class RiskAdvice(val day: LocalDate, val bills: Cents, val balance: Cents, val missing: Cents, val income: Due?) {
    /** o recebimento cai no mesmo dia das contas (senão, antes) */
    val sameDay: Boolean get() = income != null && !income.date.isBefore(day)
}

/**
 * Regras da tela Início que cruzam informações (sem Android: testado em JVM, HomeAdviceTest).
 * Nada aqui muda os números da tela: saldo, previsto, "falta receber" e "falta pagar" vêm do glossário
 * único em core/Totals.kt. Ver INICIO.md.
 */
object HomeAdvice {
    /**
     * Contas a pagar pendentes do mês [ym]: despesas fora do cartão ainda não pagas e faturas em aberto
     * que vencem no mês (no mês atual, também as atrasadas): as mesmas que formam o "A pagar" de [Totals].
     */
    fun monthBills(s: AppState, ym: YearMonth, today: LocalDate): List<Due> {
        // no mês atual, as atrasadas também (são as mesmas do "A pagar" de core/Totals.kt)
        val current = ym == today.ym()
        val bills = s.txs.filter { !it.paid && !it.isCard && it.kind == Kind.EXPENSE && (it.date.ym() == ym || (current && it.date.isBefore(ym.atDay(1)))) }
            .map { Due(it.desc, it.value, it.date, it.category, it.id) }
        val invs = if (current) s.cards.flatMap { c ->
            Finance.cardStatus(s, c, today).invoices.filter { it.open > 0 && !it.due.isAfter(ym.atEndOfMonth()) }
                .map { Due("Fatura ${c.name}", it.open, it.due, AppState.CARD_PAYMENT_CAT, c.id, invoice = true) }
        } else MonthCalendar.invoicesDue(s, ym, today).map { Due("Fatura ${it.cardName}", it.amount, it.due, AppState.CARD_PAYMENT_CAT, it.cardId, invoice = true) }
        return (bills + invs).sortedWith(compareBy<Due> { it.date }.thenBy { it.title })
    }

    /** Valores a receber pendentes do mês [ym] (os mesmos do "Falta receber"), do mais antigo para o mais novo. */
    fun monthIncome(s: AppState, ym: YearMonth, today: LocalDate? = null): List<Due> =
        s.txs.filter { !it.paid && !it.isCard && it.kind == Kind.INCOME && (it.date.ym() == ym || (today != null && ym == today.ym() && it.date.isBefore(ym.atDay(1)))) }
            .map { Due(it.desc, it.value, it.date, it.category, it.id) }
            .sortedWith(compareBy<Due> { it.date }.thenByDescending { it.amount })

    /** Próximo recebimento pendente do mês, para o cartão "Falta receber" (null = tudo recebido). */
    fun nextIncome(s: AppState, today: LocalDate): Due? = monthIncome(s, today.ym(), today).firstOrNull()

    /**
     * Risco no próximo dia com contas: some as contas pendentes até esse dia (atrasadas contam como de hoje);
     * se o saldo de hoje não cobre, devolve o aviso. null = sem risco ou sem contas pendentes no mês.
     */
    fun risk(s: AppState, today: LocalDate): RiskAdvice? {
        val ym = today.ym()
        val bills = monthBills(s, ym, today)
        if (bills.isEmpty()) return null
        fun eff(d: LocalDate) = if (d.isBefore(today)) today else d
        val day = bills.minOf { eff(it.date) }
        val c = bills.filter { !eff(it.date).isAfter(day) }.sumOf { it.amount }
        val balance = Finance.currentBalance(s)
        if (balance >= c) return null
        val income = monthIncome(s, ym, today).filter { !eff(it.date).isAfter(day) }
            .maxWithOrNull(compareBy<Due> { it.amount }.thenBy { it.date })
            ?.let { it.copy(date = eff(it.date)) }
        return RiskAdvice(day, c, balance, c - balance, income)
    }

    /**
     * Próximos vencimentos do Início, agrupados por data. [nextMonth] = a data é de um mês seguinte
     * (não entra no "Falta pagar" do mês: aparece em cor neutra).
     */
    data class DueGroup(val date: LocalDate, val nextMonth: Boolean, val overdue: Boolean, val items: List<Due>)

    fun upcoming(s: AppState, today: LocalDate, limit: Int = 4): List<DueGroup> {
        val bills = s.txs.filter { !it.paid && !it.isCard && it.kind == Kind.EXPENSE }
            .map { Due(it.desc, it.value, it.date, it.category, it.id) }
        val invs = s.cards.flatMap { c ->
            Finance.cardStatus(s, c, today).invoices.filter { it.open > 0 }
                .map { Due("Fatura ${c.name}", it.open, it.due, AppState.CARD_PAYMENT_CAT, c.id, invoice = true) }
        }
        return (bills + invs).sortedWith(compareBy<Due> { it.date }.thenBy { it.title }).take(limit)
            .groupBy { it.date }
            .map { (d, l) -> DueGroup(d, d.ym().isAfter(today.ym()), d.isBefore(today), l) }
    }

    /** Linhas do resumo que só repetem o que os cartões de cima já mostram ("faltam … a pagar", "a receber"). */
    fun repeatsCards(line: String): Boolean = line.startsWith("Ainda faltam ") || line.startsWith("A receber neste mês")
}
