# Resumo: resultado, comparação, fluxo, categorias e evolução

Reorganização da aba **Resumo**: textos, organização e apresentação. A regra de quais lançamentos entram nas contas não mudou. Receitas, despesas, reservas para meta e o saldo previsto continuam vindo de `Finance` e `Period`, os mesmos do Início. Cores só do tema (Gruvbox, Gruvbox OLED e efeito vidro), ícones só do Material Symbols e textos novos em `res/values/strings.xml`.

A regra de apresentação está em `core/SummaryView.kt` (sem Android, testada em `SummaryViewTest`).

## 1. Cabeçalho e contexto do mês

- Ficam o título "Resumo", o botão "PDF" e o seletor de mês.
- A frase fixa "Só valores realizados (pagos ou recebidos)…" saiu. A explicação virou o botão de ícone **info** ao lado do "PDF" (48dp, descrição "O que entra neste resumo"), que abre uma folha com:
  - os valores são realizados (pagos ou recebidos) e do período escolhido;
  - despesas entram na data da compra, compras no cartão incluídas, e a fatura só aparece como pagamento de conta, sem contar de novo;
  - reservas para meta não são despesa;
  - os valores são do mês selecionado.
- **Selo de status** abaixo do seletor: "Mês em andamento · dia 2 de 30" no mês atual e "Mês encerrado" nos passados. Mês que ainda não começou: "Mês futuro". Período livre (início e fim que não formam um mês inteiro) não tem selo.

## 2. Card de resultado

- Rótulo "Resultado até agora" (mês em andamento), "Resultado do mês" (encerrado ou futuro) ou "Resultado do período" (período livre). O valor fica em destaque: vermelho se negativo, verde se positivo.
- A frase "Você gastou R$ X a mais do que recebeu" saiu.
- À direita, "Saldo previsto em 30/11" com o valor na cor primária. É o mesmo cálculo do Início (`Finance.futureBalance` até o último dia do mês). Aparece no mês atual e nos futuros. Em mês encerrado e em período livre não aparece, porque um previsto no passado não faz sentido.
- "Guardado em metas": bandeira, valor na cor primária e a legenda "Não entra nas despesas". Só aparece se houver reserva no período. O valor é o líquido (guardado menos retirado), como antes.

## 3. Receitas e Despesas

- "Sem base para comparar" saiu. A comparação só aparece se o período anterior tiver dados (alguma receita ou despesa realizada). Formato "+R$ X vs. mês anterior" ou "−R$ X vs. mês anterior". Sem dados, a linha não aparece.
- Receitas sem valor recebido: "Próxima: R$ X em dd/MM" (a entrada pendente mais próxima) ou "Nada pendente".
- Despesas com compras no cartão: "Inclui R$ X no cartão · fatura em dd/MM". A data é a da fatura mais próxima de hoje em diante (se todas já venceram, a última). Sem compras no cartão, a linha não aparece.
- Os rótulos seguem com a seta para cima (receitas) e para baixo (despesas).
- Com "Ocultar valores", a comparação mostra "Variação oculta" (o valor e a direção revelariam a proporção entre os períodos), e o resto dos valores vira "R$ ••••".

## 4. Fluxo do dinheiro

| Antes | Agora |
|---|---|
| "De onde veio" / "Para onde foi" | "Entrou" / "Saiu ou foi para o cartão" |
| Explicação fixa e "As despesas passaram o valor recebido…" | Saíram: a explicação foi para a folha do botão info, e o resultado já mostra o excesso |
| Sem recebimento: barra vazia e o botão "Entrou R$ 0,00" | Linha neutra: "Nada recebido ainda. Próximo: R$ X em dd/MM" ou "Nada pendente neste mês" |
| Barra de entradas em cores fixas por origem | Uma barra "Entrou" com o total, no verde do tema, e a lista das origens |
| Saídas divididas por categoria, mais "Metas" e "Sobrou" | Barra "Saiu" dividida em **Despesas** (vermelho do tema) e **Guardado em metas** (cor primária) |

- O total de cada parte aparece no cabeçalho dela.
- Os percentuais somam sempre 100%, pelo método do maior resto (`SummaryView.percents`). Exemplo: R$ 126,00 de despesas e R$ 100,00 guardados são 56% e 44%.
- Os nomes (Despesas, Guardado em metas) ficam sempre visíveis na legenda, sem depender só da cor.
- As duas barras usam a mesma escala, então dá para ver quando saiu mais do que entrou.

