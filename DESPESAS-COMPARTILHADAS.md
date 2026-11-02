# Despesas compartilhadas

Divida uma despesa com amigos, família ou colegas e acompanhe quem já devolveu a parte dele. Só no app Android.

## Onde fica

- **Meu dinheiro › Despesas compartilhadas**: o card mostra quantas estão em aberto e quanto falta receber. Tocar no card abre o histórico; **Nova** abre o formulário.
- **Botão + › Despesa compartilhada**: atalho para registrar uma nova.

Tudo abre como as outras folhas do app (mesmos campos, cartões e botões). O bloqueio por PIN/digital vale para o módulo como para o resto do app.

## Cadastro

- **Nome**, **valor total**, **data**, **observação** (opcional).
- **Participantes** pelo nome (de 2 a 50). A primeira linha vem como "Eu", e dá para trocar.
- **Quem pagou**: um dos participantes.
- **Nota fiscal ou comprovante** (opcional): foto ou PDF de até 15 MB.

### Formas de divisão

| Divisão | Como funciona |
|---|---|
| **Dividir igualmente** | O total é dividido em partes iguais entre todos, inclusive quem pagou. Os centavos que sobram ficam com a primeira pessoa (mesma regra das parcelas). R$ 450,00 para 3 pessoas = R$ 150,00 cada. |
| **Valor de cada um** | Cada pessoa com o que consumiu. A soma precisa ser igual ao total; o formulário mostra quanto falta ou passou. |

### Quem deve a quem

Quem pagou não deve nada: ele **recebe** de cada um dos outros a parte daquela pessoa.

Exemplo: mercado de R$ 450,00 pago pelo João, com João R$ 150,00, Maria R$ 120,00 e Pedro R$ 180,00. Maria deve R$ 120,00 ao João, e Pedro deve R$ 180,00 ao João. O João tem R$ 300,00 a receber.

## Acompanhamento

O detalhe da despesa mostra:

- o total, o **recebido** e o **pendente**, com uma barra de progresso;
- a situação de cada pessoa: **Pendente**, **Pagou parte**, **Quitado** ou **Pagou a despesa**;
- a parte de cada um, quanto já devolveu e quanto falta;
- os **pagamentos registrados**, com data. Excluir um pagamento faz o valor voltar a ficar pendente.

Para cada pessoa que deve, há **Registrar pagamento** (valor total ou parcial, com data) e **Quitar tudo** (registra o que falta, com a data de hoje). Não é possível registrar mais do que a pessoa deve.

A situação da despesa é **Pendente** quando nada foi devolvido, **Parcialmente paga** quando parte foi devolvida e **Quitada** quando ninguém deve mais nada.

### Edição

Dá para mudar tudo. Pagamentos registrados nunca ficam "soltos", então a edição é recusada quando:

- uma pessoa com pagamentos é removida;
- essa pessoa passa a ser quem pagou;
- a nova parte de alguém fica menor do que o que ele já devolveu.

Nesses casos, exclua os pagamentos antes.

## Histórico

A lista mostra as despesas mais recentes primeiro, com o total, quem pagou e quanto falta. No topo ficam o **a receber** e o **já recebido** somando todas as despesas.

Filtros disponíveis:

- busca por nome, observação ou participante (sem diferenciar acentos);
- situação: Todas, Pendentes, Parciais ou Quitadas;
- período (De/Até);
- participante.

## Compartilhar como imagem

No detalhe, em **Compartilhar › Compartilhar como imagem**, o Finan+ gera uma imagem PNG (1080 px de largura) e abre o "Compartilhar" do Android, para mandar no WhatsApp, Telegram, e-mail etc. Junto vai um resumo em texto de quem falta pagar.

A imagem segue a identidade do Finan+:

- no topo, o ícone e o nome do app, com "Despesa compartilhada";
- o fundo é azul-claro com brilhos suaves, como no app;
- um cartão branco traz o nome da despesa, a data, quem pagou e a divisão, o **total** em destaque, o recebido, o pendente e a situação, com uma barra do que já voltou;
- em "Quem deve e quanto", cada pessoa aparece com a inicial, a parte, quanto devolveu, a situação e o que falta. Quem pagou aparece com quanto recebe dos outros;
- no rodapé, "Feito com Finan+ · Finanças pessoais livres e privadas" e o endereço do site.

