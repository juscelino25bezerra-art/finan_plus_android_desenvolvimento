// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanplus.R
import com.finanplus.core.AppState
import com.finanplus.core.Finance
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.MonthCalendar
import com.finanplus.core.Period
import com.finanplus.core.PeriodCompare
import com.finanplus.core.SummaryView
import com.finanplus.core.ym
import com.finanplus.ui.LocalNav
import com.finanplus.ui.MovesView
import com.finanplus.ui.Sheet
import com.finanplus.ui.Tab
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Bar
import com.finanplus.ui.components.CategoryTile
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.Tone
import com.finanplus.ui.components.br
import com.finanplus.ui.components.sensitive
import com.finanplus.ui.screenPadding
import com.finanplus.ui.theme.Fin
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val DM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

/**
 * Resumo: o mesmo período da aba Lançamentos (‹ mês › e período livre), só com valores realizados.
 * Mudança de texto, organização e apresentação (RESUMO.md): selo do mês, resultado com o previsto,
 * comparação só com dados, fluxo com "Entrou" e "Saiu", até 5 categorias e evolução só com meses com dados.
 * Receitas, despesas, reservas e o saldo previsto vêm das mesmas funções de antes (Finance e Period).
 */
@Composable
fun ReportsScreen(s: AppState) {
    val nav = LocalNav.current
    val f = nav.filters
    val p = Fin.c
    val hide = LocalPrivacy.current
    val today = LocalToday.current
    val money = rememberMoneyFmt()
    val inRange = remember(s, f.from, f.to) { s.txs.filter { f.inRange(it) } }
    val flow = remember(inRange) { Finance.flow(inRange) }
    // reservas para metas no período (1.7.0): saem das contas, mas não são despesas
    val goalMoves = remember(inRange) { Finance.goalMoves(inRange) }
    val cmp = remember(f.from, f.to, today) { PeriodCompare.of(f.from, f.to, today) }
    val prev = remember(s, cmp) { cmp?.let { c -> Finance.flow(s.txs.filter { !it.date.isBefore(c.from) && !it.date.isAfter(c.to) }) } }
    val status = remember(f.from, f.to, today) { SummaryView.status(f.from, f.to, today) }
    val fullMonth = Period.fullMonth(f.from, f.to)
    val evolution = remember(s, today) { SummaryView.evolution(s, today) }
    val categories = remember(s, inRange) { SummaryView.categories(s, inRange) }
    val nextIncome = remember(inRange) { SummaryView.nextIncome(inRange) }
    val cardSpend = remember(s, inRange, today) { SummaryView.cardSpend(s, inRange, today) }
    // opção "Acumular saldo entre os meses" (Ajustes › Cálculo do saldo): saldo que vem dos meses anteriores e o do fim do período
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val accumulate = com.finanplus.data.DevicePrefs.get(ctx).flow.collectAsStateWithLifecycle().value.accumulateBalance
    val acc = remember(s, f.from, f.to, accumulate, today) {
        if (!accumulate) null
        else {
            val carried = f.from?.let { Finance.balanceAt(s, it.minusDays(1)) } ?: s.accounts.sumOf { it.initial }
            val until = f.to ?: maxOf(today, s.txs.maxOfOrNull { it.date } ?: today)
            carried to Finance.balanceAt(s, until)
        }
    }

    LazyColumn(contentPadding = screenPadding(), modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Resumo", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                // o que entra neste resumo: a frase fixa virou esta folha
                val info = stringResource(R.string.summary_info_button)
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { nav.open(Sheet.SummaryInfo) }
                        .semantics { contentDescription = info },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(Ico.INFO, p.muted, size = 22.dp) }
                // exportar o mesmo período em PDF (dá para trocar o período na própria folha)
                Pill("PDF", icon = Ico.RECEIPT, modifier = Modifier.semantics { contentDescription = "Exportar relatório em PDF" }) {
                    nav.open(Sheet.ReportPdf(f.from, f.to))
                }
            }
        }
        item { PeriodBar(nav, today) }
        status?.let { st -> item { StatusBadge(st) } }
        if (acc != null) item {
            val (carried, closing) = acc
            Glass(Modifier.padding(top = 10.dp), radius = 20.dp, padding = 14.dp) {
                Text("Saldo acumulado", style = MaterialTheme.typography.bodySmall, color = p.muted)
                MoneyText(closing, color = if (closing < 0) p.red else p.accent, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vindo dos meses anteriores", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.muted)
                    MoneyText(carried, color = if (carried < 0) p.red else p.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                }
                Text(
                    "Saldo das contas ao fim do período, com o saldo inicial e o que sobrou dos meses anteriores (opção em Ajustes › Cálculo do saldo).",
                    style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (flow.income == 0L && flow.expense == 0L) item {
            // nada realizado: em vez de zeros, mostra o que está pendente e leva ao calendário
            val pend = remember(inRange) { Period.pending(inRange) }
            Glass(Modifier.fillMaxWidth().padding(top = 10.dp), radius = 24.dp, padding = 14.dp) {
                val name = fullMonth?.let { MonthCalendar.monthName(it) } ?: "este período"
                Text("Nada realizado em $name ainda", fontWeight = FontWeight.Bold)
                if (pend.toReceive > 0 || pend.toPay > 0) {
                    Text("Os relatórios mostram o que já foi pago ou recebido. Por enquanto, está pendente:", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniBox("A receber", pend.toReceive, p.green, Modifier.weight(1f))
                        MiniBox("A pagar", pend.toPay, p.red, Modifier.weight(1f))
                    }
                } else Text("Os relatórios mostram o que já foi pago ou recebido. Troque o período ou marque lançamentos como pagos.", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp))
                Row(
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                        nav.movesView = MovesView.CALENDAR
                        nav.calMonth = (f.from ?: today).ym()
                        nav.tab = Tab.MOVES
                    }.padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ver no calendário", color = p.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    AppIcon(Ico.NEXT, p.accent, size = 18.dp)
                }
            }
        } else {
            // 1) resultado, com o saldo previsto do fim do mês (o mesmo cálculo do Início)
            item {
                val result = flow.balance
                val forecastEnd = fullMonth?.takeIf { !it.isBefore(today.ym()) }?.atEndOfMonth()
                val label = when {
                    status?.phase == SummaryView.Phase.IN_PROGRESS -> R.string.summary_result_now
                    status != null -> R.string.summary_result_month
                    else -> R.string.summary_result_period
                }
                Glass(Modifier.padding(top = 10.dp), radius = 20.dp, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = p.muted)
                            MoneyText(
                                result, color = when { result < 0 -> p.red; result > 0 -> p.green; else -> p.text },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            )
                        }
                        if (forecastEnd != null) {
                            val future = remember(s, forecastEnd, today) { com.finanplus.core.Totals.forecastAt(s, forecastEnd, today) }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                                Text(stringResource(R.string.summary_forecast, forecastEnd.format(DM)), style = MaterialTheme.typography.bodySmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                MoneyText(future, color = if (future < 0) p.red else p.accent, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                            }
                        }
                    }
                    if (goalMoves.saved > 0L || goalMoves.withdrawn > 0L) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(Ico.GOAL, p.goal, size = 18.dp)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(stringResource(R.string.summary_goals_saved), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.summary_goals_hint), style = MaterialTheme.typography.labelSmall, color = p.muted)
                            }
                            MoneyText(goalMoves.net, color = p.goal, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold))
                        }
                    }
                }
            }
            // 2) receitas e despesas: comparação só quando o período anterior tem dados
            item {
                val deltaLabel = when {
                    cmp == null -> R.string.summary_delta_period
                    fullMonth == null -> R.string.summary_delta_period
                    fullMonth == today.ym() -> R.string.summary_delta_same_days
                    else -> R.string.summary_delta_month
                }
                @Composable fun delta(cur: Long, old: Long?): String? {
                    val d = SummaryView.delta(cur, prev, old ?: 0L) ?: return null
                    // com "Ocultar valores", a variação também fica oculta (revelaria a proporção entre os períodos)
                    if (hide) return stringResource(R.string.summary_delta_hidden)
                    return stringResource(deltaLabel, (if (d.up) "+" else "−") + Money.format(kotlin.math.abs(d.amount)))
                }
                val incomeNote = if (flow.income > 0L) null
                    else nextIncome?.let { stringResource(R.string.summary_next_income, money(it.amount), it.date.format(DM)) }
                        ?: stringResource(R.string.summary_nothing_pending)
                val cardNote = cardSpend?.let { c ->
                    if (c.invoiceDue != null) stringResource(R.string.summary_card_spend, money(c.amount), c.invoiceDue.format(DM))
                    else stringResource(R.string.summary_card_spend_no_date, money(c.amount))
                }
                // os dois cartões com a mesma altura, mesmo quando uma nota quebra em duas linhas
                Row(Modifier.padding(top = 8.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SumBox(stringResource(R.string.summary_income), Ico.UP, flow.income, p.green, listOfNotNull(delta(flow.income, prev?.income), incomeNote), Modifier.weight(1f).fillMaxHeight())
                    SumBox(stringResource(R.string.summary_expenses), Ico.DOWN, flow.expense, p.red, listOfNotNull(delta(flow.expense, prev?.expense), cardNote), Modifier.weight(1f).fillMaxHeight())
                }
            }
            item {
                val sources = remember(inRange) {
                    inRange.asSequence()
                        .filter { it.kind == Kind.INCOME && it.paid && it.isFlow }
                        .groupBy { it.category.ifBlank { "Sem categoria" } }
                        .map { (category, txs) -> category to txs.sumOf { it.value } }
                        .sortedByDescending { it.second }
                }
                MoneyFlowCard(sources, flow.income, flow.expense, goalMoves.net, nextIncome, fullMonth != null, hide)
            }
        }
        if (categories.all.isNotEmpty()) item {
            Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
                Column2("Despesas", stringResource(R.string.summary_categories_title))
                categories.top.forEach { r -> CategoryLine(r, categories.total, s.limits[r.name], onClick = null) }
                categories.others?.let { o ->
                    CategoryLine(
                        o.copy(name = stringResource(R.string.summary_others)), categories.total, null,
                        subtitle = pluralStringResource(R.plurals.summary_others_count, categories.othersCount, categories.othersCount),
                    ) { nav.open(Sheet.AllCategories(f.from, f.to)) }
                }
            }
        }
        if (evolution.isNotEmpty()) item {
            Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
                Column2("Evolução", stringResource(R.string.summary_evolution_sub))
                EvolutionChart(evolution, hide) { m -> nav.open(Sheet.MonthDetail(m.ym.year, m.ym.monthValue)) }
            }
        }
    }
}

