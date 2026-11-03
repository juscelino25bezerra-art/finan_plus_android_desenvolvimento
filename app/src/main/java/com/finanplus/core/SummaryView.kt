// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * Regras de apresentação da aba Resumo (sem Android: testado em SummaryViewTest).
 *
 * Nada aqui muda quais lançamentos entram nas contas: receitas, despesas, reservas para meta e previsto
 * continuam vindo de [Finance] e [Period], como antes e como no Início. Aqui só ficam o selo do mês,
 * os percentuais que somam 100%, a comparação com o mês anterior, as categorias (até 5 + "Outras") e a evolução.
 * Ver RESUMO.md.
 */
object SummaryView {
    /** Estado do mês mostrado no selo do topo. [day]/[length] só valem em [Phase.IN_PROGRESS]. */
    enum class Phase { IN_PROGRESS, CLOSED, FUTURE }
    data class MonthStatus(val phase: Phase, val day: Int = 0, val length: Int = 0)

    /** Selo do mês: só para mês inteiro. Período livre (ou sem início/fim) não tem selo (null). */
    fun status(from: LocalDate?, to: LocalDate?, today: LocalDate): MonthStatus? {
        val ym = Period.fullMonth(from, to) ?: return null
        return when {
            ym == today.ym() -> MonthStatus(Phase.IN_PROGRESS, today.dayOfMonth, ym.lengthOfMonth())
            ym.isBefore(today.ym()) -> MonthStatus(Phase.CLOSED)
            else -> MonthStatus(Phase.FUTURE)
        }
    }

    /**
     * Percentuais inteiros que somam sempre 100 (método do maior resto): 126/226 e 100/226 viram 56% e 44%.
     * Valores negativos contam como zero. Sem total, tudo 0.
     */
    fun percents(values: List<Long>): List<Int> {
        val v = values.map { maxOf(0L, it) }
        val total = v.sum()
        if (total <= 0L) return v.map { 0 }
        val base = v.map { (it * 100 / total).toInt() }.toMutableList()
        var left = 100 - base.sum()
        // quem tem o maior resto da divisão ganha o ponto que sobrou (empate: o maior valor, depois o primeiro)
        val order = v.indices.sortedWith(compareByDescending<Int> { (v[it] * 100) % total }.thenByDescending { v[it] }.thenBy { it })
        var i = 0
        while (left > 0) { base[order[i % order.size]]++; left--; i++ }
        return base
    }

    /** Diferença em relação ao período anterior. */
    data class Delta(val amount: Cents) { val up get() = amount >= 0 }

    /**
     * "+R$ X vs. mês anterior": só existe se o período anterior tiver dados (alguma receita ou despesa realizada).
     * Sem comparação possível ([prev] null) ou sem dados, devolve null e a linha não aparece.
     */
    fun delta(cur: Cents, prev: Flow?, prevValue: Cents): Delta? =
        if (prev == null || (prev.income == 0L && prev.expense == 0L)) null else Delta(cur - prevValue)

    /** Próxima entrada pendente do período (a mais antiga; empate: a de maior valor). null = nada pendente. */
    fun nextIncome(list: List<Tx>): Due? = list
        .filter { it.kind == Kind.INCOME && !it.paid && it.isFlow && !it.isCard }
        .minWithOrNull(compareBy<Tx> { it.date }.thenByDescending { it.value })
        ?.let { Due(it.desc, it.value, it.date, it.category, it.id) }

    /**
     * Compras no cartão do período (já contam como despesa, na data da compra) e a data da fatura em que
     * entram: a mais próxima de hoje em diante; se todas já venceram, a última. null = sem compras no cartão.
     */
    data class CardSpend(val amount: Cents, val invoiceDue: LocalDate?)

    fun cardSpend(s: AppState, list: List<Tx>, today: LocalDate): CardSpend? {
        val buys = list.filter { it.isCard && it.isFlow && it.paid && it.kind == Kind.EXPENSE }
        if (buys.isEmpty()) return null
        val dues = buys.mapNotNull { t -> s.card(t.cardId)?.let { c -> Finance.invoiceDue(c, Finance.invoiceYm(c, t.date)) } }.distinct().sorted()
        val due = dues.firstOrNull { !it.isBefore(today) } ?: dues.lastOrNull()
        return CardSpend(buys.totalOf(), due)
    }

    /** Uma linha de "Despesas por categoria". [cards] = nomes dos cartões em que houve compras nessa categoria. */
    data class CatRow(val name: String, val value: Cents, val cards: List<String>, val percent: Int = 0)

    /** [top] = até [MAX_CATEGORIES] maiores; [others] = soma do resto ("Outras"), se houver; [all] = a lista completa. */
    data class Categories(val total: Cents, val top: List<CatRow>, val others: CatRow?, val othersCount: Int, val all: List<CatRow>)

    const val MAX_CATEGORIES = 5
    const val OTHERS = "Outras"
    const val NO_CATEGORY = "Sem categoria"

    /**
     * Despesas realizadas por categoria da compra (cartão só como subtítulo; sem categoria = "Sem categoria"),
     * da maior para a menor. Mesma regra de [Finance.categoryTotals]: pagamento de fatura e reserva para meta ficam fora.
     */
    fun categories(s: AppState, list: List<Tx>, max: Int = MAX_CATEGORIES): Categories {
        val exp = list.filter { it.kind == Kind.EXPENSE && it.paid && it.isFlow }
        // "Cartão de Crédito" e "Limite Especial" antigos aparecem como "Outros" (o dado não muda; REDESIGN-COERENCIA.md)
        val rows = exp.groupBy { TxForm.displayCategory(it.category.trim()).ifBlank { NO_CATEGORY } }.map { (name, g) ->
            CatRow(name, g.totalOf(), g.filter { it.isCard }.mapNotNull { s.card(it.cardId)?.name }.distinct().sorted())
        }.sortedWith(compareByDescending<CatRow> { it.value }.thenBy { it.name })
        val total = rows.sumOf { it.value }
        val pct = percents(rows.map { it.value })
        val all = rows.mapIndexed { i, r -> r.copy(percent = pct[i]) }
        val top = all.take(max)
        val rest = all.drop(max)
        val others = if (rest.isEmpty()) null else CatRow(OTHERS, rest.sumOf { it.value }, emptyList())
        return Categories(total, top, others, rest.size, all)
    }

    /** Um mês da evolução. [current] = o mês em andamento (mostra "até dia X"). */
    data class EvoMonth(val ym: YearMonth, val income: Cents, val expense: Cents, val goals: Cents, val current: Boolean)

    /**
     * Últimos [n] meses até o de [today], só os que têm lançamentos (receita ou despesa realizada, ou reserva
     * para meta): mês sem dados não vira barra vazia.
     */
    fun evolution(s: AppState, today: LocalDate, n: Int = 6): List<EvoMonth> =
        (n - 1 downTo 0).mapNotNull { i ->
            val ym = today.ym().minusMonths(i.toLong())
            val txs = s.txs.filter { it.date.ym() == ym }
            val fl = Finance.flow(txs)
            val g = Finance.goalMoves(txs)
            if (fl.income == 0L && fl.expense == 0L && g.saved == 0L && g.withdrawn == 0L) null
            else EvoMonth(ym, fl.income, fl.expense, g.net, ym == today.ym())
        }

    /** Valor curto sobre as barras, sem "R$": "950", "1,2 mil", "3,4 mi". */
    fun short(c: Cents): String = com.finanplus.core.report.Reports.compact(c).replace("R$ ", "")
}
