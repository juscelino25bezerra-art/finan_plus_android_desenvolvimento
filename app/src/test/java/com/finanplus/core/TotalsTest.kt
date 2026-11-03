// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/** Glossário único (Totals): os mesmos números em todas as telas, sem descontar a meta duas vezes. */
class TotalsTest {
    private val today = LocalDate.parse("2026-11-02")
    private fun d(s: String) = LocalDate.parse(s)

    /**
     * O caso da proposta: saldo R$ 166,88 depois de guardar R$ 100,00 no Notebook, a receber R$ 3.413,62,
     * a pagar R$ 121,40 (Mercado Pago) + fatura de R$ 488,51.
     */
    private fun state(monthly: Long = 0): AppState = AppState(
        accounts = listOf(Account("main", "Conta principal", 26688)),
        cards = listOf(Card("sofisa", "Sofisa", 50000, close = 5, due = 15)),
        goals = listOf(Goal("g1", "Notebook", 800000, 10000, d("2028-12-31"), monthly)),
        txs = listOf(
            Tx("meta", Kind.EXPENSE, 10000, today, "Meta › Notebook", AppState.GOAL_CAT, true, "main", goalId = "g1"),
            Tx("compra", Kind.EXPENSE, 48851, d("2026-10-20"), "Compras", "Outros", true, "main", "sofisa"),
            Tx("mp", Kind.EXPENSE, 12140, d("2026-11-13"), "Mercado Pago", "Dívidas", false, "main"),
            Tx("adi", Kind.INCOME, 121362, d("2026-11-13"), "Adiantamento", "Salário", false, "main"),
            Tx("sal", Kind.INCOME, 80000, d("2026-11-30"), "Salário", "Salário", false, "main"),
            Tx("13", Kind.INCOME, 140000, d("2026-11-30"), "13º salário", "13º salário", false, "main"),
        ),
    )

    @Test fun previstoNaoDescontaAMetaDuasVezes() {
        val m = Totals.month(state(), YearMonth.of(2026, 11), today)
        assertEquals(16688L, m.opening)
        assertEquals(341362L, m.toReceive)
        assertEquals(60991L, m.toPay)
        assertEquals(0L, m.goalsPlanned)
        assertEquals(10000L, m.goalsSaved)
        assertEquals(297059L, m.closing) // R$ 2.970,59 (a proposta mostrava 2.870,59, com a meta descontada de novo)
        assertEquals(Totals.forecastAt(state(), d("2026-11-30"), today), m.closing)
    }

    @Test fun aporteMensalPlanejadoDescontaSoOQueFalta() {
        // contribuição planejada de R$ 303,85: já guardou R$ 100,00 este mês → reserva R$ 203,85
        val s = state(monthly = 30385)
        val m = Totals.month(s, YearMonth.of(2026, 11), today)
        assertEquals(20385L, m.goalsPlanned)
        assertEquals(297059L - 20385L, m.closing)
        assertEquals(Totals.forecastAt(s, d("2026-11-30"), today), m.closing)
        // meses seguintes: o aporte inteiro
        assertEquals(30385L, Totals.goalsPlanned(s, YearMonth.of(2026, 12), today))
        // mês passado: nada
        assertEquals(0L, Totals.goalsPlanned(s, YearMonth.of(2026, 10), today))
    }

    @Test fun aporteLimitadoAoQueFaltaParaAMeta() {
        val s = state(monthly = 50000).let { it.copy(goals = listOf(it.goals[0].copy(target = 40000))) } // faltam R$ 300,00
        assertEquals(30000L, Totals.goalsPlanned(s, YearMonth.of(2026, 11), today)) // 500 − 100 já guardados = 400, limitado a 300
        assertEquals(0L, Totals.goalsPlanned(s, YearMonth.of(2026, 12), today))
    }

    @Test fun realizadoSemCompraNoCartaoEComPagamentoDeFatura() {
        val s = state().let { it.copy(txs = it.txs + listOf(
            Tx("pg", Kind.EXPENSE, 20000, d("2026-11-01"), "Pagamento fatura Sofisa", AppState.CARD_PAYMENT_CAT, true, "main", cardPayment = "sofisa"),
            Tx("cine", Kind.EXPENSE, 5000, d("2026-11-01"), "Cinema", "Lazer", true, "main"),
            Tx("pix", Kind.INCOME, 3000, d("2026-11-01"), "Pix", "Extra", true, "main"),
            Tx("nov-cartao", Kind.EXPENSE, 7000, d("2026-11-01"), "Livro", "Educação", true, "main", "sofisa"),
        )) }
        val m = Totals.month(s, YearMonth.of(2026, 11), today)
        assertEquals(3000L, m.realizedIn)
        assertEquals(25000L, m.realizedOut) // cinema + pagamento da fatura; a compra no cartão conta pela fatura
        assertEquals(10000L, m.goalsSaved)
        assertEquals(3000L - 25000L - 10000L, m.result)
    }

    @Test fun pendenteAtrasadoEntraNoMesAtual() {
        val s = state().let { it.copy(txs = it.txs + Tx("luz", Kind.EXPENSE, 9000, d("2026-10-25"), "Luz", "Moradia", false, "main")) }
        val m = Totals.month(s, YearMonth.of(2026, 11), today)
        assertEquals(60991L + 9000L, m.toPay)
        assertEquals(Totals.forecastAt(s, d("2026-11-30"), today), m.closing)
        // em outubro (passado) ela aparece como pendência daquele mês
        assertEquals(9000L, Totals.month(s, YearMonth.of(2026, 10), today).toPay)
    }

    @Test fun mesFuturoContinuaDoPrevistoAnterior() {
        val s = state(monthly = 30385).let { it.copy(
            recurring = listOf(Recurring("r1", Kind.EXPENSE, "Aluguel", 90000, "Moradia", "main", "", 5, true, null, null)),
        ) }
        val nov = Totals.month(s, YearMonth.of(2026, 11), today)
        val dez = Totals.month(s, YearMonth.of(2026, 12), today)
        assertEquals(nov.closing, dez.opening)
        assertEquals(90000L, dez.toPay)
        assertEquals(30385L, dez.goalsPlanned)
        assertEquals(Totals.forecastAt(s, d("2026-12-31"), today), dez.closing)
    }

    @Test fun mesPassadoFechaNoSaldoDoFimDoMes() {
        val s = state()
        val out = Totals.month(s, YearMonth.of(2026, 10), today)
        assertEquals(Finance.balanceAt(s, d("2026-10-31")), out.closing)
        assertEquals(Finance.balanceAt(s, d("2026-09-30")), out.opening)
    }
}
