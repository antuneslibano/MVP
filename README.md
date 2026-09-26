# Art das Baterias

Aplicativo Android simples, rápido e offline para gestão de uma loja de baterias automotivas:
vendas, estoque, faturamento, custo e lucro por dia, semana e mês.

## Como obter o APK (sem instalar nada no computador)

1. Abra o repositório no GitHub e vá na aba **Actions**.
2. Selecione o workflow **Build APK**.
3. Clique em **Run workflow** (ou apenas faça um push na branch `main`).
4. Aguarde a execução terminar (em torno de 5 a 8 minutos).
5. Abra a execução concluída e, em **Artifacts**, baixe **art-das-baterias**.
6. Descompacte o `.zip` baixado: dentro está o `art-das-baterias.apk`.
7. Envie o APK para o celular (WhatsApp, Drive, cabo USB…) e toque nele para instalar.
   O Android pode pedir para permitir "instalar apps desta fonte".

As novas versões podem ser instaladas por cima da anterior **sem perder os dados**,
pois todos os APKs são assinados com a mesma chave (`app/signing/loja-baterias.jks`).

> Requisitos: Android 8.0 ou superior.

## Funcionalidades

- **Início**: faturamento, lucro, quantidade de vendas e de baterias vendidas de hoje, da semana e do mês,
  além das vendas recentes e do botão **+ Nova Venda**.
- **Nova venda**: buscar modelo → escolher a bateria → forma de pagamento (preço automático)
  → conferir quantidade, preço e desconto → confirmar. Dá baixa automática no estoque.
  - PIX → preço PIX · Débito → preço débito · Crédito → preço crédito · Dinheiro → preço PIX
  - O preço pode ser alterado manualmente antes de confirmar.
- **Vendas**: histórico de hoje/semana/mês/todas, com filtro por data, modelo e forma de pagamento.
  Detalhes, edição e cancelamento (o cancelamento devolve o estoque e retira a venda dos relatórios).
- **Estoque**: busca instantânea por modelo (sem diferenciar maiúsculas/minúsculas), destaque de estoque
  baixo e zerado, cadastro/edição de baterias, entrada de estoque, ajuste e histórico de movimentações.
- **Relatórios**: diário, semanal e mensal (com navegação para períodos anteriores): faturamento, custo,
  lucro bruto, número de vendas, baterias vendidas, ticket médio, modelos mais vendidos, com maior
  faturamento e com maior lucro, e vendas por forma de pagamento.
  O botão **Baixar PDF** gera o relatório **completo** do período selecionado:
  vendas (resumo, descontos, rankings, formas de pagamento, lista de vendas e cancelamentos),
  estoque (entradas, ajustes, posição atual modelo a modelo com valores, alertas e movimentações)
  e sucatas (resumo, compras, vendas, estoque atual e movimentações).
- **Sucatas** (menu ☰ no rodapé):
  - Na venda, informe se o cliente **deixou a sucata** (e a amperagem dela) ou **não deixou**.
    Sem sucata, o app sugere cobrar o valor da tabela conforme a amperagem da bateria vendida
    (valor editável; entra no faturamento). Vendas com várias baterias aceitam sucata parcial.
  - Estoque de sucatas por amperagem, valor estimado, entrada manual, **compra de sucatas** (valor pago), venda de sucatas
    (ex.: para o reciclador, com o valor recebido), ajuste e histórico.
  - **Tabela de sucatas**: valor da sucata por amperagem.
  - Cancelar uma venda retira do estoque a sucata que veio com ela.
- **Senha de acesso**: pedida ao abrir o app e ao voltar depois de 2 minutos em segundo plano.
  "Esqueci a senha" mostra a pergunta secreta e, com a resposta certa, revela a senha.
  A senha não fica escrita no código (apenas um hash e uma versão cifrada com a resposta).
- **Excluir registros**: vendas (na tela da venda), movimentações de estoque (entrada, ajuste,
  estoque inicial) e de sucatas (entrada, compra, venda, ajuste). O estoque é corrigido junto.
- **Baterias na carga** (Menu ☰): recebimento com cliente, telefone (botões Ligar/WhatsApp), data,
  valor, se foi pago e se emprestou uma bateria usada da loja (qual foi fica na observação; não mexe no estoque).
- **Garantias e extras** (Menu ☰): duas abas com a contagem do mês por modelo (ex.: 4 BEP60D, 7 BE50D).
  *Garantias*: baterias trocadas; só conta modelo e quantidade, o estoque não muda.
  *Extras*: baterias que a loja ganhou; entram no estoque com custo zero e, na venda, saem com lucro de 100%
  (a venda usa primeiro as extras disponíveis do modelo; cancelar/excluir a venda devolve a extra).
- **Vales de casco** (Menu ☰): na venda sem sucata, com o casco cobrado, "Deixou vale? Sim" cria um vale em aberto (valor = casco). Quando o
  cliente traz o casco, "Pagar vale" registra o valor devolvido no dia e o casco entra no estoque de sucatas.
