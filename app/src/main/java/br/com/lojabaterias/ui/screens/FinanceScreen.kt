package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.input.KeyboardCapitalization
import br.com.lojabaterias.data.CashPosition
import br.com.lojabaterias.data.Expense
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.ToastEffect
import java.time.LocalDate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.FinancePeriod
import br.com.lojabaterias.data.FinanceReport
import br.com.lojabaterias.data.FinanceSummary
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.BarGroup
import br.com.lojabaterias.ui.components.ChartColors
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.HBarList
import br.com.lojabaterias.ui.components.HBarRow
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.LegendDot
import br.com.lojabaterias.ui.components.PairedBarChart
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.StackedShareBar
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.FinanceViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financeiro") },
                actions = {
                    TextButton(onClick = { showHelp = true }) { Text("? Entenda") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FinancePeriod.entries.forEachIndexed { index, p ->
                        SegmentedButton(
                            selected = state.selection.period == p,
                            onClick = { vm.setPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(index, FinancePeriod.entries.size),
                            icon = {},
                        ) { FitText(p.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val canMove = state.selection.period != FinancePeriod.ALL
                    IconButton(onClick = vm::previous, enabled = canMove) {
                        if (canMove) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Período anterior")
                    }
                    Text(f.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::next, enabled = canMove && state.selection.offset < 0) {
                        if (canMove) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo período")
                    }
                }
            }

            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    FinanceSection.entries.forEachIndexed { index, sec ->
                        SegmentedButton(
                            selected = section == sec,
                            onClick = { section = sec },
                            shape = SegmentedButtonDefaults.itemShape(index, FinanceSection.entries.size),
                            icon = {},
                        ) { FitText(sec.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            item {
                Text(
                    section.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            when (section) {
                FinanceSection.PROFIT -> {
                    item { HeroCard(f) }
                    item { WhereMoneyWentCard(c) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Vendas", c.salesCount.toString(), Modifier.weight(1f))
                            Metric("Baterias", c.units.toString(), Modifier.weight(1f))
                            Metric("Ticket médio", Money.format(c.averageTicket), Modifier.weight(1.4f))
                        }
                    }

                    item { SectionTitle(evolutionTitle(f.period)) }
                    item { EvolutionCard(f) }

                    item { SectionTitle("A conta completa") }
                    item { StatementCard(c) }

                    item { SectionTitle("Como os clientes pagaram") }
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
                                        if (c.revenue > 0) " • ${it.revenue * 100 / c.revenue}% do que entrou" else "",
                                    )
                                },
                                color = ChartColors.slot(0),
                            )
                        }
                    }

                    item { SectionTitle("Despesas por categoria") }
                    item {
                        ChartCard {
                            if (c.expensesByCategory.isEmpty()) Empty("Nenhuma despesa paga no período.")
                            HBarList(
                                rows = c.expensesByCategory.map { (cat, total) ->
                                    HBarRow(
                                        label = cat,
                                        value = total,
                                        valueText = Money.format(total),
                                        detail = if (c.expenses > 0) "${total * 100 / c.expenses}% das despesas" else null,
                                    )
                                },
                                color = ChartColors.slot(1),
                            )
                        }
                    }

                    item { SectionTitle("Modelos que mais deram lucro") }
                    item {
                        ChartCard {
                            if (c.topModels.isEmpty()) Empty("Sem vendas no período.")
                            HBarList(
                                rows = c.topModels.map {
                                    HBarRow(
                                        label = it.model,
                                        value = it.profit,
                                        valueText = Money.format(it.profit),
                                        detail = "${it.quantity} vendida${if (it.quantity == 1) "" else "s"} • faturou ${Money.format(it.revenue)}",
                                    )
                                },
                                color = ChartColors.slot(2),
                            )
                        }
                    }
                }
                FinanceSection.CASH -> {
                    item { SectionTitle("Caixa agora (gaveta + banco)") }
                    item { CashNowCard(f.cash, onSetOpening = { askOpening = true }) }
                    item { SectionTitle("Entrou e saiu no período") }
                    item { CashFlowCard(c, f.period) }

                    item { SectionTitle("Retiradas dos sócios") }
                    item { WithdrawalsCard(c, onAdd = { askWithdrawal = true }, onDelete = { deleting = it }) }

                    item { SectionTitle("O que a loja tem agora") }
                    item {
                        ChartCard {
                            Text(
                                "Não depende do período escolhido: é a foto de hoje.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            InfoRow("Baterias no estoque (pelo custo)", Money.format(f.stockAtCost))
                            InfoRow("Baterias no estoque (se vender no PIX)", Money.format(f.stockAtPix))
                            InfoRow("Sucatas no estoque (tabela)", Money.format(f.scrapStockValue))
                            InfoRow("A receber da carga (não pago)", Money.format(f.toReceive))
                            InfoRow("Devemos aos fornecedores (boletos)", Money.format(f.supplierDebt.open))
                            if (f.supplierDebt.overdue > 0) {
                                InfoRow("Boletos vencidos", Money.format(f.supplierDebt.overdue), valueColor = dangerColor())
                            }
                        }
                    }
                }
                FinanceSection.EXTRAS -> {
                    item { SectionTitle("Baterias extras (ganhadas)") }
                    item { ExtrasCard(f) }
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
            text = "${w.category} • ${Money.format(w.amount)} em ${Periods.formatDate(w.date)}",
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

/** As três partes do Financeiro. */
private enum class FinanceSection(val label: String, val hint: String) {
    PROFIT("Lucro", "Quanto a loja ganhou com o que vendeu (o custo das baterias sai no dia da venda)."),
    CASH("Caixa", "O dinheiro de verdade: o que entrou, o que saiu, as retiradas e quanto dá para retirar."),
    EXTRAS("Extras", "Quanto as baterias extras (ganhadas) já renderam."),
}

private fun evolutionTitle(p: FinancePeriod) = when (p) {
    FinancePeriod.MONTH -> "Dia a dia do mês"
    FinancePeriod.YEAR -> "Mês a mês"
    FinancePeriod.ALL -> "Últimos 12 meses"
}

@Composable
private fun HeroCard(f: FinanceReport) {
    val c = f.current
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onPrimary
            val soft = on.copy(alpha = 0.8f)
            Text(if (c.netProfit >= 0) "Sobrou (lucro líquido)" else "Faltou (prejuízo)", style = MaterialTheme.typography.bodyMedium, color = soft)
            FitText(Money.format(c.netProfit), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Entrou", style = MaterialTheme.typography.bodyMedium, color = soft)
                    FitText(Money.format(c.revenue), style = MaterialTheme.typography.titleMedium, color = on)
                }
                Column(Modifier.weight(1f)) {
                    Text("Saiu", style = MaterialTheme.typography.bodyMedium, color = soft)
                    FitText(Money.format(c.totalOut), style = MaterialTheme.typography.titleMedium, color = on)
                }
            }
            c.marginPercent?.let {
                Text(
                    "De cada R$ 100 vendidos, sobraram R$ $it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            f.previous?.let { prev -> comparison(c, prev, f.previousLabel)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = soft, modifier = Modifier.padding(top = 4.dp))
            } }
        }
    }
}

/** "Lucro 12% maior que em agosto" (ou menor). */
private fun comparison(c: FinanceSummary, prev: FinanceSummary, prevLabel: String): String? {
    if (prev.revenue == 0L && prev.expenses == 0L) return null
    val base = kotlin.math.abs(prev.netProfit)
    if (base == 0L) return null
    val pct = (c.netProfit - prev.netProfit) * 100 / base
    return when {
        pct > 0 -> "▲ Lucro $pct% maior que em $prevLabel (${Money.format(prev.netProfit)})"
        pct < 0 -> "▼ Lucro ${-pct}% menor que em $prevLabel (${Money.format(prev.netProfit)})"
        else -> "Lucro igual ao de $prevLabel"
    }
}

@Composable
private fun WhereMoneyWentCard(c: FinanceSummary) {
    ChartCard {
        Text("Para onde foi o dinheiro que entrou", style = MaterialTheme.typography.titleSmall)
        if (c.revenue <= 0) {
            Empty("Sem vendas no período.")
            return@ChartCard
        }
        val parts = listOf(
            Triple("Sobrou (lucro)", c.netProfit.coerceAtLeast(0), ChartColors.slot(0)),
            Triple("Custo das baterias e cascos", c.batteryCost + c.cascoCost, ChartColors.slot(1)),
            Triple("Despesas", c.expenses, ChartColors.slot(2)),
            Triple("Taxas das maquininhas", c.fees, ChartColors.slot(3)),
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "R$ ${value * 100 / total} de cada R$ 100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (c.netProfit < 0) {
            Text(
                "As saídas passaram do que entrou: prejuízo de ${Money.format(-c.netProfit)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = dangerColor(),
                modifier = Modifier.padding(top = 8.dp),
            )
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
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colorA, "Faturamento")
            LegendDot(colorB, profitLabel)
        }
        val sel = selected?.let { buckets.getOrNull(it) }
        Column(Modifier.padding(top = 8.dp)) {
            if (sel != null) {
                Text(sel.fullLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Faturou ${Money.format(sel.revenue)} • ${profitLabel.lowercase()} ${Money.format(sel.profit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Nenhum movimento no período.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(
            "Toque numa barra para ver os valores.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val best = buckets.maxByOrNull { it.revenue }?.takeIf { it.revenue > 0 }
        if (best != null) {
            Text(
                "Melhor: ${best.fullLabel.replaceFirstChar { it.lowercase() }}, com ${Money.format(best.revenue)} de faturamento.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatementCard(c: FinanceSummary) {
    ChartCard {
        Text("Vendas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        InfoRow("Entrou com as vendas", Money.format(c.revenue))
        InfoRow("(−) Custo das baterias", minus(c.batteryCost))
        if (c.cascoCost > 0) InfoRow("(−) Casco cobrado (para repor o casco)", minus(c.cascoCost))
        InfoRow("(−) Taxas das maquininhas", minus(c.fees))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("= Lucro das vendas", Money.format(c.grossProfit), valueColor = moneyResultColor(c.grossProfit))
        InfoRow("(−) Despesas pagas", minus(c.expenses))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("= Lucro líquido", Money.format(c.netProfit), bold = true, valueColor = moneyResultColor(c.netProfit))

        Text(
            "Outras entradas e saídas",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        InfoRow("(+) Carga de baterias (recebido)", Money.format(c.chargesPaid))
        InfoRow("(+) Sucatas vendidas", Money.format(c.scrapSold))
        InfoRow("(−) Sucatas compradas", minus(c.scrapPurchased))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("= Resultado geral", Money.format(c.generalResult), bold = true, valueColor = moneyResultColor(c.generalResult))

        Text(
            buildString {
                append("Descontos dados nas vendas: ${Money.format(c.discounts)} (já estão fora do que entrou).")
                if (c.supplierPaid > 0) {
                    append(" Boletos de fornecedor pagos: ${Money.format(c.supplierPaid)} (não entram na conta: o custo das baterias já sai do lucro quando elas são vendidas).")
                }
                if (c.vouchersPaid > 0) {
                    append(" Vales de casco devolvidos: ${Money.format(c.vouchersPaid)} (não entram na conta: o valor já foi contado como custo na venda).")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun minus(value: Long) = if (value == 0L) Money.format(0) else "-" + Money.format(value)

@Composable
private fun ExtrasCard(f: FinanceReport) {
    val all = f.extrasAllTime
    val p = f.extrasPeriod
    val showPeriod = f.period != FinancePeriod.ALL
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onSecondaryContainer
            Text("Desde o começo, as extras deram de lucro", style = MaterialTheme.typography.bodyMedium, color = on)
            FitText(Money.format(all.profit), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Text(
                "${all.sold} extra${if (all.sold == 1) "" else "s"} vendida${if (all.sold == 1) "" else "s"}, " +
                    "somando ${Money.format(all.saleValue)} em vendas.",
                style = MaterialTheme.typography.bodyMedium,
                color = on,
            )
        }
    }
    ChartCard {
        if (showPeriod) {
            Text("Neste período (${f.label.lowercase()})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            InfoRow("Extras ganhadas", p.received.toString())
            InfoRow("Extras vendidas", p.sold.toString())
            InfoRow("Valor de venda", Money.format(p.saleValue))
            InfoRow("Lucro", Money.format(p.profit), valueColor = moneyResultColor(p.profit))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        Text("Desde o começo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        InfoRow("Extras ganhadas", all.received.toString())
        InfoRow("Extras vendidas", all.sold.toString())
        InfoRow("Valor de venda", Money.format(all.saleValue))
        InfoRow("Lucro", Money.format(all.profit), bold = true, valueColor = moneyResultColor(all.profit))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("Ainda no estoque", f.extrasInStock.toString())
        InfoRow("Valem (se vender no PIX)", Money.format(f.extrasInStockValue))
        if (all.byModel.isNotEmpty()) {
            Text(
                "Lucro das extras por modelo (desde o começo)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            HBarList(
                rows = all.byModel.map {
                    HBarRow(it.model, it.profit, Money.format(it.profit), "${it.sold} vendida${if (it.sold == 1) "" else "s"} • vendeu ${Money.format(it.saleValue)}")
                },
                color = ChartColors.slot(0),
            )
        }
        Text(
            "Extra tem custo zero, então o lucro é o valor da venda (sem o casco) menos a taxa da maquininha.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun GlossaryContent() {
    Column {
        listOf(
            "Entrou (faturamento)" to "Tudo o que os clientes pagaram nas vendas, já com os descontos.",
            "Custo das baterias" to "Quanto a loja pagou pelas baterias que vendeu.",
            "Casco cobrado" to "Entra no faturamento, mas também como custo, porque serve para repor o casco. Não vira lucro.",
            "Taxas das maquininhas" to "O que a maquininha desconta nas vendas no débito e no crédito.",
            "Lucro das vendas" to "Entrou − custo − casco − taxas.",
            "Despesas" to "Contas fixas e despesas avulsas pagas no período.",
            "Lucro líquido" to "O que sobrou de verdade: lucro das vendas − despesas.",
            "Resultado geral" to "Lucro líquido + carga recebida + sucatas vendidas − sucatas compradas.",
            "Ticket médio" to "Quanto, em média, cada venda rendeu.",
            "Caixa" to "O dinheiro de verdade da loja (gaveta + banco). É diferente do lucro: o dinheiro do custo das baterias fica no caixa até pagar os boletos.",
            "Retirada" to "Dinheiro que um sócio tira para si. Não é despesa (não muda o lucro), mas sai do caixa.",
            "Pode retirar com segurança" to "Caixa menos os boletos que vencem em 30 dias e as contas fixas do mês ainda não pagas.",
        ).forEachIndexed { i, (term, meaning) ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(term, style = MaterialTheme.typography.titleSmall)
            Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            FitText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

// ------------------------------------------------------------------ Caixa

@Composable
private fun CashFlowCard(c: FinanceSummary, period: FinancePeriod) {
    ChartCard {
        Text(
            "Aqui não é lucro: é o dinheiro que entrou e saiu de verdade" +
                if (period == FinancePeriod.ALL) "." else " no período.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Entrou", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        InfoRow("Vendas (já sem a taxa da maquininha)", Money.format(c.salesCashIn))
        if (c.chargesPaid > 0) InfoRow("Carga de baterias", Money.format(c.chargesPaid))
        if (c.scrapSold > 0) InfoRow("Sucatas vendidas", Money.format(c.scrapSold))
        InfoRow("Total que entrou", Money.format(c.cashIn), bold = true)

        Text("Saiu", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        InfoRow("Boletos de fornecedor pagos", minus(c.supplierPaid))
        InfoRow("Despesas pagas", minus(c.expenses))
        if (c.scrapPurchased > 0) InfoRow("Sucatas compradas", minus(c.scrapPurchased))
        if (c.vouchersPaid > 0) InfoRow("Vales de casco devolvidos", minus(c.vouchersPaid))
        InfoRow("Total que saiu", minus(c.cashOut), bold = true)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        InfoRow("Sobrou antes das retiradas", Money.format(c.cashBeforeWithdrawals), valueColor = moneyResultColor(c.cashBeforeWithdrawals))
        InfoRow("(−) Retiradas dos sócios", minus(c.withdrawals))
        InfoRow("= Ficou na loja", Money.format(c.cashResult), bold = true, valueColor = moneyResultColor(c.cashResult))
        Text(
            "Por que é diferente do lucro? O lucro desconta o custo das baterias no dia da venda; o caixa só " +
                "desconta quando o boleto é pago. Vendas no cartão contam no dia da venda, mesmo que a maquininha deposite depois.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CashNowCard(cash: CashPosition, onSetOpening: () -> Unit) {
    val safe = cash.safeToWithdraw
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onSecondaryContainer
            Text("Caixa agora (gaveta + banco)", style = MaterialTheme.typography.bodyMedium, color = on)
            FitText(Money.format(cash.now), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Text(
                if (cash.hasOpening) {
                    "Saldo inicial de ${Money.format(cash.openingAmount)} em ${cash.openingDate?.let { Periods.formatDate(it) }}, mais tudo o que entrou e saiu depois."
                } else {
                    "Ainda sem saldo inicial: a conta começa do zero. Conte o dinheiro da loja e informe abaixo para ficar exato."
                },
                style = MaterialTheme.typography.bodySmall,
                color = on,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = on.copy(alpha = 0.2f))
            CashLine("Boletos vencidos e dos próximos 30 dias (${cash.upcomingBillsCount})", "-" + Money.format(cash.upcomingBills), on)
            CashLine("Contas fixas deste mês a pagar (${cash.fixedBillsCount})", "-" + Money.format(cash.fixedBillsDue), on)
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = on.copy(alpha = 0.2f))
            if (safe >= 0) {
                Text("✅ Pode retirar com segurança", style = MaterialTheme.typography.titleSmall, color = on)
                FitText(Money.format(safe), style = MaterialTheme.typography.headlineSmall, color = on, fontWeight = FontWeight.Bold)
            } else {
                Text("⚠️ Falta dinheiro para os compromissos", style = MaterialTheme.typography.titleSmall, color = dangerColor())
                FitText(Money.format(-safe), style = MaterialTheme.typography.headlineSmall, color = dangerColor(), fontWeight = FontWeight.Bold)
                Text("Segure as retiradas até vender mais ou pagar menos.", style = MaterialTheme.typography.bodySmall, color = on)
            }
            OutlinedButton(onClick = onSetOpening, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(if (cash.hasOpening) "Conferir / corrigir o saldo do caixa" else "Informar o saldo do caixa")
            }
        }
    }
}

@Composable
private fun CashLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WithdrawalsCard(c: FinanceSummary, onAdd: () -> Unit, onDelete: (Expense) -> Unit) {
    ChartCard {
        Text(
            "Dinheiro que os sócios tiraram para si. Não muda o lucro, mas sai do caixa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (c.withdrawalList.isEmpty()) Empty("Nenhuma retirada no período.")
        c.withdrawalList.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.category, style = MaterialTheme.typography.titleSmall)
                    Text(Periods.formatDate(w.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(w.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onDelete(w) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir retirada", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (c.withdrawalList.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            val byPartner = c.withdrawalList.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
            if (byPartner.size > 1) byPartner.forEach { (name, total) -> InfoRow(name, Money.format(total)) }
            InfoRow("Total retirado", Money.format(c.withdrawals), bold = true)
            val over = c.withdrawals - c.netProfit.coerceAtLeast(0)
            Text(
                if (over > 0) "⚠️ Retiraram ${Money.format(over)} a mais que o lucro líquido do período (${Money.format(c.netProfit)})."
                else "Retiraram ${Money.format(c.withdrawals)} de um lucro líquido de ${Money.format(c.netProfit)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (over > 0) dangerColor() else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Registrar retirada") }
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
        title = { Text("Retirada de sócio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Quem retirou") },
                    singleLine = true,
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
                Text(
                    "Conte todo o dinheiro da loja (gaveta + conta do banco) no começo do dia escolhido e informe aqui. " +
                        "A partir desse dia o app soma o que entra e tira o que sai. Pode corrigir sempre que conferir o caixa.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Dinheiro da loja")
                DateButton("No começo do dia", date) { date = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(amount, date) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}
