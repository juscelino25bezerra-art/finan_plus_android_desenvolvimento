// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.finanplus.R
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.Ops
import com.finanplus.core.Outcome
import com.finanplus.core.Words
import com.finanplus.data.Repo
import com.finanplus.ui.LocalTxSnackbar
import com.finanplus.ui.LocalUiScope
import com.finanplus.ui.components.AppIcon
import com.finanplus.ui.components.Glass
import com.finanplus.ui.components.Ico
import com.finanplus.ui.components.IconTile
import com.finanplus.ui.components.LocalDialogs
import com.finanplus.ui.components.PrimaryButton
import com.finanplus.ui.components.SegmentedChoice
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Meu dinheiro › Categorias (REDESIGN-COERENCIA.md): tela própria; tocar edita (renomear), arrastar pela alça
 * reordena, deslizar para a esquerda arquiva (com Desfazer). Arquivar tira da lista de escolha; os lançamentos
 * antigos continuam com o nome no histórico. Aviso explica onde ficaram cartões e empréstimos.
 */
@Composable
internal fun CategoriesPage(s: AppState) {
    val p = Fin.c
    val dialogs = LocalDialogs.current
    var kind by rememberSaveable { mutableStateOf(Kind.EXPENSE) }
    val list = s.cats.of(kind)
    SegmentedChoice(
        listOf(Kind.EXPENSE to stringResource(R.string.cats_tab_expense, s.cats.expense.size), Kind.INCOME to stringResource(R.string.cats_tab_income, s.cats.income.size)),
        kind, Modifier.padding(top = 6.dp),
    ) { kind = it }
    Glass(Modifier.fillMaxWidth().padding(top = 8.dp), radius = 22.dp, padding = 0.dp) {
        key(kind, list) { ReorderableList(kind, list) }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(p.accent2.copy(alpha = 0.7f)).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(Ico.INFO, p.accent, size = 20.dp)
        Text(stringResource(R.string.cats_migration_note), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = p.text)
    }
    PrimaryButton(stringResource(R.string.cats_new), Modifier.padding(top = 14.dp)) {
        dialogs.input(stringResource0(kind), "", "Nome") { name ->
            when (val r = Ops.addCategory(Repo.state.value, kind, name)) {
                is Outcome.Err -> dialogs.notice(r.title, r.message)
                is Outcome.Ok -> Repo.replace(r.state)
            }
        }
    }
}

private fun stringResource0(kind: Kind) = if (kind == Kind.EXPENSE) "Nova categoria de despesa" else "Nova categoria de receita"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderableList(kind: Kind, list: List<String>) {
    val p = Fin.c
    val dialogs = LocalDialogs.current
    val snackbar = LocalTxSnackbar.current
    val scope = LocalUiScope.current
    var order by remember(list) { mutableStateOf(list) }
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableIntStateOf(1) }
    val archivedMsg = stringResource(R.string.cats_archived)
    val undo = stringResource(R.string.cats_undo)
    fun commit(newOrder: List<String>) { if (newOrder != list) Repo.update { it.copy(cats = it.cats.with(kind, newOrder)) } }
    fun archive(name: String) {
        val before = Repo.state.value
        when (val chk = Ops.checkDeleteCategory(before, kind, name)) {
            is Outcome.Err -> dialogs.notice(chk.title, chk.message)
            is Outcome.Ok -> {
                Repo.replace(Ops.deleteCategory(before, kind, name))
                scope?.launch {
                    if (snackbar.showSnackbar(archivedMsg.format(name), actionLabel = undo, withDismissAction = true) == SnackbarResult.ActionPerformed)
                        Repo.update { it.copy(cats = before.cats, limits = before.limits) }
                }
            }
        }
    }
    order.forEachIndexed { i, name ->
        key(name) {
            if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 1.dp, color = p.border)
            val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.EndToStart) archive(name); false })
            val up = stringResource(R.string.cats_move_up); val down = stringResource(R.string.cats_move_down); val arch = stringResource(R.string.cats_archive)
            SwipeToDismissBox(
                swipe, enableDismissFromStartToEnd = false,
                modifier = Modifier.zIndex(if (dragging == i) 1f else 0f).graphicsLayer { translationY = if (dragging == i) offset else 0f },
                backgroundContent = {
                    // o fundo "Arquivar" só aparece enquanto a linha é deslizada (parado, a linha fica limpa)
                    if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Row(Modifier.fillMaxSize().background(p.muted.copy(alpha = 0.85f)).padding(end = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(Ico.ARCHIVE, p.bg, size = 20.dp)
                            Text(arch, color = p.bg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().background(if (dragging == i || swipe.dismissDirection != SwipeToDismissBoxValue.Settled) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent).heightIn(min = 56.dp)
                        .onSizeChanged { rowPx = maxOf(1, it.height) }
                        .semantics {
                            customActions = listOfNotNull(
                                if (i > 0) CustomAccessibilityAction(up) { commit(order.toMutableList().apply { add(i - 1, removeAt(i)) }); true } else null,
                                if (i < order.lastIndex) CustomAccessibilityAction(down) { commit(order.toMutableList().apply { add(i + 1, removeAt(i)) }); true } else null,
                                CustomAccessibilityAction(arch) { archive(name); true },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // alça: arrastar reordena
                    Box(
                        Modifier.size(48.dp).pointerInput(order) {
                            detectDragGestures(
                                onDragStart = { dragging = i; offset = 0f },
                                onDragEnd = {
                                    val to = (i + (offset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                    val n = order.toMutableList().apply { add(to, removeAt(i)) }
                                    dragging = -1; offset = 0f; order = n; commit(n)
                                },
                                onDragCancel = { dragging = -1; offset = 0f },
                            ) { change, drag -> change.consume(); offset += drag.y }
                        }.semantics { contentDescription = "Arrastar $name" },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(Ico.DRAG, p.muted, size = 20.dp) }
                    Row(
                        Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            dialogs.input("Renomear categoria", "Lançamentos, recorrências e limites de “$name” passam a usar o novo nome.", "Nome", name) { nn ->
                                Repo.commit(Ops.renameCategory(Repo.state.value, kind, name, nn))?.let { dialogs.notice(it.title, it.message) }
                            }
                        }.padding(end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(TxIcons.forCategory(name), p.accent, p.accent2, size = 36.dp, iconSize = 20.dp)
                        Text(Words.ordinal(name), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
