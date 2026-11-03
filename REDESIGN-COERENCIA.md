# Redesign premium e coerente

Aplicação da proposta "Finan+ premium e coerente" (PDF de 03/11/2026). O foco é a coerência entre telas: o mesmo número com o mesmo nome e a mesma conta, um ícone e uma cor por conceito e um padrão por interação.

## 1. Glossário único de valores (`core/Totals.kt`)

Todas as telas leem os mesmos números: Início, Lançamentos (lista e calendário), Resumo (saldo previsto) e o widget.

| Termo | O que é |
|---|---|
| Realizado | O que já entrou ou saiu das contas. Compras no cartão não entram (o que sai é o pagamento da fatura). Reservas para meta ficam à parte, como "Guardado em metas" |
| A receber | Receitas pendentes fora do cartão (no mês atual, também as atrasadas) e recorrências previstas |
| A pagar | Despesas pendentes fora do cartão (no mês atual, também as atrasadas), faturas abertas que vencem no mês e recorrências previstas |
| Reservado para metas | Aportes mensais planejados (campo "Contribuição mensal" da meta) que ainda não foram feitos no mês, limitados ao que falta para a meta |
| Saldo atual | Saldo das contas hoje |
| Previsto no fim do mês | Saldo atual + a receber − a pagar − reservado para metas |

### Correções de cálculo

1. **A meta já guardada não é descontada de novo.** O PDF calculava 166,88 + 3.413,62 − 609,91 − 100,00 = 2.870,59, mas os R$ 100,00 já tinham saído do saldo atual. O previsto certo é R$ 2.970,59. Só os aportes planejados que ainda faltam no mês são descontados (`TotalsTest.previstoNaoDescontaAMetaDuasVezes`).
2. **O previsto passa a descontar os aportes planejados das metas.** Antes eles não entravam em nenhuma conta. Quem não preenche "Contribuição mensal" não vê diferença.
3. **Atrasados entram no "A pagar" e no "A receber" do mês atual.** Antes o previsto já os descontava, mas o "Falta pagar" não os mostrava: a conta do botão "info" não fechava. Agora saldo atual + a receber − a pagar − metas = previsto, sempre.
4. **Lista e calendário mostram os mesmos totais.** Antes eram quatro valores diferentes para "o que sai" (R$ 625,71 e R$ 121,40 na lista, R$ 609,91 no Início, R$ 709,91 no calendário). As compras no cartão contam pela fatura e as reservas para meta não são despesa.
5. **Previsto ao fim de um dia** (calendário) usa a mesma regra: os aportes planejados de um mês contam no último dia dele.

## 2. Formato e textos

- Plural sempre correto (`core/Words.kt`): "1 conta", "3 cartões", "1 lançamento". Saiu todo "(s)", "(ões)" e "(ns)".
- Sinal de menos tipográfico em todos os valores: "− R$ 121,40" (`Money.format`; a leitura aceita "−" e "-").
- Algarismos de mesma largura (`tnum`) em todos os valores (`MoneyText`).
- Calendário: todo valor do dia com sinal ("−489", "+1,1 mil"); dia só com reserva para meta mostra "−100" no âmbar das metas.
- "13°" com símbolo de grau é mostrado como "13º" (só na exibição; o dado não muda).
- "você": a despesa compartilhada fala "você pagou", "Sua parte"; o participante padrão novo é "Você".
- "média do último mês" (em vez de "dos últimos 1 mês"), "E se…?" como nome único do simulador, "Cálculo do saldo" no lugar de "Lançamentos" em Ajustes.

## 3. Ícones e cores

- Ícones (Material Symbols): Meu dinheiro (aba) = carteira; Contas e cartões = banco; Dívidas = request_quote; E se…? = science; Aparência = palette; Notificações e widget = notifications; Cálculo do saldo = calculate; Acesso pela rede = wifi; Dados e backup = database; Categorias = sell; Limites = speed; alerta = warning (só estados). O "sparkles" fica exclusivo do Assistente.
- Cores: duas cores novas por tema, `goal` (âmbar, metas) e `card` (lilás, cartão de crédito), definidas para os 7 temas. A cor primária fica para botão principal, aba ativa e seleção. Vermelho e verde nunca são fundo de botão.
- 12 ícones novos registrados em `THIRD_PARTY_NOTICES.md`, `third_party/material-symbols/README.md` e `TERCEIROS.txt` (71 ícones em 75 arquivos).

## 4. Componentes

| Componente | Padrão |
|---|---|
| Card de navegação | Ícone, título, resumo e chevron; abre tela própria (Ajustes e Meu dinheiro) |
| Folha | Alça, título, respeita a barra de status (`statusBarsPadding`) e o teclado |
| Seletor | `SelectField` abre uma folha com check na opção escolhida e busca (mais de 7 opções); fim dos menus nativos estreitos |
| Voltar | `BackLink`: seta + nome da tela anterior, sem fundo |
| 2–3 opções | `SegmentedChoice` (situação paga/pendente, valor total/por parcela) |
| Liga/desliga | Switch (`CheckRow` virou switch) |
| Número pequeno | `Stepper` − 1 + (parcelas, meses do simulador) |
| Linha editável | Toque edita, deslizar arquiva com Desfazer (Categorias) |
| Ação destrutiva | `DangerText`: texto discreto em vermelho + confirmação |

