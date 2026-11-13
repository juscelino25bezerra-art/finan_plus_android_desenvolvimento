// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.ui.theme.Fin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quanto tempo o aviso com "Desfazer" fica na tela antes de sumir sozinho. */
const val UNDO_VISIBLE_MS = 1_000L

private class UndoVisuals(
    override val message: String,
    override val actionLabel: String,
) : SnackbarVisuals {
    override val withDismissAction = true
    // o tempo é controlado em showUndo; Indefinite evita que o Material use os 4 s/10 s dele
    override val duration = SnackbarDuration.Indefinite
}

/**
 * Aviso com "Desfazer" (salvar lançamento, confirmar pagamento, arquivar categoria).
 *
 * Sempre some sozinho depois de [UNDO_VISIBLE_MS], contados a partir do momento em que o aviso aparece
 * (se houver outro na fila, o tempo só começa quando for a vez deste). O "×" fecha na hora.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): SnackbarResult = coroutineScope {
    val visuals = UndoVisuals(message, undoLabel)
    val timer = launch {
        val data = snapshotFlow { currentSnackbarData }.first { it?.visuals === visuals } ?: return@launch
        delay(UNDO_VISIBLE_MS)
        data.dismiss()
    }
    try { showSnackbar(visuals) } finally { timer.cancel() }
}

/**
 * Visual do aviso no tema do app: fundo do cartão do tema (sem transparência, para o texto ficar legível
 * por cima da lista), borda e sombra suaves, texto na cor do tema e "Desfazer" na cor de destaque.
 * Vale para todos os temas (Claro, Escuro, Gruvbox, OLED, Tokyo Night, Nord) porque só usa cores de [Fin.c].
 */
@Composable
fun FinSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val p = Fin.c
    val container = p.surface.compositeOver(p.bg)
    val visuals = data.visuals
    Surface(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = p.text,
        border = BorderStroke(1.dp, if (p.dark) p.border else p.accent2),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(visuals.message, Modifier.weight(1f).padding(vertical = 10.dp), color = p.text, fontSize = 15.sp)
            visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = p.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    AppIcon(Ico.CLOSE, p.muted, size = 20.dp, description = "Fechar aviso")
                }
            }
        }
    }
}