/** Selo do mês: "Mês em andamento · dia 2 de 30" ou "Mês encerrado". */
@Composable
private fun StatusBadge(st: SummaryView.MonthStatus) {
    val p = Fin.c
    val (text, tint) = when (st.phase) {
        SummaryView.Phase.IN_PROGRESS -> stringResource(R.string.summary_status_progress, st.day, st.length) to p.accent
        SummaryView.Phase.CLOSED -> stringResource(R.string.summary_status_closed) to p.muted
        SummaryView.Phase.FUTURE -> stringResource(R.string.summary_status_future) to p.muted
    }
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(99.dp)).background(tint.copy(alpha = if (p.dark) 0.16f else 0.12f)).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Ico.CALENDAR, tint, size = 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Uma linha de "Despesas por categoria": ícone, nome, valor, barra proporcional e subtítulo (cartão ou limite). */
@Composable
private fun CategoryLine(row: SummaryView.CatRow, total: Long, limit: Long?, subtitle: String? = null, onClick: (() -> Unit)?) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    val others = subtitle != null
    val over = limit != null && row.value > limit
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(if (others) "" else row.name, Tone.NEGATIVE, size = 36.dp, icon = if (others) Ico.OTHER else null)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (row.name == SummaryView.NO_CATEGORY) stringResource(R.string.summary_no_category) else row.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MoneyText(row.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                if (onClick != null) AppIcon(Ico.NEXT, p.muted, size = 18.dp)
            }
            Bar(if (total > 0) row.value.toFloat() / total else 0f, if (over) p.red else p.red.copy(alpha = 0.75f), Modifier.padding(top = 4.dp).sensitive(hide), height = 8.dp)
            val sub = subtitle ?: row.cards.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.summary_in_cards, it.size, it.joinToString(", ")) }
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 3.dp))
            if (limit != null) Text(
                stringResource(if (over) R.string.summary_limit_over else R.string.summary_limit_ok, if (hide) "R$ ••••" else Money.format(limit)),
                style = MaterialTheme.typography.labelSmall, color = if (over) p.red else p.muted,
            )
        }
    }
}