A imagem usa sempre as cores claras do app, em qualquer tema. A altura cresce com o número de pessoas. Ela é desenhada no aparelho, sem internet (`export/SharedImage.kt`), e mostra os valores mesmo com "Ocultar valores" ligado.

## Comprovante em PDF

No detalhe, em **Comprovante em PDF**:

- **Baixar PDF**: salva onde você escolher, como o relatório em PDF.
- **Enviar pelo WhatsApp**: abre o WhatsApp (ou o WhatsApp Business) com o PDF e um resumo em texto de quem falta pagar. Sem WhatsApp instalado, abre o "Compartilhar" do Android.

O PDF (A4, nas cores do relatório) traz:

- os dados da despesa, o valor total, a data, quem pagou e a forma de divisão;
- a tabela dos participantes, com a parte, quanto devolveu, quanto falta e a situação;
- quem deve e quanto, com o total a receber, recebido e pendente;
- os pagamentos registrados e a observação.

Como o relatório, o PDF mostra os valores mesmo com "Ocultar valores" ligado e não é criptografado.

## Dados e privacidade

- As despesas ficam no mesmo arquivo de dados do app (criptografado), dentro do campo `shared`. Entram no **Backup JSON** e no **backup automático**.
- O **comprovante anexado** fica só neste aparelho, criptografado com AES-256-GCM e uma chave própria no Android Keystore (`files/shared-receipts`). O backup guarda só o nome do arquivo. Restaurado em outro celular, a despesa aparece com o aviso de que o comprovante não está no aparelho; é só anexar de novo.
- Para abrir o comprovante ou enviar o PDF, o app grava uma cópia temporária em `cache/shared`. Essa pasta é esvaziada sempre que a tela de despesas compartilhadas abre. Só ela é liberada a outros apps, pelo `FileProvider` (não exportado, permissão só de leitura e só para o arquivo enviado).
- Comprovantes que nenhuma despesa usa mais são apagados (excluir a despesa, trocar ou remover o anexo). **Apagar todos os dados** também apaga os comprovantes e a chave deles.
- As despesas compartilhadas **não mexem no saldo** das contas, nos lançamentos, nos limites nem nos relatórios. São um controle de quem deve a quem.

## Formato no backup

```json
"shared": [{
  "id": "…", "name": "Mercado", "total": 450, "date": "2026-11-02",
  "payer": "<id da pessoa>", "split": "equal" | "custom", "note": "",
  "people": [{ "id": "…", "name": "João", "share": 150 }],
  "payments": [{ "id": "…", "person": "<id da pessoa>", "value": 50, "date": "2026-11-05" }],
  "receipt": "<arquivo>", "receiptType": "image/jpeg"
}]
```

- Os valores estão em reais, como no resto do backup.
- O campo só aparece quando há despesas, então um backup sem elas é idêntico ao do Finan+ web.
- Ao restaurar, uma despesa é descartada (e contada nos itens ignorados) quando falta nome, total, data ou quem pagou, quando tem menos de 2 pessoas ou quando as partes não somam o total.
- Pagamentos de quem não deve, ou acima da parte da pessoa, são ignorados.
- O limite é de 1.000 despesas, 50 pessoas e 500 pagamentos por despesa.

### Acesso pela rede

O Finan+ web não conhece as despesas compartilhadas. Quando ele grava pelo **Acesso pela rede** sem o campo `shared`, o celular mantém as despesas que já tinha (`RemoteSave.keepShared`), e nada se perde.

## Código e testes

| Arquivo | O quê |
|---|---|
| `core/Shared.kt` | Modelo, divisão, resumo, validações, pagamentos, filtros do histórico (sem Android) |
| `core/Backup.kt` | Leitura e gravação do campo `shared` |
| `core/RemoteSave.kt` | `keepShared` (gravação pelo Acesso pela rede) |
| `data/SharedReceipts.kt` | Comprovantes cifrados, cópias temporárias, limpeza |
| `export/SharedPdf.kt` | Comprovante em PDF |
| `export/SharedImage.kt` | Imagem para compartilhar, com a marca do Finan+ |
| `ui/screens/SharedExpenses.kt` | Histórico, formulário, detalhe e pagamento |
| `SharedTest` | Divisão igual e personalizada, centavos, pagamentos parciais e quitação, edição com pagamentos, validações, histórico, backup ida e volta, gravação pela rede e nome do PDF |
