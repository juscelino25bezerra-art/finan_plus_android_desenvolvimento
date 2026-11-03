// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Dados da tela Meu dinheiro (MEU-DINHEIRO.md). Só organiza números que o app já calcula:
 * saldo das contas = [Finance.accountBalance] (o mesmo do "Saldo atual" do Início), fatura e limite =
 * [Finance.cardStatus], guardado = [Goal.saved]. Nenhuma regra nova de cálculo.
 */
object MoneyView {
    /** Faixa da barra de uso do limite. */
    enum class Band { NORMAL, ALERT, CRITICAL }

    data class CardRow(
        val card: Card,
        /** fatura aberta mostrada no card (a mesma do "Pagar fatura"); null = sem fatura em aberto */
        val invoice: Invoice?,
        val used: Cents,
        val available: Cents,
        /** % do limite usado, arredondado para baixo (0 quando o limite é zero) */
        val pct: Int,
        val band: Band,
        /** dias até o vencimento da fatura aberta (negativo = vencida); null sem fatura */
        val daysToDue: Long?,
    ) {
        val open: Cents get() = invoice?.open ?: 0L
        /** destacar a linha do vencimento: faltam 15 dias ou menos */
        val dueSoon: Boolean get() = daysToDue != null && daysToDue <= 15
        /** linha de alerta visível com a seção recolhida: vence em até 7 dias ou uso >= 90% */
        val alert: Boolean get() = (daysToDue != null && daysToDue <= 7) || pct >= 90
    }

    data class Overview(val inAccounts: Cents, val inGoals: Cents, val openInvoices: Cents)

    /** Escolhe singular ou plural (pt-BR: só 1 é singular; "0 contas", "1 conta", "3 cartões"). */
    fun plural(n: Int, one: String, many: String): String = if (n == 1) one else many

    fun pct(used: Cents, limit: Cents): Int =
        if (limit <= 0) 0 else ((used.coerceAtLeast(0L) * 100) / limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** até 69%: primária; 70% a 89%: alerta; 90% ou mais: alerta com ícone e negrito. */
    fun band(pct: Int): Band = when { pct >= 90 -> Band.CRITICAL; pct >= 70 -> Band.ALERT; else -> Band.NORMAL }

    fun cardRow(s: AppState, card: Card, today: LocalDate): CardRow {
        val st = Finance.cardStatus(s, card, today)
        val inv = st.current?.takeIf { it.open > 0 }
        val pct = pct(st.used, card.limit)
        return CardRow(card, inv, st.used, st.available, pct, band(pct), inv?.let { ChronoUnit.DAYS.between(today, it.due) })
    }

    /** Cartões pelo vencimento da fatura aberta mais próxima; os sem fatura vão para o fim (pelo nome). */
    fun cards(s: AppState, today: LocalDate): List<CardRow> =
        s.cards.map { cardRow(s, it, today) }
            .sortedWith(compareBy<CardRow> { it.invoice == null }.thenBy { it.invoice?.due }.thenBy { it.card.name.lowercase() })

    fun overview(s: AppState, today: LocalDate): Overview = Overview(
        inAccounts = s.accounts.sumOf { Finance.accountBalance(s, it) },
        inGoals = s.goals.sumOf { it.saved },
        openInvoices = cards(s, today).sumOf { it.open },
    )

    /** Quanto saiu desta conta para metas e ainda não voltou (lançamentos de reserva da conta). */
    fun reservedFrom(s: AppState, account: Account): Cents =
        s.txs.filter { it.isGoal && it.accountId == account.id }
            .sumOf { if (it.kind == Kind.EXPENSE) it.value else -it.value }
            .coerceAtLeast(0L)

    /** Sufixo do vencimento quando faltam 15 dias ou menos: "· daqui a 12 dias", "· amanhã", "· hoje", "· vencida". */
    fun dueSuffix(days: Long?): String? = when {
        days == null || days > 15 -> null
        days < 0 -> "· vencida"
        days == 0L -> "· vence hoje"
        days == 1L -> "· amanhã"
        else -> "· daqui a $days dias"
    }

    /** Resumo de Metas: "Notebook · R$ 100,00 guardado" ou "2 metas · R$ X guardados". */
    fun goalsSummary(goals: List<Goal>, hide: Boolean): String? {
        if (goals.isEmpty()) return null
        val total = if (hide) "R$ ••••" else Money.format(goals.sumOf { it.saved })
        return if (goals.size == 1) "${goals[0].name} · $total guardado" else "${goals.size} metas · $total guardados"
    }

    /** Resumo de Limites: "3 limites · 1 perto do teto" (perto do teto = 80% ou mais, como na lista de limites). */
    fun limitsSummary(limits: Map<String, Cents>, usage: Map<String, Cents>): String? {
        if (limits.isEmpty()) return null
        val near = limits.count { (c, l) -> l > 0 && (usage[c] ?: 0L) * 100 >= l * 80 }
        return "${limits.size} ${plural(limits.size, "limite", "limites")} · $near perto do teto"
    }

    /** Cabeçalho recolhido de Contas: "Contas · R$ 266,88" (+ "· 2 contas" com mais de uma). */
    fun accountsCollapsed(s: AppState, hide: Boolean): String {
        val total = if (hide) "R$ ••••" else Money.format(s.accounts.sumOf { Finance.accountBalance(s, it) })
        return "Contas · $total" + if (s.accounts.size > 1) " · ${s.accounts.size} contas" else ""
    }

    /** Cabeçalho recolhido de Cartões: "Cartões · 3 · R$ 979,39 em faturas". */
    fun cardsCollapsed(s: AppState, today: LocalDate, hide: Boolean): String {
        val total = if (hide) "R$ ••••" else Money.format(cards(s, today).sumOf { it.open })
        return "Cartões · ${s.cards.size} · $total em faturas"
    }

    /** Linha de alerta: "Sofisa 6333 · vence 15/11 · 98% do limite" (só as partes que se aplicam). */
    fun alertLine(r: CardRow, hide: Boolean): String = buildList {
        add(r.card.name)
        if (r.invoice != null && r.daysToDue != null && r.daysToDue <= 7) add(if (r.daysToDue < 0) "vencida em ${dm(r.invoice.due)}" else "vence ${dm(r.invoice.due)}")
        if (r.pct >= 90) add(if (hide) "perto do limite" else "${r.pct}% do limite")
    }.joinToString(" · ")

    /** Descrição da barra para leitores de tela: "Sofisa 6333, 98% do limite usado, disponível R$ 11,49". */
    fun barDescription(r: CardRow, hide: Boolean): String =
        if (hide) "${r.card.name}, uso do limite oculto" else "${r.card.name}, ${r.pct}% do limite usado, disponível ${Money.format(r.available)}"

    fun dm(d: LocalDate): String = String.format(java.util.Locale.ROOT, "%02d/%02d", d.dayOfMonth, d.monthValue)
}
