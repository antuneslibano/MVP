package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.CashPosition
import br.com.lojabaterias.data.Expense
import br.com.lojabaterias.data.FinancePeriod
import br.com.lojabaterias.data.FinanceReport
import br.com.lojabaterias.data.FinanceSummary
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.BarGroup
import br.com.lojabaterias.ui.components.ChartColors
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HBarList
import br.com.lojabaterias.ui.components.HBarRow
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.HeroStat
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.LegendDot
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PairedBarChart
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.PeriodSwitcher
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.Segmented
import br.com.lojabaterias.ui.components.StackedShareBar
import br.com.lojabaterias.ui.components.StatTile
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.FinanceViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate

/** As três partes do Financeiro. */
private enum class FinanceSection(val label: String) { PROFIT("Lucro"), CASH("Caixa"), EXTRAS("Extras") }

@Composable
fun FinanceScreen() {
    val vm = appViewModel { FinanceViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.report
    ToastEffect(vm.messages)
    var askWithdrawal by remember { mutableStateOf(false) }
    var askOpening by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var section by rememberSaveable { mutableStateOf(FinanceSection.PROFIT) }
    var showHelp by remember { mutableStateOf(false) }
    val c = f.current

    MainPage { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Segmented(
                    options = FinancePeriod.entries.map { it.label },
                    selected = state.selection.period.ordinal,
                    onSelect = { vm.setPeriod(FinancePeriod.entries[it]) },
                )
            }
            item {
                val canMove = state.selection.period != FinancePeriod.ALL
                PeriodSwitcher(
                    label = f.label,
                    onPrevious = vm::previous,
                    onNext = vm::next,
                    canPrevious = canMove,
                    canNext = canMove && state.selection.offset < 0,
                    trailing = {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "Entenda os termos", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
            item {
                Segmented(
                    options = FinanceSection.entries.map { it.label },
                    selected = section.ordinal,
                    onSelect = { section = FinanceSection.entries[it] },
                )
            }
            when (section) {
                FinanceSection.PROFIT -> {
                    item { ProfitHero(f) }
                    item {
                        EqualRow {
                            StatTile("Vendas", c.salesCount.toString(), Modifier.weight(1f))
                            StatTile("Baterias", c.units.toString(), Modifier.weight(1f))
                            StatTile("Ticket médio", Money.format(c.averageTicket), Modifier.weight(1.3f))
                        }
                    }
                    item { WhereMoneyWentCard(c) }

                    item { SectionHeader(evolutionTitle(f.period)) }
                    item { EvolutionCard(f) }

                    item { SectionHeader("Conta completa") }
                    item { StatementCard(c) }

                    item { SectionHeader("Formas de pagamento") }
                    item {
                        ChartCard {
                            if (c.byPayment.isEmpty()) Empty("Sem vendas no período.")
                            HBarList(
                                rows = c.byPayment.map {
                                    HBarRow(
                                        label = it.method.label,
                                        value = it.revenue,
                                        valueText = Money.format(it.revenue),
                                        detail = "${it.salesCount} ${if (it.salesCount == 1) "venda" else "vendas"}" +
                                            if (c.revenue > 0) " · ${it.revenue * 100 / c.revenue}%" else "",
                                    )
                                },
                                color = ChartColors.slot(0),
                            )
                        }
                    }

                    if (c.expensesByCategory.isNotEmpty()) {
                        item { SectionHeader("Despesas por categoria") }
                        item {
                            ChartCard {
                                HBarList(
                                    rows = c.expensesByCategory.map { (cat, total) ->
                                        HBarRow(
                                            label = cat,
                                            value = total,
                                            valueText = Money.format(total),
                                            detail = if (c.expenses > 0) "${total * 100 / c.expenses}%" else null,
                                        )
                                    },
                                    color = ChartColors.slot(1),
                                )
                            }
                        }
                    }

                    item { SectionHeader("Modelos que mais lucraram") }
                    item {
                        ChartCard {
                            if (c.topModels.isEmpty()) Empty("Sem vendas no período.")
                            HBarList(
                                rows = c.topModels.map {
                                    HBarRow(
                                        label = it.model,
                                        value = it.profit,
                                        valueText = Money.format(it.profit),
                                        detail = "${it.quantity} vendida${if (it.quantity == 1) "" else "s"} · ${Money.format(it.revenue)}",
                                    )
                                },
                                color = ChartColors.slot(2),
                            )
                        }
                    }
                }
                FinanceSection.CASH -> {
                    item { CashNowCard(f.cash, onSetOpening = { askOpening = true }) }
                    item { SectionHeader("Entrou e saiu") }
                    item { CashFlowCard(c) }

                    item { SectionHeader("Retiradas dos sócios") }
                    item { WithdrawalsCard(c, onAdd = { askWithdrawal = true }, onDelete = { deleting = it }) }

                    item { SectionHeader("A loja tem hoje") }
                    item {
                        ChartCard {
                            InfoRow("Estoque pelo custo", Money.format(f.stockAtCost))
                            InfoRow("Estoque no preço PIX", Money.format(f.stockAtPix))
                            if (f.scrapStockValue > 0) InfoRow("Sucatas", Money.format(f.scrapStockValue))
                            if (f.toReceive > 0) InfoRow("A receber", Money.format(f.toReceive))
                            InfoRow("Devemos (boletos)", Money.format(f.supplierDebt.open))
                            if (f.supplierDebt.overdue > 0) {
                                InfoRow("Boletos vencidos", Money.format(f.supplierDebt.overdue), valueColor = dangerColor())
                            }
                        }
                    }
                }
                FinanceSection.EXTRAS -> {
                    item { ExtrasCards(f) }
                }
            }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Entenda os termos") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { GlossaryContent() } },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendi") } },
        )
    }
    if (askWithdrawal) {
        WithdrawalDialog(
            partners = f.partners,
            onConfirm = { name, amount, date ->
                vm.addWithdrawal(name, amount, date)
                askWithdrawal = false
            },
            onDismiss = { askWithdrawal = false },
        )
    }
    if (askOpening) {
        OpeningDialog(
            current = f.cash,
            onConfirm = { amount, date ->
                vm.setOpeningBalance(amount, date)
                askOpening = false
            },
            onDismiss = { askOpening = false },
        )
    }
    deleting?.let { w ->
        ConfirmDialog(
            title = "Excluir retirada?",
            text = "${w.category} · ${Money.format(w.amount)} · ${Periods.formatDate(w.date)}",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = {
                vm.deleteWithdrawal(w.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

private fun evolutionTitle(p: FinancePeriod) = when (p) {
    FinancePeriod.MONTH -> "Dia a dia"
    FinancePeriod.YEAR -> "Mês a mês"
    FinancePeriod.ALL -> "Últimos 12 meses"
}

@Composable
private fun ProfitHero(f: FinanceReport) {
    val c = f.current
    HeroCard(label = if (c.netProfit >= 0) "Lucro líquido" else "Prejuízo", value = Money.format(c.netProfit)) {
        Row(Modifier.padding(top = 12.dp)) {
            HeroStat("Entrou", Money.format(c.revenue), Modifier.weight(1f))
            HeroStat("Saiu", Money.format(c.totalOut), Modifier.weight(1f))
            c.marginPercent?.let { HeroStat("Margem", "$it%", Modifier.weight(0.7f)) }
        }
        f.previous?.let { prev ->
            comparison(c, prev, f.previousLabel)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** "▲ 12% vs agosto" (ou menor). */
private fun comparison(c: FinanceSummary, prev: FinanceSummary, prevLabel: String): String? {
    if (prev.revenue == 0L && prev.expenses == 0L) return null
    val base = kotlin.math.abs(prev.netProfit)
    if (base == 0L) return null
    val pct = (c.netProfit - prev.netProfit) * 100 / base
    return when {
        pct > 0 -> "▲ $pct% vs $prevLabel"
        pct < 0 -> "▼ ${-pct}% vs $prevLabel"
        else -> "Igual a $prevLabel"
    }
}

@Composable
private fun WhereMoneyWentCard(c: FinanceSummary) {
    if (c.revenue <= 0) return
    ChartCard {
        Text("Para onde foi cada R$ 100", style = MaterialTheme.typography.titleSmall)
        val parts = listOf(
            Triple("Lucro", c.netProfit.coerceAtLeast(0), ChartColors.slot(0)),
            Triple("Custo", c.batteryCost + c.cascoCost, ChartColors.slot(1)),
            Triple("Despesas", c.expenses, ChartColors.slot(2)),
            Triple("Taxas", c.fees, ChartColors.slot(3)),
        )
        val total = parts.sumOf { it.second }.coerceAtLeast(1)
        StackedShareBar(
            parts = parts.map { it.second to it.third },
            modifier = Modifier.padding(vertical = 12.dp),
            description = parts.joinToString { "${it.first}: ${Money.format(it.second)}" },
        )
        parts.forEach { (label, value, color) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color, label, Modifier.weight(1f))
                Text(
                    "R$ ${value * 100 / total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(Money.format(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EvolutionCard(f: FinanceReport) {
    val buckets = f.buckets
    val profitLabel = if (f.period == FinancePeriod.MONTH) "Lucro das vendas" else "Lucro líquido"
    val defaultIndex = buckets.indexOfLast { it.revenue != 0L || it.profit != 0L }.takeIf { it >= 0 }
    var selected by remember(f.period, f.label) { mutableStateOf(defaultIndex) }
    val colorA = ChartColors.slot(0)
    val colorB = ChartColors.slot(2)
    ChartCard {
        val sel = selected?.let { buckets.getOrNull(it) }
        if (sel != null) {
            Text(sel.fullLabel, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(colorA, Money.format(sel.revenue))
                LegendDot(colorB, Money.format(sel.profit))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(colorA, "Faturamento")
                LegendDot(colorB, profitLabel)
            }
        }
        PairedBarChart(
            groups = buckets.map { BarGroup(it.label, it.revenue, it.profit) },
            colorA = colorA,
            colorB = colorB,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(top = 12.dp),
            description = "Gráfico de faturamento e lucro. " + buckets.filter { it.revenue != 0L }
                .joinToString { "${it.fullLabel}: ${Money.format(it.revenue)}" },
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colorA, "Faturamento")
            LegendDot(colorB, profitLabel)
        }
    }
}

@Composable
private fun StatementCard(c: FinanceSummary) {
    ChartCard {
        InfoRow("Vendas", Money.format(c.revenue))
        InfoRow("Custo das baterias", minus(c.batteryCost))
        if (c.cascoCost > 0) InfoRow("Casco cobrado", minus(c.cascoCost))
        InfoRow("Taxas das maquininhas", minus(c.fees))
        Divider()
        InfoRow("Lucro das vendas", Money.format(c.grossProfit), bold = true, valueColor = moneyResultColor(c.grossProfit))
        InfoRow("Despesas", minus(c.expenses))
        Divider()
        InfoRow("Lucro líquido", Money.format(c.netProfit), bold = true, valueColor = moneyResultColor(c.netProfit))

        if (c.chargesPaid > 0 || c.scrapSold > 0 || c.scrapPurchased > 0) {
            Divider()
            if (c.chargesPaid > 0) InfoRow("Carga de baterias", Money.format(c.chargesPaid))
            if (c.scrapSold > 0) InfoRow("Sucatas vendidas", Money.format(c.scrapSold))
            if (c.scrapPurchased > 0) InfoRow("Sucatas compradas", minus(c.scrapPurchased))
            InfoRow("Resultado geral", Money.format(c.generalResult), bold = true, valueColor = moneyResultColor(c.generalResult))
        }
        if (c.discounts > 0) Hint("Descontos dados: ${Money.format(c.discounts)}", Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

private fun minus(value: Long) = if (value == 0L) Money.format(0) else "-" + Money.format(value)

@Composable
private fun ExtrasCards(f: FinanceReport) {
    val all = f.extrasAllTime
    val p = f.extrasPeriod
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HeroCard(label = "Lucro das extras (desde o começo)", value = Money.format(all.profit)) {
            Row(Modifier.padding(top = 12.dp)) {
                HeroStat("Vendidas", all.sold.toString(), Modifier.weight(1f))
                HeroStat("No estoque", f.extrasInStock.toString(), Modifier.weight(1f))
                HeroStat("Valem (PIX)", Money.format(f.extrasInStockValue), Modifier.weight(1.3f))
            }
        }
        if (f.period != FinancePeriod.ALL) {
            SectionHeader(f.label.replaceFirstChar { it.uppercase() })
            ChartCard {
                InfoRow("Ganhadas", p.received.toString())
                InfoRow("Vendidas", p.sold.toString())
                InfoRow("Valor de venda", Money.format(p.saleValue))
                InfoRow("Lucro", Money.format(p.profit), bold = true, valueColor = moneyResultColor(p.profit))
            }
        }
        if (all.byModel.isNotEmpty()) {
            SectionHeader("Por modelo")
            ChartCard {
                HBarList(
                    rows = all.byModel.map {
                        HBarRow(it.model, it.profit, Money.format(it.profit), "${it.sold} vendida${if (it.sold == 1) "" else "s"}")
                    },
                    color = ChartColors.slot(0),
                )
            }
        }
    }
}

@Composable
private fun GlossaryContent() {
    Column {
        listOf(
            "Entrou" to "Tudo o que os clientes pagaram nas vendas, já com desconto.",
            "Custo" to "Quanto a loja pagou pelas baterias vendidas.",
            "Casco cobrado" to "Entra no faturamento e também como custo: serve para repor o casco.",
            "Taxas" to "O que a maquininha desconta no débito e no crédito.",
            "Lucro das vendas" to "Entrou − custo − casco − taxas.",
            "Lucro líquido" to "Lucro das vendas − despesas.",
            "Caixa" to "O dinheiro de verdade (gaveta + banco). O custo das baterias só sai quando o boleto é pago.",
            "Retirada" to "Dinheiro que um sócio tira. Não muda o lucro, mas sai do caixa.",
            "Pode retirar" to "Caixa menos os boletos vencidos e os dos próximos 30 dias.",
        ).forEachIndexed { i, (term, meaning) ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(term, style = MaterialTheme.typography.titleSmall)
            Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(content: @Composable ColumnScope.() -> Unit) = PanelCard(content = content)

@Composable
private fun Empty(text: String) = Hint(text, Modifier.padding(vertical = 8.dp))

// ------------------------------------------------------------------ Caixa

@Composable
private fun CashFlowCard(c: FinanceSummary) {
    ChartCard {
        InfoRow("Vendas (sem as taxas)", Money.format(c.salesCashIn))
        if (c.chargesPaid > 0) InfoRow("Carga de baterias", Money.format(c.chargesPaid))
        if (c.scrapSold > 0) InfoRow("Sucatas vendidas", Money.format(c.scrapSold))
        InfoRow("Entrou", Money.format(c.cashIn), bold = true)
        Divider()
        InfoRow("Boletos pagos", minus(c.supplierPaid))
        InfoRow("Despesas", minus(c.expenses))
        if (c.scrapPurchased > 0) InfoRow("Sucatas compradas", minus(c.scrapPurchased))
        if (c.vouchersPaid > 0) InfoRow("Vales de casco", minus(c.vouchersPaid))
        InfoRow("Saiu", minus(c.cashOut), bold = true)
        Divider()
        InfoRow("Sobrou", Money.format(c.cashBeforeWithdrawals), valueColor = moneyResultColor(c.cashBeforeWithdrawals))
        InfoRow("Retiradas", minus(c.withdrawals))
        InfoRow("Ficou na loja", Money.format(c.cashResult), bold = true, valueColor = moneyResultColor(c.cashResult))
    }
}

@Composable
private fun CashNowCard(cash: CashPosition, onSetOpening: () -> Unit) {
    val safe = cash.safeToWithdraw
    HeroCard(label = "Caixa agora", value = Money.format(cash.now)) {
        val on = MaterialTheme.colorScheme.onPrimary
        if (!cash.hasOpening) {
            Text("Saldo inicial não informado", style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f))
        }
        Row(Modifier.padding(top = 12.dp)) {
            HeroStat("Boletos 30 dias", "-" + Money.format(cash.upcomingBills), Modifier.weight(1f))
            HeroStat(if (safe >= 0) "Pode retirar" else "Falta", Money.format(kotlin.math.abs(safe)), Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = onSetOpening,
            shape = MaterialTheme.shapes.medium,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = on),
            border = androidx.compose.foundation.BorderStroke(1.dp, on.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text(if (cash.hasOpening) "Corrigir saldo" else "Informar saldo")
        }
    }
}

@Composable
private fun WithdrawalsCard(c: FinanceSummary, onAdd: () -> Unit, onDelete: (Expense) -> Unit) {
    ChartCard {
        if (c.withdrawalList.isEmpty()) Empty("Nenhuma retirada no período.")
        c.withdrawalList.forEach { w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.category, style = MaterialTheme.typography.titleSmall)
                    Hint(Periods.formatDate(w.date))
                }
                Text(Money.format(w.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onDelete(w) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir retirada", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (c.withdrawalList.isNotEmpty()) {
            Divider()
            val byPartner = c.withdrawalList.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
            if (byPartner.size > 1) byPartner.forEach { (name, total) -> InfoRow(name, Money.format(total)) }
            InfoRow("Total", Money.format(c.withdrawals), bold = true)
            val over = c.withdrawals - c.netProfit.coerceAtLeast(0)
            if (over > 0) Hint("${Money.format(over)} acima do lucro líquido", color = dangerColor())
        }
        FilledTonalButton(
            onClick = onAdd,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
        ) { Text("Registrar retirada") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WithdrawalDialog(partners: List<String>, onConfirm: (String, Long, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(partners.firstOrNull().orEmpty()) }
    var amount by remember { mutableLongStateOf(0L) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retirada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Quem retirou") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (partners.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        partners.take(6).forEach { p -> FilterChip(selected = name == p, onClick = { name = p }, label = { Text(p) }) }
                    }
                }
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Valor")
                DateButton("Data", date) { date = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, amount, date) }) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}

@Composable
private fun OpeningDialog(current: CashPosition, onConfirm: (Long, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableLongStateOf(if (current.hasOpening) current.openingAmount else 0L) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saldo do caixa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint("Gaveta + banco no começo do dia escolhido.")
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Valor")
                DateButton("Dia", date) { date = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(amount, date) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}
