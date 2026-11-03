// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanplus.R
import com.finanplus.core.MoneyView
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.clickable
import com.finanplus.ui.components.AppIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.Ops
import com.finanplus.core.Outcome
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.ManageItem
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin

@Composable
fun AccountsScreen(s: AppState) {
    val nav = LocalNav.current
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = com.finanplus.ui.components.LocalToday.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(ctx) { com.finanplus.data.DevicePrefs.get(ctx) }
    val dev by prefs.flow.collectAsStateWithLifecycle()
    val accountsOpen = dev.moneyAccountsOpen
    val cardsOpen = dev.moneyCardsOpen
    val usage = remember(s, today) { Finance.budgetUsage(s, today.ym()) }
    val overview = remember(s, today) { MoneyView.overview(s, today) }
    val cards = remember(s, today) { MoneyView.cards(s, today) }
    val firstDue = cards.firstOrNull { it.invoice != null }?.card?.id
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // índices fixos dos itens (os cartões vêm logo depois do cabeçalho de Cartões)
    val cardsHeaderIndex = 4

    LazyColumn(state = list, contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        // 0: título (sem subtítulo)
        item { Text(stringResource(R.string.money_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }

        // 1: visão geral em três colunas
        item { OverviewCard(overview) }

        // 2: cabeçalho de Contas (recolhível) com "+ Conta"
        item {
            CollapsibleHeader(
                title = stringResource(R.string.money_accounts),
                collapsedSummary = MoneyView.accountsCollapsed(s, hide),
                open = accountsOpen,
                onToggle = { prefs.update { it.copy(moneyAccountsOpen = !accountsOpen) } },
                addLabel = stringResource(R.string.money_add_account), addDescription = stringResource(R.string.money_add_account_cd),
                tag = "money_accounts_header",
            ) { nav.open(Sheet.AccountEdit()) }
        }
        // 3: contas
        item {
            AnimatedVisibility(accountsOpen, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                Glass(Modifier.fillMaxWidth().padding(top = 4.dp), radius = 22.dp, padding = 0.dp) {
                    if (s.accounts.isEmpty()) Text(
                        stringResource(R.string.money_accounts_empty), style = MaterialTheme.typography.bodySmall, color = p.muted,
                        modifier = Modifier.padding(16.dp),
                    )
                    s.accounts.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 1.dp, color = p.border)
                        val reserved = MoneyView.reservedFrom(s, a)
                        ListRow(
                            icon = Ico.BANK, title = a.name,
                            support = if (reserved > 0) stringResource(R.string.money_account_reserved, if (hide) "R$ ••••" else Money.format(reserved))
                            else stringResource(R.string.money_account_balance),
                            value = Finance.accountBalance(s, a),
                        ) { nav.open(Sheet.AccountEdit(a.id)) }
                    }
                }
            }
        }

        // 4: cabeçalho de Cartões com "+ Cartão" e, recolhido, os alertas
        item {
            Column {
                CollapsibleHeader(
                    title = stringResource(R.string.money_cards),
                    collapsedSummary = MoneyView.cardsCollapsed(s, today, hide),
                    open = cardsOpen,
                    onToggle = { prefs.update { it.copy(moneyCardsOpen = !cardsOpen) } },
                    addLabel = stringResource(R.string.money_add_card), addDescription = stringResource(R.string.money_add_card_cd),
                    tag = "money_cards_header",
                ) { nav.open(Sheet.CardEdit()) }
                AnimatedVisibility(!cardsOpen, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                    Column {
                        cards.forEachIndexed { i, r ->
                            if (r.alert) AlertRow(MoneyView.alertLine(r, hide)) {
                                prefs.update { it.copy(moneyCardsOpen = true) }
                                scope.launch { kotlinx.coroutines.delay(220); list.animateScrollToItem(cardsHeaderIndex + 1 + i) }
                            }
                        }
                    }
                }
                if (cardsOpen && s.cards.isEmpty()) Text(
                    stringResource(R.string.money_cards_empty), style = MaterialTheme.typography.bodySmall, color = p.muted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
        // 5..: um card por cartão, com a fatura dentro
        items(cards.size, key = { "card-" + cards[it].card.id }) { i ->
            val r = cards[i]
            AnimatedVisibility(cardsOpen, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                CardCard(r, primaryPay = r.card.id == firstDue, onOpen = { nav.open(Sheet.CardDetail(r.card.id)) }, onPay = { nav.open(Sheet.PayInvoice(r.card.id)) })
            }
        }

        // Planejamento: metas e limites num card só
        item {
            SectionTitle(stringResource(R.string.money_planning))
            Glass(Modifier.fillMaxWidth().padding(top = 4.dp), radius = 22.dp, padding = 0.dp) {
                ListRow(Ico.GOAL, stringResource(R.string.money_goals), MoneyView.goalsSummary(s.goals, hide) ?: stringResource(R.string.money_goals_empty)) { nav.open(Sheet.GoalsList) }
                HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 1.dp, color = p.border)
                ListRow(Ico.LIMIT, stringResource(R.string.money_limits), MoneyView.limitsSummary(s.limits, usage) ?: stringResource(R.string.money_limits_empty)) { nav.open(Sheet.LimitsList) }
            }
        }

        // estes dois abrem uma folha própria: a seta aponta para o lado, como os itens de Ajustes
        item { MoneyLink(Ico.GROUP, "Despesas compartilhadas", sharedCardSubtitle(s, hide)) { nav.open(Sheet.SharedList) } }
        item { MoneyLink(Ico.ASSIST, "Simular meu dinheiro", "Teste cenários sem alterar seus dados") { nav.open(Sheet.Simulator()) } }

        item { CategoryManager(s) }
    }
}

/** Visão geral: Na conta · Em metas · Faturas abertas, separadas por divisórias finas. */
@Composable
private fun OverviewCard(o: MoneyView.Overview) {
    val p = Fin.c
    Glass(Modifier.fillMaxWidth().padding(top = 12.dp), radius = 22.dp, padding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            OverviewCell(stringResource(R.string.money_in_accounts), o.inAccounts, if (o.inAccounts < 0) p.red else p.text)
            VerticalDivider(thickness = 1.dp, color = p.border)
            OverviewCell(stringResource(R.string.money_in_goals), o.inGoals, p.accent)
            VerticalDivider(thickness = 1.dp, color = p.border)
            OverviewCell(stringResource(R.string.money_open_invoices), o.openInvoices, if (o.openInvoices > 0) p.red else p.text)
        }
    }
}

@Composable
private fun RowScope.OverviewCell(label: String, value: Long, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Fin.c.muted, maxLines = 2)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, Modifier.padding(top = 18.dp, bottom = 2.dp, start = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

/**
 * Cabeçalho recolhível (Contas, Cartões): título e seta girando; recolhido mostra o resumo numa linha.
 * O botão "+" continua funcionando com a seção recolhida.
 */
@Composable
private fun CollapsibleHeader(
    title: String, collapsedSummary: String, open: Boolean, onToggle: () -> Unit,
    addLabel: String, addDescription: String, tag: String, onAdd: () -> Unit,
) {
    val p = Fin.c
    val rotation by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "seta $title")
    val state = stringResource(if (open) R.string.money_section_expanded else R.string.money_section_collapsed, title)
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onToggle)
                .testTag(tag)
                .semantics(mergeDescendants = true) { contentDescription = state; heading() }
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (open) title else collapsedSummary, Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            AppIcon(Ico.EXPAND, p.muted, Modifier.padding(start = 4.dp).rotate(rotation), size = 22.dp)
        }
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(p.accent2)
                .clickable(role = Role.Button, onClick = onAdd).semantics { contentDescription = addDescription }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(Ico.ADD, p.accent, size = 18.dp)
            Text(addLabel, Modifier.padding(start = 4.dp), color = p.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Linha de alerta visível com Cartões recolhida (vence em até 7 dias ou uso >= 90%). */
@Composable
private fun AlertRow(text: String, onClick: () -> Unit) {
    val p = Fin.c
    val cd = stringResource(R.string.money_card_alert_cd, text)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .background(p.warningContainer).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cd }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Ico.WARNING, p.warning, size = 20.dp)
        Text(text, Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        AppIcon(Ico.NEXT, p.muted, size = 20.dp)
    }
}

