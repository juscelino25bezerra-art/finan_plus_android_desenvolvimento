// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Aviso principal do Assistente no Início (HomeAdvice.risk) e os textos de apoio dos cartões. */
class HomeAdviceTest {
    private val today = LocalDate.parse("2026-11-02")
    private fun d(s: String) = LocalDate.parse(s)
    private fun tx(id: String, kind: Kind, v: Long, date: String, paid: Boolean = false, desc: String = id, cat: String = "Outros", card: String = "") =
        Tx(id, kind, v, d(date), desc, cat, paid, "main", card)

    /** Os dados do print: saldo 166,88; em 13/11 vencem 121,40 + 300,00 e cai o adiantamento de 1.213,62. */
    private fun novembro(vararg extra: Tx) = AppState(
        accounts = listOf(Account("main", "Conta principal", 16688)),
        txs = listOf(
            tx("emp", Kind.EXPENSE, 12140, "2026-11-13", desc = "Empréstimo", cat = "Empréstimo"),
            tx("cel", Kind.EXPENSE, 30000, "2026-11-13", desc = "Celular", cat = "Outros"),
            tx("adi", Kind.INCOME, 121362, "2026-11-13", desc = "Adiantamento", cat = "Salário"),
            tx("luz", Kind.EXPENSE, 20000, "2026-11-25", desc = "Luz"),
        ) + extra,
    )

    @Test fun riscoComRecebimentoNoMesmoDia() {
        val r = assertNotNull(HomeAdvice.risk(novembro(), today))
        assertEquals(d("2026-11-13"), r.day)
        assertEquals(42140L, r.bills)            // 121,40 + 300,00 (a luz do dia 25 fica de fora)
        assertEquals(16688L, r.balance)
        assertEquals(25452L, r.missing)          // "faltariam R$ 254,52"
        assertEquals("Adiantamento", r.income!!.title)
        assertEquals(121362L, r.income!!.amount)
        assertTrue(r.sameDay)
    }

    @Test fun riscoComRecebimentoAntes() {
        val s = novembro().let { it.copy(txs = it.txs.map { t -> if (t.id == "adi") t.copy(date = d("2026-11-10")) else t }) }
        val r = assertNotNull(HomeAdvice.risk(s, today))
        assertFalse(r.sameDay)
        assertEquals(d("2026-11-10"), r.income!!.date)
    }

    @Test fun riscoSemRecebimentoAteLa() {
        val s = novembro().let { it.copy(txs = it.txs.map { t -> if (t.id == "adi") t.copy(date = d("2026-11-20")) else t }) }
        val r = assertNotNull(HomeAdvice.risk(s, today))
        assertNull(r.income)
        assertEquals(25452L, r.missing)
    }

    @Test fun contaAtrasadaContaComoDeHoje() {
        val r = assertNotNull(HomeAdvice.risk(novembro(tx("agua", Kind.EXPENSE, 50000, "2026-11-01")), today))
        assertEquals(today, r.day)               // a atrasada puxa o dia para hoje
        assertEquals(50000L, r.bills)
        assertNull(r.income)                     // o adiantamento só cai no dia 13
        // se o saldo cobre a atrasada, não há aviso de risco
        assertNull(HomeAdvice.risk(novembro(tx("agua", Kind.EXPENSE, 1000, "2026-11-01")).copy(accounts = listOf(Account("main", "C", 100000))), today))
    }

    @Test fun semRisco() {
        val s = novembro().copy(accounts = listOf(Account("main", "Conta principal", 50000)))
        assertNull(HomeAdvice.risk(s, today))   // 500,00 cobre os 421,40 do dia 13
    }

    @Test fun mesSemContasPendentes() {
        val s = AppState(
            accounts = listOf(Account("main", "Conta principal", 0)),
            txs = listOf(
                tx("pago", Kind.EXPENSE, 10000, "2026-11-01", paid = true),
                tx("dez", Kind.EXPENSE, 90000, "2026-12-05"),          // mês seguinte não conta
                tx("cartao", Kind.EXPENSE, 9000, "2026-11-05", card = "itau"),
            ),
        )
        assertTrue(HomeAdvice.monthBills(s, today.ym(), today).isEmpty())
        assertNull(HomeAdvice.risk(s, today))
    }

    @Test fun proximoRecebimentoEContasDoMes() {
        val s = novembro()
        assertEquals("Adiantamento", HomeAdvice.nextIncome(s, today)!!.title)
        assertEquals(3, HomeAdvice.monthBills(s, today.ym(), today).size)
        val recebido = s.copy(txs = s.txs.map { if (it.kind == Kind.INCOME) it.copy(paid = true) else it })
        assertNull(HomeAdvice.nextIncome(recebido, today))   // "Tudo recebido este mês"
    }

    @Test fun contasDoMesBatemComOFaltaPagar() {
        val s = novembro()
        val p = Period.monthPending(s, today.ym(), today)
        assertEquals(p.toPay, HomeAdvice.monthBills(s, today.ym(), today).sumOf { it.amount })
        assertEquals(p.toReceive, HomeAdvice.monthIncome(s, today.ym()).sumOf { it.amount })
    }

    @Test fun proximosVencimentosAgrupadosEMesSeguinte() {
        val s = novembro(tx("dez", Kind.EXPENSE, 5000, "2026-12-01", desc = "Fatura extra"))
        val g = HomeAdvice.upcoming(s, today, limit = 4)
        assertEquals(listOf(d("2026-11-13"), d("2026-11-25"), d("2026-12-01")), g.map { it.date })
        assertEquals(2, g[0].items.size)
        assertEquals(listOf(false, false, true), g.map { it.nextMonth })
    }

    @Test fun linhasQueRepetemOsCartoes() {
        assertTrue(HomeAdvice.repeatsCards("Ainda faltam R$ 10,00 a pagar até o fim do mês."))
        assertTrue(HomeAdvice.repeatsCards("A receber neste mês: R$ 5,00."))
        assertFalse(HomeAdvice.repeatsCards("Você gastou 20% a mais que em outubro."))
    }
}
