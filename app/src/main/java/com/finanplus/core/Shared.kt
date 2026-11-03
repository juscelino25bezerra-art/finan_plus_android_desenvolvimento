// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import java.time.LocalDate

/**
 * Despesas compartilhadas: uma despesa paga por uma pessoa e dividida com outras.
 * Quem pagou recebe de cada participante a parte dele; os pagamentos podem ser parciais.
 *
 * Não mexe no saldo das contas nem nos lançamentos: é um controle de quem deve a quem.
 * Sem Android: testado em JVM (SharedTest). Regras e formato em DESPESAS-COMPARTILHADAS.md.
 */

/** Participante e a parte dele na despesa ([share], em centavos). */
data class SharedPerson(val id: String, val name: String, val share: Cents)

/** Valor que [personId] devolveu a quem pagou a despesa. */
data class SharedPayment(val id: String, val personId: String, val value: Cents, val date: LocalDate)

enum class SplitMode(val json: String) {
    /** o total dividido em partes iguais (centavos que sobram ficam com o primeiro participante) */
    EQUAL("equal"),
    /** cada participante com o valor que consumiu */
    CUSTOM("custom");

    companion object { fun of(v: Any?) = entries.firstOrNull { it.json == v } ?: EQUAL }
}

data class SharedExpense(
    val id: String,
    val name: String,
    val total: Cents,
    val date: LocalDate,
    /** id do participante que pagou a conta */
    val payerId: String,
    val people: List<SharedPerson>,
    val split: SplitMode = SplitMode.EQUAL,
    val note: String = "",
    /** nome do arquivo do comprovante, guardado cifrado só neste aparelho ("" = sem comprovante) */
    val receipt: String = "",
    /** tipo do comprovante ("image/jpeg", "application/pdf"…) */
    val receiptType: String = "",
    val payments: List<SharedPayment> = emptyList(),
) {
    fun person(id: String) = people.firstOrNull { it.id == id }
    val payer get() = person(payerId)
}

/** Situação de um participante: quanto deve a quem pagou, quanto já devolveu e quanto falta. */
data class PersonStatus(val person: SharedPerson, val isPayer: Boolean, val owed: Cents, val paid: Cents) {
    val remaining get() = maxOf(0L, owed - paid)
    val state get() = when {
        isPayer || owed == 0L -> State.NONE
        remaining == 0L -> State.PAID
        paid > 0 -> State.PARTIAL
        else -> State.PENDING
    }
    enum class State(val label: String) { NONE("Pagou a despesa"), PAID("Quitado"), PARTIAL("Pagou parte"), PENDING("Pendente") }
}

/** Resumo da despesa: [toReceive] = soma do que os outros devem a quem pagou. */
data class SharedSummary(val people: List<PersonStatus>, val toReceive: Cents, val received: Cents) {
    val pending get() = maxOf(0L, toReceive - received)
    val state get() = when {
        toReceive == 0L || pending == 0L -> State.SETTLED
        received > 0 -> State.PARTIAL
        else -> State.OPEN
    }
    enum class State(val label: String) { OPEN("Pendente"), PARTIAL("Parcialmente paga"), SETTLED("Quitada") }
}

/** Linha do formulário: [id] vazio = participante novo; [share] em texto (só na divisão personalizada). */
data class SharedPersonDraft(val id: String, val name: String, val share: String = "")

data class SharedDraft(
    val name: String,
    val total: String,
    val date: LocalDate?,
    val split: SplitMode,
    val people: List<SharedPersonDraft>,
    /** posição de quem pagou em [people] */
    val payerIndex: Int,
    val note: String = "",
    val receipt: String = "",
    val receiptType: String = "",
)

/** Filtros do histórico. [person] compara o nome sem diferenciar maiúsculas. */
data class SharedFilter(
    val query: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val person: String = "",
    /** null = todas */
    val state: SharedSummary.State? = null,
)

object Shared {
    const val NAME_MAX = 60
    const val PERSON_MAX = 40
    const val NOTE_MAX = 500
    const val PEOPLE_MAX = 50
    const val PAYMENTS_MAX = 500

    private fun err(msg: String, title: String = "Revise os dados") = Outcome.Err(title, msg)

    /** Partes iguais: reaproveita a divisão das parcelas (o resto dos centavos fica com o primeiro). */
    fun equalShares(total: Cents, n: Int): List<Cents> = if (n <= 0) emptyList() else Finance.splitInstallments(total, n)

