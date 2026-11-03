// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.finanplus.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Money
import com.finanplus.core.assist.Ask
import com.finanplus.core.assist.Categorizer
import com.finanplus.core.assist.Insight
import com.finanplus.core.assist.Insights
import com.finanplus.core.assist.MoneyFmt
import com.finanplus.core.assist.Source
import com.finanplus.data.AssistData
import com.finanplus.data.DevicePrefs
import com.finanplus.data.DeviceSettings
import com.finanplus.ui.LocalNav
import com.finanplus.ui.Nav
import com.finanplus.ui.Sheet
import com.finanplus.ui.Tab
import com.finanplus.ui.components.Eyebrow
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.LocalPrivacy
import com.finanplus.ui.components.ManageItem
import com.finanplus.ui.components.Pill
import com.finanplus.ui.components.SwitchRow
import com.finanplus.ui.theme.Fin
import java.time.LocalDate

/*
 * Interface do assistente do Finan+. Todo o cálculo está em core/assist (sem Android, testado).
 * Aqui só mostramos os resultados, com "Por quê?" em cada item e respeitando "Ocultar valores".
 */

private val HIDDEN_MONEY: MoneyFmt = { _ -> "R$ ••••" }
private val VISIBLE_MONEY: MoneyFmt = { c -> Money.format(c) }

/** Formatação de valores que respeita "Ocultar valores". */
@Composable
fun rememberMoneyFmt(): MoneyFmt = if (LocalPrivacy.current) HIDDEN_MONEY else VISIBLE_MONEY

/** Abre a aba Lançamentos já filtrada pelo que a dica/resposta usou. */
internal fun openMoves(nav: Nav, query: String?, from: LocalDate?, to: LocalDate?, kind: Kind? = null, paid: Boolean? = null) {
    val f = nav.filters
    f.query = query ?: ""
    f.from = from; f.to = to
    f.kind = kind; f.paid = paid
    nav.sheet = null
    nav.tab = Tab.MOVES
}

@Composable
private fun WhyToggle(why: String) {
    var open by remember(why) { mutableStateOf(false) }
    val p = Fin.c
    Text(
        if (open) "Ocultar explicação" else "Por quê?",
        color = p.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "explicação aberta" else "explicação fechada" }.padding(vertical = 6.dp),
    )
    if (open) Text(
        why, style = MaterialTheme.typography.bodySmall, color = p.muted,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.accent2.copy(alpha = 0.6f)).padding(10.dp),
    )
}

@Composable
private fun TipItem(t: Insight, onDismiss: (() -> Unit)?, onOpen: (() -> Unit)?) {
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(p.accent2.copy(alpha = 0.45f)).padding(12.dp)) {
        Eyebrow(t.type.label)
        Text(t.title, fontWeight = FontWeight.Bold)
        Text(t.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        WhyToggle(t.why)
        if (onOpen != null || onDismiss != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            if (onOpen != null) Pill("Ver lançamentos", onClick = onOpen)
            if (onDismiss != null) Pill("Dispensar", onClick = onDismiss)
        }
    }
}

private fun visibleTips(s: AppState, dev: DeviceSettings, money: MoneyFmt, today: LocalDate) =
    Insights.tips(s, today, money).filter { it.id !in dev.dismissedTips }

// ------------------------------------------------------------------ cartão no Início

