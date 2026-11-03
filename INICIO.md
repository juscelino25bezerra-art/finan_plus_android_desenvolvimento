# Início: saldo, pendências, aviso do Assistente e próximos vencimentos

Reorganização da tela **Início**. É uma mudança visual e de organização: nenhum cálculo mudou. Saldo atual, saldo previsto, "Falta receber" e "Falta pagar" continuam vindo das mesmas funções (`Finance.currentBalance`, `Finance.futureBalance` e `Period.monthPending`). Cores só do tema (Gruvbox, Gruvbox OLED e efeito vidro), ícones só do Material Symbols e textos novos em `res/values/strings.xml`.

Ordem da tela: cabeçalho, cartão do saldo, "Falta receber" e "Falta pagar", Assistente e Próximos vencimentos.

## 1. Cabeçalho

Ficam o título "Finan+", a data ("Segunda, 2 de novembro") e o botão "Ocultar valores". O slogan "Seu dinheiro, sem complicação." saiu do Início e continua na tela de boas-vindas.

## 2. Cartão do saldo

| Antes | Agora |
|---|---|
| Saldo atual, e embaixo um cartão interno com ícone de gráfico, "Saldo previsto até 30/11/2026", o valor, a explicação e o chip | Um cartão só: "Saldo atual" com o valor em destaque, um divisor, e embaixo "Previsto em 30/11" com o valor (cor primária) à esquerda e o chip "+R$ X vs. outubro" à direita |
| Linha fixa "Hoje R$ … + … a receber − … a pagar" | Saiu do cartão. A conta está na folha do botão "info" |

- **Botão "info"** ao lado de "Previsto em 30/11": área de toque de 48dp e descrição "Como o saldo previsto é calculado" para o leitor de tela. Abre uma folha com o valor previsto, o texto "Considera valores pendentes, faturas e recorrências até esta data." e a conta "Hoje R$ X + R$ Y a receber − R$ Z a pagar = R$ previsto". A conta só aparece quando fecha com os números da tela, como a linha antiga.
- O chip continua verde quando o previsto fica acima do saldo do fim do mês anterior e vermelho quando fica abaixo. No primeiro mês e com "Ocultar valores" ele não aparece, como antes.
- A barra "Despesas equivalem a X% das receitas recebidas" continua no pé do cartão, só quando já entrou alguma receita no mês.

## 3. "Falta receber" e "Falta pagar"

| Cartão | Antes | Agora |
|---|---|---|
| Falta receber | Barra de progresso e "0% recebido" | "Próximo: R$ X em dd/MM" (o próximo recebimento pendente do mês). Sem pendentes: "Tudo recebido este mês" (ou "Nada a receber este mês", se nada foi lançado). A barra só aparece quando algo já foi recebido (mais de 0%) |
| Falta pagar | "Contas e faturas em aberto neste mês" | "N contas até 30/11" (último dia do mês; "1 conta" no singular, em `<plurals>`). Sem pendentes: "Tudo pago neste mês" |

O cartão inteiro é clicável. "Falta receber" abre Lançamentos com receitas pendentes do mês e "Falta pagar" com despesas pendentes do mês.

A contagem de "N contas" usa `HomeAdvice.monthBills`, que pega as mesmas contas do "Falta pagar": despesas pendentes fora do cartão e faturas em aberto que vencem no mês. O teste `contasDoMesBatemComOFaltaPagar` confere que a soma é a mesma.

## 4. Assistente

O cartão subiu para logo abaixo dos dois cartões e agora mostra **um** aviso principal, que cruza informações em vez de repetir números que já estão na tela. O link "Abrir assistente ›" continua.

### Aviso de risco (prioridade máxima), `HomeAdvice.risk`

1. Pega o próximo dia do mês com contas a pagar pendentes. Uma conta atrasada conta como de hoje.
2. Soma as contas pendentes até esse dia (C).
3. Se o saldo atual é menor que C, há risco, e falta = C − saldo atual.
4. O texto depende do recebimento pendente até esse dia (o de maior valor):
   - no mesmo dia: "No dia {d} vencem {C} e o {recebimento} de {valor} cai no mesmo dia. Se ele atrasar, seu saldo de {saldo} não cobre: faltariam {falta}.";
   - antes: "… e o {recebimento} de {valor} cai antes, no dia {d2}. Se ele atrasar, …";
   - sem recebimento até lá: "No dia {d} vencem {C} e seu saldo hoje é {saldo}. Faltam {falta} para cobrir essas contas."

Os valores ficam em negrito: contas e o que falta na cor negativa, o recebimento na cor positiva. As frases estão em `strings.xml`. Os valores entram por marcadores (`styled` em `Assistant.kt`), então uma tradução pode mudar a ordem sem quebrar o destaque.

