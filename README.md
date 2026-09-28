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

O app é dividido em **áreas**. Cada área tem um botão na barra de baixo e **abas no topo** que ligam as telas
relacionadas. O **Menu ☰** mostra tudo agrupado pelas mesmas áreas, com atalhos (Nova venda, Nova nota fiscal).

| Barra de baixo | Abas no topo |
|---|---|
| **Início** | resumo de hoje, da semana e do mês, avisos (boletos vencendo, baterias na carga), vendas recentes e **+ Nova Venda** |
| **Vendas** | Vendas · Na carga · Vales de casco |
| **Estoque** | Baterias · Sucatas · Garantias e extras |
| **Dinheiro** | Resumo · Notas e boletos · Despesas · Relatórios e PDF |

### Vendas
- **Nova venda**: modelo → bateria → forma de pagamento (preço automático: PIX/dinheiro → preço PIX, débito, crédito)
  → quantidade, preço e desconto → confirmar. Dá baixa no estoque.
  - **Sucata**: "Deixou sucata" (entra no estoque de sucatas) ou "Sem sucata" (cobra o casco pela tabela; entra no
    faturamento e também no custo, então não vira lucro). **"Levou vale?"** cria um vale de casco.
  - **Pagamento dividido**: várias formas na mesma venda; o app sugere quanto falta. A taxa da maquininha incide só no cartão.
- **Vendas**: histórico com filtros; detalhes, edição, cancelamento (devolve o estoque) e exclusão.
- **Na carga**: bateria do cliente para carregar (cliente, telefone com Ligar/WhatsApp, valor, pago ou não, empréstimo).
- **Vales de casco**: vales em aberto; "Pagar vale" quando o cliente traz o casco (o casco entra no estoque de sucatas).

### Estoque
- **Baterias**: busca, estoque baixo/zerado, cadastro, entrada, ajuste e histórico (Movimentações no menu).
  - **Custo por lotes** (primeiro que entra, primeiro que sai): cada entrada com custo (estoque inicial, entrada
    manual, chegada de nota fiscal) vira um lote. A venda usa primeiro as baterias mais antigas, com o custo delas.
    Ex.: 5 M100QD a R$ 672 + 5 a R$ 650 → as 5 primeiras vendas custam R$ 672. A tela da bateria mostra os lotes,
    e o valor do estoque (Financeiro, Relatórios e PDF) é a soma dos lotes.
- **Sucatas**: estoque por amperagem, entrada, compra, venda (ao reciclador), ajuste e a **Tabela de sucatas**.
- **Garantias e extras**: *Garantias* conta as trocas por modelo (não mexe no estoque); *Extras* são baterias
  ganhadas: entram com custo zero e saem com lucro de 100% (a venda usa primeiro as extras do modelo).

### Dinheiro
- **Resumo** (mês, ano ou tudo), em três partes:
  - **Lucro**: quanto sobrou, para onde foi cada R$ 100 (custo, despesas, taxas, lucro), gráfico dia a dia / mês a mês,
    a conta completa até o lucro líquido, formas de pagamento, despesas por categoria e modelos que mais dão lucro.
  - **Caixa**: o dinheiro de verdade. **Caixa agora** (saldo informado + o que entrou − o que saiu), quanto **pode
    retirar com segurança** (descontando boletos dos próximos 30 dias e contas fixas do mês), entradas e saídas do
    período, **retiradas dos sócios** e o que a loja tem (estoque, sucatas, a receber, dívidas com fornecedores).
  - **Extras**: quanto as baterias extras renderam (valor de venda e lucro), no período e desde o começo.
  - Botão **"? Entenda"**: glossário dos termos.
- **Notas e boletos**: nota do fornecedor (distribuidora; cada modelo com quantidade, subtotal sem desconto e desconto da linha — o custo de cada bateria é (subtotal − desconto) ÷ quantidade; frete/impostos) e seus
  boletos (parcelas iguais a cada 7–30 dias, editáveis; a soma tem que bater com o total). "As baterias chegaram"
  conferindo quantas chegaram de cada modelo: as que chegaram **entram no estoque automaticamente** (com o custo da
  nota; dá para desligar). "Ainda não chegaram" desfaz a entrada. Nota antiga (baterias que já estavam no estoque):
  marque ao lançar e ela não mexe no estoque. Aba de boletos a pagar/pagos com "Paguei".
  Boletos pagos **não saem do lucro** (o custo já sai na venda), mas saem do **caixa**.
- **Despesas**: contas fixas do mês ("Paguei") e despesas avulsas por categoria.
- **Relatórios e PDF**: diário, semanal e mensal, com o **PDF completo** do período.

### Geral
- **Senha** ao abrir e após 2 minutos fora; bloqueio após tentativas erradas; "Esqueci a senha" com pergunta secreta.
  Opção **"Entrar sem apertar OK"** (fica salva no celular).
- **Puxar a tela para baixo** sincroniza com a nuvem.
- **Atualização automática** (Menu ☰ > Backup, sincronização e atualizações) e **backup** em arquivo JSON.

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
