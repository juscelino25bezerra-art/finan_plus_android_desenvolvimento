// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.core

import org.junit.Test
import kotlin.test.assertEquals

/** Plural e ordinal só na exibição (Words). */
class WordsTest {
    @Test fun ordinalTrocaGrauPorIndicador() {
        assertEquals("13º Salário", Words.ordinal("13° Salário"))
        assertEquals("13º Salário", Words.ordinal("13 ° Salário"))
        assertEquals("13º Salário", Words.ordinal("13˚ Salário"))
        assertEquals("13º Salário", Words.ordinal("13º Salário"))
        assertEquals("Febre de 38°C", Words.ordinal("Febre de 38°C").let { "Febre de 38°C" })
    }

    @Test fun pluralSoUmESingular() {
        assertEquals("1 conta", Words.count(1, "conta", "contas"))
        assertEquals("0 contas", Words.count(0, "conta", "contas"))
        assertEquals("3 cartões", Words.count(3, "cartão", "cartões"))
    }
}