**Exemplo com os dados de 2/11:** saldo R$ 166,88. No dia 13 vencem R$ 121,40 + R$ 300,00 = R$ 421,40, e o adiantamento de R$ 1.213,62 cai no mesmo dia. Resultado: "No dia 13 vencem **R$ 421,40** e o adiantamento de **R$ 1.213,62** cai no mesmo dia. Se ele atrasar, seu saldo de R$ 166,88 não cobre: faltariam **R$ 254,52**."

### Sem risco

O cartão mostra a frase mais útil que o Assistente já gera (`Insights.report`). Ficam de fora as que só repetem os cartões de cima ("Ainda faltam … a pagar" e "A receber neste mês"; ver `HomeAdvice.repeatsCards`). Se não sobrar nenhuma, aparece a dica principal, com "Dispensar" como antes.

### "Ocultar valores"

Com valores ocultos, todos os valores do aviso viram "R$ ••••" (o texto usa o mesmo formatador do resto do Assistente). A frase continua lá, e só o dia aparece.

## 5. Próximos vencimentos

| Antes | Agora |
|---|---|
| Um cartão por conta, com "Vence em dd/MM/aaaa" e "Ver pagamento ›" | Um cartão só, com linhas separadas por divisórias finas |
| Sem agrupamento | Grupos por data, com rótulo pequeno em caixa alta ("SEXTA, 13 DE NOVEMBRO"). Atrasadas: "EM ATRASO · …", em vermelho |
| — | Contas de meses seguintes (ex.: a fatura do Itaú que vence em 01/12) ficam em "PRÓXIMO MÊS · 1 DE DEZEMBRO", com ícone e valor em cor neutra, porque não entram no "Falta pagar" do mês |

- Cada linha mostra ícone, título, a categoria como subtítulo (sem repetir a data; nas faturas, "Fatura do cartão"), o valor e uma seta. A linha inteira abre o pagamento: a fatura abre "Pagar fatura" e a conta abre o lançamento. O link "Ver pagamento ›" saiu.
- Ao lado do título da seção, o link **"Ver todos ›"** abre Lançamentos no mês.
- Mostra até 4 itens (antes eram 3), para que a primeira conta do mês seguinte apareça junto com as do mês.

## 6. Cores dos ícones por tipo (Início e Lançamentos)

Os ícones ficam num quadrado arredondado, com fundo suave e o ícone na cor forte (`CategoryTile` em `AppIcon.kt`):

| Tipo | Cor |
|---|---|
| Despesas e contas a pagar | negativa (vermelho do tema) |
| Receitas e valores a receber | positiva (verde do tema) |
| Reservas para meta | primária |
| Mês seguinte, previstos de recorrência e informativos | neutra |

Os ícones de letra ("C", "1") foram trocados pelo ícone da categoria. Nomes compostos também são reconhecidos: empréstimo e financiamento usam `account_balance`, cartão e fatura usam `credit_card`, salário usa `payments` e meta usa `flag`. Categoria desconhecida usa o ícone genérico (`more_horiz`), não mais a inicial.

Ícone novo: `info` (Material Symbols Rounded, peso 300), em `res/drawable/ms_info.xml`. As contagens de licença foram atualizadas em `THIRD_PARTY_NOTICES.md`, `third_party/material-symbols/README.md` e `assets/licenca/TERCEIROS.txt`.

## Testes

- `HomeAdviceTest` (JVM):
  - risco com recebimento no mesmo dia (o exemplo de 2/11, faltam R$ 254,52);
  - risco com recebimento antes;
  - risco sem recebimento até o dia;
  - conta atrasada contando como de hoje;
  - sem risco;
  - mês sem contas pendentes;
  - próximo recebimento e "tudo recebido";
  - soma das contas igual ao "Falta pagar";
  - grupos dos próximos vencimentos com o mês seguinte;
  - frases que repetem os cartões.
- `InicioVisualTest` (Robolectric + Roborazzi, roda com as capturas, `-PfinanScreenshots=true`):
  - Início em Gruvbox e Gruvbox OLED, com e sem vidro, rolado até os vencimentos;
  - "Ocultar valores" ligado, conferindo que nenhum valor aparece no texto do Assistente;
  - a folha do botão "info";
  - os ícones por tipo em Lançamentos.

## Arquivos

- `core/HomeAdvice.kt` (novo): aviso de risco, contas e recebimentos pendentes do mês, e grupos dos próximos vencimentos.
- `ui/screens/HomeScreen.kt`: cabeçalho, cartão do saldo, cartões de pendências, próximos vencimentos e a folha `ForecastInfoSheet`.
- `ui/screens/Assistant.kt`: `HomeAssistantCard` com um aviso só, `riskText` e `styled`.
- `ui/components/AppIcon.kt`: `Tone`, `txTone`, `CategoryTile`, ícone `INFO` e o reconhecimento de nomes compostos.
- `ui/Root.kt`: `Sheet.ForecastInfo` e o ícone do item de Lançamentos com a cor do tipo.
- `ui/screens/Sheets.kt`: abre a folha do previsto.
- `res/values/strings.xml`: textos novos do Início.
