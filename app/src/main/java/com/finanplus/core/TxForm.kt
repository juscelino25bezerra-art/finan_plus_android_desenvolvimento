// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import com.finanplus.core.assist.Text as AssistText

/**
 * Valor digitado na tela de lançamento: formatação em tempo real no padrão pt-BR.
 * Só texto: o valor salvo continua sendo calculado por [Money.parse], como antes.
 */
object AmountInput {
    /** Mais algarismos que isto passam do limite de [Money.MAX_ABS]. */
    const val MAX_INT_DIGITS = 13

    /**
     * Texto digitado → texto formatado: milhar com ponto, vírgula decimal com até 2 casas.
     * "" → "", "0" → "0", "12,5" → "12,5", "1234,56" → "1.234,56", "1234" → "1.234".
     * Um ponto digitado no fim ("12.") vale como vírgula (teclado com ponto decimal); os pontos do meio são o
     * agrupamento de milhar que a própria formatação põe, então saem e voltam.
     */
    fun format(raw: String): String {
        var s = raw.filter { it in '0'..'9' || it == ',' || it == '.' }
        if (s.endsWith('.') && ',' !in s) s = s.dropLast(1) + ","
        s = s.replace(".", "")
        val comma = s.indexOf(',')
        val intDigits = (if (comma >= 0) s.substring(0, comma) else s).filter { it in '0'..'9' }.take(MAX_INT_DIGITS)
        val dec = if (comma >= 0) s.substring(comma + 1).filter { it in '0'..'9' }.take(2) else null
        if (intDigits.isEmpty() && dec == null) return ""
        // zeros à esquerda não valem ("007" → "7"), mas "0" e "0,5" ficam
        val clean = intDigits.trimStart('0').ifEmpty { if (intDigits.isEmpty() && dec == null) "" else "0" }
        val sb = StringBuilder()
        clean.forEachIndexed { i, c -> if (i > 0 && (clean.length - i) % 3 == 0) sb.append('.'); sb.append(c) }
        return if (dec != null) "$sb,$dec" else sb.toString()
    }

    /** Valor em centavos do texto formatado (null se vazio). */
    fun cents(text: String): Cents? = Money.parse(text)

    /** Só valor maior que zero pode ser salvo (zero e vazio não). */
    fun isPositive(text: String): Boolean = (cents(text) ?: 0L) > 0L

    /** Digitou algo, mas o valor é zero ("0", "0,00"): é quando o erro discreto aparece embaixo do valor. */
    fun isZeroTyped(text: String): Boolean = text.isNotEmpty() && cents(text) == 0L

    /** Valor guardado (centavos) → texto do campo, já formatado ("1.234,56"). */
    fun fromCents(c: Cents): String = format(Money.input(c))
}

/**
 * Regras da tela de lançamento (Receita e Despesa) que não dependem do Android: categorias, recentes e
 * sugestões de descrição. Testado em TxFormTest. Ver LANCAMENTO-NOVA-TELA.md.
 */
object TxForm {
    /** Cartão é forma de pagamento (campo "Pagar com"), não categoria. */
    const val LEGACY_CARD_CATEGORY = "Cartão de Crédito"
    const val OTHERS = "Outros"

    private fun isLegacyCard(c: String) = AssistText.fold(c.trim()) == AssistText.fold(LEGACY_CARD_CATEGORY)

    /** Nome mostrado: lançamento antigo salvo como "Cartão de Crédito" aparece como "Outros" (o dado não muda). */
    fun displayCategory(c: String): String = if (isLegacyCard(c)) OTHERS else c

    /** Categorias escolhidas na gaveta: as do tipo, sem "Cartão de Crédito". */
    fun choices(cats: List<String>): List<String> = cats.filterNot { isLegacyCard(it) }.distinct()

    /**
     * Ao trocar Receita/Despesa: a categoria escolhida continua se existir no novo tipo; senão a seleção é limpa ("").
     * A comparação ignora maiúsculas e acentos ("Saude" = "Saúde").
     */
    fun categoryAfterKindChange(current: String, available: List<String>): String =
        available.firstOrNull { AssistText.fold(it) == AssistText.fold(current) && current.isNotBlank() } ?: ""

    /** Busca na gaveta: contém o texto, sem diferenciar maiúsculas e acentos. Busca vazia = tudo. */
    fun filter(list: List<String>, query: String): List<String> {
        val q = AssistText.fold(query.trim())
        return if (q.isEmpty()) list else list.filter { AssistText.fold(it).contains(q) }
    }

    /**
     * "Usadas recentemente": até [n] categorias do tipo, da usada mais recentemente para a menos
     * (data do lançamento; empate: o lançamento gravado depois). Só as que ainda existem em [available].
     * Reservas para meta e pagamentos de fatura não contam. Sem histórico, lista vazia (a seção some).
     */
    fun recentCategories(txs: List<Tx>, kind: Kind, available: List<String>, n: Int = 4): List<String> {
        val ok = available.associateBy { AssistText.fold(it) }
        return txs.withIndex()
            .filter { (_, t) -> t.kind == kind && t.isFlow && t.category.isNotBlank() }
            .sortedWith(compareByDescending<IndexedValue<Tx>> { it.value.date }.thenByDescending { it.index })
            .mapNotNull { (_, t) -> ok[AssistText.fold(t.category)] }
            .distinct().take(n)
    }

    /**
     * Sugestões de descrição: as mais usadas do tipo (empate: a mais recente), sem repetir. Descrição igual à
     * categoria não entra (é o que o app grava quando a descrição fica em branco).
     */
    fun descriptionSuggestions(txs: List<Tx>, kind: Kind, n: Int = 8): List<String> {
        val used = txs.withIndex().filter { (_, t) -> t.kind == kind && t.isFlow && t.desc.isNotBlank() && t.desc.trim() != t.category.trim() }
        val groups = used.groupBy { AssistText.fold(it.value.desc.trim()) }
        return groups.values.map { g ->
            val last = g.maxWith(compareBy<IndexedValue<Tx>> { it.value.date }.thenBy { it.index })
            Triple(last.value.desc.trim(), g.size, last)
        }.sortedWith(compareByDescending<Triple<String, Int, IndexedValue<Tx>>> { it.second }
            .thenByDescending { it.third.value.date }.thenByDescending { it.third.index })
            .map { it.first }.take(n)
    }
}
