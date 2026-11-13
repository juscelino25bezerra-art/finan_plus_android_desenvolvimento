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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finanplus.ui.theme.Fin

/**
 * Aviso com "Desfazer" (salvar lançamento, confirmar pagamento, arquivar categoria).
 *
 * Sempre some sozinho: com um botão de ação o Material deixa o aviso na tela até a pessoa tocar
 * (SnackbarDuration.Indefinite), por isso a duração é sempre [SnackbarDuration.Long] (cerca de 10 s;
 * o Android pode dar mais tempo quando um leitor de tela está ligado). O "×" fecha na hora.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): SnackbarResult =
    showSnackbar(message, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)

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
