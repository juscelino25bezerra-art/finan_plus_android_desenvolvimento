// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

/**
 * Textos com número: plural correto em pt-BR (só 1 é singular), nunca "conta(s)".
 * Ordinal com "º"/"ª" (nunca o símbolo de grau). Ver REDESIGN-COERENCIA.md.
 */
object Words {
    /** "1 conta", "0 contas", "3 cartões" */
    fun count(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

    /** Troca o símbolo de grau usado como ordinal ("13° Salário") pelo indicador ordinal ("13º Salário"). Só para exibir. */
    fun ordinal(text: String): String = text.replace(Regex("(\\d)\\s?[°˚ᵒ]"), "$1º")
}