- **Despesas** (Menu ☰): contas fixas do mês (cadastradas uma vez; aparecem como "a pagar" e é só tocar
  em "Paguei") e despesas avulsas por categoria. Mostra entrou × saiu × sobrou (lucro líquido); os Relatórios
  e o PDF também trazem as despesas e o lucro líquido.
- **Casco cobrado**: entra no faturamento, mas também no custo (serve para repor o casco), então não vira lucro.
- **Atualização automática**: cada build vira uma Release no GitHub; o app avisa no Início e instala
  com um toque (Menu > Backup e sincronização > Atualizações).
- **Menu ☰**: acesso a todas as áreas (Início, Nova venda, Vendas, Estoque, Sucatas, Relatórios,
  Movimentações, Tabela de sucatas e Backup).
- **Backup**: exporta/importa todos os dados em um arquivo JSON (ícone de engrenagem na tela inicial).

### Regras de cálculo

| Indicador     | Fórmula                                                     |
|---------------|-------------------------------------------------------------|
| Valor final   | preço unitário × quantidade − desconto                      |
| Faturamento   | soma do valor final das vendas válidas (não canceladas)     |
| Custo         | soma do custo **histórico** dos itens vendidos              |
| Lucro bruto   | faturamento − custo                                         |
| Ticket médio  | faturamento ÷ quantidade de vendas                          |

O custo de cada item é gravado no momento da venda. Se o custo do produto mudar depois,
as vendas antigas continuam com o custo e o lucro originais.

A semana vai de segunda a domingo.

## Sincronização entre celulares (Supabase)

Vários celulares da mesma loja compartilham os dados por um banco na nuvem (Supabase, plano gratuito).
O app funciona **offline primeiro**: cada celular tem seu banco local e sincroniza a cada 30 s,
logo após cada alteração e ao abrir. Sem internet, as alterações ficam pendentes e são enviadas depois.

- Não há login: a senha do app libera o acesso a uma conta interna da loja na nuvem.
- O estoque é a soma das movimentações, então vendas simultâneas em celulares diferentes são somadas corretamente.
- Exclusões também são sincronizadas. Em edições simultâneas do mesmo registro, vale a mais recente.
- Um celular que já tem dados próprios não mistura com a nuvem: o app avisa e oferece baixar os dados da nuvem.

Configuração (uma vez): rode `supabase/schema.sql` no SQL Editor do Supabase, crie o usuário interno
da loja e desative novos cadastros. O workflow **Verificar Supabase** (Actions) confere a configuração.

## Arquitetura

- **Kotlin + Jetpack Compose (Material 3)**: app nativo, leve e rápido.
- **Room (SQLite)**: banco local; os dados ficam no aparelho e persistem após fechar o app.
- **Sem servidor**: funciona 100% offline.
- Valores monetários armazenados em **centavos (Long)** para evitar erros de arredondamento.
- R8 (minificação + remoção de recursos) habilitado no release.

```
app/src/main/java/br/com/lojabaterias/
├── domain/        Regras de negócio puras (cálculos, dinheiro, períodos) – com testes unitários
├── data/          Room: entidades, DAOs, banco, repositório (transações) e backup
└── ui/
    ├── components/  Componentes reutilizáveis (campo monetário, cartões, seletores…)
    ├── screens/     Telas (Início, Vendas, Nova venda, Estoque, Relatórios, Backup…)
    ├── viewmodel/   ViewModels (estado das telas)
    └── theme/       Cores e tipografia
```

### Banco de dados

- `products`: id, modelo (único), custo atual, preço PIX, débito, crédito, estoque, estoque mínimo
- `sales`: id, data/hora, forma de pagamento, valor bruto, desconto, valor final, custo total, lucro bruto, status
- `sale_items`: venda, produto, modelo no momento da venda, quantidade, preço unitário, custo unitário histórico, subtotal
- `stock_movements`: produto, data/hora, tipo (inicial, entrada, ajuste, venda, edição, cancelamento), quantidade, saldo
- `scrap_prices`: amperagem (única) e valor da sucata
- `scrap_movements`: data/hora, tipo (recebida na venda, entrada, venda, ajuste, edição, cancelamento), amperagem, quantidade, valor recebido

Atualizações do banco são feitas por **migrações** que preservam os dados (testadas automaticamente
no GitHub Actions). Os esquemas de cada versão ficam em `app/schemas/`.

## GitHub Actions

O workflow `.github/workflows/android.yml` roda em push na `main` (e em branches `claude/**`),
em pull requests e manualmente (`workflow_dispatch`). Ele:

1. faz checkout do código;
2. configura Java 17, Android SDK e Gradle;
3. executa os testes unitários;
4. compila o APK de release assinado;
5. publica `art-das-baterias.apk` como Artifact.

### Assinatura própria (opcional)

Por padrão o APK é assinado com a chave incluída no projeto. Para usar uma chave privada,
cadastre em *Settings → Secrets and variables → Actions*:

- `SIGNING_KEYSTORE_BASE64` (arquivo .jks em base64)
- `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`

Atenção: trocar a chave exige desinstalar a versão anterior do app (faça um backup antes).

## Compilar localmente (opcional)

```
./gradlew testDebugUnitTest assembleRelease
```

O APK fica em `app/build/outputs/apk/release/`.
