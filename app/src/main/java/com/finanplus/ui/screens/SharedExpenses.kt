// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finanplus.BuildConfig
import com.finanplus.core.AppState
import com.finanplus.core.Money
import com.finanplus.core.Outcome
import com.finanplus.core.PersonStatus
import com.finanplus.core.Shared
import com.finanplus.core.SharedDraft
import com.finanplus.core.SharedExpense
import com.finanplus.core.SharedFilter
import com.finanplus.core.SharedPersonDraft
import com.finanplus.core.SharedSummary
import com.finanplus.core.SplitMode
import com.finanplus.data.Repo
import com.finanplus.data.SharedReceipts
import com.finanplus.export.SharedPdf
import com.finanplus.security.AppLock
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.DateField
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.MoneyField
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Despesas compartilhadas (core/Shared.kt): histórico, formulário, detalhe com pagamentos,
 * comprovante em PDF (baixar ou enviar pelo WhatsApp) e nota fiscal anexada.
 * Abre como as outras folhas do app (SheetHost), a partir de Meu dinheiro e do botão +.
 */

@Composable
private fun SharedHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Fin.c.muted)
    Spacer(Modifier.height(12.dp))
}

private fun money(hide: Boolean): (Long) -> String = { if (hide) "R$ ••••" else Money.format(it) }

@Composable
private fun stateColor(st: SharedSummary.State) = when (st) {
    SharedSummary.State.SETTLED -> Fin.c.green
    SharedSummary.State.PARTIAL -> Fin.c.accent
    SharedSummary.State.OPEN -> Fin.c.red
}

/** Resumo do card em Meu dinheiro. */
fun sharedCardSubtitle(s: AppState, hide: Boolean): String {
    if (s.shared.isEmpty()) return "Divida contas com amigos e família e acompanhe quem já pagou"
    val (pending, _) = Shared.totals(s.shared)
    val open = s.shared.count { Shared.summary(it).state != SharedSummary.State.SETTLED }
    return if (open == 0) "${s.shared.size} despesa(s) · tudo quitado"
    else "$open em aberto · a receber ${if (hide) "R$ ••••" else Money.format(pending)}"
}