## 5. Telas

- **Lançamentos · lista:** um número em destaque (Previsto no fim do mês, ou Resultado previsto do mês com o saldo isolado) e linhas rotuladas que somam até ele; frase informativa ("Já saíram R$ X este mês… As compras no cartão contam pela fatura."). A conta some da linha do lançamento quando só existe uma; o nome ocupa até duas linhas; meta em âmbar e cartão em lilás.
- **Lançamentos · calendário:** os mesmos totais da lista (A receber, A pagar + metas, Previsto); meta com cor própria.
- **Meu dinheiro:** resumo no topo (saldo nas contas, fatura aberta em lilás e guardado em metas em âmbar), alerta "A meta X vai passar do prazo" com "Ajustar contribuição" e cards que abrem telas próprias: Contas e cartões (o conteúdo da 1.10.0, com a fatura dentro de cada cartão), Dívidas (novo), Limites mensais, Metas, Despesas compartilhadas, E se…? e Categorias.
- **Dívidas:** montadas a partir dos lançamentos da categoria Dívidas (ou o antigo Empréstimo), agrupando as parcelas: próxima parcela, quanto falta e progresso (`core/Debts.kt`). Nenhum dado novo é gravado.
- **Ajustes:** título "Ajustes" com cadeado e "Tudo fica criptografado neste aparelho."; itens agrupados em listas, cada um abre a sua tela com "‹ Ajustes"; convite discreto para ativar a digital quando não há bloqueio.
- **Nova despesa / Nova receita:** "Ler boleto" no topo, segmentado Despesa/Receita, valor em destaque, categorias em chips (pré-seleção = última usada) com "Todas ›", data em chips (Hoje, Ontem, Escolher), descrição com sugestões, "Mais opções" recolhido com resumo ("Conta principal · já paga · à vista") com forma de pagamento, situação (segmentado), parcelas (stepper) e repetir (switch). Salvar fixo na cor primária; ao salvar, aviso com "Desfazer".
- **Seletor de categoria:** folha com busca, lista com ícone e check e "Gerenciar categorias" (abre Meu dinheiro › Categorias).
- **Categorias:** tela própria com Despesas · N / Receitas · N, arrastar pela alça para ordenar (também por ações de acessibilidade "Mover para cima/baixo"), deslizar para arquivar com Desfazer, toque para renomear, aviso de migração e "Nova categoria".
- **Despesa compartilhada:** "Falta receber" em âmbar (atenção, não erro), "Quem participa" com "Pagou tudo"/"Deve R$ X", "Registrar" e "Quitar", "Cobrar" com Imagem e PDF, "Editar despesa" e "Excluir" discretos no rodapé.
- **E se eu economizar…?:** meses com stepper, selo neutro, sobra negativa em vermelho, plano inviável vira alerta com o quanto reduzir, e "Transformar em meta" passa a ação secundária quando o plano não fecha.

## 6. Categorias × formas de pagamento

Migração automática, uma vez por aparelho (`TxForm.migrate`, `Repo.migrateCategories`):

- **Cartão de Crédito** e **Limite Especial** saem da lista de categorias de despesa. Os lançamentos antigos continuam com o nome salvo e aparecem como "Outros".
- **Empréstimo** vira **Dívidas** (lançamentos, recorrências e limites acompanham). Se "Dívidas" já existir, as duas se juntam.
- Instalações novas já vêm com "Dívidas" na lista padrão.
- Pagar a fatura nunca é despesa (já era assim): é dinheiro que sai da conta para o cartão.

## Decisões (podem ser revistas)

1. **Teclado:** o teclado numérico do sistema continua no lugar do teclado desenhado do PDF (acessibilidade e fonte grande).
2. **Caju como conta de benefício** e **limite especial como atributo da conta** pediriam campos novos no modelo e no backup (compatível com o Finan+ web e Linux). Não foram criados: "Limite Especial" saiu das categorias e "Caju" segue como categoria de receita até você decidir (dá para criar uma conta "Caju" e lançar a recarga nela).
3. **Arquivar categoria** usa a exclusão que já existia (tira da lista de escolha, mantém o histórico) com Desfazer, sem campo novo.
4. **Ícones das categorias** ficam todos na cor primária (o PDF colore cada categoria, mas isso daria a uma cor dois significados).
5. **Salvar** usa a cor primária (o PDF proíbe vermelho e verde como fundo de botão). O tipo continua na cor do valor.
6. A imagem e o PDF da despesa compartilhada (que vão para outras pessoas) continuam com o nome do participante como foi cadastrado.
7. As seções recolhíveis de Contas e Cartões da 1.10.0 viraram a tela "Contas e cartões" (o PDF pede que os cards naveguem em vez de expandir).

## Testes

- `TotalsTest` (glossário e correções de cálculo), `DebtsTest`, `CategoryMigrationTest`, ajustes em `CoreTest` (sinal de menos) e no Assistente (plural).
- Interface: `LancamentoTelaTest` (seletor de categoria, pré-seleção, Desfazer), `MeuDinheiroVisualTest` (resumo, alerta de meta, Contas e cartões, Dívidas, Categorias, Ocultar valores), e os testes de Início, Lançamentos, abas e licenças atualizados. Capturas em Gruvbox e Gruvbox OLED, com e sem vidro.
