// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.R
import com.finanplus.core.AmountInput
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Ops
import com.finanplus.core.RepsMode
import com.finanplus.core.TxDraft
import com.finanplus.core.TxForm
import com.finanplus.data.Repo
import com.finanplus.ui.LocalTxSnackbar
import com.finanplus.ui.LocalUiScope
import com.finanplus.ui.Sheet
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Field
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.LocalToday
import com.finanplus.ui.components.SelectField
import com.finanplus.ui.components.br
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A tela de Receita e Despesa (novo e editar) é a tela cheia abaixo. Pagamento de fatura e reserva para meta
 * continuam no formulário em folha de antes (não são "lançar uma receita ou despesa").
 */
fun usesTxScreen(s: AppState, sheet: Sheet.TxEdit): Boolean {
    val tx = sheet.id?.let { id -> s.txs.firstOrNull { it.id == id } }
    return tx == null || (!tx.isGoal && tx.cardPayment.isEmpty())
}

/** Cor do texto sobre um fundo colorido: branco, ou o escuro do tema quando o branco não chega a 4,5:1. */
@Composable
internal fun onFill(fill: Color): Color {
    fun contrast(a: Color, b: Color): Float { val l1 = a.luminance() + 0.05f; val l2 = b.luminance() + 0.05f; return if (l1 > l2) l1 / l2 else l2 / l1 }
    val white = Color.White
    if (contrast(white, fill) >= 4.5f) return white
    val dark = Fin.c.bg
    return if (contrast(dark, fill) >= contrast(Color.Black, fill)) dark else Color.Black
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxScreen(s: AppState, sheet: Sheet.TxEdit, close: () -> Unit) {
    val p = Fin.c
    val dialogs = LocalDialogs.current
    val today = LocalToday.current
    val tx = sheet.id?.let { id -> s.txs.firstOrNull { it.id == id } }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val snackbar = LocalTxSnackbar.current
    val uiScope = LocalUiScope.current

    // ---- estado (sobrevive a girar a tela)
    val initialKind = tx?.kind ?: sheet.kind
    val initialDate = tx?.date ?: sheet.date ?: today
    val initialAmount = tx?.let { AmountInput.fromCents(it.value) } ?: ""
    val initialCategory = tx?.category ?: ""     // novo: sem categoria escolhida ("Escolher categoria")
    val initialPaid = tx?.paid ?: Ops.defaultPaid(initialDate, today)
    val initialAccount = tx?.accountId ?: s.accounts[0].id
    val initialCard = tx?.cardId?.ifEmpty { null } ?: s.cards.firstOrNull()?.id ?: ""
    val initialUseCard = tx?.isCard == true
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var date by rememberSaveable { mutableStateOf<LocalDate?>(initialDate) }
    var desc by rememberSaveable { mutableStateOf(tx?.desc ?: "") }
    var paid by rememberSaveable { mutableStateOf(initialPaid) }
    // Se a pessoa escolher a situação, mudar a data não a sobrescreve.
    var paidTouched by rememberSaveable { mutableStateOf(false) }
    var useCard by rememberSaveable { mutableStateOf(initialUseCard) }
    var accountId by rememberSaveable { mutableStateOf(initialAccount) }
    var cardId by rememberSaveable { mutableStateOf(initialCard) }
    var reps by rememberSaveable { mutableStateOf("1") }
    var repsMode by rememberSaveable { mutableStateOf(RepsMode.TOTAL) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable(sheet.id) { mutableStateOf(false) }
    var drawer by rememberSaveable { mutableStateOf(false) }
    var payPicker by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }

    val dirty = kind != initialKind || amount != initialAmount || category != initialCategory || date != initialDate ||
        desc != (tx?.desc ?: "") || paid != initialPaid || useCard != initialUseCard || accountId != initialAccount || cardId != initialCard ||
        (tx == null && (reps != "1" || repsMode != RepsMode.TOTAL || recurring))
    val leave: () -> Unit = {
        if (dirty) dialogs.confirm("Descartar alterações?", "O que você digitou neste formulário será perdido.", ok = "Descartar", cancel = "Continuar editando", danger = true) { close() }
        else close()
    }
    BackHandler(onBack = leave)

    val isExpense = kind == Kind.EXPENSE
    val canCard = isExpense && s.cards.isNotEmpty()
    val card = useCard && canCard
    val toneTarget = if (isExpense) p.red else p.green
    val tone by animateColorAsState(toneTarget, tween(220), label = "tom do tipo")
    val canSave = AmountInput.isPositive(amount)
    val choices = TxForm.choices(s.cats.of(kind)).let { l ->
        val cur = TxForm.displayCategory(category)
        if (category.isNotBlank() && cur !in l) l + cur else l
    }

    val savedExpense = stringResource(R.string.tx_saved_expense)
    val savedIncome = stringResource(R.string.tx_saved_income)
    fun save() {
        // sem categoria escolhida, salva em "Outros" (ou na primeira do tipo, se não houver "Outros")
        val cats = s.cats.of(kind)
        val cat = category.ifBlank { cats.firstOrNull { it == TxForm.OTHERS } ?: cats[0] }
        val draft = TxDraft(kind, desc, amount, cat, date, paid, accountId, if (card) cardId else "", reps.toIntOrNull() ?: 1, repsMode, recurring)
        val err = Repo.commit(Ops.saveTx(Repo.state.value, tx?.id, draft))
        if (err == null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val msg = if (isExpense) savedExpense else savedIncome
            close()
            uiScope?.launch { snackbar.showSnackbar(msg) }
        } else dialogs.notice(err.title, err.message)
    }

    androidx.compose.material3.Surface(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
        color = p.bg,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // ---- barra superior: fechar e título centralizado (sem outros botões)
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                val closeLabel = stringResource(R.string.tx_close)
                Box(
                    Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(48.dp).clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = leave).semantics { contentDescription = closeLabel },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(Ico.CLOSE, p.text, size = 24.dp) }
                Text(
                    stringResource(if (tx == null) R.string.tx_title_new else R.string.tx_title_edit),
                    Modifier.align(Alignment.Center).padding(horizontal = 64.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // ---- Receita / Despesa em duas metades
                KindSwitch(kind) { k ->
                    if (k != kind) {
                        kind = k
                        category = TxForm.categoryAfterKindChange(category, TxForm.choices(s.cats.of(k)))
                        if (k == Kind.INCOME) useCard = false
                    }
                }

                // ---- valor em destaque
                AmountEntry(amount, { amount = it }, tone, autoFocus = tx == null, modifier = Modifier.padding(top = 18.dp))
                Box(Modifier.fillMaxWidth().heightIn(min = 22.dp), contentAlignment = Alignment.Center) {
                    if (AmountInput.isZeroTyped(amount)) Text(
                        stringResource(R.string.tx_amount_error), style = MaterialTheme.typography.bodySmall, color = p.red,
                        modifier = Modifier.semantics { contentDescription = "Erro: valor igual a zero" },
                    )
                }

                // ---- lista: categoria, pagar com / receber em, data
                Glass(Modifier.padding(top = 8.dp), radius = 22.dp, padding = 0.dp) {
                    FieldRow(
                        icon = TxIcons.forCategory(category), tint = tone, tile = tone.copy(alpha = if (p.dark) 0.18f else 0.13f),
                        label = stringResource(R.string.tx_category),
                        value = if (category.isBlank()) stringResource(R.string.tx_choose_category) else TxForm.displayCategory(category),
                        placeholder = category.isBlank(), chevron = true, modifier = Modifier.testTag(TxTags.CATEGORY_ROW),
                    ) { focus.clearFocus(); keyboard?.hide(); drawer = true }
                    HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 1.dp, color = p.border)
                    val payName = if (card) s.card(cardId)?.name ?: "" else s.account(accountId)?.name ?: ""
                    FieldRow(
                        icon = if (card) Ico.CARD else Ico.WALLET, tint = p.accent, tile = p.accent2,
                        label = stringResource(if (isExpense) R.string.tx_pay_with else R.string.tx_receive_in), value = payName, chevron = true,
                    ) { focus.clearFocus(); keyboard?.hide(); payPicker = true }
                    HorizontalDivider(Modifier.padding(start = 72.dp), thickness = 1.dp, color = p.border)
                    FieldRow(
                        icon = Ico.CALENDAR, tint = p.accent, tile = p.accent2,
                        label = stringResource(R.string.tx_date),
                        value = date?.let { if (it == today) stringResource(R.string.tx_today) + " · " + it.br() else it.br() } ?: "—", chevron = false,
                    ) { focus.clearFocus(); keyboard?.hide(); datePicker = true }
                }

                // ---- descrição e sugestões
                Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 0.dp) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(Ico.EDIT, p.muted, size = 22.dp)
                        val hint = stringResource(R.string.tx_description_hint)
                        TextField(
                            desc, { desc = it.take(Ops.TX_DESC_MAX) }, Modifier.weight(1f).semantics { contentDescription = hint },
                            placeholder = { Text(hint) }, singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = tone,
                                focusedPlaceholderColor = p.muted, unfocusedPlaceholderColor = p.muted,
                            ),
                        )
                    }
                }
                val suggestions = remember(s.txs, kind) { TxForm.descriptionSuggestions(s.txs, kind) }
                if (suggestions.isNotEmpty()) {
                    val sugLabel = stringResource(R.string.tx_description_suggestions)
                    LazyRow(
                        Modifier.fillMaxWidth().padding(top = 6.dp).semantics { contentDescription = sugLabel },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(suggestions) { d ->
                            SuggestionChip(onClick = { desc = d.take(Ops.TX_DESC_MAX) }, label = { Text(d, maxLines = 1) })
                        }
                    }
                }
                // Assistente: sugere categoria com base na descrição (como antes)
                CategoryHint(s, kind, desc, category, enabled = tx == null || desc != tx.desc) { category = it }

                // ---- mais opções: situação, parcelas e repetição (continuam disponíveis, como antes)
                TextButton(onClick = { moreOpen = !moreOpen }, Modifier.padding(top = 4.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(if (moreOpen) R.string.tx_less else R.string.tx_more))
                    AppIcon(Ico.DROPDOWN, p.accent, size = 18.dp)
                }
                AnimatedVisibility(visible = moreOpen) {
                    Column {
                        if (tx == null && isExpense) {
                            val nav = com.finanplus.ui.LocalNav.current
                            // atalho: preencher a partir de uma fatura em PDF, linha digitável do boleto ou Pix copia e cola (FATURAS.md)
                            com.finanplus.ui.components.Pill("Ler fatura ou boleto", Modifier.fillMaxWidth(), icon = Ico.RECEIPT) { nav.open(Sheet.BillImport()) }
                        }
                        if (!card) {
                            val settled = if (kind == Kind.INCOME) "Já recebida" else "Já paga"
                            val pending = if (kind == Kind.INCOME) "A receber" else "A pagar"
                            Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
                                Text(if (kind == Kind.INCOME) "Situação da receita" else "Situação da despesa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                PaymentStatusChoice(settled, paid) { paid = true; paidTouched = true }
                                PaymentStatusChoice(pending, !paid) { paid = false; paidTouched = true }
                                Text(
                                    if (paid) "O valor será considerado no saldo realizado da conta." else "O valor ficará pendente até ser recebido ou pago.",
                                    style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                )
                            }
                        }
                        if (tx?.scheduledDate != null) Text(
                            "Data prevista original: ${tx.scheduledDate.br()}. Ela será restaurada se o lançamento voltar a ficar pendente.",
                            style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                        if (tx == null) Glass(Modifier.padding(top = 10.dp), radius = 22.dp, padding = 14.dp) {
                            Text("Parcelamento e repetição", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Field("Número de parcelas", reps, { reps = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, maxLength = 2)
                            if ((reps.toIntOrNull() ?: 1) > 1) SelectField(
                                "O valor informado é", listOf(RepsMode.TOTAL to "O total da compra (divide entre as parcelas)", RepsMode.EACH to "O valor de cada parcela"), repsMode, { repsMode = it },
                            )
                            CheckRow(if (kind == Kind.INCOME) "Repetir esta receita mensalmente" else "Repetir esta despesa mensalmente", recurring) { recurring = it }
                        }
                    }
                }
                if (tx != null) DeleteButton("Excluir lançamento") {
                    dialogs.confirm("Excluir lançamento", "Excluir este lançamento?", ok = "Excluir", danger = true) {
                        val later = Ops.laterParcels(Repo.state.value, tx.id)
                        if (later.isEmpty()) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() }
                        else dialogs.confirm(
                            "Parcelas", "Excluir também as ${later.size} parcela(s) seguinte(s)?", ok = "Excluir também", cancel = "Só esta",
                            onCancel = { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, false)); close() },
                        ) { Repo.replace(Ops.deleteTx(Repo.state.value, tx.id, true)); close() }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- Salvar: fixo no rodapé, 56dp, cor do tipo; desabilitado (mais transparente) com valor zero ou vazio
            val fg = onFill(toneTarget)
            Button(
                onClick = { save() }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp).testTag(TxTags.SAVE),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tone, contentColor = fg,
                    disabledContainerColor = tone.copy(alpha = 0.38f), disabledContentColor = fg.copy(alpha = 0.7f),
                ),
            ) { Text(stringResource(if (isExpense) R.string.tx_save_expense else R.string.tx_save_income), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) }
        }
    }

    // ---- gaveta de categorias
    if (drawer) CategoryDrawer(
        choices = choices, recent = remember(s.txs, kind, choices) { TxForm.recentCategories(s.txs, kind, choices) },
        current = TxForm.displayCategory(category).takeIf { category.isNotBlank() }, tone = tone,
        onPick = { category = it; drawer = false }, onClose = { drawer = false },
    )
    // ---- pagar com / receber em
    if (payPicker) PayWithSheet(s, isExpense, canCard, card, cardId, accountId, onClose = { payPicker = false }) { isCard, id ->
        if (isCard) { useCard = true; cardId = id } else { useCard = false; accountId = id }
        payPicker = false
    }
    // ---- seletor de data nativo do Material
    if (datePicker) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        date = d
                        if (tx == null && !paidTouched) paid = Ops.defaultPaid(d, today)
                    }
                    datePicker = false
                }) { Text(stringResource(R.string.tx_date_ok)) }
            },
            dismissButton = { TextButton({ datePicker = false }) { Text(stringResource(R.string.tx_date_cancel)) } },
        ) { DatePicker(st) }
    }
}

