// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.finanplus.MainActivity
import com.finanplus.core.Account
import com.finanplus.core.AppState
import com.finanplus.core.Card
import com.finanplus.core.Goal
import com.finanplus.core.Kind
import com.finanplus.core.SharedExpense
import com.finanplus.core.SharedPerson
import com.finanplus.core.ThemeId
import com.finanplus.core.Tx
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Redesign (REDESIGN-COERENCIA.md): Lançamentos lista e calendário com o glossário único, Ajustes agrupado,
 * despesa compartilhada e "E se eu economizar…?". Gruvbox sem vidro e Gruvbox OLED com vidro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class RedesignVisualTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = DevicePrefs.get(app)

    private fun shot(name: String) { compose.waitForIdle(); captureScreenRoboImage(File(dir, "$name.png").path) }
    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun tab(label: String) {
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
    }
    private fun tapText(t: String) { compose.onAllNodes(hasText(t, substring = true) and hasClickAction()).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); compose.waitForIdle() }

    /** Os números da proposta: saldo R$ 166,88 depois de guardar R$ 100,00, a receber R$ 3.413,62, a pagar R$ 609,91. */
    private fun estado(): AppState {
        val t = LocalDate.now()
        val end = t.withDayOfMonth(t.lengthOfMonth())
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 26688)),
            cards = listOf(Card("sofisa", "Sofisa", 50000, close = 28, due = 15)),
            goals = listOf(Goal("g1", "Notebook", 800000, 10000, t.plusYears(2), 0)),
            txs = listOf(
                Tx("meta", Kind.EXPENSE, 10000, t, "Meta › Notebook", AppState.GOAL_CAT, true, "main", goalId = "g1"),
                Tx("mp", Kind.EXPENSE, 12140, end, "Mercado Pago", "Dívidas", false, "main"),
                Tx("fat", Kind.EXPENSE, 48851, end, "Fatura do cartão", "Outros", false, "main"),
                Tx("adi", Kind.INCOME, 121362, end, "Adiantamento quinzenal", "Salário", false, "main"),
                Tx("sal", Kind.INCOME, 80000, end, "Salário", "Salário", false, "main"),
                Tx("13", Kind.INCOME, 140000, end, "13° salário · 1ª parcela", "13° Salário", false, "main"),
            ),
            shared = listOf(SharedExpense("sh1", "Lanche", 12500, t, "me", listOf(SharedPerson("me", "Eu", 6250), SharedPerson("amor", "Amor", 6250)))),
        )
    }

    @After fun reset() = prefs.update { it.copy(glass = false, accumulateBalance = false) }

    @Test
    fun telasDoRedesign() {
        prefs.askedNotifications = true
        prefs.welcomeSeen = true
        for ((theme, name, glass) in listOf(Triple(ThemeId.GRUVBOX, "gruvbox", false), Triple(ThemeId.GRUVBOX_OLED, "gruvbox-oled-vidro", true))) {
            prefs.update { it.copy(glass = glass, accumulateBalance = true) }
            Repo.showForScreenshots(estado().copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                tab("Lançamentos")
                assertTrue("sem o previsto ($name)", has("Previsto no fim do mês") && has("R$ 2.970,59"))
                assertTrue("linhas rotuladas ($name)", has("Saldo atual") && has("A receber") && has("A pagar"))
                assertTrue("13º com ordinal ($name)", has("13º salário") && !has("13° salário"))
                assertFalse("conta única aparece na linha ($name)", has("Salário · Conta principal"))
                shot("redesign-lancamentos-$name")
                compose.onNode(hasContentDescription("Visualização em calendário")).performClick(); compose.waitForIdle()
                assertTrue("totais do calendário iguais aos da lista ($name)", has("R$ 3.413,62") && has("R$ 609,91") && has("R$ 2.970,59"))
                shot("redesign-calendario-$name")
                compose.onNode(hasContentDescription("Visualização em lista")).performClick(); compose.waitForIdle()

                tab("Ajustes")
                assertTrue("Ajustes ($name)", has("Tudo fica criptografado neste aparelho.") && has("Cálculo do saldo") && has("Dados e backup"))
                shot("redesign-ajustes-$name")
                tapText("Aparência")
                assertTrue("tela de Aparência ($name)", has("Efeito vidro"))
                compose.onNode(hasContentDescription("Voltar para Ajustes")).performClick(); compose.waitForIdle()

                tab("Meu dinheiro")
                tapText("Despesas compartilhadas")
                tapText("Lanche")
                assertTrue("você pagou ($name)", has("você pagou") && has("Sua parte") && !has("pago por Eu"))
                assertTrue("falta receber ($name)", has("Falta receber") && has("Deve R$ 62,50") && has("Quitar"))
                shot("redesign-compartilhada-$name")
                compose.onNode(hasContentDescription("Voltar para Despesas compartilhadas")).performClick(); compose.waitForIdle()
            }
        }
    }

    @Test
    fun eSeEconomizarComPlanoQueNaoFecha() {
        prefs.askedNotifications = true
        prefs.welcomeSeen = true
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Meu dinheiro")
            tapText("E se…?")
            tapText("E se eu economizar")
            assertTrue("stepper de meses", has("12 meses"))
            assertTrue("rendimento", has("Não considera rendimento"))
            shot("redesign-e-se-economizar")
        }
    }
}
