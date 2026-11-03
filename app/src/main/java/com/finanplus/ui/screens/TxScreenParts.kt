// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.R
import com.finanplus.core.AmountInput
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.TxForm
import com.finanplus.core.assist.Text as AssistText
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.categoryIcon
import com.finanplus.ui.theme.Fin

/** Tags usadas pelos testes de interface. */
internal object TxTags {
    const val AMOUNT = "tx_amount"
    const val SAVE = "tx_save"
    const val CATEGORY_ROW = "tx_category_row"
    const val DRAWER = "tx_drawer"
    const val KIND_EXPENSE = "tx_kind_expense"
    const val KIND_INCOME = "tx_kind_income"
    const val ALL_CATEGORIES = "tx_all_categories"
    const val PAY_ROW = "tx_pay_row"
    fun cell(name: String) = "tx_cat_$name"
    fun chip(name: String) = "tx_chip_$name"
}

/** Ícones da tela de lançamento (mapeamento da proposta; o resto cai no ícone geral da categoria e, por fim, em more_horiz). */
internal object TxIcons {
    fun forCategory(category: String): Ico {
        if (category.isBlank()) return Ico.OTHER
        val c = AssistText.fold(TxForm.displayCategory(category)).trim()
        return when {
            c.startsWith("13") -> Ico.GIFTS
            c == "alimentacao" -> Ico.RESTAURANT
            c == "transporte" -> Ico.TRANSPORT
            c == "moradia" -> Ico.HOME
            c == "saude" -> Ico.HEALTH
            c == "lazer" -> Ico.GAMES
            c == "educacao" -> Ico.EDUCATION
            c == "emprestimo" || c == "emprestimos" -> Ico.BANK
            c == "cartao" -> Ico.CARD
            c == "salario" -> Ico.SALARY
            c == "investimentos" || c == "investimento" -> Ico.TREND
            c == "outros" -> Ico.OTHER
            else -> categoryIcon(category) ?: Ico.OTHER
        }
    }
}

/** Seletor Despesa | Receita (segmentado): a cor do tipo fica no valor; o fundo dos botões nunca é vermelho/verde. */
@Composable
internal fun KindSwitch(kind: Kind, onChange: (Kind) -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).height(52.dp).clip(RoundedCornerShape(16.dp))
            .background(p.accent2.copy(alpha = 0.7f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(Kind.EXPENSE to R.string.tx_kind_expense, Kind.INCOME to R.string.tx_kind_income).forEach { (k, label) ->
            val on = k == kind
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.surface else Color.Transparent, tween(200), label = "metade $k")
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(13.dp)).background(bg)
                    .testTag(if (k == Kind.EXPENSE) TxTags.KIND_EXPENSE else TxTags.KIND_INCOME)
                    .semantics { selected = on }
                    .clickable(role = Role.Tab) { onChange(k) },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(label), color = if (on) p.text else p.muted, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

/** Rótulo pequeno de cada bloco do formulário. */
@Composable
internal fun FormLabel(text: String) =
    Text(text, Modifier.padding(top = 14.dp, bottom = 6.dp, start = 2.dp), style = MaterialTheme.typography.labelLarge, color = Fin.c.muted, fontWeight = FontWeight.SemiBold)

/** Chip do formulário (categoria, data): selecionado = cor primária. Área de toque de 48dp. */
@Composable
internal fun FormChip(label: String, selected: Boolean, icon: Ico?, modifier: Modifier = Modifier, trailing: Ico? = null, onClick: () -> Unit) {
    val p = Fin.c
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) p.accent else p.surface)
                .border(1.dp, if (selected) p.accent else p.border, RoundedCornerShape(20.dp))
                .semantics { this.selected = selected }.clickable(role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 40.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { AppIcon(icon, if (selected) p.onAccent else p.accent, size = 18.dp); Spacer(Modifier.width(6.dp)) }
            Text(label, color = if (selected) p.onAccent else p.text, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
            if (trailing != null) { Spacer(Modifier.width(4.dp)); AppIcon(trailing, if (selected) p.onAccent else p.muted, size = 18.dp) }
        }
    }
}

/**
 * Valor em destaque: ≈52sp, "R$" menor ao lado, formatação pt-BR enquanto digita.
 * Com fonte grande do sistema ou valor longo, a fonte do valor encolhe para caber na largura.
 */
@Composable
internal fun AmountEntry(amount: String, onChange: (String) -> Unit, tone: Color, autoFocus: Boolean, modifier: Modifier = Modifier) {
    val p = Fin.c
    val label = stringResource(R.string.tx_amount_label)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var field by remember { mutableStateOf(TextFieldValue(amount, TextRange(amount.length))) }
    if (field.text != amount) field = TextFieldValue(amount, TextRange(amount.length))
    LaunchedEffect(Unit) { if (autoFocus) { runCatching { focus.requestFocus() }; keyboard?.show() } }

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val shown = amount.ifEmpty { "0,00" }
        // largura aproximada de um algarismo ≈ 0,6 do tamanho da fonte; reserva espaço para o "R$"
        val basePx = with(density) { 52.sp.toPx() }
        val available = with(density) { maxWidth.toPx() } * 0.78f
        val needed = (shown.length.coerceAtLeast(4)) * 0.6f * basePx
        val scale = if (needed > available) (available / needed).coerceAtLeast(0.45f) else 1f
        val size = (52f * scale).sp
        val fieldMax = maxWidth * 0.82f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.tx_currency), color = tone, fontWeight = FontWeight.Bold, fontSize = (22f * scale.coerceAtLeast(0.7f)).sp,
                modifier = Modifier.padding(end = 6.dp, top = 6.dp))
            BasicTextField(
                value = field,
                onValueChange = { v ->
                    val f = AmountInput.format(v.text)
                    field = TextFieldValue(f, TextRange(f.length)); onChange(f)
                },
                modifier = Modifier.focusRequester(focus).widthIn(min = 48.dp, max = fieldMax).heightIn(min = 64.dp)
                    .testTag(TxTags.AMOUNT).semantics { contentDescription = label },
                singleLine = true,
                textStyle = TextStyle(color = tone, fontSize = size, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(tone),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (amount.isEmpty()) Text(stringResource(R.string.tx_amount_placeholder), color = tone.copy(alpha = 0.45f),
                            fontSize = size, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        inner()
                    }
                },
            )
        }
    }
}

