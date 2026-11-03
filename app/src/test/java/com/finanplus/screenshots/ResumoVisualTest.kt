// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
 * Resumo (RESUMO.md): selo do mês, resultado com o previsto, comparação só com dados, fluxo "Entrou" e
 * "Saiu ou foi para o cartão", categorias e evolução. Gruvbox e Gruvbox OLED, com e sem vidro, e com
 * "Ocultar valores". Roda com as capturas: `./gradlew testDebugUnitTest -PfinanScreenshots=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class ResumoVisualTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun shot(name: String) { compose.waitForIdle(); captureScreenRoboImage(File(dir, "$name.png").path) }
    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun tab(label: String) {
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
    }
    /** Itens da lista do Resumo (sem saldo acumulado): 0 título, 1 período, 2 selo, 3 resultado, 4 receitas/despesas, 5 fluxo, 6 categorias, 7 evolução. */
    private fun scrollToItem(index: Int) {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(index)
        compose.waitForIdle()
    }

    /**
     * O caso do exemplo: nada recebido no mês, uma compra no cartão de R$ 126,00 e R$ 100,00 guardados
     * numa meta (56% e 44% da barra "Saiu"), com o mês anterior tendo dados e um recebimento pendente.
     */
    private fun semRecebimento(): AppState {
        val today = LocalDate.now(); val ym = today.ym(); val prev = ym.minusMonths(1)
        fun tx(id: String, kind: Kind, v: Long, d: LocalDate, cat: String, paid: Boolean = true, card: String = "") =
            Tx(id, kind, v, d, id, cat, paid, "main", card)
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 50000)),
            cards = listOf(Card("itau", "Itaú", 200000, close = 24, due = 1)),
            txs = listOf(
                tx("p-sal", Kind.INCOME, 250000, prev.atDay(1), "Salário"),
                tx("p-alug", Kind.EXPENSE, 90000, prev.atDay(1), "Moradia"),
                tx("p-merc", Kind.EXPENSE, 30000, prev.atDay(1), "Mercado"),
                tx("merc", Kind.EXPENSE, 12600, today, "Mercado", card = "itau"),
                tx("meta", Kind.EXPENSE, 10000, today, com.finanplus.core.AppState.GOAL_CAT).copy(goalId = "g1"),
                tx("adi", Kind.INCOME, 121362, ym.atEndOfMonth(), "Salário", paid = false),
            ),
        )
    }

    private fun comRecebimento(): AppState = semRecebimento().let { s ->
        val today = LocalDate.now()
        s.copy(txs = s.txs + listOf(
            Tx("sal", Kind.INCOME, 250000, today, "sal", "Salário", true, "main"),
            Tx("cat1", Kind.EXPENSE, 8000, today, "t", "Transporte", true, "main"),
            Tx("cat2", Kind.EXPENSE, 7000, today, "l", "Lazer", true, "main"),
            Tx("cat3", Kind.EXPENSE, 6000, today, "s", "Saúde", true, "main"),
            Tx("cat4", Kind.EXPENSE, 5000, today, "a", "Assinaturas", true, "main"),
            Tx("cat5", Kind.EXPENSE, 4000, today, "e", "Educação", true, "main"),
            Tx("cat6", Kind.EXPENSE, 3000, today, "v", "", true, "main"),
        ))
    }

    @Test
    fun resumoSemRecebimentoNosTemasEscurosComESemVidro() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        val s = semRecebimento()
        for ((theme, name) in listOf(ThemeId.GRUVBOX to "gruvbox", ThemeId.GRUVBOX_OLED to "gruvbox-oled")) for (glass in listOf(false, true)) {
            DevicePrefs.get(app).update { it.copy(glass = glass) }
            Repo.showForScreenshots(s.copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                tab("Resumo")
                val tag = "$name, vidro=$glass"
                assertTrue("sem o selo do mês ($tag)", has("Mês em andamento · dia"))
                assertTrue("frase fixa continua ($tag)", !has("Só valores realizados"))
                assertTrue("sem o resultado ($tag)", has("Resultado até agora") && has("Saldo previsto em"))
                assertTrue("sem a linha do cartão ($tag)", has("Inclui R$ 126,00 no cartão · fatura em"))
                assertTrue("sem a próxima entrada ($tag)", has("Próxima: R$ 1.213,62 em"))
                assertTrue("texto antigo continua ($tag)", !has("Sem base para comparar"))
                val suffix = name + if (glass) "-vidro" else ""
                shot("resumo-$suffix")
                scrollToItem(5)
                assertTrue("sem 'Nada recebido ainda' ($tag)", has("Nada recebido ainda. Próximo: R$ 1.213,62 em"))
                assertTrue("sem 'Saiu ou foi para o cartão' ($tag)", has("Saiu ou foi para o cartão"))
                assertTrue("percentuais 56% e 44% ($tag)", has("56%") && has("44%"))
                assertTrue("rótulos Despesas e Guardado em metas ($tag)", has("Despesas") && has("Guardado em metas"))
                shot("resumo-fluxo-$suffix")
                scrollToItem(7)
                assertTrue("sem 'até dia' na evolução ($tag)", has("até dia"))
                shot("resumo-evolucao-$suffix")
            }
        }
        DevicePrefs.get(app).update { it.copy(glass = false) }
    }

    @Test
    fun resumoComRecebimentoCategoriasEDetalheDoMes() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = false) }
        Repo.showForScreenshots(comRecebimento().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Resumo")
            scrollToItem(5)
            assertTrue("a linha neutra apareceu com recebimento", !has("Nada recebido ainda"))
            scrollToItem(6)
            assertTrue("sem 'Outras'", has("Outras"))
            shot("resumo-categorias")
            compose.onNode(hasText("Outras") and androidx.compose.ui.test.hasClickAction()).performClick()
            compose.waitForIdle()
            assertTrue("a lista completa não abriu", has("Todas as categorias"))
            shot("resumo-categorias-todas")
        }
    }

    @Test
    fun resumoComValoresOcultos() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = true) }
        Repo.showForScreenshots(semRecebimento().copy(theme = ThemeId.GRUVBOX, privacy = true))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Resumo")
            assertTrue("valor vazou no topo", !has("126,00") && !has("1.213,62") && !has("100,00"))
            shot("resumo-oculto")
            scrollToItem(5)
            assertTrue("valor vazou no fluxo", !has("126,00") && !has("1.213,62") && !has("100,00"))
            shot("resumo-fluxo-oculto")
        }
        DevicePrefs.get(app).update { it.copy(glass = false) }
    }

    @Test
    fun folhaDoQueEntraNoResumo() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = false) }
        Repo.showForScreenshots(semRecebimento().copy(theme = ThemeId.GRUVBOX_OLED))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Resumo")
            compose.onNode(hasContentDescription("O que entra neste resumo")).performClick()
            compose.waitForIdle()
            assertTrue("folha sem a explicação das despesas", has("Despesas entram na data da compra"))
            assertTrue("folha sem as metas", has("Reservas para meta não são despesa"))
            shot("resumo-info")
        }
    }
}