@Composable
fun HomeAssistantCard(s: AppState) {
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val dev by prefs.flow.collectAsStateWithLifecycle()
    if (!dev.assistTips && !dev.assistAsk) return
    val nav = LocalNav.current
    val money = rememberMoneyFmt()
    val today = com.finanplus.ui.components.LocalToday.current
    val report = remember(s, money, today) { Insights.report(s, today, money) }
    val tips = remember(s, money, today, dev.dismissedTips) { visibleTips(s, dev, money, today) }
    val risk = remember(s, today) { com.finanplus.core.HomeAdvice.risk(s, today) }
    val p = Fin.c

    // UM aviso principal, que cruza informações (ver INICIO.md):
    // 1) risco no próximo dia com contas; 2) a frase mais útil do resumo que não repete os cartões de cima;
    // 3) a dica principal. O resumo completo, as dicas e as perguntas ficam na folha do assistente.
    Glass(Modifier.padding(top = 12.dp), radius = 26.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.ASSIST, p.accent, size = 22.dp, filled = true)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_assistant), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.semantics { heading() })
        }
        if (dev.assistTips) {
            val line = (report.highlights + report.lines).firstOrNull { !com.finanplus.core.HomeAdvice.repeatsCards(it) }
            Spacer(Modifier.height(8.dp))
            when {
                risk != null -> AdviceRow(com.finanplus.ui.components.Ico.WARNING, p.red, riskText(risk, money)) { nav.open(Sheet.Assistant) }
                line != null -> AdviceRow(com.finanplus.ui.components.Ico.RECEIPT, p.accent, AnnotatedString(line)) { nav.open(Sheet.Assistant) }
                else -> tips.firstOrNull()?.let { t ->
                    TipItem(
                        t,
                        onDismiss = { prefs.update { it.copy(dismissedTips = it.dismissedTips + t.id) } },
                        onOpen = if (t.query != null || t.from != null) ({ openMoves(nav, t.query, t.from, t.to) }) else null,
                    )
                }
            }
        } else Text(stringResource(R.string.home_ask), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { nav.open(Sheet.Assistant) }
                .padding(vertical = 12.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.home_open_assistant), color = p.accent, fontWeight = FontWeight.Bold)
            com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.NEXT, p.accent, size = 18.dp)
        }
    }
}

/** Faixa do aviso principal: ícone num quadrado suave e o texto (com valores em destaque). */
@Composable
private fun AdviceRow(icon: com.finanplus.ui.components.Ico, tint: androidx.compose.ui.graphics.Color, text: AnnotatedString, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(innerBg())
            .clickable(role = Role.Button, onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        com.finanplus.ui.components.IconTile(icon, tint, tint.copy(alpha = if (Fin.c.dark) 0.16f else 0.12f), size = 36.dp, iconSize = 19.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
    }
}

/**
 * Frase do aviso de risco (strings.xml), com os valores em negrito: contas na cor negativa,
 * recebimento na positiva. Com "Ocultar valores", [money] devolve "R$ •••" e nada vaza.
 */
@Composable
internal fun riskText(r: com.finanplus.core.RiskAdvice, money: MoneyFmt): AnnotatedString {
    val p = Fin.c
    val day = r.day.dayOfMonth.toString()
    val inc = r.income
    return when {
        inc == null -> styled(
            R.string.home_risk_no_income,
            day to null, money(r.bills) to p.red, money(r.balance) to null, money(r.missing) to p.red,
        )
        r.sameDay -> styled(
            R.string.home_risk_same_day,
            day to null, money(r.bills) to p.red, inc.title to null, money(inc.amount) to p.green, money(r.balance) to null, money(r.missing) to p.red,
        )
        else -> styled(
            R.string.home_risk_before,
            day to null, money(r.bills) to p.red, inc.title to null, money(inc.amount) to p.green, money(r.balance) to null, money(r.missing) to p.red,
            inc.date.dayOfMonth.toString() to null,
        )
    }
}

/**
 * Monta um texto de strings.xml com argumentos em destaque: cada argumento com cor sai em negrito
 * nessa cor; os sem cor entram como texto normal. Os marcadores evitam depender da posição no texto
 * (a tradução pode mudar a ordem).
 */
@Composable
internal fun styled(id: Int, vararg args: Pair<String, androidx.compose.ui.graphics.Color?>): AnnotatedString {
    val marks = args.indices.map { "\u0001$it\u0002" }
    val raw = stringResource(id, *marks.toTypedArray())
    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            val st = raw.indexOf('\u0001', i)
            if (st < 0) { append(raw.substring(i)); break }
            append(raw.substring(i, st))
            val en = raw.indexOf('\u0002', st)
            val (text, color) = args[raw.substring(st + 1, en).toInt()]
            if (color == null) append(text)
            else withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(text) }
            i = en + 1
        }
    }
}

