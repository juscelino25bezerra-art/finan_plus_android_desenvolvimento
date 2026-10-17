// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finanplus.ui.theme.Fin

/**
 * Finan+ V2: primitivas exclusivamente visuais.
 *
 * Não leem nem alteram AppState, Repo ou operações financeiras.
 * As cores vêm do tema ativo para manter Light, OLED, Tokyo, Nord e Gruvbox.
 */
object V2Space {
    val screen = 20.dp
    val section = 24.dp
    val card = 18.dp
    val item = 12.dp
}

@Composable
fun V2PageHeading(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val p = Fin.c
    Column(modifier.fillMaxWidth().padding(bottom = V2Space.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = p.text, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.muted)
    }
}

@Composable
fun V2Card(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = Fin.c
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.fillMaxWidth().clip(shape).background(p.surface, shape)
            .border(1.dp, p.border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(V2Space.card),
        verticalArrangement = Arrangement.spacedBy(V2Space.item),
        content = content,
    )
}

@Composable
fun V2SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = V2Space.section, bottom = V2Space.item),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V2Space.item),
    ) {
        Text(title, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Fin.c.text)
        action?.invoke()
    }
}
