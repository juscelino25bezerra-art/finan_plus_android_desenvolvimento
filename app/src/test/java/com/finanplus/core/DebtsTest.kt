// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dívidas montadas a partir dos lançamentos da categoria Dívidas (ou o antigo Empréstimo). */
class DebtsTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun parcel(n: Int, paid: Boolean, cat: String = "Dívidas") =
        Tx("mp$n", Kind.EXPENSE, 12140, d("2026-10-13").plusMonths(n - 1L), "Mercado Pago ($n/4)", cat, paid, "main", groupId = "mp", parcelN = n, parcelTotal = 4)

    @Test fun agrupaParcelasEMostraAProxima() {
        val s = AppState(txs = listOf(parcel(1, true), parcel(2, false), parcel(3, false), parcel(4, false)))
        val l = Debts.list(s)
        assertEquals(1, l.size)
        assertEquals("Mercado Pago", l[0].name)
        assertEquals("mp2", l[0].next?.id)
        assertEquals(3, l[0].pendingCount)
        assertEquals(3 * 12140L, l[0].pendingTotal)
        assertEquals(4, l[0].totalCount)
        assertEquals("1 dívida · parcela de R$ 121,40 em 13/11", Debts.summary(s, false))
        assertEquals("1 dívida · parcela de R$ •••• em 13/11", Debts.summary(s, true))
    }

    @Test fun categoriaAntigaEmprestimoTambemConta() {
        val s = AppState(txs = listOf(parcel(1, false, "Empréstimo"), Tx("x", Kind.EXPENSE, 500, d("2026-11-01"), "Mercado", "Alimentação", false, "main")))
        assertEquals(1, Debts.list(s).size)
        assertTrue(Debts.isDebtCategory("emprestimos"))
    }

    @Test fun quitadaNaoEntraNoResumo() {
        val s = AppState(txs = (1..4).map { parcel(it, true) })
        assertTrue(Debts.list(s)[0].done)
        assertNull(Debts.summary(s, false))
    }
}
