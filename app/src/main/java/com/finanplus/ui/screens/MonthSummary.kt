// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finanplus.R
import com.finanplus.core.Money
import com.finanplus.core.Totals
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.MoneyText
import com.finanplus.ui.theme.Fin

/**
 * Resumo do mês de Lançamentos (lista e calendário) com os números do glossário único (core/Totals.kt).
 * Um número em destaque e as linhas rotuladas que somam até ele: saldo atual + a receber − a pagar − metas = previsto.
 * [accumulate] = opção "Cálculo do saldo › Saldo acumulado entre os meses": desligada, o destaque é o resultado do mês.
 */
@Composable
fun MonthSummaryCard(t: Totals.Month, accumulate: Boolean, modifier: Modifier = Modifier) {
    val p = Fin.c
    val hide = LocalPrivacy.current
    val past = t.whenIs == Totals.When.PAST
    val (title, main) = when {
        accumulate && past -> stringResource(R.string.ms_balance_end) to t.closing
        accumulate -> stringResource(R.string.ms_forecast_end) to t.closing
        past -> stringResource(R.string.ms_result) to t.result
        else -> stringResource(R.string.ms_result_forecast) to t.resultForecast
    }
    Glass(modifier.padding(top = 10.dp), radius = 28.dp, padding = 16.dp) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = p.muted, fontWeight = FontWeight.SemiBold)
        MoneyText(main, color = if (main < 0) p.red else p.text, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        if (accumulate) Line(
            stringResource(when (t.whenIs) { Totals.When.CURRENT -> R.string.ms_balance_now; Totals.When.FUTURE -> R.string.ms_forecast_start; Totals.When.PAST -> R.string.ms_balance_start }),
            t.opening, null, if (t.opening < 0) p.red else p.text,
        )
        if (past || !accumulate) {
            Line(stringResource(R.string.ms_in), t.realizedIn, '+', p.green)
            Line(stringResource(R.string.ms_out), t.realizedOut, '−', p.red)
            if (t.goalsSaved != 0L) Line(stringResource(R.string.ms_goals_saved), t.goalsSaved, '−', p.goal)
        }
        if (!past) {
            Line(stringResource(R.string.ms_to_receive), t.toReceive, '+', p.green)
            Line(stringResource(R.string.ms_to_pay), t.toPay, '−', p.red)
            if (t.goalsPlanned > 0) Line(stringResource(R.string.ms_goals_planned), t.goalsPlanned, '−', p.goal)
        } else if (t.toReceive > 0 || t.toPay > 0) {
            if (t.toReceive > 0) Line(stringResource(R.string.ms_left_to_receive), t.toReceive, null, p.muted)
            if (t.toPay > 0) Line(stringResource(R.string.ms_left_to_pay), t.toPay, null, p.muted)
        }
        // frase informativa, sem julgamento
        if (!hide && !past && (t.realizedOut > 0 || t.goalsSaved > 0)) Text(
            when {
                t.realizedOut > 0 && t.goalsSaved > 0 -> stringResource(R.string.ms_note_out_goals, Money.format(t.realizedOut), Money.format(t.goalsSaved))
                t.realizedOut > 0 -> stringResource(R.string.ms_note_out, Money.format(t.realizedOut))
                else -> stringResource(R.string.ms_note_goals, Money.format(t.goalsSaved))
            } + " " + stringResource(R.string.ms_note_card),
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 8.dp),
        )
        if (accumulate && !past && t.goalsPlanned == 0L && t.goalsSaved > 0) Text(
            stringResource(R.string.ms_note_goal_once), style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Linha rotulada: rótulo à esquerda, valor com sinal à direita (algarismos de mesma largura). */
@Composable
private fun Line(label: String, value: Long, sign: Char?, color: Color) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Fin.c.text)
        MoneyText(value, color = color, prefix = sign?.let { "$it " } ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
    }
}

/** Calendário: três totais lado a lado, os mesmos da lista (a receber, a pagar + metas, previsto). */
@Composable
fun MonthTotalsRow(t: Totals.Month, modifier: Modifier = Modifier) {
    val p = Fin.c
    val past = t.whenIs == Totals.When.PAST
    Glass(modifier.fillMaxWidth().padding(top = 10.dp), radius = 22.dp, padding = 0.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (past) {
                Cell(stringResource(R.string.ms_in), t.realizedIn, p.green)
                VerticalDivider(thickness = 1.dp, color = p.border)
                Cell(stringResource(R.string.ms_out), t.realizedOut + t.goalsSaved, p.red)
                VerticalDivider(thickness = 1.dp, color = p.border)
                Cell(stringResource(R.string.ms_balance_end_short), t.closing, if (t.closing < 0) p.red else p.accent)
            } else {
                Cell(stringResource(R.string.ms_to_receive), t.toReceive, p.green)
                VerticalDivider(thickness = 1.dp, color = p.border)
                Cell(stringResource(if (t.goalsPlanned > 0) R.string.ms_to_pay_goals else R.string.ms_to_pay), t.toPay + t.goalsPlanned, p.red)
                VerticalDivider(thickness = 1.dp, color = p.border)
                Cell(stringResource(R.string.ms_forecast_short), t.closing, if (t.closing < 0) p.red else p.accent)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(label: String, value: Long, color: Color) {
    Column(Modifier.weight(1f).padding(horizontal = 12.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Fin.c.muted, maxLines = 2)
        MoneyText(value, color = color, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 2.dp))
    }
}
