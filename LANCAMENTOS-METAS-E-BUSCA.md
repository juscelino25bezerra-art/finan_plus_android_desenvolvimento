# Lançamentos: reservas para meta como "guardado" e busca recolhível

Duas mudanças na aba **Lançamentos**. São só de apresentação: nenhum cálculo de saldo, saldo previsto, "falta pagar", Receitas/Despesas ou Resumo mudou.

## 1. Reservas para meta aparecem como "guardado"

### Por quê

A reserva para meta (o lançamento "Meta › nome", ver [METAS.md](METAS.md)) tira o dinheiro da conta, mas não é um gasto: o dinheiro continua do usuário, separado na meta. Na lista, ela aparecia como "− R$ 100,00" em vermelho e entrava no total do dia, como se fosse uma despesa.

Exemplo de 2/11: uma compra "Cartão de Crédito − R$ 126,00" (no cartão) e uma reserva "Meta › Notebook R$ 100,00". O cabeçalho do dia mostrava **"− R$ 100,00" em vermelho**.

### O que mudou

| Onde | Antes | Agora |
|---|---|---|
| Item da reserva | "− R$ 100,00" | "R$ 100,00", sem sinal, na cor primária do tema; continua com a bandeira e "Guardado na meta" |
| Item de retirada da meta | "+ R$ 25,00" na cor primária | "+ R$ 25,00" em verde (o dinheiro voltou para a conta) |
| Total do dia (lista) | Somava receitas, despesas e reservas | Soma só receitas e despesas. Se o dia tem reservas, uma segunda linha alinhada à direita: bandeira + "R$ X em metas", na cor primária. Dia só com reservas: só essa linha, sem "R$ 0,00" |
| Calendário: número do dia | Saldo do dia com as reservas | O mesmo total da lista; dia só com reserva mostra o valor guardado, na cor primária |
| Calendário: dia escolhido | "resultado das movimentações" com as reservas | Sem as reservas, e uma linha "R$ X em metas" logo abaixo |
| Leitor de tela (calendário) | "saldo do dia menos R$ 100,00" | "R$ 100,00 guardados em metas" |

Compras no cartão continuam fora do total do dia, como já era (contam na fatura). Os totais do mês no calendário (Entradas, Saídas, Resultado) não mudaram.

No caso de 2/11, o cabeçalho agora mostra só **"R$ 100,00 em metas"**, na cor primária.

### Regra de cor (vale para todo o app)

- **Verde:** entrou na conta.
- **Vermelho:** saiu ou vai sair.
- **Cor primária do tema:** continua do usuário, separado numa meta.

A cor primária muda com o tema (azul no Claro, `#83A598` no Gruvbox e no Gruvbox OLED) e funciona com e sem o efeito vidro. Nenhuma cor fixa no código.

### Código

- `core/Period.kt`: `DayTotal` e `Period.dayTotal(txs)`, com `net` (receitas − despesas, sem cartão e sem reservas), `goals` (guardado − retirado) e `hasFlow` (o dia tem receita ou despesa).
- `core/MonthCalendar.kt`: `CalDay.goals`, `CalDay.hasFlow` e `CalDay.flowNet`. `income` e `expense` continuam iguais (alimentam os totais do mês).
- `ui/Root.kt` (`TxRow`), `ui/screens/MovesScreen.kt` (`DayGroupHeader`), `ui/screens/CalendarView.kt`.
- Textos em `res/values/strings.xml`: `day_goals_saved` ("%1$s em metas") e `day_goals_withdrawn`.

## 2. Busca recolhível

### Por quê

O campo de busca fixo ocupava uma linha inteira abaixo do mês, e a maioria das vezes ninguém busca nada. Recolhido, a lista começa mais perto do topo.

### Como funciona

- O campo fixo saiu. No cabeçalho, ao lado do seletor Lista/Calendário, há um botão de lupa (48 dp, "Buscar descrição ou categoria" para o leitor de tela). Ele aparece só na visão Lista, porque o calendário mostra o mês inteiro.
- **Tocar na lupa** abre o campo logo abaixo do cabeçalho, com uma animação curta, já com foco e teclado. Texto de exemplo: "Buscar descrição ou categoria".
- A busca filtra **na hora**, no mês mostrado, pela descrição e pela categoria (sem diferenciar acentos), junto com os chips Todos/Receitas/Despesas/Pendentes, como antes.
- **"X" no campo:** limpa o texto e recolhe a busca.
- **Voltar do sistema** com a busca aberta: só recolhe o campo, sem sair da tela.
- **Busca recolhida com texto:** a lupa fica destacada (fundo na cor primária), para lembrar que a lista está filtrada. Trocar de mês mantém a busca.
- **Sem resultados:** "Nenhum lançamento encontrado", com a dica de tentar outra palavra.

### Código

- `ui/screens/MovesScreen.kt`: `SearchToggle` (botão) e `SearchField` (campo com foco, teclado e "X"), mais o `BackHandler`. O texto continua em `Filters.query`.
- Ícone novo `ms_close` (Material Symbols "close").
- Textos em `strings.xml`: `moves_search_open`, `moves_search_placeholder`, `moves_search_clear`, `moves_search_empty` e `moves_search_empty_hint`.

## Testes

- `DayTotalTest`: só receitas e despesas; só reservas; misto (reserva fora do total, retirada descontada das metas); compra no cartão + reserva (o caso de 2/11, na lista e no calendário); e saldo atual igual ao de antes.
- `LancamentosVisualTest` (capturas, com `-PfinanScreenshots=true`): desenha a lista nos temas Gruvbox e Gruvbox OLED, com e sem vidro, e confere "R$ 100,00 em metas" e que a reserva não aparece como "− R$ 100,00". Também testa a busca: fechada por padrão, abre pela lupa, filtra, mostra "Nenhum lançamento encontrado" e o "X" limpa e recolhe. As imagens ficam em `docs/screenshots/lancamentos-*.png`.
