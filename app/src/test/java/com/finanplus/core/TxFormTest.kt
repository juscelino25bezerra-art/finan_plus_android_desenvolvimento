// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tela de lançamento: valor em pt-BR, bloqueio de zero, categorias, recentes e sugestões (AmountInput e TxForm). */
class TxFormTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun tx(id: String, kind: Kind, date: String, cat: String, desc: String = id, goal: String = "") =
        Tx(id, kind, 1000, d(date), desc, cat, true, "main", goalId = goal)

    // ---- valor
    @Test fun formataOValorEmPtBr() {
        assertEquals("", AmountInput.format(""))
        assertEquals("0", AmountInput.format("0"))
        assertEquals("12,5", AmountInput.format("12,5"))
        assertEquals("1.234,56", AmountInput.format("1234,56"))
        assertEquals("1.234", AmountInput.format("1234"))
        assertEquals("1.234.567,89", AmountInput.format("1234567,89"))
    }

    @Test fun formataEmTempoRealDigitoADigito() {
        var text = ""
        val seen = ArrayList<String>()
        for (ch in "1234,5") { text = AmountInput.format(text + ch); seen += text }
        assertEquals(listOf("1", "12", "123", "1.234", "1.234,", "1.234,5"), seen)
        // apagar o último caractere do agrupamento também reformata
        assertEquals("123", AmountInput.format("1.23"))
        assertEquals("1.234", AmountInput.format("1.2345".dropLast(1)))
    }

    @Test fun pontoDigitadoNoFimVaiComoVirgula() {
        assertEquals("12,", AmountInput.format("12."))
        assertEquals("1.234,", AmountInput.format("1.234."))
        assertEquals("12,5", AmountInput.format("12,5"))
    }

    @Test fun limitaCasasEZerosAEsquerda() {
        assertEquals("1,23", AmountInput.format("1,2345"))
        assertEquals("7", AmountInput.format("007"))
        assertEquals("0,5", AmountInput.format("0,5"))
        assertEquals("0,5", AmountInput.format(",5"))
        assertEquals("12", AmountInput.format("1a2b"))                 // letras e símbolos são ignorados
        assertEquals("1.234,56", AmountInput.format("1,2,3,4"
            .replace(",", "") + ",56"))
        assertEquals(13, AmountInput.format("1".repeat(20)).count { it == '1' })   // máximo de 13 algarismos de reais
    }

    @Test fun valorEmCentavosBateComOQueOAppSalva() {
        assertEquals(123456L, AmountInput.cents("1.234,56"))
        assertEquals(1250L, AmountInput.cents("12,5"))
        assertEquals(0L, AmountInput.cents("0"))
        assertEquals(null, AmountInput.cents(""))
        assertEquals("1.234,56", AmountInput.fromCents(123456))
        assertEquals("12,50", AmountInput.fromCents(1250))
    }

    // ---- bloqueio de valor zero
    @Test fun zeroEVazioNaoPodemSerSalvos() {
        assertFalse(AmountInput.isPositive(""))
        assertFalse(AmountInput.isPositive("0"))
        assertFalse(AmountInput.isPositive("0,00"))
        assertTrue(AmountInput.isPositive("0,01"))
        assertTrue(AmountInput.isPositive("12,5"))
        // o erro discreto só aparece quando digitou algo e deu zero
        assertFalse(AmountInput.isZeroTyped(""))
        assertTrue(AmountInput.isZeroTyped("0"))
        assertTrue(AmountInput.isZeroTyped("0,00"))
        assertFalse(AmountInput.isZeroTyped("0,01"))
        // e o salvamento recusa o zero do mesmo jeito (regra que já existia em Ops.saveTx)
        val s = AppState(accounts = listOf(Account("main", "Conta", 0)))
        assertTrue(Ops.saveTx(s, null, TxDraft(Kind.EXPENSE, "", "0,00", "Outros", d("2026-11-03"), true, "main"), d("2026-11-03")) is Outcome.Err)
    }

    // ---- trocar o tipo limpa a categoria que não existe no novo
    @Test fun trocarDeTipoLimpaACategoriaQueNaoExiste() {
        val despesas = listOf("Alimentação", "Transporte", "Outros")
        val receitas = listOf("Salário", "Extra", "Outros")
        assertEquals("", TxForm.categoryAfterKindChange("Transporte", receitas))      // só existe em despesas
        assertEquals("Outros", TxForm.categoryAfterKindChange("Outros", receitas))    // existe nos dois: continua
        assertEquals("Salário", TxForm.categoryAfterKindChange("Salario", receitas))   // sem diferenciar acento
        assertEquals("", TxForm.categoryAfterKindChange("Salário", despesas))
        assertEquals("", TxForm.categoryAfterKindChange("", despesas))                // nada escolhido: continua sem
    }

    // ---- "Cartão de Crédito" não é categoria
    @Test fun cartaoDeCreditoAparecePorOutrosEFicaForaDaLista() {
        assertEquals("Outros", TxForm.displayCategory("Cartão de Crédito"))
        assertEquals("Outros", TxForm.displayCategory("cartao de credito"))
        assertEquals("Outros", TxForm.displayCategory("  CARTÃO DE CRÉDITO "))
        assertEquals("Mercado", TxForm.displayCategory("Mercado"))
        assertEquals(listOf("Alimentação", "Outros"), TxForm.choices(listOf("Alimentação", "Cartão de Crédito", "Outros")))
        // o dado não muda: a função só devolve o nome de exibição
        val t = tx("a", Kind.EXPENSE, "2026-11-01", "Cartão de Crédito")
        TxForm.displayCategory(t.category)
        assertEquals("Cartão de Crédito", t.category)
    }

    // ---- busca na gaveta
    @Test fun buscaSemDiferenciarAcentoNemCaixa() {
        val l = listOf("Alimentação", "Saúde", "Educação", "Outros")
        assertEquals(listOf("Alimentação"), TxForm.filter(l, "alimen"))
        assertEquals(listOf("Saúde"), TxForm.filter(l, "SAUDE"))
        assertEquals(listOf("Alimentação", "Educação"), TxForm.filter(l, "ç"))
        assertEquals(l, TxForm.filter(l, "  "))
        assertEquals(emptyList(), TxForm.filter(l, "zzz"))
    }

    // ---- recentes
    @Test fun recentesDoMaisRecenteParaOMenosEAteQuatro() {
        val all = listOf("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Educação", "Outros")
        val txs = listOf(
            tx("1", Kind.EXPENSE, "2026-10-01", "Moradia"),
            tx("2", Kind.EXPENSE, "2026-10-20", "Lazer"),
            tx("3", Kind.EXPENSE, "2026-10-25", "Alimentação"),
            tx("4", Kind.EXPENSE, "2026-10-25", "Transporte"),   // mesmo dia: o gravado depois vem antes
            tx("5", Kind.EXPENSE, "2026-10-10", "Alimentação"),  // repetida: conta pela mais recente
            tx("6", Kind.EXPENSE, "2026-09-01", "Saúde"),
            tx("7", Kind.INCOME, "2026-10-30", "Salário"),       // outro tipo: fora
            tx("8", Kind.EXPENSE, "2026-10-31", "Meta", goal = "g1"),   // reserva para meta: fora
        )
        assertEquals(listOf("Transporte", "Alimentação", "Lazer", "Moradia"), TxForm.recentCategories(txs, Kind.EXPENSE, all))
        assertEquals(listOf("Salário"), TxForm.recentCategories(txs, Kind.INCOME, listOf("Salário", "Extra")))
        // categoria apagada depois não aparece; sem histórico, a lista é vazia (a seção some)
        assertEquals(listOf("Transporte", "Lazer", "Moradia", "Saúde"), TxForm.recentCategories(txs, Kind.EXPENSE, all - "Alimentação"))
        assertEquals(emptyList(), TxForm.recentCategories(emptyList(), Kind.EXPENSE, all))
        assertEquals(2, TxForm.recentCategories(txs, Kind.EXPENSE, all, n = 2).size)
    }

    // ---- sugestões de descrição
    @Test fun sugestoesDeDescricaoMaisUsadasPrimeiro() {
        val txs = listOf(
            tx("1", Kind.EXPENSE, "2026-10-01", "Alimentação", "Mercado"),
            tx("2", Kind.EXPENSE, "2026-10-05", "Alimentação", "mercado"),
            tx("3", Kind.EXPENSE, "2026-10-06", "Transporte", "Uber"),
            tx("4", Kind.EXPENSE, "2026-10-07", "Alimentação", "iFood"),
            tx("5", Kind.EXPENSE, "2026-10-08", "Outros", "Outros"),          // igual à categoria: não entra
            tx("6", Kind.INCOME, "2026-10-09", "Salário", "Empresa"),
        )
        assertEquals(listOf("mercado", "iFood", "Uber"), TxForm.descriptionSuggestions(txs, Kind.EXPENSE))
        assertEquals(listOf("Empresa"), TxForm.descriptionSuggestions(txs, Kind.INCOME))
        assertEquals(emptyList(), TxForm.descriptionSuggestions(emptyList(), Kind.EXPENSE))
    }
}
