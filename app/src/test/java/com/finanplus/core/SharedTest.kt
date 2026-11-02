// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Despesas compartilhadas: divisão, quem deve, pagamentos parciais, histórico e backup. */
class SharedTest {
    private val day = LocalDate.parse("2026-11-02")
    private fun AppState.ok(o: Outcome): AppState = when (o) { is Outcome.Ok -> o.state; is Outcome.Err -> error("esperava Ok: ${o.message}") }
    private fun Outcome.msg(): String = when (this) { is Outcome.Err -> message; is Outcome.Ok -> error("esperava erro") }
    private fun people(vararg n: String) = n.map { SharedPersonDraft("", it) }

    private fun mercadoIgual(): AppState =
        AppState().ok(Shared.save(AppState(), null, SharedDraft("Mercado", "450", day, SplitMode.EQUAL, people("João", "Maria", "Pedro"), payerIndex = 0)))

    @Test fun divisaoIgualCadaUmDeveSuaParteAQuemPagou() {
        val e = mercadoIgual().shared.single()
        assertEquals(listOf(15000L, 15000L, 15000L), e.people.map { it.share })
        val st = Shared.summary(e)
        assertEquals("João", e.payer!!.name)
        assertEquals(PersonStatus.State.NONE, st.people[0].state)
        assertEquals(listOf(0L, 15000L, 15000L), st.people.map { it.owed })
        assertEquals(30000L, st.toReceive); assertEquals(0L, st.received); assertEquals(30000L, st.pending)
        assertEquals(SharedSummary.State.OPEN, st.state)
    }

    @Test fun divisaoIgualComCentavosQueSobram() {
        assertEquals(listOf(3334L, 3333L, 3333L), Shared.equalShares(10000, 3))
    }

    @Test fun divisaoPersonalizadaPrecisaFecharComOTotal() {
        val rows = listOf(SharedPersonDraft("", "João", "150"), SharedPersonDraft("", "Maria", "120"), SharedPersonDraft("", "Pedro", "180"))
        val s = AppState().ok(Shared.save(AppState(), null, SharedDraft("Mercado", "450,00", day, SplitMode.CUSTOM, rows, payerIndex = 0)))
        val st = Shared.summary(s.shared.single())
        assertEquals(listOf(0L, 12000L, 18000L), st.people.map { it.owed })
        assertEquals(30000L, st.pending)

        val wrong = rows.toMutableList().also { it[2] = it[2].copy(share = "100") }
        assertTrue(Shared.save(AppState(), null, SharedDraft("Mercado", "450", day, SplitMode.CUSTOM, wrong, 0)).msg().contains("Faltam R$ 80,00"))
    }

    @Test fun pagamentosParciaisETotais() {
        var s = mercadoIgual()
        val e = s.shared.single(); val maria = e.people[1].id; val pedro = e.people[2].id
        s = s.ok(Shared.addPayment(s, e.id, maria, "50", day))
        var st = Shared.summary(s.shared.single())
        assertEquals(PersonStatus.State.PARTIAL, st.people[1].state); assertEquals(10000L, st.people[1].remaining)
        assertEquals(SharedSummary.State.PARTIAL, st.state)
        // acima do que falta: recusado
        assertTrue(Shared.addPayment(s, e.id, maria, "100,01", day).msg().contains("R$ 100,00"))
        // quem pagou não devolve
        assertTrue(Shared.addPayment(s, e.id, e.payerId, "10", day) is Outcome.Err)
        s = s.ok(Shared.settle(s, e.id, maria, day))
        s = s.ok(Shared.settle(s, e.id, pedro, day))
        st = Shared.summary(s.shared.single())
        assertEquals(30000L, st.received); assertEquals(0L, st.pending); assertEquals(SharedSummary.State.SETTLED, st.state)
        assertTrue(Shared.settle(s, e.id, pedro, day) is Outcome.Err)
        // excluir um pagamento volta a dever
        val first = s.shared.single().payments.first()
        s = Shared.deletePayment(s, e.id, first.id)
        assertEquals(5000L, Shared.summary(s.shared.single()).pending)
    }

    @Test fun editarNaoDeixaPagamentosSoltos() {
        var s = mercadoIgual()
        val e = s.shared.single()
        s = s.ok(Shared.addPayment(s, e.id, e.people[1].id, "150", day))
        val rows = e.people.map { SharedPersonDraft(it.id, it.name) }
        // tirar a Maria (que já pagou): recusado
        assertTrue(Shared.save(s, e.id, SharedDraft("Mercado", "450", day, SplitMode.EQUAL, rows.filter { it.name != "Maria" }, 0)).msg().contains("Maria"))
        // diminuir o total abaixo do que ela já devolveu: recusado
        assertTrue(Shared.save(s, e.id, SharedDraft("Mercado", "300", day, SplitMode.EQUAL, rows, 0)) is Outcome.Err)
        // incluir mais uma pessoa: as partes caem para 112,50 e a Maria passaria do devido → recusado
        assertTrue(Shared.save(s, e.id, SharedDraft("Mercado", "450", day, SplitMode.EQUAL, rows + SharedPersonDraft("", "Ana"), 0)) is Outcome.Err)
        // mudar o nome mantém os ids e os pagamentos
        val ren = rows.map { if (it.name == "Pedro") it.copy(name = "Pedro H.") else it }
        s = s.ok(Shared.save(s, e.id, SharedDraft("Mercado do mês", "450", day, SplitMode.EQUAL, ren, 0)))
        val e2 = s.shared.single()
        assertEquals(e.people.map { it.id }, e2.people.map { it.id })
        assertEquals(1, e2.payments.size); assertEquals("Mercado do mês", e2.name)
    }

