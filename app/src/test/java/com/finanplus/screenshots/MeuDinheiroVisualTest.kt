// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onFirst
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

    /** O caso da proposta: conta com R$ 100,00 reservados numa meta que vai passar do prazo, Sofisa quase no limite e um empréstimo em parcelas. */
    private fun estado(): AppState {
        val today = LocalDate.now()
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 26688)),
            cards = listOf(
                Card("nu", "Nubank", 100000, close = 28, due = 5),
                Card("santander", "Santander", 200000, close = 28, due = 20),
                Card("sofisa", "Sofisa 6333", 50000, close = 28, due = 10),
            ),
            goals = listOf(Goal("g1", "Notebook", 800000, 10000, today.plusMonths(6), 1000)),
            limits = mapOf("Lazer" to 20000L, "Alimentação" to 50000L),
            txs = listOf(
                Tx("meta", Kind.EXPENSE, 10000, today, "Meta › Notebook", AppState.GOAL_CAT, true, "main", goalId = "g1"),
                Tx("s1", Kind.EXPENSE, 48851, today, "Compras", "Outros", true, "main", "sofisa"),
                Tx("t1", Kind.EXPENSE, 49088, today, "Compras", "Lazer", true, "main", "santander"),
                Tx("mp1", Kind.EXPENSE, 12140, today.plusDays(10), "Mercado Pago (1/3)", "Empréstimo", false, "main", groupId = "mp", parcelN = 1, parcelTotal = 3),
                Tx("mp2", Kind.EXPENSE, 12140, today.plusDays(40), "Mercado Pago (2/3)", "Empréstimo", false, "main", groupId = "mp", parcelN = 2, parcelTotal = 3),
            ),
        )
    }

    private fun start() {
        prefs.askedNotifications = true
        prefs.welcomeSeen = true
        prefs.categoriesMigrated = false
    }
    private fun openCard(title: String) { compose.onNode(hasText(title) and hasClickAction()).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); compose.waitForIdle() }
    private fun back() { compose.onNode(hasContentDescription("Voltar para Meu dinheiro")).performClick(); compose.waitForIdle() }

    @After fun reset() = prefs.update { it.copy(glass = false) }

    @Test
    fun meuDinheiroNosTemasComESemVidro() {
        start()
        for ((theme, name) in listOf(ThemeId.GRUVBOX to "gruvbox", ThemeId.GRUVBOX_OLED to "gruvbox-oled")) for (glass in listOf(false, true)) {
            prefs.update { it.copy(glass = glass) }
            prefs.categoriesMigrated = false
            Repo.showForScreenshots(estado().copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                tab()
                val tag = "$name, vidro=$glass"
                assertTrue("resumo do topo ($tag)", has("Saldo nas contas") && has("R$ 166,88") && has("Fatura aberta") && has("Guardado em metas"))
                assertTrue("alerta da meta ($tag)", has("A meta Notebook vai passar do prazo") && has("Ajustar contribuição"))
                assertTrue("cards de navegação ($tag)", has("Contas e cartões") && has("1 conta · 3 cartões") && has("Dívidas") && has("E se…?") && has("Categorias"))
                assertTrue("dívida montada das parcelas ($tag)", has("1 dívida · parcela de R$ 121,40"))
                assertTrue("limites ($tag)", has("2 limites · 1 perto do teto"))
                assertTrue("metas ($tag)", has("1 meta · R$ 100,00 guardados"))
                assertFalse("plural antigo ($tag)", has("conta(s)") || has("cartão(ões)"))
                val suffix = name + if (glass) "-vidro" else ""
                shot("meu-dinheiro-$suffix")

                openCard("Contas e cartões")
                assertTrue("linha da reserva ($tag)", has("+ R$ 100,00 guardados em metas"))
                assertTrue("botões + ($tag)", hasDesc("Adicionar conta") && hasDesc("Adicionar cartão"))
                assertTrue("fatura dentro do card ($tag)", has("Fatura vence em") && has("Pagar fatura"))
                assertTrue("uso do limite ($tag)", has("97% do limite · disponível R$ 11,49"))
                assertTrue("cartão sem fatura ($tag)", has("Sem fatura em aberto"))
                shot("meu-dinheiro-contas-$suffix")
                back()

                openCard("Dívidas")
                assertTrue("dívida ($tag)", has("Mercado Pago") && has("0 de 3 parcelas pagas"))
                shot("meu-dinheiro-dividas-$suffix")
                back()

                openCard("Categorias")
                assertTrue("categorias ($tag)", has("Despesas ·") && has("Cartões e limite especial agora ficam em Contas e cartões") && has("Nova categoria"))
                assertFalse("Empréstimo continua ($tag)", compose.onAllNodes(hasText("Empréstimo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
                shot("meu-dinheiro-categorias-$suffix")
                back()
            }
        }
    }

    @Test
    fun meuDinheiroComValoresOcultos() {
        start()
        prefs.update { it.copy(glass = true) }
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX_OLED, privacy = true))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab()
            assertFalse("valor vazou no topo", has("166,88") || has("100,00") || has("979,39") || has("121,40"))
            shot("meu-dinheiro-oculto")
            openCard("Contas e cartões")
            assertFalse("valor vazou nos cartões", has("488,51") || has("11,49") || has("500,00"))
            shot("meu-dinheiro-cartoes-oculto")
        }
    }
}