/** Linha tocável: ícone em caixa, título, linha de apoio, valor à direita (opcional) e chevron. */
@Composable
private fun ListRow(icon: Ico, title: String, support: String, value: Long? = null, onClick: () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, p.accent, p.accent2, size = 40.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(support, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) MoneyText(value, color = if (value < 0) p.red else p.text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End))
        AppIcon(Ico.NEXT, p.muted, Modifier.padding(start = 6.dp), size = 22.dp)
    }
}

/** Cartão com a fatura dentro: (1) nome, vencimento e valor; (2) barra de uso; (3) ciclo e "Pagar fatura". */
@Composable
private fun CardCard(r: MoneyView.CardRow, primaryPay: Boolean, onOpen: () -> Unit, onPay: () -> Unit) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    val barColor = when (r.band) { MoneyView.Band.NORMAL -> p.accent; else -> p.warning }
    Glass(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("money_card_" + r.card.id), radius = 22.dp, padding = 16.dp, onClick = onOpen) {
        // 1: ícone, nome, vencimento e valor da fatura aberta
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ico.CARD, p.accent, p.accent2, size = 40.dp, iconSize = 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.card.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.invoice != null) {
                    val suffix = MoneyView.dueSuffix(r.daysToDue)
                    Text(
                        stringResource(R.string.money_card_due, MoneyView.dm(r.invoice.due)) + (suffix?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = if (r.dueSoon) p.red else p.muted,
                        fontWeight = if (r.dueSoon) FontWeight.SemiBold else FontWeight.Normal,
                    )
                } else Text(stringResource(R.string.money_card_no_invoice), style = MaterialTheme.typography.bodySmall, color = p.muted)
            }
            if (r.invoice != null) MoneyText(r.open, color = p.red, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End))
        }
        // 2: barra de uso do limite
        val barCd = MoneyView.barDescription(r, hide)
        Column(Modifier.padding(top = 14.dp).semantics(mergeDescendants = true) { contentDescription = barCd }) {
            Bar(r.pct / 100f, barColor, Modifier.sensitive(hide))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.band == MoneyView.Band.CRITICAL) AppIcon(Ico.WARNING, p.warning, Modifier.padding(end = 4.dp), size = 16.dp)
                Text(
                    if (hide) stringResource(R.string.money_card_limit_hidden) else stringResource(R.string.money_card_limit_line, r.pct, Money.format(r.available)),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = if (r.band == MoneyView.Band.NORMAL) p.muted else p.text,
                    fontWeight = if (r.band == MoneyView.Band.CRITICAL) FontWeight.Bold else FontWeight.Normal,
                )
                Text(stringResource(R.string.money_card_limit_of, if (hide) "R$ ••••" else Money.format(r.card.limit)), style = MaterialTheme.typography.labelMedium, color = p.muted, textAlign = TextAlign.End)
            }
        }
        // 3: ciclo e pagar fatura
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.money_card_cycle, r.card.close, r.card.due), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.muted)
            if (r.invoice != null) {
                val label = stringResource(R.string.money_card_pay)
                if (primaryPay) Button(onClick = onPay, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent)) { Text(label, fontWeight = FontWeight.Bold) }
                else FilledTonalButton(onClick = onPay, Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = p.accent2, contentColor = p.accent)) { Text(label, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CategoryManager(s: AppState) {
    val p = Fin.c
    val dialogs = LocalDialogs.current
    var newCategory by rememberSaveable { mutableStateOf("") }
    var newKind by rememberSaveable { mutableStateOf(Kind.EXPENSE) }
    MoneySection("categorias", Ico.TUNE, "Categorias", "${s.cats.expense.size} de despesa · ${s.cats.income.size} de receita") {
        run {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Nova categoria", newCategory, { newCategory = it }, Modifier.weight(1.3f), maxLength = 40)
                SelectField("Tipo", listOf(Kind.EXPENSE to "Despesa", Kind.INCOME to "Receita"), newKind, { newKind = it }, Modifier.weight(1f))
            }
            PrimaryButton("Adicionar categoria") {
                when (val result = Ops.addCategory(com.finanplus.data.Repo.state.value, newKind, newCategory)) {
                    is Outcome.Err -> dialogs.notice(result.title, result.message)
                    is Outcome.Ok -> { com.finanplus.data.Repo.replace(result.state); newCategory = "" }
                }
            }
            listOf(Kind.EXPENSE to "Despesas", Kind.INCOME to "Receitas").forEach { (kind, label) ->
                Eyebrow(label, Modifier.padding(top = 14.dp, bottom = 4.dp))
                s.cats.of(kind).forEach { category ->
                    ManageItem(category, if (kind == Kind.EXPENSE) "Despesa" else "Receita") {
                        Pill("Renomear") {
                            dialogs.input("Renomear categoria", "Lançamentos, recorrências e limites de “$category” passam a usar o novo nome.", "Novo nome", category) { name ->
                                com.finanplus.data.Repo.commit(Ops.renameCategory(com.finanplus.data.Repo.state.value, kind, category, name))?.let { dialogs.notice(it.title, it.message) }
                            }
                        }
                        Pill("Excluir", danger = true) {
                            when (val check = Ops.checkDeleteCategory(com.finanplus.data.Repo.state.value, kind, category)) {
                                is Outcome.Err -> dialogs.notice(check.title, check.message)
                                is Outcome.Ok -> {
                                    val count = Ops.categoryUseCount(com.finanplus.data.Repo.state.value, kind, category)
                                    val message = if (count > 0) "Excluir “$category” da lista? $count lançamento(s) antigo(s) continuará(ão) com essa categoria no histórico." else "Excluir a categoria “$category”?"
                                    dialogs.confirm("Excluir categoria", message, ok = "Excluir", danger = true) {
                                        com.finanplus.data.Repo.replace(Ops.deleteCategory(com.finanplus.data.Repo.state.value, kind, category))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Seção recolhível de Meu dinheiro, no mesmo estilo de Ajustes: toque no cabeçalho para abrir ou fechar.
 * Fechada, o resumo ao lado do título já mostra o principal. "Contas e cartões" começa aberta.
 */
@Composable
private fun MoneySection(key: String, icon: Ico, title: String, summary: String, startOpen: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(startOpen) }
    Glass(Modifier.padding(top = 10.dp), radius = 24.dp, padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "expandido" else "recolhido" }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(icon, title, summary, if (open) Ico.DROPDOWN else Ico.NEXT)
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content)
    }
}

/** Item que abre outra folha (despesas compartilhadas, simulador): cabeçalho com a seta para o lado. */
@Composable
private fun MoneyLink(icon: Ico, title: String, summary: String, onClick: () -> Unit) {
    Glass(Modifier.padding(top = 10.dp), radius = 24.dp, padding = 0.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(icon, title, summary, Ico.NEXT)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SectionHeader(icon: Ico, title: String, summary: String, chevron: Ico) {
    val p = Fin.c
    IconTile(icon, p.accent, p.accent2, size = 40.dp, iconSize = 20.dp)
    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(summary, style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    AppIcon(chevron, p.muted, size = 20.dp)
}