    @Test fun validacoesDoFormulario() {
        fun save(d: SharedDraft) = Shared.save(AppState(), null, d)
        assertTrue(save(SharedDraft("", "10", day, SplitMode.EQUAL, people("A", "B"), 0)) is Outcome.Err)
        assertTrue(save(SharedDraft("X", "0", day, SplitMode.EQUAL, people("A", "B"), 0)) is Outcome.Err)
        assertTrue(save(SharedDraft("X", "10", null, SplitMode.EQUAL, people("A", "B"), 0)) is Outcome.Err)
        assertTrue(save(SharedDraft("X", "10", day, SplitMode.EQUAL, people("A"), 0)) is Outcome.Err)
        assertTrue(save(SharedDraft("X", "10", day, SplitMode.EQUAL, people("Ana", "ana"), 0)) is Outcome.Err)
        assertTrue(save(SharedDraft("X", "10", day, SplitMode.EQUAL, people("A", " "), 0)) is Outcome.Err)
        assertTrue(save(SharedDraft("X", "10", day, SplitMode.EQUAL, people("A", "B"), 5)) is Outcome.Err)
    }

    @Test fun historicoComBuscaPeriodoPessoaESituacao() {
        var s = mercadoIgual()
        s = s.ok(Shared.save(s, null, SharedDraft("Pizza", "90", day.minusMonths(1), SplitMode.EQUAL, people("Eu", "Ana", "Pedro"), 0, note = "aniversário")))
        val pizza = s.shared.first { it.name == "Pizza" }
        s = s.ok(Shared.settle(s, pizza.id, pizza.people[1].id, day)).let { it.ok(Shared.settle(it, pizza.id, pizza.people[2].id, day)) }
        val all = s.shared
        assertEquals(listOf("Mercado", "Pizza"), Shared.filter(all, SharedFilter()).map { it.name })
        assertEquals(listOf("Pizza"), Shared.filter(all, SharedFilter(query = "aniversario")).map { it.name })
        assertEquals(listOf("Pizza"), Shared.filter(all, SharedFilter(person = "ana")).map { it.name })
        assertEquals(listOf("Mercado", "Pizza"), Shared.filter(all, SharedFilter(person = "Pedro")).map { it.name })
        assertEquals(listOf("Mercado"), Shared.filter(all, SharedFilter(from = day.withDayOfMonth(1))).map { it.name })
        assertEquals(listOf("Pizza"), Shared.filter(all, SharedFilter(state = SharedSummary.State.SETTLED)).map { it.name })
        assertEquals(listOf("Ana", "Eu", "João", "Maria", "Pedro"), Shared.peopleNames(all))
        assertEquals(30000L to 6000L, Shared.totals(all))
    }

    @Test fun backupLevaEVoltaComPagamentosEComprovante() {
        var s = mercadoIgual()
        val e = s.shared.single()
        s = s.ok(Shared.addPayment(s, e.id, e.people[2].id, "20,50", day))
        s = Shared.setReceipt(s, e.id, "rcpt123", "image/jpeg")
        val back = Backup.parse(Backup.toJson(s))
        assertEquals(0, back.dropped.total)
        assertEquals(s.shared, back.state.shared)
        // sem despesas, o campo nem aparece (o arquivo continua igual ao do Finan+ web)
        assertFalse(Backup.toJson(AppState()).contains("\"shared\""))
    }

    @Test fun backupDescartaDespesaQueNaoFecha() {
        val json = """{"txs":[],"shared":[{"id":"a","name":"X","total":10,"date":"2026-11-02","payer":"p1","people":[{"id":"p1","name":"A","share":5},{"id":"p2","name":"B","share":4}]}]}"""
        val n = Backup.parse(json)
        assertTrue(n.state.shared.isEmpty()); assertEquals(1, n.dropped.shared)
        // pagamento acima da parte é ignorado
        val json2 = """{"txs":[],"shared":[{"id":"a","name":"X","total":10,"date":"2026-11-02","payer":"p1","people":[{"id":"p1","name":"A","share":5},{"id":"p2","name":"B","share":5}],"payments":[{"id":"x","person":"p2","value":6,"date":"2026-11-02"},{"id":"y","person":"p1","value":1,"date":"2026-11-02"}]}]}"""
        assertTrue(Backup.parse(json2).state.shared.single().payments.isEmpty())
    }

    @Test fun gravacaoPelaRedeSemOCampoMantemAsDoCelular() {
        val s = mercadoIgual()
        assertEquals(s.shared, RemoteSave.keepShared(s, AppState(), hasField = false).shared)
        assertTrue(RemoteSave.keepShared(s, AppState(), hasField = true).shared.isEmpty())
    }

    @Test fun nomeDoPdf() {
        assertEquals("comprovante-mercado-do-mes-2026-11-02.pdf", Shared.pdfName(mercadoIgual().shared.single().copy(name = "Mercado do Mês!")))
    }
}
