// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
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
 * Início (INICIO.md): cartão do saldo com o previsto, "Falta receber/pagar", aviso de risco do Assistente
 * e próximos vencimentos agrupados. Gruvbox e Gruvbox OLED, com e sem vidro, e com "Ocultar valores".
 * Roda com as capturas: `./gradlew testDebugUnitTest -PfinanScreenshots=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class InicioVisualTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(dir, "$name.png").path)
    }

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** Rola a lista do Início até o item [index] (0 topo, 1 saldo, 2 pendências, 3 assistente, 4 vencimentos). */
    private fun scrollToItem(index: Int) {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(index)
        compose.waitForIdle()
    }

    /**
     * Os números do print do usuário: saldo 166,88; num dia do mês vencem 121,40 (empréstimo) e 300,00,
     * e cai o adiantamento de 1.213,62; a fatura do Itaú vence no dia 1º do mês seguinte.
     */
    private fun state(): AppState {
        val today = LocalDate.now()
        val ym = today.ym()
        val day = minOf(today.plusDays(11), ym.atEndOfMonth())
        val prev = ym.minusMonths(1)
        fun tx(id: String, kind: Kind, v: Long, d: LocalDate, desc: String, cat: String, paid: Boolean, card: String = "") =
            Tx(id, kind, v, d, desc, cat, paid, "main", card)
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 0)),
            cards = listOf(Card("itau", "Itaú 7414", 200000, close = 24, due = 1)),
            txs = listOf(
                tx("sal", Kind.INCOME, 250000, prev.atDay(5), "Salário", "Salário", true),
                tx("alug", Kind.EXPENSE, 233312, prev.atDay(10), "Aluguel", "Moradia", true),
                tx("emp", Kind.EXPENSE, 12140, day, "Empréstimo Caixa", "Empréstimo", false),
                tx("cel", Kind.EXPENSE, 30000, day, "Conta do celular", "Assinaturas", false),
                tx("adi", Kind.INCOME, 121362, day, "adiantamento", "Salário", false),
                tx("merc", Kind.EXPENSE, 18990, today, "Mercado", "Mercado", true, card = "itau"),
            ),
        )
    }

    @Test
    fun inicioNosTemasEscurosComESemVidro() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        val s = state()
        for ((theme, name) in listOf(ThemeId.GRUVBOX to "gruvbox", ThemeId.GRUVBOX_OLED to "gruvbox-oled")) for (glass in listOf(false, true)) {
            DevicePrefs.get(app).update { it.copy(glass = glass) }
            Repo.showForScreenshots(s.copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitForIdle()
                val tag = "$name, vidro=$glass"
                assertTrue("slogan continua no Início ($tag)", !has("Seu dinheiro, sem complicação."))
                assertTrue("sem o aviso de risco ($tag)", has("faltariam R$ 254,52"))
                assertTrue("sem o próximo recebimento ($tag)", has("Próximo: R$ 1.213,62 em"))
                assertTrue("linha fixa do previsto continua ($tag)", !has("a receber − "))
                val suffix = name + if (glass) "-vidro" else ""
                shot("inicio-$suffix")
                scrollToItem(3)
                assertTrue("sem o grupo do mês seguinte ($tag)", has("PRÓXIMO MÊS · 1 DE"))
                assertTrue("link antigo continua ($tag)", !has("Ver pagamento"))
                shot("inicio-vencimentos-$suffix")
            }
        }
        DevicePrefs.get(app).update { it.copy(glass = false) }
    }

    @Test
    fun ocultarValoresTambemNoAssistente() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = true) }
        Repo.showForScreenshots(state().copy(theme = ThemeId.GRUVBOX, privacy = true))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            assertTrue("o aviso não aparece com valores ocultos", has("Se ele atrasar"))
            assertTrue("valor vazou no Assistente", !has("254,52") && !has("166,88") && !has("421,40"))
            shot("inicio-oculto")
        }
        DevicePrefs.get(app).update { it.copy(glass = false) }
    }

    @Test
    fun folhaDoPrevisto() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = false) }
        Repo.showForScreenshots(state().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNode(hasContentDescription("Como o saldo previsto é calculado")).performClick()
            compose.waitForIdle()
            assertTrue("folha sem a explicação", has("Considera valores pendentes (inclusive atrasados)"))
            assertTrue("folha sem a conta", has("Hoje R$ 166,88 + R$ 1.213,62 a receber"))
            shot("inicio-previsto-info")
        }
    }

    @Test
    fun iconesPorTipoEmLancamentos() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = false) }
        Repo.showForScreenshots(state().copy(theme = ThemeId.GRUVBOX_OLED))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNode(hasText("Lançamentos") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            compose.waitForIdle()
            shot("lancamentos-icones")
        }
    }
}
