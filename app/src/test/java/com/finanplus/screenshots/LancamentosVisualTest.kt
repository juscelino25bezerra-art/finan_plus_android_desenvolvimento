// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.finanplus.MainActivity
import com.finanplus.core.AppState
import com.finanplus.core.Kind
import com.finanplus.core.ThemeId
import com.finanplus.core.Tx
import com.finanplus.core.ym
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
 * Lançamentos: reservas para meta como "guardado" (fora do total do dia) e a busca recolhível.
 * Desenha a lista nos temas Gruvbox e Gruvbox OLED, com e sem o efeito vidro (LANCAMENTOS-METAS-E-BUSCA.md).
 * Roda com as capturas: `./gradlew testDebugUnitTest -PfinanScreenshots=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class LancamentosVisualTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(dir, "$name.png").path)
    }

    private fun tab(label: String) {
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Dados de demonstração + o caso de 2/11: num dia novo (o primeiro da lista), uma compra no cartão e
     * uma reserva para meta. O cabeçalho do dia deve mostrar só "R$ 100,00 em metas".
     */
    private fun state(): AppState {
        val today = LocalDate.now()
        val demo = DemoData.state(today)
        val last = demo.txs.filter { it.date.ym() == today.ym() }.maxOf { it.date }
        val day = if (last.plusDays(1).ym() == today.ym()) last.plusDays(1) else last
        val extra = listOf(
            Tx("shot-card", Kind.EXPENSE, 12600, day, "Cartão de Crédito", "Outros", true, "main", "nu"),
            Tx("shot-goal", Kind.EXPENSE, 10000, day, "Meta › Viagem de férias", AppState.GOAL_CAT, true, "main", goalId = "g1"),
        )
        return demo.copy(txs = demo.txs + extra)
    }

    @Test
    fun metasNoTotalDoDiaNosTemasEscurosComESemVidro() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        val s = state()
        for ((theme, name) in listOf(ThemeId.GRUVBOX to "gruvbox", ThemeId.GRUVBOX_OLED to "gruvbox-oled")) for (glass in listOf(false, true)) {
            DevicePrefs.get(app).update { it.copy(glass = glass) }
            Repo.showForScreenshots(s.copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                tab("Lançamentos")
                assertTrue("linha de metas ausente ($name, vidro=$glass)", has("R$ 100,00 em metas"))
                shot("lancamentos-metas-$name" + if (glass) "-vidro" else "")
            }
        }
        DevicePrefs.get(app).update { it.copy(glass = false) }
    }

    @Test
    fun buscaRecolhivel() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = false) }
        Repo.showForScreenshots(state().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Lançamentos")
            // fechada por padrão: sem campo de texto na tela
            assertTrue("campo de busca já aberto", compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
            compose.onNode(hasContentDescription("Buscar descrição ou categoria")).performClick()
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).performTextInput("café")
            compose.waitForIdle()
            assertTrue("busca não achou", has("Café"))
            shot("lancamentos-busca")
            // sem resultado
            compose.onNode(hasSetTextAction()).performTextInput("zzzz")
            compose.waitForIdle()
            assertTrue("sem a mensagem de vazio", has("Nenhum lançamento encontrado"))
            // X: limpa e recolhe
            compose.onNode(hasContentDescription("Limpar e fechar a busca")).performClick()
            compose.waitForIdle()
            assertTrue("campo continuou aberto", compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
            assertTrue("mensagem de vazio continuou", !has("Nenhum lançamento encontrado"))
        }
    }
}
