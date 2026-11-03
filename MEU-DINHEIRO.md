# Meu dinheiro: visão geral, cartões com fatura e seções recolhíveis

Mudança de organização visual. **Nenhuma regra de cálculo mudou**: a tela só reorganiza números que o app já calcula (`core/MoneyView.kt`).

## O que mudou

- **Cabeçalho:** fica só o título "Meu dinheiro" (o subtítulo saiu).
- **Visão geral em três colunas** com divisórias finas: **Na conta** (soma dos saldos das contas, o mesmo número do "Saldo atual" do Início), **Em metas** (total guardado nas metas, cor primária) e **Faturas abertas** (soma da fatura aberta de cada cartão, a mesma mostrada em cada card, cor negativa). O "1 conta(s) · 3 cartão(ões)" saiu. Onde houver contagem, o plural é tratado (`MoneyView.plural`: "1 conta", "3 cartões").
- **Contas:** cabeçalho com **+ Conta**. Cada conta é uma linha tocável (account_balance, nome, linha de apoio, saldo à direita e chevron) que abre o formulário da conta, onde ficam editar e excluir. A linha de apoio mostra **"+ R$ X guardados em metas"** quando saiu dinheiro daquela conta para metas (soma dos lançamentos de reserva da conta, menos as retiradas). O botão "Editar" saiu.
- **Cartões:** cabeçalho com **+ Cartão**. Cada cartão é um card com a fatura dentro, em três níveis:
  1. credit_card, nome, "Fatura vence em dd/MM" e o valor aberto em destaque. Com 15 dias ou menos, "· daqui a N dias" e a linha fica na cor negativa ("· amanhã", "· vence hoje", "· vencida" nos casos-limite).
  2. Barra de uso do limite (usado ÷ limite, o mesmo "usado" de antes, que inclui parcelas futuras) com "N% do limite · disponível R$ X" e "de R$ limite". Até 69% cor primária; 70%–89% tom de alerta do tema; 90% ou mais tom de alerta, ícone warning e texto em negrito. A porcentagem é arredondada para baixo e a faixa segue o número mostrado.
  3. "Fecha dia X · vence dia Y" e **Pagar fatura**: preenchido só no cartão que vence primeiro, tonal nos demais, escondido com "Sem fatura em aberto" quando não há fatura.
  - Ordenados pelo vencimento da fatura aberta (os sem fatura no fim). O card inteiro abre o **detalhe do cartão** (fatura aberta com pagar, compras desta fatura, histórico de faturas e "Editar cartão"). A lista separada de faturas e os botões "Editar" saíram.
- **Planejamento:** Metas e Limites mensais num card só, em linhas tocáveis. Metas: "Notebook · R$ 100,00 guardado" ou "N metas · R$ X guardados". Limites: "Defina um limite por categoria" sem limites, senão "N limites · M perto do teto" (80% ou mais, o mesmo critério de "Atenção" da lista). Cada linha abre a lista correspondente numa folha (o conteúdo que antes ficava nas seções recolhíveis).
- **Seções recolhíveis (Contas e Cartões):** tocar no cabeçalho recolhe ou expande (seta expand_more girando, `AnimatedVisibility` de 200 ms). Recolhido: "Contas · R$ total" (+ "· N contas" com mais de uma) e "Cartões · N · R$ total em faturas". Com Cartões recolhida, aparece uma linha de alerta por cartão com fatura vencendo em até 7 dias ou uso >= 90% (warning + texto, por exemplo "Sofisa 6333 · vence 15/11 · 98% do limite"); tocar expande e rola até o cartão. O estado fica salvo no aparelho (`DevicePrefs`: `moneyAccountsOpen`, `moneyCardsOpen`; padrão: as duas abertas).
- **Acessibilidade:** toques de 48dp; "+ Conta"/"+ Cartão" com contentDescription "Adicionar conta"/"Adicionar cartão"; cabeçalhos com papel de botão e "Cartões, recolhido, toque para expandir"; a barra é lida como "Sofisa 6333, 98% do limite usado, disponível R$ 11,49".
- **Ocultar valores:** vale para todos os números, inclusive visão geral, resumos recolhidos, disponível, limite e linhas de alerta (a porcentagem vira "perto do limite").
- Ícone novo: `expand_more` (Material Symbols, registrado nas licenças). O tom de alerta dos limites, que era uma cor fixa, passou a ser o `warning` do tema.

## Itens 1 e 8 (investigados, sem alteração de cálculo)

- **Saldo da conta × Início:** o "Saldo atual" do Início é `Finance.currentBalance` = soma de `Finance.accountBalance`, que é exatamente o que esta tela mostra. A reserva para meta feita saindo da conta é um lançamento pago que já é descontado nos dois. Pelo código, com uma conta só, os dois números não divergem. Falta um print ou backup para achar a origem da diferença relatada.
- **Sofisa (R$ 300,00 em 13/11):** o vencimento das faturas do cartão vem sempre do "vence dia" do cartão, e "Pagar fatura" salva como pago com o nome "Pagamento fatura …". Logo, "Fatura Sofisa R$ 300,00" é um lançamento comum pendente, fora do cartão. O "Falta pagar" soma esse lançamento e a fatura aberta do cartão: conta em dobro se for a mesma fatura. Proposta: remover/pagar o lançamento manual e avisar de possível duplicidade.
- **Santander fora de "Próximos vencimentos":** a lista junta contas pendentes e faturas abertas, ordena por data e mostra só as 4 primeiras. Proposta: garantir a próxima fatura de cada cartão, ou aumentar o limite.

## Decisões (podem ser revistas)

1. **Faturas abertas** soma a fatura mostrada em cada card (a fechada com saldo, senão a aberta), não as parcelas futuras. O uso do limite continua incluindo as parcelas futuras, como antes.
2. **Detalhe da conta** é o formulário de conta que já existia (editar e excluir).
3. Os alertas só aparecem com Cartões recolhida (com a seção aberta os cards já mostram o alerta).
4. Com "Ocultar valores", a barra fica desfocada e a porcentagem não aparece, como nos limites.
5. As frases montadas (resumos, alertas) ficam em `MoneyView` para serem testadas, como em `HomeAdvice`/`SummaryView`; rótulos fixos estão em `strings.xml`.

## Testes

- Unitários (`core/MoneyViewTest.kt`): saldo da conta igual ao do Início com reserva para meta (e retirada), soma das faturas abertas (com pagamento parcial), porcentagem e disponível, faixas (69, 70, 89, 90), ordenação por vencimento, "daqui a N dias", plural de contas e cartões, resumos de metas e limites, resumo recolhido e condições da linha de alerta.
- Interface (`screenshots/MeuDinheiroVisualTest.kt`, Robolectric): Gruvbox e Gruvbox OLED com e sem vidro, estado recolhido salvo e restaurado ao abrir de novo, alerta que expande a seção e "Ocultar valores".
