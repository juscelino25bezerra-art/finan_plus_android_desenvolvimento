// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finanplus.R
import com.finanplus.core.AppState
import com.finanplus.core.assist.Text as AssistText
import com.finanplus.ui.theme.Fin

/**
 * Ícones do app: Material Symbols Rounded, traço leve (peso 300), em tamanhos contidos (18–22dp).
 * Os desenhos estão em res/drawable/ms_*.xml (Google, licença Apache 2.0, compatível com a GPL-3.0).
 */
enum class Ico(@DrawableRes val res: Int, @DrawableRes val filled: Int = 0) {
    HOME(R.drawable.ms_home, R.drawable.ms_home_fill),
    MOVES(R.drawable.ms_swap_horiz, R.drawable.ms_swap_horiz_fill),
    REPORTS(R.drawable.ms_pie_chart, R.drawable.ms_pie_chart_fill),
    SETTINGS(R.drawable.ms_settings, R.drawable.ms_settings_fill),
    ADD(R.drawable.ms_add),
    REMOVE(R.drawable.ms_remove),
    GOAL(R.drawable.ms_flag),
    CHECK(R.drawable.ms_check),
    CARD(R.drawable.ms_credit_card),
    INFO(R.drawable.ms_info),
    EDIT(R.drawable.ms_edit),
    GAMES(R.drawable.ms_sports_esports),
    TREND(R.drawable.ms_trending_up),
    EXPAND(R.drawable.ms_expand_more),
    // tabela de ícones do redesign (REDESIGN-COERENCIA.md): um conceito, um ícone
    PALETTE(R.drawable.ms_palette),
    BELL(R.drawable.ms_notifications),
    CALC(R.drawable.ms_calculate),
    WIFI(R.drawable.ms_wifi),
    DATABASE(R.drawable.ms_database),
    SCIENCE(R.drawable.ms_science),
    DEBT(R.drawable.ms_request_quote),
    TAG(R.drawable.ms_sell),
    SPEED(R.drawable.ms_speed),
    DRAG(R.drawable.ms_drag_indicator),
    ARCHIVE(R.drawable.ms_archive),
    LOCK(R.drawable.ms_lock),
    ASSIST(R.drawable.ms_auto_awesome),
    FINGERPRINT(R.drawable.ms_fingerprint),
    UP(R.drawable.ms_arrow_upward),
    DOWN(R.drawable.ms_arrow_downward),
    BACKSPACE(R.drawable.ms_backspace),
    DROPDOWN(R.drawable.ms_arrow_drop_down),
    WARNING(R.drawable.ms_warning),
    SHIELD(R.drawable.ms_shield),
    CALENDAR(R.drawable.ms_calendar_month),
    LIST(R.drawable.ms_view_list),
    PREV(R.drawable.ms_chevron_left),
    NEXT(R.drawable.ms_chevron_right),
    SEARCH(R.drawable.ms_search),
    CLOSE(R.drawable.ms_close),
    RECEIPT(R.drawable.ms_receipt_long),
    TUNE(R.drawable.ms_tune),
    WALLET(R.drawable.ms_account_balance_wallet),
    FORECAST(R.drawable.ms_bar_chart),
    INCOME(R.drawable.ms_arrow_upward),
    EXPENSE(R.drawable.ms_arrow_downward),
    LIMIT(R.drawable.ms_track_changes),
    GROUP(R.drawable.ms_group),
    SHARE(R.drawable.ms_share),
    ATTACH(R.drawable.ms_attach_file),

    // categorias
    FOOD(R.drawable.ms_shopping_cart),
    RESTAURANT(R.drawable.ms_restaurant),
    TRANSPORT(R.drawable.ms_directions_car),
    FUEL(R.drawable.ms_local_gas_station),
    HEALTH(R.drawable.ms_favorite),
    FITNESS(R.drawable.ms_fitness_center),
    LEISURE(R.drawable.ms_theaters),
    SUBSCRIPTIONS(R.drawable.ms_subscriptions),
    EDUCATION(R.drawable.ms_school),
    OTHER(R.drawable.ms_more_horiz),
    SALARY(R.drawable.ms_payments),
    EXTRA(R.drawable.ms_attach_money),
    INVEST(R.drawable.ms_savings),
    PETS(R.drawable.ms_pets),
    CLOTHES(R.drawable.ms_checkroom),
    SHOPPING(R.drawable.ms_shopping_bag),
    BEAUTY(R.drawable.ms_spa),
    TAXES(R.drawable.ms_receipt_long),
    GIFTS(R.drawable.ms_redeem),
    BANK(R.drawable.ms_account_balance),
    WORK(R.drawable.ms_work),
    REPEAT(R.drawable.ms_repeat),
}

/** Ícone com a cor do tema. Com [description] nulo o ícone é decorativo (o texto ao lado já descreve). */
@Composable
fun AppIcon(
    icon: Ico,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    filled: Boolean = false,
    description: String? = null,
) {
    val sem = if (description == null) Modifier.clearAndSetSemantics { } else Modifier.clearAndSetSemantics { contentDescription = description }
    Icon(
        painterResource(if (filled && icon.filled != 0) icon.filled else icon.res),
        contentDescription = null, tint = tint,
        modifier = modifier.size(size).then(sem),
    )
}