/** Linha de 64dp do cartão de campos: ícone em caixa, rótulo pequeno e valor. */
@Composable
internal fun FieldRow(
    icon: Ico, tint: Color, tile: Color, label: String, value: String, placeholder: Boolean = false, chevron: Boolean = true,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val p = Fin.c
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint, tile, size = 40.dp, iconSize = 22.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = p.muted)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (placeholder) p.accent else p.text,
                fontWeight = if (placeholder) FontWeight.Normal else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) AppIcon(Ico.NEXT, p.muted, size = 22.dp)
    }
}

/** Seletor de categoria (folha): busca, lista com ícone e check na escolhida e "Gerenciar categorias". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDrawer(kind: Kind, choices: List<String>, current: String?, onPick: (String) -> Unit, onClose: () -> Unit, onManage: () -> Unit) {
    val p = Fin.c
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val shown = TxForm.filter(choices, query)
    val closeLabel = stringResource(R.string.tx_close)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.statusBarsPadding().testTag(TxTags.DRAWER)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tx_category), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center) { AppIcon(Ico.CLOSE, p.text, size = 24.dp) }
            }
            val searchLabel = stringResource(R.string.tx_drawer_search)
            val clearLabel = stringResource(R.string.tx_drawer_search_clear)
            TextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(top = 4.dp).semantics { contentDescription = searchLabel },
                placeholder = { Text(searchLabel) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                leadingIcon = { AppIcon(Ico.SEARCH, p.muted, size = 22.dp) },
                trailingIcon = if (query.isEmpty()) null else ({
                    Box(Modifier.size(48.dp).clickable(role = Role.Button) { query = "" }.semantics { contentDescription = clearLabel }, contentAlignment = Alignment.Center) {
                        AppIcon(Ico.CLOSE, p.muted, size = 20.dp)
                    }
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = p.track, unfocusedContainerColor = p.track,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
                    focusedPlaceholderColor = p.muted, unfocusedPlaceholderColor = p.muted,
                ),
            )
            SectionLabel(stringResource(if (kind == Kind.EXPENSE) R.string.tx_drawer_expenses else R.string.tx_drawer_incomes))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (shown.isEmpty()) item { Text(stringResource(R.string.tx_drawer_empty), color = p.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp)) }
                items(shown.size, key = { shown[it] }) { i ->
                    val c = shown[i]
                    val on = c == current
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) p.accent2 else Color.Transparent)
                            .testTag(TxTags.cell(c)).semantics { selected = on }
                            .clickable(role = Role.RadioButton) { onPick(c) }.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(TxIcons.forCategory(c), p.accent, p.accent2, size = 38.dp, iconSize = 20.dp)
                        Text(com.finanplus.core.Words.ordinal(c), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        if (on) AppIcon(Ico.CHECK, p.accent, size = 22.dp, description = "Selecionada")
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.TextButton(onClick = onManage, Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.tx_manage_categories), color = p.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = Fin.c.muted)

/** "Pagar com" (contas e cartões) ou "Receber em" (contas), com os cadastros que já existem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayWithSheet(
    s: AppState, isExpense: Boolean, canCard: Boolean, card: Boolean, cardId: String, accountId: String,
    onClose: () -> Unit, onPick: (isCard: Boolean, id: String) -> Unit,
) {
    val p = Fin.c
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(if (isExpense) R.string.tx_pay_with else R.string.tx_receive_in), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() })
            if (isExpense) SectionLabelPad(stringResource(R.string.tx_pay_accounts))
            s.accounts.forEach { a -> PickRow(Ico.WALLET, a.name, !card && a.id == accountId) { onPick(false, a.id) } }
            if (canCard) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.border)
                SectionLabelPad(stringResource(R.string.tx_pay_cards))
                s.cards.forEach { c -> PickRow(Ico.CARD, c.name, card && c.id == cardId) { onPick(true, c.id) } }
            }
        }
    }
}

@Composable
private fun SectionLabelPad(text: String) = Box(Modifier.padding(horizontal = 20.dp)) { SectionLabel(text) }

@Composable
private fun PickRow(icon: Ico, name: String, on: Boolean, onClick: () -> Unit) {
    val p = Fin.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = on }.clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, p.accent, p.accent2, size = 36.dp, iconSize = 20.dp)
        Text(name, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (on) AppIcon(Ico.CHECK, p.accent, size = 22.dp, description = "Selecionado")
    }
}
