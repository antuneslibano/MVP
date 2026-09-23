# Loja de Baterias

Aplicativo Android simples, rápido e offline para gestão de uma loja de baterias automotivas:
vendas, estoque, faturamento, custo e lucro por dia, semana e mês.

## Como obter o APK (sem instalar nada no computador)

1. Abra o repositório no GitHub e vá na aba **Actions**.
2. Selecione o workflow **Build APK**.
3. Clique em **Run workflow** (ou apenas faça um push na branch `main`).
4. Aguarde a execução terminar (em torno de 5 a 8 minutos).
5. Abra a execução concluída e, em **Artifacts**, baixe **loja-baterias**.
6. Descompacte o `.zip` baixado: dentro está o `loja-baterias.apk`.
7. Envie o APK para o celular (WhatsApp, Drive, cabo USB…) e toque nele para instalar.
   O Android pode pedir para permitir "instalar apps desta fonte".

As novas versões podem ser instaladas por cima da anterior **sem perder os dados**,
pois todos os APKs são assinados com a mesma chave (`app/signing/loja-baterias.jks`).

> Requisitos: Android 8.0 ou superior.

## Funcionalidades

- **Início**: faturamento, lucro e quantidade de vendas de hoje, da semana e do mês,
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
  O botão **Baixar PDF** gera o relatório do período selecionado (resumo, rankings,
  formas de pagamento e lista de vendas) e salva onde você escolher.
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

## GitHub Actions

O workflow `.github/workflows/android.yml` roda em push na `main` (e em branches `claude/**`),
em pull requests e manualmente (`workflow_dispatch`). Ele:

1. faz checkout do código;
2. configura Java 17, Android SDK e Gradle;
3. executa os testes unitários;
4. compila o APK de release assinado;
5. publica `loja-baterias.apk` como Artifact.

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