## 5. Despesas por categoria

- Agrupa pela categoria da compra. O cartão aparece só como subtítulo ("No cartão Itaú", ou "Nos cartões Itaú, Nubank"). Compra sem categoria (vazia ou só espaços) entra em "Sem categoria".
- Cada linha: ícone da categoria num quadrado na cor negativa, nome, valor e barra proporcional em vermelho do tema. O aviso de limite mensal continua embaixo da linha.
- Ordem do maior para o menor, no máximo 5 categorias. O resto soma em **"Outras"** ("N categorias · toque para ver todas"), que abre uma folha com a lista completa.

## 6. Evolução (últimos 6 meses)

- Só aparecem os meses com lançamentos (receita ou despesa realizada, ou reserva para meta). Mês sem dados não vira barra vazia.
- O valor, em tamanho pequeno, fica sobre cada barra ("950", "1,2 mil"). Tocar no mês abre o detalhe: receitas, despesas, guardado em metas e resultado, com o atalho "Ver os lançamentos do mês".
- Cores: receitas em verde escuro e despesas em vermelho claro, derivados das cores do tema com diferença de claridade, para quem tem daltonismo. A legenda tem o texto "Receitas" e "Despesas".
- O mês em andamento leva a marca "até dia X".

## 7. Regras de cálculo (não mudaram)

- Saldo, previsto, "a receber" e "a pagar" continuam vindo dos mesmos métodos do Início.
- Reservas para meta continuam fora das despesas.
- Compras no cartão continuam contando como despesa **na data da compra**. Veja "Decisões em aberto".

## Decisões em aberto

1. **Quando a compra no cartão conta como despesa.** Hoje conta na data da compra, e a fatura aparece só como pagamento de conta, sem contar de novo. O Início, o "Falta pagar" e o Resumo seguem a mesma regra. O contrário (contar no vencimento da fatura) mudaria o resultado de cada mês, então ficou como está. A linha "Inclui R$ X no cartão · fatura em dd/MM" ajuda a ver isso.
2. **Comparação no mês atual.** A comparação continua sendo com os **mesmos dias** do mês anterior (comparar o começo de um mês com o anterior inteiro seria injusto). O texto vira "vs. mês anterior (mesmos dias)", e não só "vs. mês anterior".
3. **Previsto em mês encerrado.** O "Saldo previsto" não aparece em mês encerrado nem em período livre.
4. **"Dados" do mês anterior.** Conta como tendo dados quando há alguma receita ou despesa realizada. Se só as despesas tiveram valor, a comparação de Receitas aparece como "+R$ X vs. …" (contra zero).
5. **"Guardado em metas" na barra "Saiu".** Usa o valor líquido (guardado menos retirado), o mesmo do card de resultado. "Retirado de metas" e "Sobrou" saíram do fluxo.
6. **Mês futuro:** ganhou o selo "Mês futuro", que não estava na especificação.

## Testes

- `SummaryViewTest` (JVM): percentuais que somam 100%, comparação com e sem dados, selo do mês, Receitas zeradas (próxima entrada ou nada pendente), compras no cartão e data da fatura, "Sem categoria" e cartão como subtítulo, 5 categorias mais "Outras", evolução só com meses com dados.
- `ResumoVisualTest` (Robolectric + Roborazzi, roda com as capturas, `-PfinanScreenshots=true`): Gruvbox e Gruvbox OLED, com e sem vidro, "Ocultar valores" (conferindo que nenhum valor aparece), a lista de categorias, e a folha do botão info.

## Arquivos

- `core/SummaryView.kt` (novo): selo do mês, percentuais, comparação, próxima entrada, compras no cartão, categorias e evolução.
- `ui/screens/ReportsScreen.kt`: a tela, e as folhas `SummaryInfoSheet`, `AllCategoriesSheet` e `MonthDetailSheet`.
- `ui/Root.kt` e `ui/screens/Sheets.kt`: `Sheet.SummaryInfo`, `Sheet.AllCategories` e `Sheet.MonthDetail`.
- `res/values/strings.xml`: textos novos do Resumo.