// ------------------------------------------------------------------ histórico

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedListSheet(s: AppState) {
    val p = Fin.c
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val hide = LocalPrivacy.current
    val m = money(hide)
    // ao abrir: some com cópias decifradas antigas e com anexos que nenhuma despesa usa
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { SharedReceipts.clearTemp(ctx); SharedReceipts.cleanup(ctx, Shared.receiptsInUse(Repo.state.value)) }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var to by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var person by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val filter = SharedFilter(query, from, to, person, SharedSummary.State.entries.firstOrNull { it.name == state })
    val list = remember(s.shared, filter) { Shared.filter(s.shared, filter) }
    val (pending, received) = remember(s.shared) { Shared.totals(s.shared) }

    SharedHeader("Despesas compartilhadas", "Divida uma despesa e acompanhe quem já devolveu a parte dela.")
    Glass(radius = 22.dp, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("A receber", style = MaterialTheme.typography.bodySmall, color = p.muted)
                MoneyText(pending, color = if (pending > 0) p.red else p.text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
            Column(Modifier.weight(1f)) {
                Text("Já recebido", style = MaterialTheme.typography.bodySmall, color = p.muted)
                MoneyText(received, color = p.green, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton("Nova despesa compartilhada") { nav.open(Sheet.SharedEdit()) }

    if (s.shared.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Field("Pesquisar", query, { query = it }, placeholder = "Nome, observação ou participante")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Todas", selected = state.isEmpty()) { state = "" }
            Pill("Pendentes", selected = state == SharedSummary.State.OPEN.name) { state = SharedSummary.State.OPEN.name }
            Pill("Parciais", selected = state == SharedSummary.State.PARTIAL.name) { state = SharedSummary.State.PARTIAL.name }
            Pill("Quitadas", selected = state == SharedSummary.State.SETTLED.name) { state = SharedSummary.State.SETTLED.name }
            Pill(if (showFilters) "Menos filtros" else "Período e pessoa", selected = from != null || to != null || person.isNotEmpty(), icon = Ico.TUNE) { showFilters = !showFilters }
        }
        if (showFilters) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("De", from, { from = it }, Modifier.weight(1f), allowClear = true)
                DateField("Até", to, { to = it }, Modifier.weight(1f), allowClear = true)
            }
            SelectField("Participante", listOf("" to "Todos") + Shared.peopleNames(s.shared).map { it to it }, person, { person = it })
        }
    }

    if (s.shared.isEmpty()) Glass(Modifier.padding(top = 12.dp), radius = 22.dp, padding = 16.dp) {
        Text("Nenhuma despesa compartilhada ainda", fontWeight = FontWeight.Bold)
        Text(
            "Registre o mercado, o aluguel da casa de praia ou a conta do restaurante, diga quem pagou e o Finan+ calcula quanto cada pessoa deve devolver.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp),
        )
    } else if (list.isEmpty()) Text(
        "Nenhuma despesa encontrada com esses filtros.",
        style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 14.dp),
    )

    list.forEach { e ->
        val st = Shared.summary(e)
        Glass(Modifier.padding(top = 8.dp), radius = 20.dp, padding = 14.dp, onClick = { nav.open(Sheet.SharedDetail(e.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Ico.GROUP, p.accent, p.accent2, size = 42.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(e.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${e.date.br()} · pago por ${e.payer?.name ?: "—"} · ${e.people.size} pessoas",
                        style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        st.state.label + if (st.pending > 0) " · falta ${m(st.pending)}" else "",
                        style = MaterialTheme.typography.bodySmall, color = stateColor(st.state), fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
                MoneyText(e.total, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}

// ------------------------------------------------------------------ formulário

/** Linhas do formulário sobrevivem ao bloqueio do app (estado salvável). */
private val RowsSaver = listSaver<SnapshotStateList<SharedPersonDraft>, String>(
    save = { l -> l.map { listOf(it.id, it.name, it.share).joinToString("\u0001") } },
    restore = { l -> l.map { val x = it.split("\u0001"); SharedPersonDraft(x[0], x.getOrElse(1) { "" }, x.getOrElse(2) { "" }) }.toMutableStateList() },
)

@Composable
private fun SplitChoice(selected: SplitMode, onSelect: (SplitMode) -> Unit) {
    val p = Fin.c
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(17.dp)).background(p.accent2.copy(alpha = 0.7f)).padding(4.dp)) {
        listOf(SplitMode.EQUAL to "Dividir igualmente", SplitMode.CUSTOM to "Valor de cada um").forEach { (k, l) ->
            val on = k == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(k) }.semantics { this.selected = on }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Normal, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedEditor(s: AppState, id: String?, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val hide = LocalPrivacy.current
    val m = money(hide)
    val old = id?.let { x -> s.shared.firstOrNull { it.id == x } }
    if (id != null && old == null) { SharedHeader("Despesa não encontrada", "Ela pode ter sido excluída."); return }

    var name by rememberSaveable { mutableStateOf(old?.name ?: "") }
    var total by rememberSaveable { mutableStateOf(old?.let { Money.input(it.total) } ?: "") }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(old?.date ?: today) }
    var split by rememberSaveable { mutableStateOf(old?.split ?: SplitMode.EQUAL) }
    var note by rememberSaveable { mutableStateOf(old?.note ?: "") }
    var receipt by rememberSaveable { mutableStateOf(old?.receipt ?: "") }
    var receiptType by rememberSaveable { mutableStateOf(old?.receiptType ?: "") }
    var seq by rememberSaveable { mutableStateOf(0) }
    // participantes novos ganham um id provisório ("n1", "n2"…); ao salvar viram ids definitivos
    val rows = rememberSaveable(saver = RowsSaver) {
        (old?.people?.map { SharedPersonDraft(it.id, it.name, Money.input(it.share)) }
            ?: listOf(SharedPersonDraft("n-me", "Eu"), SharedPersonDraft("n-0", ""))).toMutableStateList()
    }
    var payer by rememberSaveable { mutableStateOf(old?.payerId ?: "n-me") }
    var busy by rememberSaveable { mutableStateOf(false) }

    val guard = LocalSheetGuard.current
    SideEffect {
        guard.dirty = name != (old?.name ?: "") || total != (old?.let { Money.input(it.total) } ?: "") || note != (old?.note ?: "") ||
            receipt != (old?.receipt ?: "") || split != (old?.split ?: SplitMode.EQUAL) ||
            (old != null && (rows.map { it.id to it.name } != old.people.map { it.id to it.name } || payer != old.payerId)) ||
            (old == null && rows.any { it.name.isNotBlank() && it.id != "n-me" })
    }

    val attach = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        AppLock.endExternal()
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try { Result.success(SharedReceipts.save(ctx, uri)) } catch (e: SharedReceipts.TooBig) { Result.failure(e) } catch (e: Exception) { Result.failure(e) }
            }
            busy = false
            r.onSuccess { (file, type) ->
                // um anexo escolhido agora e trocado de novo antes de salvar não fica perdido no aparelho
                if (receipt.isNotEmpty() && receipt != old?.receipt) SharedReceipts.delete(ctx, receipt)
                receipt = file; receiptType = type
            }.onFailure { e ->
                if (e is SharedReceipts.TooBig) dialogs.notice("Arquivo grande demais", "Escolha uma foto ou PDF de até 15 MB.")
                else dialogs.notice("Não foi possível anexar", "O arquivo não pôde ser lido. Tente de novo ou escolha outro.")
            }
        }
    }

    SharedHeader(if (old == null) "Nova despesa compartilhada" else "Editar despesa", "Quem pagou, quem divide e quanto cada um deve.")

    Glass(radius = 22.dp, padding = 14.dp) {
        Field("Nome da despesa", name, { name = it }, placeholder = "Ex.: Mercado do mês", maxLength = Shared.NAME_MAX)
        MoneyField("Valor total (R$)", total, { total = it })
        DateField("Data", date, { date = it })
    }

    Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        Text("Participantes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        SplitChoice(split) { split = it }
        val totalC = Money.parse(total)?.takeIf { it > 0 }
        val equal = if (split == SplitMode.EQUAL && totalC != null) Shared.equalShares(totalC, rows.size) else emptyList()
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Pessoa ${i + 1}", r.name, { v -> rows[i] = rows[i].copy(name = v) }, Modifier.weight(1f), placeholder = "Nome", maxLength = Shared.PERSON_MAX)
                if (split == SplitMode.CUSTOM) MoneyField("Valor (R$)", r.share, { v -> rows[i] = rows[i].copy(share = v) }, Modifier.weight(0.8f))
                else if (equal.isNotEmpty()) Text(m(equal[i]), Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (rows.size > 2) Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) {
                        rows.removeAt(i)
                        if (payer == r.id) payer = rows.first().id
                    }.semantics { contentDescription = "Remover ${r.name.ifBlank { "pessoa ${i + 1}" }}" },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(Ico.REMOVE, p.muted, size = 20.dp) }
            }
        }
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Adicionar participante", icon = Ico.ADD) {
                if (rows.size < Shared.PEOPLE_MAX) { seq++; rows.add(SharedPersonDraft("n${seq}", "")) }
                else dialogs.notice("Limite de participantes", "Use no máximo ${Shared.PEOPLE_MAX} pessoas por despesa.")
            }
        }
        if (split == SplitMode.EQUAL && equal.isNotEmpty()) Text(
            "${rows.size} pessoas · cada uma com ${m(equal.last())}" + if (equal.first() != equal.last()) " (os centavos que sobram ficam com a primeira)" else "",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp),
        )
        if (split == SplitMode.CUSTOM && totalC != null) {
            val sum = rows.sumOf { Money.parse(it.share)?.takeIf { v -> v >= 0 } ?: 0L }
            val diff = totalC - sum
            Text(
                when { diff == 0L -> "As partes somam o total."; diff > 0 -> "Soma ${m(sum)} · faltam ${m(diff)}"; else -> "Soma ${m(sum)} · passou ${m(-diff)}" },
                style = MaterialTheme.typography.bodySmall, color = if (diff == 0L) p.green else p.red, modifier = Modifier.padding(top = 4.dp),
            )
        }
        SelectField(
            "Quem pagou",
            rows.mapIndexed { i, r -> r.id to r.name.ifBlank { "Pessoa ${i + 1}" } },
            payer.takeIf { x -> rows.any { it.id == x } } ?: rows.first().id,
            { payer = it },
        )
    }

    Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        Field("Observação (opcional)", note, { note = it }, placeholder = "Ex.: compra do churrasco", maxLength = Shared.NOTE_MAX)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (busy) "Anexando…" else if (receipt.isEmpty()) "Anexar nota ou comprovante" else "Trocar anexo", icon = Ico.ATTACH) {
                if (!busy) { AppLock.allowExternalOnce(); attach.launch(arrayOf("image/*", "application/pdf")) }
            }
            if (receipt.isNotEmpty()) Pill("Remover", danger = true) {
                if (receipt != old?.receipt) SharedReceipts.delete(ctx, receipt)
                receipt = ""; receiptType = ""
            }
        }
        Text(
            if (receipt.isEmpty()) "Foto ou PDF de até 15 MB. Fica só neste aparelho, criptografado."
            else "Comprovante anexado (${if (receiptType == "application/pdf") "PDF" else "imagem"}). Fica só neste aparelho, criptografado; o backup não leva o arquivo.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp),
        )
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(if (old == null) "Salvar despesa" else "Salvar alterações") {
        if (busy) return@PrimaryButton
        val payerIndex = rows.indexOfFirst { it.id == payer }.takeIf { it >= 0 } ?: 0
        val draft = SharedDraft(name, total, date, split, rows.toList(), payerIndex, note, receipt, if (receipt.isEmpty()) "" else receiptType)
        val before = s.shared.mapTo(HashSet()) { it.id }
        when (val o = Shared.save(Repo.state.value, id, draft)) {
            is Outcome.Err -> dialogs.notice(o.title, o.message)
            is Outcome.Ok -> {
                Repo.replace(o.state)
                // o anexo antigo foi trocado ou removido: apaga o arquivo do aparelho
                if (old != null && old.receipt.isNotEmpty() && old.receipt != receipt) SharedReceipts.delete(ctx, old.receipt)
                guard.dirty = false
                val newId = id ?: o.state.shared.firstOrNull { it.id !in before }?.id
                if (newId != null) nav.open(Sheet.SharedDetail(newId)) else close()
            }
        }
    }
    if (old != null) {
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Excluir despesa", danger = true) { confirmDelete(dialogs, ctx, old, close) }
    }
}