/** Receitas em verde escuro e despesas em vermelho claro: tons do tema com diferença de claridade (daltonismo). */
@Composable
private fun incomeBarColor(): Color = lerp(Fin.c.green, Color.Black, 0.30f)
@Composable
private fun expenseBarColor(): Color = lerp(Fin.c.red, Color.White, 0.35f)

/**
 * Evolução dos últimos meses com lançamentos: valor pequeno sobre cada barra, "até dia X" no mês em andamento,
 * legenda em texto e toque no mês para abrir o detalhe.
 */
@Composable
private fun EvolutionChart(months: List<SummaryView.EvoMonth>, hide: Boolean, onMonth: (SummaryView.EvoMonth) -> Unit) {
    val p = Fin.c
    val inc = incomeBarColor(); val exp = expenseBarColor()
    val max = maxOf(1L, months.maxOf { maxOf(it.income, it.expense) })
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", com.finanplus.ui.components.BR)
    val desc = months.joinToString("; ") { m -> m.ym.format(monthFmt) + if (hide) "" else ": receitas ${Money.format(m.income)}, despesas ${Money.format(m.expense)}" }
    val chartDesc = stringResource(R.string.summary_evolution_chart_desc, desc)
    val barArea = 84.dp
    Row(Modifier.fillMaxWidth().semantics { contentDescription = chartDesc }, horizontalArrangement = Arrangement.SpaceEvenly) {
        months.forEach { m ->
            val name = m.ym.format(monthFmt)
            val itemDesc = if (hide) stringResource(R.string.summary_evolution_month_desc_hidden, name)
                else stringResource(R.string.summary_evolution_month_desc, name, Money.format(m.income), Money.format(m.expense))
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onMonth(m) }
                    .semantics(mergeDescendants = true) { contentDescription = itemDesc }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // valores empilhados acima do par de barras (lado a lado eles se sobrepunham: "1,5 mil,7 mil")
                Column(Modifier.height(26.dp).clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (m.income > 0) Text(if (hide) "••" else SummaryView.short(m.income), fontSize = 9.sp, lineHeight = 11.sp, color = p.green, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    if (m.expense > 0) Text(if (hide) "••" else SummaryView.short(m.expense), fontSize = 9.sp, lineHeight = 11.sp, color = p.red, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Row(Modifier.height(barArea + 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    listOf(m.income to inc, m.expense to exp).forEach { (v, color) ->
                        // mês sem valor não ganha barra; valor pequeno ganha um traço mínimo
                        Box(Modifier.width(18.dp).height(if (v > 0) maxOf(3f, barArea.value * v / max).dp else 0.dp).sensitive(hide).clip(RoundedCornerShape(6.dp)).background(color))
                    }
                }
                Text(m.ym.format(DateTimeFormatter.ofPattern("MMM", com.finanplus.ui.components.BR)), style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                if (m.current) Text(stringResource(R.string.summary_evolution_until_day, LocalToday.current.dayOfMonth), fontSize = 9.sp, color = p.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(inc) }
        Text(stringResource(R.string.summary_income), Modifier.padding(start = 5.dp, end = 14.dp), style = MaterialTheme.typography.labelSmall, color = p.muted)
        Canvas(Modifier.size(10.dp)) { drawCircle(exp) }
        Text(stringResource(R.string.summary_expenses), Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = p.muted)
    }
}

private data class FlowPart(val label: String, val value: Long, val color: Color)

/**
 * Fluxo agregado do período: "Entrou" e "Saiu ou foi para o cartão" (despesas e guardado em metas, com
 * percentuais que somam 100%). Não associa uma entrada a uma despesa específica.
 */
@Composable
private fun MoneyFlowCard(
    sources: List<Pair<String, Long>>,
    income: Long,
    expense: Long,
    toGoals: Long,
    nextIncome: com.finanplus.core.Due?,
    isMonth: Boolean,
    hide: Boolean,
) {
    val p = Fin.c
    val money = rememberMoneyFmt()
    val goals = maxOf(0L, toGoals)
    val totalOut = expense + goals
    val scale = maxOf(income, totalOut, 1L)
    val out = listOfNotNull(
        FlowPart("Despesas", expense, p.red).takeIf { expense > 0L },
        FlowPart(stringResource(R.string.summary_goals_saved), goals, p.goal).takeIf { goals > 0L },
    )
    val outPct = SummaryView.percents(out.map { it.value })
    val inPct = SummaryView.percents(sources.map { it.second })
    val description = stringResource(R.string.summary_flow_chart_desc, if (hide) "valor oculto" else Money.format(income), if (hide) "valor oculto" else Money.format(totalOut))
    Glass(Modifier.padding(top = 10.dp), radius = 25.dp) {
        Column2("Análises", stringResource(R.string.summary_flow_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.summary_flow_in), Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (income > 0L) MoneyText(income, color = p.green, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
        }
        if (income == 0L) {
            // sem recebimento: uma linha neutra no lugar da barra vazia
            Text(
                if (nextIncome != null) stringResource(R.string.summary_flow_none_received, money(nextIncome.amount), nextIncome.date.format(DM))
                else stringResource(if (isMonth) R.string.summary_flow_none_pending else R.string.summary_flow_none_pending_period),
                style = MaterialTheme.typography.bodyMedium, color = p.muted, modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            SegmentedFlowBar(listOf(FlowPart(stringResource(R.string.summary_flow_in), income, p.green)), scale, hide)
            FlowLegend(sources.map { FlowPart(it.first, it.second, p.green) }, inPct, hide)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.summary_flow_out), Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            if (totalOut > 0L) MoneyText(totalOut, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
        }
        if (out.isEmpty()) {
            Text(stringResource(R.string.summary_flow_no_expense), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
        } else {
            SegmentedFlowBar(out, scale, hide)
            // nomes sempre visíveis (a cor sozinha não basta)
            FlowLegend(out, outPct, hide)
        }
        Spacer(Modifier.height(1.dp).semantics { contentDescription = description })
    }
}

@Composable
private fun SegmentedFlowBar(parts: List<FlowPart>, scale: Long, hide: Boolean) {
    val p = Fin.c
    Canvas(
        Modifier.fillMaxWidth().height(14.dp).padding(top = 4.dp).sensitive(hide)
            .semantics { contentDescription = "Gráfico de barras proporcionais do fluxo de dinheiro" },
    ) {
        val totalWidth = size.width
        var left = 0f
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        parts.forEach { part ->
            val width = totalWidth * (part.value.toDouble() / scale.coerceAtLeast(1L)).toFloat()
            if (width > 0f) {
                drawRoundRect(part.color, Offset(left, 0f), Size(width, size.height), radius)
                left += width
            }
        }
        if (left < totalWidth) drawRoundRect(p.track, Offset(left, 0f), Size(totalWidth - left, size.height), radius)
    }
}

@Composable
private fun FlowLegend(parts: List<FlowPart>, percents: List<Int>, hide: Boolean) {
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        parts.forEachIndexed { i, part ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(12.dp).height(12.dp)) { drawCircle(part.color) }
                Text(part.label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(part.value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Text(if (hide) "••%" else "${percents.getOrElse(i) { 0 }}%", style = MaterialTheme.typography.labelSmall, color = p.muted)
                }
            }
        }
    }
}

/** Receitas ou despesas do período, com as linhas de apoio embaixo (comparação, próxima entrada, cartão). */
@Composable
private fun SumBox(label: String, icon: Ico, value: Long, color: Color, notes: List<String>, modifier: Modifier) {
    val p = Fin.c
    Glass(modifier, radius = 22.dp, padding = 13.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, p.muted, size = 14.dp)
            Spacer(Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
        MoneyText(value, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
        notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = p.muted, modifier = Modifier.padding(top = 2.dp)) }
    }
}

// ------------------------------------------------------------------ folhas do Resumo

/** Botão "info" do cabeçalho: o que entra neste resumo. */
@Composable
fun SummaryInfoSheet() {
    val p = Fin.c
    Text(stringResource(R.string.summary_info_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    listOf(R.string.summary_info_realized, R.string.summary_info_expenses, R.string.summary_info_goals, R.string.summary_info_month).forEach { id ->
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            AppIcon(Ico.CHECK, p.accent, size = 18.dp)
            Text(stringResource(id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** "Outras": a lista completa de categorias do período. */
@Composable
fun AllCategoriesSheet(s: AppState, from: LocalDate?, to: LocalDate?) {
    val p = Fin.c
    val rows = remember(s, from, to) {
        val list = s.txs.filter { (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
        SummaryView.categories(s, list)
    }
    Text(stringResource(R.string.summary_all_categories), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(Period.label(from, to), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
    rows.all.forEach { r -> CategoryLine(r, rows.total, s.limits[r.name], onClick = null) }
}

/** Evolução › toque em um mês: receitas, despesas, guardado em metas e o resultado. */
@Composable
fun MonthDetailSheet(s: AppState, ym: YearMonth, close: () -> Unit) {
    val p = Fin.c
    val nav = LocalNav.current
    val today = LocalToday.current
    val txs = remember(s, ym) { s.txs.filter { it.date.ym() == ym } }
    val fl = remember(txs) { Finance.flow(txs) }
    val g = remember(txs) { Finance.goalMoves(txs) }
    Text(MonthCalendar.monthTitle(ym), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    if (ym == today.ym()) Text(stringResource(R.string.summary_month_until, today.dayOfMonth), style = MaterialTheme.typography.bodySmall, color = p.muted)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp)).background(innerBg()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailRow(Ico.UP, stringResource(R.string.summary_income), fl.income, p.green)
        DetailRow(Ico.DOWN, stringResource(R.string.summary_expenses), fl.expense, p.red)
        DetailRow(Ico.GOAL, stringResource(R.string.summary_goals_saved), g.net, p.goal)
        DetailRow(Ico.CHECK, stringResource(R.string.summary_month_result), fl.balance, when { fl.balance < 0 -> p.red; fl.balance > 0 -> p.green; else -> p.text })
    }
    Row(
        Modifier.padding(top = 8.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
            openMoves(nav, null, ym.atDay(1), ym.atEndOfMonth()); close()
        }.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.summary_month_detail_open), color = p.accent, fontWeight = FontWeight.Bold)
        AppIcon(Ico.NEXT, p.accent, size = 18.dp)
    }
}

@Composable
private fun DetailRow(icon: Ico, label: String, value: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, color, size = 18.dp)
        Text(label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
        MoneyText(value, color = color, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun MiniBox(label: String, value: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = Fin.c
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(p.accent2).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.muted)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}
