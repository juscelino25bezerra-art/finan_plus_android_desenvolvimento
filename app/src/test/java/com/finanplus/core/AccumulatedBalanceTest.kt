// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/** Opção "Acumular saldo entre os meses": saldo das contas ao fim de um dia, realizado e previsto. */
class AccumulatedBalanceTest {
    private val today = LocalDate.parse("2026-11-02")
    private fun d(s: String) = LocalDate.parse(s)
    private fun tx(id: String, kind: Kind, v: Long, date: String, paid: Boolean, account: String = "main", card: String = "") =
        Tx(id, kind, v, d(date), id, "Outros", paid, account, card)

    private val base = AppState(
        accounts = listOf(Account("main", "Conta principal", 100000), Account("poup", "Poupança", 50000)),
        txs = listOf(
            tx("sal-set", Kind.INCOME, 300000, "2026-09-05", true),
            tx("alug-set", Kind.EXPENSE, 120000, "2026-09-10", true),
            tx("sal-out", Kind.INCOME, 300000, "2026-10-05", true),
            tx("alug-out", Kind.EXPENSE, 120000, "2026-10-10", true, account = "poup"),
            tx("luz-nov", Kind.EXPENSE, 20000, "2026-11-20", false),
            tx("freela-dez", Kind.INCOME, 80000, "2026-12-15", false),
            // compra no cartão não mexe no saldo da conta
            tx("mercado", Kind.EXPENSE, 9999, "2026-10-12", true, card = "visa"),
            // lançamento de conta que não existe mais não entra (como em accountBalance)
            tx("orfao", Kind.INCOME, 777, "2026-10-01", true, account = "apagada"),
        ),
    )

    @Test fun saldoInicialEntraAntesDeQualquerLancamento() {
        assertEquals(150000L, Finance.balanceAt(base, d("2026-08-31")))
    }

    @Test fun acumulaOsMesesAnteriores() {
        // fim de setembro: 1.500 iniciais + 3.000 − 1.200
        assertEquals(330000L, Finance.balanceAt(base, d("2026-09-30")))
        // fim de outubro: + 3.000 − 1.200
        assertEquals(510000L, Finance.balanceAt(base, d("2026-10-31")))
    }

    @Test fun mesFuturoMantemORealizadoESomaAsPendenciasNoPrevisto() {
        assertEquals(510000L, Finance.balanceAt(base, d("2026-11-30")))
        assertEquals(490000L, Finance.forecastAt(base, d("2026-11-30"), today))
        assertEquals(570000L, Finance.forecastAt(base, d("2026-12-31"), today))
    }

    @Test fun previstoDeHojeEmDianteBateComOSaldoPrevistoDoInicio() {
        for (until in listOf(today, d("2026-11-30"), d("2026-12-31")))
            assertEquals(Finance.futureBalance(base, until, today), Finance.forecastAt(base, until, today))
    }

    @Test fun somaDoSaldoDasContasBateComOSaldoAtual() {
        assertEquals(Finance.currentBalance(base), Finance.balanceAt(base, today))
    }
}