private fun confirmDelete(dialogs: com.finanplus.ui.components.Dialogs, ctx: Context, e: SharedExpense, close: () -> Unit) {
    dialogs.confirm(
        "Excluir despesa",
        "Excluir “${e.name}”, com os pagamentos registrados" + (if (e.receipt.isNotEmpty()) " e o comprovante anexado" else "") + "? Isso não pode ser desfeito.",
        ok = "Excluir", danger = true,
    ) {
        Repo.update { Shared.delete(it, e.id) }
        SharedReceipts.delete(ctx, e.receipt)
        close()
    }
}

// ------------------------------------------------------------------ detalhe

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedDetailSheet(s: AppState, id: String, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val hide = LocalPrivacy.current
    val m = money(hide)
    val e = s.shared.firstOrNull { it.id == id }
    if (e == null) { SharedHeader("Despesa não encontrada", "Ela pode ter sido excluída."); Pill("Ver todas", icon = Ico.PREV) { nav.open(Sheet.SharedList) }; return }
    val st = remember(e) { Shared.summary(e) }
    var busy by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        AppLock.endExternal()
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val cur = Repo.state.value.shared.first { it.id == id }
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { SharedPdf.write(it, cur, BuildConfig.VERSION_NAME) }
                }.isSuccess
            }
            busy = false
            if (ok) dialogs.notice("PDF salvo", "Comprovante salvo no local escolhido.")
            else dialogs.notice("Não foi possível gerar o PDF", "Tente de novo ou escolha outro local para salvar.")
        }
    }

    Pill("Todas as despesas", icon = Ico.PREV) { nav.open(Sheet.SharedList) }
    Spacer(Modifier.height(8.dp))
    SharedHeader(e.name, "${e.date.br()} · pago por ${e.payer?.name ?: "—"} · " + if (e.split == SplitMode.EQUAL) "dividida igualmente" else "valor de cada um")

    Glass(radius = 22.dp, padding = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Total", e.total, p.text), Triple("Recebido", st.received, p.green), Triple("Pendente", st.pending, if (st.pending > 0) p.red else p.text)).forEach { (l, v, c) ->
                Column(Modifier.weight(1f)) {
                    Text(l, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1)
                    MoneyText(v, color = c, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                }
            }
        }
        if (st.toReceive > 0) Bar(st.received.toFloat() / st.toReceive, p.green, Modifier.padding(top = 10.dp))
        Text(
            st.state.label + " · a receber de ${st.people.count { it.owed > 0 }} pessoa(s): ${m(st.toReceive)}",
            style = MaterialTheme.typography.bodySmall, color = stateColor(st.state), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp),
        )
    }

    Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        Text("Quem deve e quanto", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        st.people.forEach { ps ->
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(p.accent2.copy(alpha = 0.55f)).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ps.person.name + if (ps.isPayer) " · pagou a despesa" else "", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (ps.isPayer) "Parte dele: ${m(ps.person.share)} · recebe ${m(st.toReceive)} dos outros"
                            else "Parte: ${m(ps.owed)} · devolveu ${m(ps.paid)}" + if (ps.remaining > 0) " · falta ${m(ps.remaining)}" else "",
                            style = MaterialTheme.typography.bodySmall, color = p.muted,
                        )
                    }
                    val c = when (ps.state) { PersonStatus.State.PAID -> p.green; PersonStatus.State.PENDING -> p.red; PersonStatus.State.PARTIAL -> p.accent; else -> p.muted }
                    Text(ps.state.label, color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (!ps.isPayer && ps.remaining > 0) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Registrar pagamento") { nav.open(Sheet.SharedPay(e.id, ps.person.id)) }
                    Pill("Quitar tudo", icon = Ico.CHECK) {
                        dialogs.confirm("Quitar", "Registrar que ${ps.person.name} devolveu ${m(ps.remaining)} hoje?", ok = "Quitar") {
                            Repo.commit(Shared.settle(Repo.state.value, e.id, ps.person.id, today))?.let { dialogs.notice(it.title, it.message) }
                        }
                    }
                }
            }
        }
    }

    if (e.payments.isNotEmpty()) Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        Text("Pagamentos registrados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        e.payments.sortedWith(compareByDescending<com.finanplus.core.SharedPayment> { it.date }.thenByDescending { it.id }).forEach { pay ->
            val who = e.person(pay.personId)?.name ?: "Participante"
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(who, fontWeight = FontWeight.SemiBold)
                    Text(pay.date.br(), style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
                MoneyText(pay.value, color = p.green, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Box(
                    Modifier.padding(start = 4.dp).size(40.dp).clip(CircleShape).clickable(role = Role.Button) {
                        dialogs.confirm("Excluir pagamento", "Excluir o pagamento de ${m(pay.value)} de $who? O valor volta a ficar pendente.", ok = "Excluir", danger = true) {
                            Repo.update { Shared.deletePayment(it, e.id, pay.id) }
                        }
                    }.semantics { contentDescription = "Excluir pagamento de $who" },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(Ico.REMOVE, p.muted, size = 20.dp) }
            }
        }
    }

    if (e.note.isNotBlank() || e.receipt.isNotEmpty()) Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        if (e.note.isNotBlank()) {
            Text("Observação", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(e.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (e.receipt.isNotEmpty()) Pill("Ver comprovante anexado", Modifier.padding(top = 8.dp), icon = Ico.ATTACH) {
            scope.launch {
                val uri = withContext(Dispatchers.IO) {
                    runCatching { SharedReceipts.read(ctx, e.receipt)?.let { SharedReceipts.tempUri(ctx, "comprovante." + SharedReceipts.extension(e.receiptType), it) } }.getOrNull()
                }
                if (uri == null) dialogs.notice("Comprovante indisponível", "O arquivo não está neste aparelho (por exemplo, depois de restaurar um backup em outro celular). Anexe de novo em Editar.")
                else if (!openExternal(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(uri, e.receiptType.ifEmpty { "*/*" }), uri))
                    dialogs.notice("Nenhum app para abrir", "Instale um visualizador de imagens ou de PDF.")
            }
        }
    }

    Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
        Text("Compartilhar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            "Uma imagem com a marca do Finan+ para mandar no grupo, ou o comprovante completo em PDF: a parte de cada pessoa, quem já pagou e o que falta. Os dois mostram os valores mesmo com “Ocultar valores” ligado.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp),
        )
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (busy) "Gerando…" else "Compartilhar como imagem", icon = Ico.SHARE) {
                if (busy) return@Pill
                busy = true
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        runCatching {
                            val cur = Repo.state.value.shared.first { it.id == id }
                            SharedReceipts.tempUri(ctx, Shared.imageName(cur), com.finanplus.export.SharedImage.png(ctx, cur))
                        }.getOrNull()
                    }
                    busy = false
                    if (uri == null) dialogs.notice("Não foi possível gerar a imagem", "Tente de novo em instantes.")
                    else shareImage(ctx, uri, shareText(e, st))
                }
            }
            Pill(if (busy) "Gerando…" else "Baixar PDF", icon = Ico.RECEIPT) {
                if (!busy) { AppLock.allowExternalOnce(); save.launch(Shared.pdfName(e)) }
            }
            Pill("PDF pelo WhatsApp", icon = Ico.SHARE) {
                if (busy) return@Pill
                busy = true
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        runCatching {
                            val cur = Repo.state.value.shared.first { it.id == id }
                            val bytes = java.io.ByteArrayOutputStream().also { SharedPdf.write(it, cur, BuildConfig.VERSION_NAME) }.toByteArray()
                            SharedReceipts.tempUri(ctx, Shared.pdfName(cur), bytes)
                        }.getOrNull()
                    }
                    busy = false
                    if (uri == null) dialogs.notice("Não foi possível gerar o PDF", "Tente de novo em instantes.")
                    else sendWhatsApp(ctx, uri, shareText(e, st))
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Editar despesa") { nav.open(Sheet.SharedEdit(e.id)) }
        Pill("Excluir", danger = true) { confirmDelete(dialogs, ctx, e, close) }
    }
}