// ------------------------------------------------------------------ folha do assistente

@Composable
fun AssistantSheet(s: AppState, close: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val dev by prefs.flow.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val money = rememberMoneyFmt()
    val today = com.finanplus.ui.components.LocalToday.current
    val p = Fin.c
    var question by rememberSaveable { mutableStateOf("") }
    var asked by rememberSaveable { mutableStateOf("") }
    var showDismissed by remember { mutableStateOf(false) }

    Text("Assistente", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("Tudo é calculado neste aparelho, sem internet, a partir dos seus lançamentos. Toque em “Por quê?” para ver a regra usada.",
        style = MaterialTheme.typography.bodyMedium, color = p.muted)
    Spacer(Modifier.height(12.dp))

    if (dev.assistAsk) {
        Eyebrow("Pergunte")
        Field("Sua pergunta", question, { question = it }, placeholder = "Ex.: quanto gastei com mercado em agosto?", maxLength = 120)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ask.EXAMPLES.forEach { ex -> Pill(ex) { question = ex; asked = ex } }
        }
        com.finanplus.ui.components.PrimaryButton("Perguntar") { asked = question.trim() }
        if (asked.isNotEmpty()) {
            val a = remember(asked, s, money) { Ask.answer(asked, s, today, money) }
            Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(p.accent2.copy(alpha = 0.55f)).padding(14.dp)) {
                Text(a.text, fontWeight = FontWeight.SemiBold)
                Text(a.understood, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 6.dp))
                if (a.matches.isNotEmpty()) Pill("Ver lançamentos", Modifier.padding(top = 8.dp)) {
                    openMoves(nav, a.parsed.category ?: a.parsed.words.firstOrNull(), a.parsed.period.from, a.parsed.period.to, a.parsed.kind)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (dev.assistTips) {
        val report = remember(s, money, today) { Insights.report(s, today, money) }
        Eyebrow("Resumo")
        Text(report.title, style = MaterialTheme.typography.titleMedium)
        report.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        WhyToggle(report.why)
        Spacer(Modifier.height(12.dp))

        val all = remember(s, money, today) { Insights.tips(s, today, money) }
        val visible = all.filter { it.id !in dev.dismissedTips }
        val dismissed = all.filter { it.id in dev.dismissedTips }
        Eyebrow("Dicas")
        if (visible.isEmpty()) Text("Nenhuma dica no momento: nada fora do padrão nos seus lançamentos.", style = MaterialTheme.typography.bodySmall, color = p.muted)
        visible.forEach { t ->
            TipItem(
                t,
                onDismiss = { prefs.update { it.copy(dismissedTips = it.dismissedTips + t.id) } },
                onOpen = if (t.query != null || t.from != null) ({ openMoves(nav, t.query, t.from, t.to) }) else null,
            )
        }
        if (dismissed.isNotEmpty()) {
            Pill(if (showDismissed) "Esconder dispensadas" else "Mostrar dispensadas (${dismissed.size})", Modifier.padding(top = 6.dp)) { showDismissed = !showDismissed }
            if (showDismissed) dismissed.forEach { t ->
                TipItem(t, onDismiss = null, onOpen = null)
                Pill("Mostrar de novo") { prefs.update { it.copy(dismissedTips = it.dismissedTips - t.id) } }
            }
        }
    }
    if (!dev.assistAsk && !dev.assistTips) Text("O resumo, as dicas e as perguntas estão desligados em Ajustes › Assistente.", color = p.muted)
    Spacer(Modifier.height(12.dp))
    com.finanplus.ui.components.PrimaryButton("Fechar", onClick = close)
}

// ------------------------------------------------------------------ sugestão de categoria no editor

/**
 * Mostra "Sugestão: X · Usar" abaixo da descrição. Só aparece quando há confiança suficiente
 * e quando a categoria sugerida é diferente da escolhida.
 */
@Composable
fun CategoryHint(s: AppState, kind: Kind, desc: String, current: String, enabled: Boolean, onUse: (String) -> Unit) {
    val ctx = LocalContext.current
    val dev by DevicePrefs.get(ctx).flow.collectAsStateWithLifecycle()
    if (!dev.assistCategory || !enabled) return
    val categorizer = remember(s.txs, s.cats, kind) { Categorizer.build(s, kind, AssistData.dictionary(ctx)) }
    val sug = remember(categorizer, desc) { if (desc.trim().length < 2) null else categorizer.suggest(desc) } ?: return
    if (sug.category == current) return
    val p = Fin.c
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(p.accent.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.finanplus.ui.components.AppIcon(com.finanplus.ui.components.Ico.ASSIST, p.accent, size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Sugestão: ${sug.category}", fontWeight = FontWeight.Bold)
                }
                Text(
                    when (sug.source) { Source.SAME_DESCRIPTION -> "pelo que você já lançou"; Source.LEARNED -> "aprendido com seus lançamentos"; Source.DICTIONARY -> "pelo dicionário" },
                    style = MaterialTheme.typography.labelSmall, color = p.muted,
                )
            }
            Pill("Usar", selected = true) { onUse(sug.category) }
        }
        WhyToggle(sug.why)
    }
}

