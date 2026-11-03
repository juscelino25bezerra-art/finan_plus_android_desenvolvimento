// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.finanplus.MainActivity
import com.finanplus.core.Account
import com.finanplus.core.AppState
import com.finanplus.core.Card
import com.finanplus.core.Kind
import com.finanplus.core.ThemeId
import com.finanplus.core.Tx
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.finanplus.ui.screens.TxTags
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Nova tela de lançamento (LANCAMENTO.md): abrir a gaveta, escolher categoria, trocar o tipo mudando a cor do botão
 * e salvar com o snackbar. Capturas em Gruvbox e Gruvbox OLED, com e sem vidro, e com fonte do sistema grande.
 * Roda com as capturas: `./gradlew testDebugUnitTest -PfinanScreenshots=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class LancamentoTelaTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun shot(name: String) { compose.waitForIdle(); captureScreenRoboImage(File(dir, "$name.png").path) }
    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { compose.onNode(hasTestTag(tag)).performClick(); compose.waitForIdle() }

    /** Histórico com categorias e descrições repetidas, para as seções "Usadas recentemente" e as sugestões. */
    private fun estado(): AppState {
        val d = LocalDate.now()
        fun tx(id: String, kind: Kind, v: Long, days: Long, desc: String, cat: String) = Tx(id, kind, v, d.minusDays(days), desc, cat, true, "main")
        return AppState(
            accounts = listOf(Account("main", "Conta principal", 150000), Account("pix", "Carteira", 5000)),
            cards = listOf(Card("itau", "Itaú", 200000, close = 24, due = 1)),
            txs = listOf(
                tx("a", Kind.EXPENSE, 4500, 1, "Uber", "Transporte"),
                tx("b", Kind.EXPENSE, 23000, 2, "Mercado", "Alimentação"),
                tx("c", Kind.EXPENSE, 6000, 3, "iFood", "Alimentação"),
                tx("d", Kind.EXPENSE, 3800, 4, "Uber", "Transporte"),
                tx("e", Kind.EXPENSE, 9000, 5, "Cinema", "Lazer"),
                tx("f", Kind.EXPENSE, 12000, 6, "Farmácia", "Saúde"),
                tx("g", Kind.EXPENSE, 18000, 7, "Mercado", "Alimentação"),
                tx("h", Kind.EXPENSE, 9900, 8, "Anuidade", "Cartão de Crédito"),
                tx("i", Kind.INCOME, 350000, 9, "Salário", "Salário"),
            ),
        )
    }

    private fun abrirNovo(kindLabel: String) {
        compose.onNode(hasContentDescription("Adicionar movimentação")).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(if (kindLabel == "Despesa") "Dinheiro que saiu" else "Dinheiro que entrou")).performClick()
        compose.waitForIdle()
    }

    /** Cor do botão Salvar (pixel perto da borda esquerda, longe do texto). */
    private var colorShot = 0
    private fun saveColor(): Int {
        compose.waitForIdle()
        // captureToImage não redesenha no Robolectric; a captura do Roborazzi gera o PNG e lemos o pixel dele
        val file = File(dir, "tmp-botao-salvar-${colorShot++}.png")
        compose.onNode(hasTestTag(TxTags.SAVE)).captureRoboImage(file.path)
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path)
        file.delete()
        return bmp.getPixel(bmp.width / 12, bmp.height / 2)
    }

    @After fun reset() {
        RuntimeEnvironment.setFontScale(1f)
        DevicePrefs.get(app).update { it.copy(glass = false) }
    }

    @Test
    fun gavetaCategoriaTrocaDeTipoESalvar() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        DevicePrefs.get(app).update { it.copy(glass = false) }
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            abrirNovo("Despesa")
            assertTrue("sem o título", has("Novo lançamento"))
            assertTrue("sem 'Escolher categoria'", has("Escolher categoria"))
            assertTrue("sem 'Pagar com'", has("Pagar com"))
            assertTrue("sem 'Salvar despesa'", has("Salvar despesa"))
            assertTrue("sugestões de descrição", has("Mercado") && has("Uber"))

            // gaveta: abre, mostra recentes e não oferece "Cartão de Crédito"
            click(TxTags.CATEGORY_ROW)
            assertTrue("a gaveta não abriu", hasTag(TxTags.DRAWER))
            assertTrue("sem 'Usadas recentemente'", has("Usadas recentemente"))
            assertTrue("'Cartão de Crédito' continua na grade", !hasTag(TxTags.cell("Cartão de Crédito")))
            click(TxTags.cell("Transporte"))
            assertTrue("a gaveta não fechou", !hasTag(TxTags.DRAWER))
            assertTrue("a categoria não foi escolhida", !has("Escolher categoria") && has("Transporte"))

            // botão habilita com valor, troca de cor ao mudar de tipo e a categoria some (não existe em receita)
            compose.onNode(hasTestTag(TxTags.AMOUNT)).performTextInput("12,5")
            compose.waitForIdle()
            val expense = saveColor()
            click(TxTags.KIND_INCOME)
            compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
            val income = saveColor()
            assertNotEquals("a cor do botão não mudou", expense, income)
            assertTrue("vermelho esperado na despesa", android.graphics.Color.red(expense) > android.graphics.Color.green(expense) + 40)
            assertTrue("verde esperado na receita", android.graphics.Color.green(income) >= android.graphics.Color.red(income) - 10)
            assertTrue("sem 'Salvar receita'", has("Salvar receita") && has("Receber em"))
            assertTrue("categoria não foi limpa", has("Escolher categoria"))
            click(TxTags.KIND_EXPENSE)
            compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
            assertEquals("a cor da despesa voltou diferente", expense, saveColor())

            // salvar: fecha e mostra o snackbar
            click(TxTags.SAVE)
            assertTrue("a tela não fechou", !has("Novo lançamento"))
            assertTrue("sem o snackbar", has("Despesa salva"))
            val saved = Repo.state.value.txs.first { it.value == 1250L }
            assertEquals(1250L, saved.value)
            assertEquals("Outros", saved.category)
        }
    }

    @Test
    fun capturasNosTemasComESemVidro() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        for ((theme, name) in listOf(ThemeId.GRUVBOX to "gruvbox", ThemeId.GRUVBOX_OLED to "gruvbox-oled")) for (glass in listOf(false, true)) {
            DevicePrefs.get(app).update { it.copy(glass = glass) }
            Repo.showForScreenshots(estado().copy(theme = theme))
            ActivityScenario.launch(MainActivity::class.java).use {
                val suffix = name + if (glass) "-vidro" else ""
                abrirNovo("Despesa")
                compose.onNode(hasTestTag(TxTags.AMOUNT)).performTextInput("1234,56")
                compose.waitForIdle()
                assertTrue("formatação pt-BR ($suffix)", has("1.234,56"))
                shot("lancamento-despesa-$suffix")
                click(TxTags.CATEGORY_ROW)
                shot("lancamento-gaveta-$suffix")
                click(TxTags.cell("Alimentação"))
                click(TxTags.KIND_INCOME)
                compose.mainClock.advanceTimeBy(400)
                shot("lancamento-receita-$suffix")
            }
        }
    }

    @Test
    fun valorZeroMostraErroEBloqueia() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX_OLED))
        ActivityScenario.launch(MainActivity::class.java).use {
            abrirNovo("Despesa")
            val before = Repo.state.value.txs.size
            compose.onNode(hasTestTag(TxTags.AMOUNT)).performTextInput("0,00")
            compose.waitForIdle()
            assertTrue("sem o erro discreto", has("Informe um valor maior que zero"))
            compose.onNode(hasTestTag(TxTags.SAVE)).performClick()
            compose.waitForIdle()
            assertEquals("salvou valor zero", before, Repo.state.value.txs.size)
            shot("lancamento-valor-zero")
        }
    }

    @Test
    fun fonteGrandeDoSistema() {
        DevicePrefs.get(app).askedNotifications = true
        DevicePrefs.get(app).welcomeSeen = true
        RuntimeEnvironment.setFontScale(1.6f)
        Repo.showForScreenshots(estado().copy(theme = ThemeId.GRUVBOX))
        ActivityScenario.launch(MainActivity::class.java).use {
            abrirNovo("Receita")
            compose.onNode(hasTestTag(TxTags.AMOUNT)).performTextInput("123456789,99")
            compose.waitForIdle()
            assertTrue("valor longo não formatado", has("123.456.789,99"))
            assertTrue("botão sumiu com fonte grande", hasTag(TxTags.SAVE))
            shot("lancamento-fonte-grande")
        }
    }
}
