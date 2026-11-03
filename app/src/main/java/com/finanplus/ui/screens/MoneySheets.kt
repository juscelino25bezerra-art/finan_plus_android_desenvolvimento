// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finanplus.R
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Money
import com.finanplus.core.MoneyView
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.BR
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.theme.Fin

/** Detalhe do cartão: fatura aberta com "Pagar fatura", compras desta fatura, histórico e "Editar cartão". */
@Composable
internal fun CardDetailSheet(s: AppState, id: String, close: () -> Unit) {
    val nav = LocalNav.current
    val p = Fin.c
    val today = LocalToday.current
    val card = s.card(id) ?: run { Header("Cartão", "Este cartão não existe mais."); PrimaryButton("Fechar", onClick = close); return }
    val r = MoneyView.cardRow(s, card, today)
    val status = Finance.cardStatus(s, card, today)
    Header(card.name, stringResource(R.string.money_card_cycle, card.close, card.due))

    Glass(Modifier.fillMaxWidth().padding(top = 8.dp), radius = 20.dp, padding = 14.dp) {
        Text(stringResource(R.string.card_detail_open), style = MaterialTheme.typography.labelMedium, color = p.muted)
        if (r.invoice != null) {
            MoneyText(r.open, color = p.red, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            Text(
                stringResource(R.string.money_card_due, MoneyView.dm(r.invoice.due)) + (MoneyView.dueSuffix(r.daysToDue)?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = if (r.dueSoon) p.red else p.muted,
            )
            PrimaryButton(stringResource(R.string.money_card_pay), Modifier.padding(top = 10.dp)) { nav.open(Sheet.PayInvoice(card.id)) }
        } else Text(stringResource(R.string.money_card_no_invoice), style = MaterialTheme.typography.bodyMedium)
        Bar(r.pct / 100f, if (r.band == MoneyView.Band.NORMAL) p.accent else p.warning, Modifier.padding(top = 12.dp).sensitive(LocalPrivacy.current))
        Text(
            if (LocalPrivacy.current) stringResource(R.string.money_card_limit_hidden) else stringResource(R.string.money_card_limit_line, r.pct, Money.format(r.available)),
            style = MaterialTheme.typography.labelMedium, color = p.muted, modifier = Modifier.padding(top = 4.dp),
        )
    }

    // compras da fatura mostrada (a aberta; sem fatura aberta, a mais recente)
    val shown = r.invoice ?: status.invoices.lastOrNull()
    Text(stringResource(R.string.card_detail_purchases), Modifier.padding(top = 16.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val purchases = if (shown == null) emptyList() else s.txs
        .filter { it.cardId == card.id && Finance.invoiceYm(card, it.date) == shown.ym }
        .sortedByDescending { it.date }
    if (purchases.isEmpty()) Text(stringResource(R.string.card_detail_no_purchases), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
    else Glass(Modifier.fillMaxWidth().padding(top = 6.dp), radius = 20.dp, padding = 0.dp) {
        purchases.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = p.border)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.desc, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.date.br()} · ${com.finanplus.core.TxForm.displayCategory(t.category)}", style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1)
                }
                MoneyText(t.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
            }
        }
    }

    // histórico: uma linha por fatura (mais recente primeiro)
    if (status.invoices.isNotEmpty()) {
        Text(stringResource(R.string.card_detail_history), Modifier.padding(top = 16.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Glass(Modifier.fillMaxWidth().padding(top = 6.dp), radius = 20.dp, padding = 0.dp) {
            status.invoices.reversed().forEachIndexed { i, inv ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = p.border)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(inv.ym.label().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (inv.open <= 0) stringResource(R.string.card_detail_paid) + " · vence ${inv.due.br()}"
                            else stringResource(R.string.card_detail_open_value, if (LocalPrivacy.current) "R$ ••••" else Money.format(inv.open)) + " · vence ${inv.due.br()}",
                            style = MaterialTheme.typography.bodySmall, color = if (inv.open > 0) p.text else p.muted,
                        )
                    }
                    MoneyText(inv.total, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
                }
            }
        }
    }
    Pill(stringResource(R.string.card_detail_edit), Modifier.fillMaxWidth().padding(top = 16.dp), icon = Ico.EDIT) { nav.open(Sheet.CardEdit(card.id)) }
}

/** Planejamento › Metas: a lista que antes ficava na seção recolhível de Meu dinheiro. */
@Composable
internal fun GoalsListSheet(s: AppState) {
    val nav = LocalNav.current
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = LocalToday.current
    Header(stringResource(R.string.goals_sheet_title), stringResource(R.string.goals_sheet_subtitle))
    Pill(stringResource(R.string.goals_sheet_new), Modifier.fillMaxWidth().padding(top = 4.dp), icon = Ico.ADD) { nav.open(Sheet.GoalEdit()) }
    s.goals.forEach { goal ->
        val plan = Finance.goalPlan(goal, today)
        Glass(Modifier.fillMaxWidth().padding(top = 9.dp), radius = 19.dp, padding = 12.dp, onClick = { nav.open(Sheet.GoalEdit(goal.id)) }) {
            Row(Modifier.fillMaxWidth()) {
                Text(goal.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MoneyText(goal.saved); Text(" / "); MoneyText(goal.target)
            }
            Spacer(Modifier.height(8.dp))
            Bar(if (goal.target > 0) goal.saved.toFloat() / goal.target else 0f, p.accent, Modifier.sensitive(hide))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(goal.deadline?.let { "Até ${it.br()}" } ?: "Sem prazo", style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(1f))
                Text(
                    if (plan.done) "Meta atingida" else listOfNotNull(
                        plan.needed?.let { if (hide) "Precisa de R$ ••••/mês" else "Precisa de ${Money.format(it)}/mês" },
                        plan.eta?.let { "Conclui em ${it.label()}" + if (plan.late) " (após o prazo)" else "" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Planejamento › Limites mensais: a lista que antes ficava na seção recolhível de Meu dinheiro. */
@Composable
internal fun LimitsListSheet(s: AppState) {
    val nav = LocalNav.current
    val p = Fin.c
    val hide = LocalPrivacy.current
    val usage = Finance.budgetUsage(s, LocalToday.current.ym())
    Header(stringResource(R.string.limits_sheet_title), stringResource(R.string.limits_sheet_subtitle))
    Pill(stringResource(R.string.limits_sheet_new), Modifier.fillMaxWidth().padding(top = 4.dp), icon = Ico.ADD) { nav.open(Sheet.LimitEdit()) }
    s.limits.forEach { (category, limit) ->
        val spent = usage[category] ?: 0L
        val pct = if (limit > 0) spent * 100.0 / limit else 0.0
        val color = when { spent > limit -> p.red; pct >= 80 -> p.warning; else -> p.accent }
        Glass(Modifier.fillMaxWidth().padding(top = 9.dp), radius = 19.dp, padding = 12.dp, onClick = { nav.open(Sheet.LimitEdit(category)) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                MoneyText(spent, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                Text(" / ", style = MaterialTheme.typography.bodySmall)
                MoneyText(limit, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            }
            Bar((pct / 100).toFloat(), color, Modifier.sensitive(hide).padding(top = 6.dp))
            Text(
                when {
                    spent > limit -> "Limite ultrapassado"
                    pct >= 100 -> "Limite atingido"
                    hide -> if (pct >= 80) "Atenção: perto do limite" else "Dentro do limite"
                    pct >= 80 -> "Atenção: ${"%.0f".format(BR, pct)}% usado"
                    else -> "${"%.0f".format(BR, pct)}% usado"
                },
                style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