/** Mensagem que acompanha o PDF no WhatsApp. */
private fun shareText(e: SharedExpense, st: SharedSummary): String = buildString {
    append(e.name).append(" — ").append(Money.format(e.total)).append(" (").append(e.date.br()).append(")\n")
    append("Pago por ").append(e.payer?.name ?: "—").append('\n')
    val debtors = st.people.filter { it.remaining > 0 }
    if (debtors.isEmpty()) append("Tudo quitado. Obrigado!")
    else debtors.forEach { append("• ").append(it.person.name).append(": falta ").append(Money.format(it.remaining)).append('\n') }
}.trimEnd()

/** Abre outro app com a permissão de leitura do arquivo; false se nenhum app aceitar. */
private fun openExternal(ctx: Context, intent: Intent, uri: Uri): Boolean {
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri("", uri) }
    AppLock.allowExternalOnce()
    return try { ctx.startActivity(intent); true }
    catch (_: ActivityNotFoundException) { AppLock.endExternal(); false }
}

/** Compartilha a imagem pelo app que a pessoa escolher (WhatsApp, Telegram, e-mail…). */
private fun shareImage(ctx: Context, uri: Uri, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri("", uri) }
    AppLock.allowExternalOnce()
    try { ctx.startActivity(Intent.createChooser(send, "Compartilhar despesa").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { AppLock.endExternal() }
}

/** Envia o PDF direto pelo WhatsApp (ou WhatsApp Business); sem ele instalado, abre o "Compartilhar" do Android. */
private fun sendWhatsApp(ctx: Context, uri: Uri, text: String) {
    fun base() = Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri("", uri) }
    AppLock.allowExternalOnce()
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try { ctx.startActivity(base().setPackage(pkg)); return } catch (_: ActivityNotFoundException) { }
    }
    try { ctx.startActivity(Intent.createChooser(base(), "Enviar comprovante").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { AppLock.endExternal() }
}

// ------------------------------------------------------------------ registrar pagamento

@Composable
fun SharedPaySheet(s: AppState, expenseId: String, personId: String) {
    val p = Fin.c
    val nav = LocalNav.current
    val dialogs = LocalDialogs.current
    val today = LocalToday.current
    val e = s.shared.firstOrNull { it.id == expenseId }
    val ps = e?.let { Shared.summary(it).people.firstOrNull { x -> x.person.id == personId } }
    if (e == null || ps == null) { SharedHeader("Pagamento", "A despesa ou o participante não existe mais."); return }
    var value by rememberSaveable { mutableStateOf(Money.input(ps.remaining)) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(today) }
    val guard = LocalSheetGuard.current
    SideEffect { guard.dirty = value != Money.input(ps.remaining) || date != today }

    SharedHeader("Registrar pagamento", "${ps.person.name} devolvendo a ${e.payer?.name ?: "quem pagou"} · ${e.name}")
    Glass(radius = 22.dp, padding = 14.dp) {
        Text("Falta ${if (LocalPrivacy.current) "R$ ••••" else Money.format(ps.remaining)} de ${if (LocalPrivacy.current) "R$ ••••" else Money.format(ps.owed)}", color = p.muted, style = MaterialTheme.typography.bodySmall)
        MoneyField("Valor pago (R$)", value, { value = it })
        DateField("Data do pagamento", date, { date = it })
        Text("Pode ser parcial: o restante continua pendente.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 4.dp))
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Salvar pagamento") {
        val err = Repo.commit(Shared.addPayment(Repo.state.value, expenseId, personId, value, date))
        if (err != null) dialogs.notice(err.title, err.message)
        else { guard.dirty = false; nav.open(Sheet.SharedDetail(expenseId)) }
    }
}
