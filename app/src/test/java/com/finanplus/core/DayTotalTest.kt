// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Total do dia em Lançamentos e no calendário: só receitas e despesas; reservas para meta à parte
 * ("R$ X em metas"); compras no cartão fora, como antes. Os saldos não mudam (ver o último teste).
 */
class DayTotalTest {
    private val day = LocalDate.parse("2026-11-02")
    private fun tx(id: String, kind: Kind, value: Long, paid: Boolean = true, card: String = "", goal: String = "") =
        Tx(id, kind, value, day, id, if (goal.isNotEmpty()) AppState.GOAL_CAT else "Outros", paid || card.isNotEmpty() || goal.isNotEmpty(), "main", card, goalId = goal)

    @Test fun soReceitasEDespesas() {
        val t = Period.dayTotal(listOf(tx("a", Kind.INCOME, 50000), tx("b", Kind.EXPENSE, 12000, paid = false)))
        assertEquals(DayTotal(net = 38000, goals = 0, hasFlow = true), t)
    }

    @Test fun soReservas() {
        val t = Period.dayTotal(listOf(tx("m", Kind.EXPENSE, 10000, goal = "g1")))
        assertEquals(0L, t.net); assertEquals(10000L, t.goals); assertFalse(t.hasFlow)
    }

    @Test fun mistoReservaFicaForaDoTotal() {
        val t = Period.dayTotal(listOf(tx("a", Kind.INCOME, 50000), tx("m", Kind.EXPENSE, 10000, goal = "g1"), tx("r", Kind.INCOME, 2500, goal = "g1")))
        assertEquals(50000L, t.net)          // a reserva não entra como despesa no total do dia
        assertEquals(7500L, t.goals)         // guardou 100, retirou 25
        assertTrue(t.hasFlow)
    }

    @Test fun compraNoCartaoMaisReserva_caso2DeNovembro() {
        // "Cartão de Crédito − R$ 126,00 (no cartão)" + "Meta › Notebook R$ 100,00": só "R$ 100,00 em metas"
        val txs = listOf(tx("c", Kind.EXPENSE, 12600, card = "itau"), tx("m", Kind.EXPENSE, 10000, goal = "g1"))
        val t = Period.dayTotal(txs)
        assertEquals(DayTotal(net = 0, goals = 10000, hasFlow = false), t)

        // calendário: mesmo total do dia, e os totais do mês continuam como antes
        val s = AppState(txs = txs, cards = listOf(Card("itau", "Itaú", 20000, close = 24, due = 1)))
        val cal = MonthCalendar.build(s, YearMonth.of(2026, 11), day).getValue(day)
        assertEquals(0L, cal.flowNet); assertEquals(10000L, cal.goals); assertFalse(cal.hasFlow)
        assertEquals(-10000L, cal.net) // income/expense do dia não mudaram: a reserva segue saindo da conta
    }

    @Test fun saldosNaoMudam() {
        val s = AppState(
            accounts = listOf(Account("main", "Conta principal", 50000)),
            txs = listOf(tx("m", Kind.EXPENSE, 10000, goal = "g1"), tx("b", Kind.EXPENSE, 5000)),
        )
        assertEquals(35000L, Finance.currentBalance(s))
    }
}
