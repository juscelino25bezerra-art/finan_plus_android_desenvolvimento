// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Resumo: selo do mês, percentuais, comparação, Receitas zeradas, categorias e evolução (SummaryView). */
class SummaryViewTest {
    private val today = LocalDate.parse("2026-11-02")
    private fun d(s: String) = LocalDate.parse(s)
    private fun tx(id: String, kind: Kind, v: Long, date: String, paid: Boolean = true, cat: String = "Outros", card: String = "", goal: String = "") =
        Tx(id, kind, v, d(date), id, if (goal.isNotEmpty()) AppState.GOAL_CAT else cat, paid || card.isNotEmpty() || goal.isNotEmpty(), "main", card, goalId = goal)

    // ---- percentuais que somam 100
    @Test fun percentuaisSomam100() {
        assertEquals(listOf(56, 44), SummaryView.percents(listOf(12600, 10000)))     // 126/226 e 100/226
        assertEquals(listOf(34, 33, 33), SummaryView.percents(listOf(100, 100, 100)))
        assertEquals(listOf(100), SummaryView.percents(listOf(777)))
        assertEquals(listOf(0, 0), SummaryView.percents(listOf(0, 0)))
        for (v in listOf(listOf(1L, 1L, 1L, 1L, 1L, 1L, 1L), listOf(5L, 3L, 2L), listOf(999L, 1L), listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)))
            assertEquals(100, SummaryView.percents(v).sum())
    }

    // ---- comparação com o mês anterior
    @Test fun comparacaoSoComDadosNoMesAnterior() {
        val prev = Flow(income = 100000, expense = 40000)
        assertEquals(SummaryView.Delta(20000), SummaryView.delta(120000, prev, prev.income))
        assertEquals(SummaryView.Delta(-10000), SummaryView.delta(30000, prev, prev.expense))
        assertNull(SummaryView.delta(30000, Flow(0, 0), 0))       // mês anterior sem dados: a linha não aparece
        assertNull(SummaryView.delta(30000, null, 0))             // sem comparação possível
        assertTrue(SummaryView.delta(5, prev, 5)!!.up)           // igual: "+R$ 0,00"
    }

    // ---- selo do mês
    @Test fun seloDoMes() {
        assertEquals(SummaryView.MonthStatus(SummaryView.Phase.IN_PROGRESS, 2, 30), SummaryView.status(d("2026-11-01"), d("2026-11-30"), today))
        assertEquals(SummaryView.Phase.CLOSED, SummaryView.status(d("2026-10-01"), d("2026-10-31"), today)!!.phase)
        assertEquals(SummaryView.Phase.FUTURE, SummaryView.status(d("2026-12-01"), d("2026-12-31"), today)!!.phase)
        assertNull(SummaryView.status(d("2026-11-01"), d("2026-11-15"), today))     // período livre: sem selo
    }

    // ---- Receitas zeradas
    @Test fun receitasZeradasMostramAProximaEntradaOuNadaPendente() {
        val semRecebido = listOf(
            tx("c", Kind.EXPENSE, 12600, "2026-11-01", card = "itau"),
            tx("adi", Kind.INCOME, 121362, "2026-11-13", paid = false),
            tx("sal", Kind.INCOME, 250000, "2026-11-05", paid = false),
        )
        assertEquals(0L, Finance.flow(semRecebido).income)
        val next = assertNotNull(SummaryView.nextIncome(semRecebido))
        assertEquals(d("2026-11-05"), next.date)        // a mais próxima, não a maior
        assertEquals(250000L, next.amount)
        assertNull(SummaryView.nextIncome(semRecebido.filter { it.kind == Kind.EXPENSE }))   // "Nada pendente"
    }

    // ---- compras no cartão e fatura
    @Test fun inclusNoCartaoComDataDaFatura() {
        val s = AppState(cards = listOf(Card("itau", "Itaú", 200000, close = 24, due = 1)),
            txs = listOf(tx("a", Kind.EXPENSE, 12600, "2026-11-01", card = "itau"), tx("b", Kind.EXPENSE, 5000, "2026-11-02", card = "itau"), tx("x", Kind.EXPENSE, 999, "2026-11-02")))
        val c = assertNotNull(SummaryView.cardSpend(s, s.txs, today))
        assertEquals(17600L, c.amount)
        assertEquals(d("2026-12-01"), c.invoiceDue)     // fecha dia 24, vence dia 1º do mês seguinte
        assertNull(SummaryView.cardSpend(s, s.txs.filter { !it.isCard }, today))   // sem compras no cartão: linha omitida
    }

    // ---- categorias
    @Test fun semCategoriaECartaoComoSubtitulo() {
        val s = AppState(cards = listOf(Card("itau", "Itaú", 200000, 24, 1)), txs = listOf(
            tx("a", Kind.EXPENSE, 10000, "2026-11-01", cat = "Mercado", card = "itau"),
            tx("b", Kind.EXPENSE, 5000, "2026-11-01", cat = ""),
            tx("c", Kind.EXPENSE, 2000, "2026-11-01", cat = "   "),
            tx("meta", Kind.EXPENSE, 9999, "2026-11-01", goal = "g1"),                  // reserva: fora
            tx("pend", Kind.EXPENSE, 7777, "2026-11-09", paid = false, cat = "Luz"),    // pendente: fora
        ))
        val c = SummaryView.categories(s, s.txs)
        assertEquals(listOf("Mercado", SummaryView.NO_CATEGORY), c.all.map { it.name })
        assertEquals(7000L, c.all[1].value)             // vazia e só espaços juntam em "Sem categoria"
        assertEquals(listOf("Itaú"), c.all[0].cards)
        assertEquals(17000L, c.total)
        assertNull(c.others)
    }

    @Test fun cincoCategoriasMaisOutras() {
        val cats = listOf("A" to 700L, "B" to 600L, "C" to 500L, "D" to 400L, "E" to 300L, "F" to 200L, "G" to 100L)
        val s = AppState(txs = cats.mapIndexed { i, (n, v) -> tx("t$i", Kind.EXPENSE, v, "2026-11-01", cat = n) })
        val c = SummaryView.categories(s, s.txs)
        assertEquals(listOf("A", "B", "C", "D", "E"), c.top.map { it.name })
        assertEquals(SummaryView.OTHERS, c.others!!.name)
        assertEquals(300L, c.others!!.value)            // F + G
        assertEquals(2, c.othersCount)
        assertEquals(7, c.all.size)                     // "Outras" abre a lista completa
        assertEquals(100, c.all.sumOf { it.percent })
        // exatamente 5: sem "Outras"
        assertNull(SummaryView.categories(s, s.txs.take(5)).others)
    }

    // ---- evolução
    @Test fun evolucaoSoComMesesComLancamentos() {
        val s = AppState(txs = listOf(
            tx("s", Kind.INCOME, 300000, "2026-09-05"), tx("g", Kind.EXPENSE, 10000, "2026-09-06", goal = "g1"),
            tx("o", Kind.EXPENSE, 50000, "2026-07-10"),
            tx("n", Kind.INCOME, 1000, "2026-11-01"),
            tx("pend", Kind.EXPENSE, 9000, "2026-10-20", paid = false),                  // só pendente: não é mês com dados
        ))
        val e = SummaryView.evolution(s, today)
        assertEquals(listOf("2026-07", "2026-09", "2026-11"), e.map { it.ym.toString() })   // agosto e outubro não viram barras vazias
        assertEquals(listOf(false, false, true), e.map { it.current })
        assertEquals(10000L, e[1].goals)
    }

    @Test fun valorCurtoSobreAsBarras() {
        assertEquals("950", SummaryView.short(95000))
        assertEquals("1,2 mil", SummaryView.short(120000))
    }
}
