// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Categorias × formas de pagamento: cartão e limite especial saem da lista; Empréstimo vira Dívidas. */
class CategoryMigrationTest {
    private val day = LocalDate.parse("2026-11-02")
    private fun st() = AppState(
        cats = Categories(listOf("Alimentação", "Cartão de Crédito", "Empréstimo", "Limite Especial", "Outros"), listOf("Salário", "Caju")),
        txs = listOf(
            Tx("a", Kind.EXPENSE, 100, day, "Anuidade", "Cartão de Crédito", true, "main"),
            Tx("b", Kind.EXPENSE, 12140, day, "Mercado Pago (2/4)", "Empréstimo", false, "main"),
            Tx("c", Kind.EXPENSE, 800, day, "Juros", "Limite Especial", true, "main"),
        ),
        limits = mapOf("Empréstimo" to 50000L),
    )

    @Test fun migraUmaVezSemPerderHistorico() {
        val m = TxForm.migrate(st())!!
        assertEquals(listOf("Alimentação", "Dívidas", "Outros"), m.cats.expense)
        assertEquals("Dívidas", m.txs.first { it.id == "b" }.category)
        assertEquals(50000L, m.limits["Dívidas"])
        // cartão e limite especial: o dado fica, a exibição mostra "Outros"
        assertEquals("Cartão de Crédito", m.txs.first { it.id == "a" }.category)
        assertEquals("Outros", TxForm.displayCategory("Limite Especial"))
        assertEquals(listOf("Salário", "Caju"), m.cats.income)
        assertNull(TxForm.migrate(m)) // idempotente
    }

    @Test fun juntaEmDividasQuandoJaExiste() {
        val s = st().copy(cats = Categories(listOf("Dívidas", "Empréstimo", "Outros"), listOf("Salário")))
        val m = TxForm.migrate(s)!!
        assertEquals(listOf("Dívidas", "Outros"), m.cats.expense)
        assertTrue(m.txs.none { it.category == "Empréstimo" })
    }

    @Test fun padraoNovoJaTemDividas() {
        assertTrue("Dívidas" in AppState.DEFAULT_EXPENSE)
        assertNull(TxForm.migrate(AppState()))
    }
}