    fun summary(e: SharedExpense): SharedSummary {
        val paidBy = HashMap<String, Cents>()
        for (p in e.payments) paidBy[p.personId] = (paidBy[p.personId] ?: 0L) + p.value
        val people = e.people.map { p ->
            val isPayer = p.id == e.payerId
            PersonStatus(p, isPayer, if (isPayer) 0L else p.share, paidBy[p.id] ?: 0L)
        }
        val toReceive = people.sumOf { it.owed }
        // um pagamento acima da parte (não deveria acontecer: Ops barra) não conta como "recebido" a mais
        val received = people.sumOf { minOf(it.paid, it.owed) }
        return SharedSummary(people, toReceive, received)
    }

    /** Totais de todas as despesas: o que falta receber e o que já foi recebido. */
    fun totals(list: List<SharedExpense>): Pair<Cents, Cents> {
        var pending = 0L; var received = 0L
        for (e in list) { val s = summary(e); pending += s.pending; received += s.received }
        return pending to received
    }

    /** Nomes de todos os participantes já usados (para o filtro), sem repetir e em ordem alfabética. */
    fun peopleNames(list: List<SharedExpense>): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (e in list) for (p in e.people) seen.putIfAbsent(fold(p.name), p.name)
        return seen.values.sortedBy { fold(it) }
    }

    /** Histórico: mais recentes primeiro, com busca (nome, observação, participantes), período, pessoa e situação. */
    fun filter(list: List<SharedExpense>, f: SharedFilter): List<SharedExpense> {
        val q = fold(f.query.trim())
        val who = fold(f.person.trim())
        return list.filter { e ->
            (f.from == null || !e.date.isBefore(f.from)) && (f.to == null || !e.date.isAfter(f.to)) &&
                (who.isEmpty() || e.people.any { fold(it.name) == who }) &&
                (f.state == null || summary(e).state == f.state) &&
                (q.isEmpty() || fold(e.name + " " + e.note + " " + e.people.joinToString(" ") { it.name }).contains(q))
        }.sortedWith(compareByDescending<SharedExpense> { it.date }.thenByDescending { it.id })
    }

    private fun fold(s: String) = com.finanplus.core.assist.Text.fold(s)

    // ---------------- alterações (validadas; o estado só muda com Outcome.Ok) ----------------

    fun save(s: AppState, editId: String?, d: SharedDraft): Outcome {
        val name = d.name.trim().take(NAME_MAX)
        if (name.isEmpty()) return err("Dê um nome para a despesa. Ex.: Mercado do mês", "Nome da despesa")
        val total = Money.parse(d.total)
        if (total == null || total <= 0) return err("Informe o valor total, maior que zero. Ex.: 450,00", "Valor inválido")
        val date = d.date ?: return err("Informe a data da despesa.", "Data inválida")
        val rows = d.people.map { it.copy(name = it.name.trim().take(PERSON_MAX)) }
        if (rows.any { it.name.isEmpty() }) return err("Preencha o nome de todos os participantes (ou remova a linha vazia).", "Participantes")
        if (rows.size < 2) return err("Adicione pelo menos duas pessoas: quem pagou e quem vai dividir.", "Participantes")
        if (rows.size > PEOPLE_MAX) return err("Use no máximo $PEOPLE_MAX participantes.", "Participantes")
        val names = HashSet<String>()
        for (r in rows) if (!names.add(fold(r.name))) return err("\"${r.name}\" aparece duas vezes. Use nomes diferentes (ex.: João S. e João P.).", "Participantes")
        if (d.payerIndex !in rows.indices) return err("Escolha quem pagou a despesa.", "Quem pagou")

        val shares: List<Cents> = when (d.split) {
            SplitMode.EQUAL -> equalShares(total, rows.size)
            SplitMode.CUSTOM -> {
                val list = rows.map { r -> if (r.share.isBlank()) 0L else Money.parse(r.share) ?: return err("Valor inválido para ${r.name}. Ex.: 150,00", "Valor inválido") }
                if (list.any { it < 0 }) return err("Os valores não podem ser negativos.", "Valor inválido")
                val sum = list.fold(0L) { a, b -> a + b }
                if (sum != total) {
                    val diff = total - sum
                    return err(
                        "A soma das partes (${Money.format(sum)}) precisa ser igual ao total (${Money.format(total)}). " +
                            (if (diff > 0) "Faltam ${Money.format(diff)}." else "Passou ${Money.format(-diff)}."),
                        "Divisão não fecha",
                    )
                }
                list
            }
        }

        val old = editId?.let { id -> s.shared.firstOrNull { it.id == id } ?: return err("Despesa não encontrada.") }
        val oldIds = old?.people?.mapTo(HashSet()) { it.id } ?: emptySet()
        val people = rows.mapIndexed { i, r -> SharedPerson(r.id.takeIf { it in oldIds } ?: Ids.new(), r.name, shares[i]) }
        val payerId = people[d.payerIndex].id
        val payments = old?.payments ?: emptyList()

        val e = SharedExpense(
            id = old?.id ?: Ids.new(), name = name, total = total, date = date, payerId = payerId, people = people,
            split = d.split, note = d.note.trim().take(NOTE_MAX), receipt = d.receipt, receiptType = d.receiptType,
            payments = payments,
        )
        // editar não pode deixar pagamentos "soltos": de quem saiu, de quem virou o pagador ou acima da nova parte
        if (old != null) {
            val st = summary(e)
            for (pid in payments.mapTo(LinkedHashSet()) { it.personId }) {
                val who = st.people.firstOrNull { it.person.id == pid }
                val oldName = old.person(pid)?.name ?: "um participante"
                if (who == null) return err("$oldName tem pagamentos registrados. Exclua os pagamentos antes de remover essa pessoa.", "Pagamentos registrados")
                if (who.isPayer) return err("${who.person.name} tem pagamentos registrados e não pode ser quem pagou. Exclua os pagamentos antes.", "Pagamentos registrados")
                if (who.paid > who.owed) return err(
                    "${who.person.name} já devolveu ${Money.format(who.paid)}, mais do que a nova parte (${Money.format(who.owed)}). Exclua algum pagamento antes.",
                    "Pagamentos registrados",
                )
            }
        }
        val next = if (old == null) s.copy(shared = s.shared + e) else s.copy(shared = s.shared.map { if (it.id == e.id) e else it })
        return s.growthError(next)?.let { err("$it Exclua despesas antigas antes de criar novas.", "Limite atingido") } ?: Outcome.Ok(next)
    }

    /** Registra que [personId] devolveu [value] (total ou parcial) a quem pagou. */
    fun addPayment(s: AppState, expenseId: String, personId: String, value: String, date: LocalDate?): Outcome {
        val e = s.shared.firstOrNull { it.id == expenseId } ?: return err("Despesa não encontrada.")
        val who = summary(e).people.firstOrNull { it.person.id == personId } ?: return err("Participante não encontrado.")
        if (who.isPayer) return err("${who.person.name} pagou a despesa: não há o que devolver.", "Quem pagou")
        val v = Money.parse(value)
        if (v == null || v <= 0) return err("Informe um valor maior que zero. Ex.: 50,00", "Valor inválido")
        if (who.remaining == 0L) return err("${who.person.name} já quitou a parte dele.", "Já quitado")
        if (v > who.remaining) return err("${who.person.name} deve ${Money.format(who.remaining)}. Informe até esse valor.", "Valor acima do devido")
        val d = date ?: return err("Informe a data do pagamento.", "Data inválida")
        if (e.payments.size >= PAYMENTS_MAX) return err("Limite de $PAYMENTS_MAX pagamentos nesta despesa.", "Limite atingido")
        val pay = SharedPayment(Ids.new(), personId, v, d)
        return Outcome.Ok(s.copy(shared = s.shared.map { if (it.id == expenseId) it.copy(payments = it.payments + pay) else it }))
    }

    /** Quita tudo o que [personId] ainda deve, na data [date]. */
    fun settle(s: AppState, expenseId: String, personId: String, date: LocalDate): Outcome {
        val e = s.shared.firstOrNull { it.id == expenseId } ?: return err("Despesa não encontrada.")
        val who = summary(e).people.firstOrNull { it.person.id == personId } ?: return err("Participante não encontrado.")
        return addPayment(s, expenseId, personId, Money.input(who.remaining), date)
    }

    fun deletePayment(s: AppState, expenseId: String, paymentId: String): AppState =
        s.copy(shared = s.shared.map { if (it.id == expenseId) it.copy(payments = it.payments.filterNot { p -> p.id == paymentId }) else it })

    fun delete(s: AppState, id: String): AppState = s.copy(shared = s.shared.filterNot { it.id == id })

    /** Troca (ou remove, com "") o comprovante de uma despesa já salva. */
    fun setReceipt(s: AppState, id: String, file: String, type: String): AppState =
        s.copy(shared = s.shared.map { if (it.id == id) it.copy(receipt = file, receiptType = if (file.isEmpty()) "" else type) else it })

    /** Arquivos de comprovante em uso (os demais podem ser apagados do aparelho). */
    fun receiptsInUse(s: AppState): Set<String> = s.shared.mapNotNullTo(HashSet()) { it.receipt.ifEmpty { null } }

    /** Nome da imagem: "finanplus-mercado-do-mes-2026-11-02.png". */
    fun imageName(e: SharedExpense): String = pdfName(e).removePrefix("comprovante-").removeSuffix(".pdf").let { "finanplus-$it.png" }

    /** Nome do PDF: "comprovante-mercado-do-mes-2026-11-02.pdf". */
    fun pdfName(e: SharedExpense): String {
        val slug = fold(e.name).lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "despesa" }
        return "comprovante-$slug-${e.date}.pdf"
    }
}
