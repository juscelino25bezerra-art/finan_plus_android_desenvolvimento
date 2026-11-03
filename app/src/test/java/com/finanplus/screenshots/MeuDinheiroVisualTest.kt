// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.finanplus.MainActivity
import com.finanplus.core.Account
import com.finanplus.core.AppState
import com.finanplus.core.Card
import com.finanplus.core.Goal
import com.finanplus.core.Kind
import com.finanplus.core.ThemeId
import com.finanplus.core.Tx
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Meu dinheiro (MEU-DINHEIRO.md): visão geral, contas com "guardados em metas", cartões com a fatura dentro,
 * planejamento e seções recolhíveis com estado salvo. Gruvbox e Gruvbox OLED, com e sem vidro, e "Ocultar valores".
 * Roda com as capturas: `./gradlew testDebugUnitTest -PfinanScreenshots=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class MeuDinheiroVisualTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = DevicePrefs.get(app)

    private fun shot(name: String) { compose.waitForIdle(); captureScreenRoboImage(File(dir, "$name.png").path) }
    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasDesc(text: String) = compose.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun tab() {
        compose.onNode(hasText("Meu dinheiro") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
    }
    /** Itens da lista: 0 título, 1 visão geral, 2 cabeçalho Contas, 3 contas, 4 cabeçalho Cartões, 5.. cartões, depois Planejamento. */
    private fun scrollToItem(index: Int) {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(index)
        compose.waitForIdle()
    }

    /** O caso relatado: conta com R$ 100,00 reservados numa meta, Sofisa quase no limite, Santander e um cartão sem fatura. */
    private fun estado(): AppState {
        val today = LocalDate.now()
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 26688)),
            cards = listOf(
                Card("nu", "Nubank", 100000, close = 28, due = 5),
                Card("santander", "Santander", 200000, close = 28, due = 20),
                Card("sofisa", "Sofisa 6333", 50000, close = 28, due = 10),
            ),
            goals = listOf(Goal("g1", "Notebook", 300000, 10000, null, 0)),
            limits = mapOf("Lazer" to 20000L, "Alimentação" to 50000L),
            txs = listOf(
                Tx("meta", Kind.EXPENSE, 10000, today, "Meta › Notebook", AppState.GOAL_CAT, true, "main", goalId = "g1"),
                Tx("s1", Kind.EXPENSE, 48851, today, "Compras", "Outros", true, "main", "sofisa"),
                Tx("t1", Kind.EXPENSE, 49088, today, "Compras", "Lazer", true, "main", "santander"),
            ),
        )
    }

    private fun start() {
        prefs.askedNotifications = true
        prefs.welcomeSeen = true
    }

    @After fun reset() = prefs.update { it.copy(glass = false, moneyAccountsOpen = true, moneyCardsOpen = true) }

    @Test
    fun meuDinheiroNosTemasComESemVidro() {
        start()
        for ((theme, name) in listOf(ThemeId.GRUVBOX to "gruvbox", ThemeId.GRUVBOX_OLED to "gruvbox-oled")) for (glass in listOf(false, true)) {
            prefs.update { it.copy(glass = glass, moneyAccountsOpen = true, moneyCardsOpen = true) }
            Repo.showForScreenshots(estado().copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                tab()
                val tag = "$name, vidro=$glass"
                assertFalse("subtítulo antigo ($tag)", has("Contas, cartões e seus planos"))
                assertTrue("visão geral ($tag)", has("Na conta") && has("Em metas") && has("Faturas abertas"))
                assertTrue("saldo igual ao do Início ($tag)", has("R$ 166,88"))
                assertTrue("linha da reserva ($tag)", has("+ R$ 100,00 guardados em metas"))
                assertFalse("plural antigo ($tag)", has("conta(s)") || has("cartão(ões)"))
                assertTrue("botões + ($tag)", hasDesc("Adicionar conta") && hasDesc("Adicionar cartão"))
                val suffix = name + if (glass) "-vidro" else ""
                shot("meu-dinheiro-$suffix")
                scrollToItem(5)
                assertTrue("fatura dentro do card ($tag)", has("Fatura vence em") && has("Pagar fatura"))
                assertTrue("uso do limite ($tag)", has("97% do limite · disponível R$ 11,49"))
                assertTrue("descrição da barra ($tag)", hasDesc("Sofisa 6333, 97% do limite usado, disponível R$ 11,49"))
                assertTrue("cartão sem fatura ($tag)", has("Sem fatura em aberto"))
                assertFalse("lista antiga de faturas ($tag)", has("Fatura nov.") || has("Fatura dez."))
                shot("meu-dinheiro-cartoes-$suffix")
                scrollToItem(8)
                assertTrue("planejamento ($tag)", has("Planejamento") && has("Notebook · R$ 100,00 guardado") && has("2 limites · 1 perto do teto"))
                shot("meu-dinheiro-planejamento-$suffix")
            }
        }
    }

    @Test
    fun secoesRecolhidasSalvamEstadoEMostramAlertas() {
        start()
        prefs.update { it.copy(moneyAccountsOpen = true, moneyCardsOpen = true) }
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab()
            compose.onNode(hasTestTag("money_accounts_header")).performClick()
            compose.onNode(hasTestTag("money_cards_header")).performClick()
            compose.waitForIdle()
            assertFalse("estado de Contas não salvo", prefs.value.moneyAccountsOpen)
            assertFalse("estado de Cartões não salvo", prefs.value.moneyCardsOpen)
            assertTrue("resumo de Contas recolhida", has("Contas · R$ 166,88"))
            assertTrue("resumo de Cartões recolhida", has("Cartões · 3 · R$ 979,39 em faturas"))
            assertTrue("descrição com o estado", hasDesc("Cartões, recolhido, toque para expandir"))
            assertTrue("alerta do Sofisa (uso >= 90%)", has("Sofisa 6333") && has("97% do limite"))
            assertFalse("Nubank sem fatura não alerta", has("Nubank ·"))
            assertTrue("+ Cartão funciona recolhido", hasDesc("Adicionar cartão"))
            shot("meu-dinheiro-recolhido")
        }
        // ao abrir de novo, o estado volta
        ActivityScenario.launch(MainActivity::class.java).use {
            tab()
            assertTrue("estado não restaurado", has("Cartões · 3 ·") && has("Contas · R$ 166,88"))
            // tocar no alerta expande Cartões
            compose.onNode(hasContentDescription("Alerta: Sofisa 6333", substring = true)).performClick()
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            assertTrue("alerta não expandiu a seção", prefs.value.moneyCardsOpen)
        }
    }

    @Test
    fun meuDinheiroComValoresOcultos() {
        start()
        prefs.update { it.copy(glass = true, moneyAccountsOpen = true, moneyCardsOpen = true) }
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX_OLED, privacy = true))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab()
            assertFalse("valor vazou no topo", has("166,88") || has("100,00") || has("979,39"))
            shot("meu-dinheiro-oculto")
            scrollToItem(5)
            assertFalse("valor vazou nos cartões", has("488,51") || has("11,49") || has("500,00"))
            shot("meu-dinheiro-cartoes-oculto")
        }
    }
}
