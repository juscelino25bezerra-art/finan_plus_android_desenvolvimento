// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Meu dinheiro: saldo igual ao do Início, faturas, uso do limite, faixas, ordem, textos e plural (MoneyView). */
class MoneyViewTest {
    private val today = LocalDate.parse("2026-11-03")
    private fun d(s: String) = LocalDate.parse(s)

    /** Conta com R$ 266,88 antes da meta; R$ 100,00 reservados na meta saindo da conta. Três cartões. */
    private fun state(): AppState {
        val sofisa = Card("sofisa", "Sofisa 6333", 50000, close = 5, due = 15)
        val santander = Card("santander", "Santander", 200000, close = 20, due = 1)
        val nu = Card("nu", "Nubank", 100000, close = 1, due = 8)
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 26688)),
            cards = listOf(nu, santander, sofisa),
            goals = listOf(Goal("g1", "Notebook", 300000, 10000, null, 0)),
            txs = listOf(
                Tx("meta", Kind.EXPENSE, 10000, d("2026-11-02"), "Meta › Notebook", AppState.GOAL_CAT, true, "main", goalId = "g1"),
                Tx("s1", Kind.EXPENSE, 48851, d("2026-10-20"), "Compras", "Outros", true, "main", "sofisa"),
                Tx("t1", Kind.EXPENSE, 49088, d("2026-11-01"), "Compras", "Outros", true, "main", "santander"),
            ),
        )
    }

    @Test fun saldoDaContaIgualAoDoInicioComReservaParaMeta() {
        val s = state()
        val acc = s.accounts[0]
        assertEquals(16688L, Finance.accountBalance(s, acc))
        assertEquals(Finance.currentBalance(s), MoneyView.overview(s, today).inAccounts)
        assertEquals(10000L, MoneyView.reservedFrom(s, acc))
        // retirada da meta volta para a conta e reduz o "guardado em metas" da linha
        val back = s.copy(txs = s.txs + Tx("ret", Kind.INCOME, 4000, today, "Retirada da meta › Notebook", AppState.GOAL_CAT, true, "main", goalId = "g1"))
        assertEquals(6000L, MoneyView.reservedFrom(back, acc))
        assertEquals(Finance.currentBalance(back), MoneyView.overview(back, today).inAccounts)
    }

    @Test fun somaDasFaturasAbertasEGuardadoEmMetas() {
        val o = MoneyView.overview(state(), today)
        assertEquals(48851L + 49088L, o.openInvoices)
        assertEquals(10000L, o.inGoals)
        // pagamento parcial abate da fatura aberta
        val paid = state().let { it.copy(txs = it.txs + Tx("p", Kind.EXPENSE, 851, today, "Pagamento fatura Sofisa 6333", AppState.CARD_PAYMENT_CAT, true, "main", cardPayment = "sofisa")) }
        assertEquals(48000L + 49088L, MoneyView.overview(paid, today).openInvoices)
    }

    @Test fun porcentagemEDisponivelDoLimite() {
        val r = MoneyView.cards(state(), today).first { it.card.id == "sofisa" }
        assertEquals(97, r.pct) // 488,51 de 500,00 = 97,7% → 97 (para baixo)
        assertEquals(1149L, r.available)
        assertEquals(0, MoneyView.pct(1000, 0))
        assertEquals(100, MoneyView.pct(60000, 50000).coerceAtMost(100))
        assertEquals("Sofisa 6333, 97% do limite usado, disponível R$ 11,49", MoneyView.barDescription(r, hide = false))
        assertFalse(MoneyView.barDescription(r, hide = true).contains("11,49"))
    }

    @Test fun faixasDeCorDaBarra() {
        assertEquals(MoneyView.Band.NORMAL, MoneyView.band(0))
        assertEquals(MoneyView.Band.NORMAL, MoneyView.band(69))
        assertEquals(MoneyView.Band.ALERT, MoneyView.band(70))
        assertEquals(MoneyView.Band.ALERT, MoneyView.band(89))
        assertEquals(MoneyView.Band.CRITICAL, MoneyView.band(90))
        assertEquals(MoneyView.Band.CRITICAL, MoneyView.band(120))
        // 89,9% fica em alerta (arredonda para baixo, a faixa segue o número mostrado)
        assertEquals(89, MoneyView.pct(8999, 10000))
    }

    @Test fun ordenaPeloVencimentoMaisProximo() {
        val order = MoneyView.cards(state(), today).map { it.card.id }
        assertEquals(listOf("sofisa", "santander", "nu"), order) // 15/11, 01/12, sem fatura
        val nu = MoneyView.cards(state(), today).last()
        assertNull(nu.invoice)
        assertEquals(0L, nu.open)
    }

    @Test fun textoDaquiANDias() {
        val r = MoneyView.cards(state(), today)
        assertEquals(12L, r[0].daysToDue)
        assertEquals("· daqui a 12 dias", MoneyView.dueSuffix(12))
        assertEquals("· daqui a 15 dias", MoneyView.dueSuffix(15))
        assertNull(MoneyView.dueSuffix(16))
        assertNull(MoneyView.dueSuffix(null))
        assertEquals("· amanhã", MoneyView.dueSuffix(1))
        assertEquals("· vence hoje", MoneyView.dueSuffix(0))
        assertEquals("· vencida", MoneyView.dueSuffix(-2))
        assertTrue(r[0].dueSoon)
        assertFalse(r[1].dueSoon) // 28 dias
    }

    @Test fun pluralDeContasECartoes() {
        assertEquals("conta", MoneyView.plural(1, "conta", "contas"))
        assertEquals("contas", MoneyView.plural(0, "conta", "contas"))
        assertEquals("cartões", MoneyView.plural(3, "cartão", "cartões"))
        assertEquals("cartão", MoneyView.plural(1, "cartão", "cartões"))
        assertEquals("1 limite · 0 perto do teto", MoneyView.limitsSummary(mapOf("Lazer" to 10000L), emptyMap()))
        assertEquals("2 limites · 1 perto do teto", MoneyView.limitsSummary(mapOf("Lazer" to 10000L, "Saúde" to 10000L), mapOf("Lazer" to 8000L)))
        assertNull(MoneyView.limitsSummary(emptyMap(), emptyMap()))
    }

    @Test fun resumoDasMetas() {
        val g1 = Goal("g1", "Notebook", 300000, 10000, null, 0)
        val g2 = Goal("g2", "Viagem", 300000, 5000, null, 0)
        assertEquals("Notebook · R$ 100,00 guardado", MoneyView.goalsSummary(listOf(g1), false))
        assertEquals("2 metas · R$ 150,00 guardados", MoneyView.goalsSummary(listOf(g1, g2), false))
        assertEquals("Notebook · R$ •••• guardado", MoneyView.goalsSummary(listOf(g1), true))
        assertNull(MoneyView.goalsSummary(emptyList(), false))
    }

    @Test fun resumoComSecaoRecolhida() {
        val s = state()
        assertEquals("Contas · R$ 166,88", MoneyView.accountsCollapsed(s, false))
        val two = s.copy(accounts = s.accounts + Account("pp", "Poupança", 10000))
        assertEquals("Contas · R$ 266,88 · 2 contas", MoneyView.accountsCollapsed(two, false))
        assertEquals("Cartões · 3 · R$ 979,39 em faturas", MoneyView.cardsCollapsed(s, today, false))
        assertEquals("Cartões · 3 · R$ •••• em faturas", MoneyView.cardsCollapsed(s, today, true))
        assertEquals("Contas · R$ ••••", MoneyView.accountsCollapsed(s, true))
    }

    @Test fun linhaDeAlertaSoNasCondicoes() {
        val s = state()
        // hoje 03/11: Sofisa vence em 12 dias, mas usa 97% → alerta (uso); Santander 24% e 28 dias → sem alerta
        val r = MoneyView.cards(s, today)
        assertTrue(r.first { it.card.id == "sofisa" }.alert)
        assertEquals("Sofisa 6333 · 97% do limite", MoneyView.alertLine(r.first { it.card.id == "sofisa" }, false))
        assertFalse(r.first { it.card.id == "santander" }.alert)
        assertFalse(r.first { it.card.id == "nu" }.alert)
        // a 7 dias do vencimento a linha aparece e mostra a data
        val r8 = MoneyView.cards(s, d("2026-11-08")).first { it.card.id == "sofisa" }
        assertEquals("Sofisa 6333 · vence 15/11 · 97% do limite", MoneyView.alertLine(r8, false))
        assertEquals("Sofisa 6333 · vence 15/11 · perto do limite", MoneyView.alertLine(r8, true))
        // sem uso alto, só o prazo: 8 dias não alerta, 7 alerta
        val low = s.copy(cards = s.cards.map { if (it.id == "sofisa") it.copy(limit = 1000000) else it })
        assertFalse(MoneyView.cards(low, d("2026-11-07")).first { it.card.id == "sofisa" }.alert)
        assertTrue(MoneyView.cards(low, d("2026-11-08")).first { it.card.id == "sofisa" }.alert)
        assertEquals("Sofisa 6333 · vence 15/11", MoneyView.alertLine(MoneyView.cards(low, d("2026-11-08")).first { it.card.id == "sofisa" }, false))
    }
}