// ------------------------------------------------------------------ Ajustes › Assistente

@Composable
fun AssistantSettings(s: AppState, dev: DeviceSettings) {
    val ctx = LocalContext.current
    val prefs = DevicePrefs.get(ctx)
    val p = Fin.c
    Text("Funciona só neste aparelho, sem internet e sem enviar dados. Cada função pode ser desligada.",
        style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
    SwitchRow("Sugerir categoria", "Ao digitar a descrição de um lançamento novo", dev.assistCategory) { v -> prefs.update { it.copy(assistCategory = v) } }
    SwitchRow("Resumo e dicas", "No Início: resumo do mês, gastos fora do padrão, fixos, duplicados", dev.assistTips) { v -> prefs.update { it.copy(assistTips = v) } }
    SwitchRow("Perguntas rápidas", "Ex.: “quanto gastei com mercado em agosto?”", dev.assistAsk) { v -> prefs.update { it.copy(assistAsk = v) } }
    if (dev.dismissedTips.isNotEmpty()) Pill("Restaurar " + (if (dev.dismissedTips.size == 1) "1 dica dispensada" else "${dev.dismissedTips.size} dicas dispensadas"), Modifier.padding(top = 6.dp)) {
        prefs.update { it.copy(dismissedTips = emptySet()) }
    }

    var open by rememberSaveable { mutableStateOf(false) }
    Text(
        if (open) "Ocultar o que o assistente aprendeu" else "Ver o que o assistente aprendeu",
        color = p.accent, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 6.dp),
    )
    if (open) {
        val dict = AssistData.dictionary(ctx)
        Text(
            "O aprendizado vem dos seus próprios lançamentos (que ficam criptografados no aparelho). Não existe uma cópia separada: " +
                "corrigir a categoria de um lançamento corrige o aprendizado, e apagar o lançamento apaga o que ele ensinou.",
            style = MaterialTheme.typography.bodySmall, color = p.muted,
        )
        for (k in listOf(Kind.EXPENSE, Kind.INCOME)) {
            val c = remember(s.txs, s.cats, k) { Categorizer.build(s, k, dict) }
            val words = c.learnedWords()
            Eyebrow(if (k == Kind.EXPENSE) "Despesas · ${com.finanplus.core.Words.count(c.trainingSize, "lançamento analisado", "lançamentos analisados")}" else "Receitas · ${com.finanplus.core.Words.count(c.trainingSize, "lançamento analisado", "lançamentos analisados")}", Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (words.isEmpty()) Text("Ainda não há palavras repetidas o suficiente.", style = MaterialTheme.typography.bodySmall, color = p.muted)
            words.forEach { (cat, ws) -> ManageItem(cat, ws.joinToString(", ") { (w, n) -> "$w ($n)" }) {} }
        }
        Text(
            "Dicionário inicial: ${dict.sections.size} seções, arquivo aberto assets/assistente/dicionario.txt. " +
                "As regras de cada função estão descritas em ASSISTENTE.md no código-fonte.",
            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 10.dp),
        )
    }
}
