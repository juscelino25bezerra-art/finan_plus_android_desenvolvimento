# Nova tela de lançamento (Receita e Despesa)

Mudança de interface e de fluxo. **Nenhuma regra de cálculo, nenhum campo salvo e nenhum total de outra tela mudou.** Não foi preciso criar campo novo: a tela monta o mesmo `TxDraft` de antes e salva por `Ops.saveTx`.

## O que mudou

- **Tela cheia** no lugar da folha: fechar (X) à esquerda e "Novo lançamento" centralizado (ao editar: "Editar lançamento"), sem outros botões. Voltar ou X com algo digitado pede confirmação para descartar, como antes.
- **Despesa | Receita** em duas metades. A metade escolhida fica preenchida (vermelho do tema para despesa, verde para receita). A troca anima a cor do valor e do botão Salvar (220 ms). Se a categoria escolhida não existe no novo tipo, a seleção é limpa (`TxForm.categoryAfterKindChange`).
- **Valor em destaque**: 52sp centralizado com "R$" menor ao lado, foco e teclado `Decimal` ao abrir um lançamento novo, formatação pt-BR enquanto digita (`AmountInput.format`: `0,00`, `12,50`, `1.234,56`). Valor zero mostra "Informe um valor maior que zero." abaixo do valor. A fonte encolhe (até 45%) quando o valor é longo ou a fonte do sistema é grande.
- **Cartão de campos** com linhas de 64dp: Categoria (ícone na caixa com o tom do tipo), **Pagar com** (despesa: contas e cartões já cadastrados) / **Receber em** (receita: contas) e **Data** (ícone de calendário, sem seta, seletor nativo do Material, padrão hoje).
- **Gaveta de categorias** (`ModalBottomSheet`, 85% da altura): busca sem diferenciar acento, "Usadas recentemente" (até 4, pela data do uso mais recente; some sem histórico ou durante a busca) e "Todas" em grade de 3 colunas (84dp, ícone + nome; a escolhida tem borda e fundo no tom do tipo). Tocar escolhe e fecha.
- **Ícones** da proposta: restaurant, directions_car, home, favorite, sports_esports, school, account_balance, credit_card, payments, card_giftcard*, trending_up e more_horiz; o que não tem ícone usa more_horiz (`TxIcons.forCategory`).
- **"Cartão de Crédito" saiu da lista de categorias** (cartão é forma de pagamento). Lançamentos antigos com essa categoria aparecem como **"Outros"** no formulário e na lista de Lançamentos, **sem alterar o dado salvo** (`TxForm.displayCategory`).
- **Descrição** opcional com ícone de lápis e "Descrição (opcional)"; abaixo, chips com as descrições mais usadas do tipo (empate: a mais recente). Some sem histórico. A sugestão de categoria do assistente continua.
- **Salvar** fixo no rodapé: 56dp, cor do tipo, "Salvar despesa"/"Salvar receita", mais transparente e desabilitado com valor vazio ou zero. Ao salvar: vibração leve, a tela fecha e aparece "Despesa salva"/"Receita salva".
- **"Mais opções"** (recolhido) guarda o que já existia: ler fatura ou boleto, situação paga/pendente, parcelas e repetir mensalmente. Editar mostra "Excluir lançamento", como antes.

## Por quê

O formulário em folha misturava campos raros com os do dia a dia. A nova tela deixa à vista só valor, tipo, categoria, forma de pagamento e data, que é o que quase todo lançamento precisa.

## Decisões tomadas (podem ser revistas)

1. **Migração de "Cartão de Crédito"**: só na exibição (como pedido). O dado continua salvo como "Cartão de Crédito" e o **Resumo continua mostrando o nome salvo** (não mexi em totais nem agrupamentos de outras telas).
2. **Sem categoria escolhida**, salvar usa "Outros" (ou a primeira categoria do tipo, se "Outros" não existir). Antes ficava a primeira da lista.
3. **Pagamento de fatura e reserva para meta** continuam no formulário antigo em folha (não são "receita ou despesa" comum). A nova tela é usada para lançamentos novos e para editar receitas e despesas.
4. **Foco automático no valor** só em lançamento novo; ao editar, o teclado não abre sozinho.
5. **Texto sobre vermelho/verde (conflito na proposta)**: a proposta pede texto branco *e* contraste de 4,5:1. Nos temas Gruvbox o vermelho (`#FF7F7F` / `#FB4934`) e o verde (`#B8BB26`) são claros: branco fica entre ~2:1 e ~3,4:1. A tela usa branco quando passa de 4,5:1 e, senão, o escuro do tema (`onFill`). Na prática, nos dois Gruvbox o texto do seletor e do botão sai escuro. Se preferir branco mesmo assim, é uma linha.
6. **13º salário** usa o desenho `redeem` (mesmo visual de presente) que o app já tem, porque o `card_giftcard` não estava disponível no pacote de ícones usado; troco se preferir.
7. Ordem do seletor: **Despesa | Receita** (despesa é o caso mais comum).
8. Vibração: `HapticFeedbackType.TextHandleMove` (a mais leve disponível em todas as versões).
9. Os ícones novos (edit, sports_esports, trending_up) foram registrados em `THIRD_PARTY_NOTICES.md`, `third_party/material-symbols/README.md` e `TERCEIROS.txt`.

## Testes

- Unitários (`core/TxFormTest.kt`): formatação pt-BR (0, 12,5, 1234,56, digitação dígito a dígito, ponto final, limites), bloqueio de valor zero (inclusive `Ops.saveTx`), limpeza da categoria ao trocar o tipo, "Cartão de Crédito" → "Outros", busca, ordem das recentes e sugestões de descrição.
- Interface (`screenshots/LancamentoTelaTest.kt`, Robolectric): abre a gaveta, escolhe categoria, troca de tipo mudando a cor do botão, salva com snackbar, bloqueia zero, fonte do sistema 1,6× e capturas em Gruvbox e Gruvbox OLED com e sem vidro.
