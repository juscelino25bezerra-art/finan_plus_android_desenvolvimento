// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finanplus.ui.theme.Fin

/** Voltar no padrão único do app: seta + nome da tela anterior, sem fundo (REDESIGN-COERENCIA.md). */
@Composable
fun BackLink(previous: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Fin.c
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Voltar para $previous" }.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(Ico.PREV, p.accent, size = 22.dp)
        Text(previous, color = p.accent, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