/**
 * Ícone da categoria. Reconhece os nomes padrão e variações comuns,
 * sem diferenciar acento e maiúscula. Categorias desconhecidas: null (o app mostra a inicial).
 */
fun categoryIcon(category: String): Ico? {
    val c = AssistText.fold(category)
    return when {
        category == AppState.CARD_PAYMENT_CAT -> Ico.CARD
        category == AppState.GOAL_CAT -> Ico.GOAL
        c in setOf("alimentacao", "mercado", "supermercado", "comida", "feira") -> Ico.FOOD
        c in setOf("restaurante", "delivery", "lanche", "lanches") -> Ico.RESTAURANT
        c in setOf("transporte", "carro", "uber", "mobilidade") -> Ico.TRANSPORT
        c in setOf("combustivel", "gasolina", "posto") -> Ico.FUEL
        c in setOf("moradia", "casa", "aluguel", "contas") -> Ico.HOME
        c in setOf("saude", "farmacia", "medico") -> Ico.HEALTH
        c in setOf("academia", "esporte", "esportes") -> Ico.FITNESS
        c in setOf("lazer", "diversao", "entretenimento", "viagem") -> Ico.LEISURE
        c in setOf("assinaturas", "assinatura", "streaming") -> Ico.SUBSCRIPTIONS
        c in setOf("educacao", "estudos", "escola", "faculdade", "cursos") -> Ico.EDUCATION
        c in setOf("outros", "outro", "diversos") -> Ico.OTHER
        c in setOf("salario", "pagamento", "pro labore") -> Ico.SALARY
        c in setOf("extra", "extras", "renda extra", "freela", "bonus") -> Ico.EXTRA
        c in setOf("investimentos", "investimento", "poupanca", "rendimentos", "reserva") -> Ico.INVEST
        c in setOf("pets", "pet", "animais") -> Ico.PETS
        c in setOf("vestuario", "roupas", "roupa") -> Ico.CLOTHES
        c in setOf("compras", "shopping") -> Ico.SHOPPING
        c in setOf("beleza", "cuidados pessoais", "estetica") -> Ico.BEAUTY
        c in setOf("impostos", "taxas", "tarifas", "impostos e taxas") -> Ico.TAXES
        c in setOf("presentes", "doacoes", "presente", "doacao") -> Ico.GIFTS
        c in setOf("emprestimo", "emprestimos", "financiamento", "banco", "dividas") -> Ico.BANK
        c in setOf("trabalho", "adiantamento quinzenal", "adiantamento", "comissao") -> Ico.WORK
        // nomes compostos ou criados pela pessoa ("Cartão Itaú", "Empréstimo Caixa", "Meta viagem")
        "cartao" in c || "fatura" in c -> Ico.CARD
        "emprestimo" in c || "financiamento" in c -> Ico.BANK
        "salario" in c -> Ico.SALARY
        c.startsWith("meta") -> Ico.GOAL
        else -> null
    }
}

/** Tom do ícone de um item nas listas (Início e Lançamentos): a cor diz o tipo, não a categoria. */
enum class Tone { NEGATIVE, POSITIVE, PRIMARY, NEUTRAL, GOAL, CARD }

@Composable
fun Tone.color(): Color = Fin.c.let { p -> when (this) { Tone.NEGATIVE -> p.red; Tone.POSITIVE -> p.green; Tone.PRIMARY -> p.accent; Tone.NEUTRAL -> p.muted; Tone.GOAL -> p.goal; Tone.CARD -> p.card } }

/**
 * Tom de um lançamento: despesa e conta a pagar = negativo; receita e valor a receber = positivo;
 * reserva para meta = âmbar das metas; cartão (compra ou fatura) = lilás; [neutral] = mês seguinte ou só informativo.
 */
fun txTone(kind: com.finanplus.core.Kind, goal: Boolean, neutral: Boolean = false, card: Boolean = false): Tone = when {
    neutral -> Tone.NEUTRAL
    goal -> Tone.GOAL
    card -> Tone.CARD
    kind == com.finanplus.core.Kind.INCOME -> Tone.POSITIVE
    else -> Tone.NEGATIVE
}

/** Quadrado arredondado com fundo suave e o ícone da categoria na cor forte do [tone]. */
@Composable
fun CategoryTile(category: String, tone: Tone, size: Dp = 42.dp, icon: Ico? = null) {
    val c = tone.color()
    IconTile(icon ?: categoryIcon(category) ?: Ico.OTHER, c, c.copy(alpha = if (Fin.c.dark) 0.16f else 0.12f), size = size, iconSize = size * 0.48f)
}

/** Ícone de categoria solto (sem o quadrado): o da categoria ou, se desconhecida, o genérico (sem letras). */
@Composable
fun CategoryGlyph(category: String, tint: Color) = AppIcon(categoryIcon(category) ?: Ico.OTHER, tint, size = 20.dp)
