package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.InvoiceWithBills
import br.com.lojabaterias.data.items
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.BillRow
import br.com.lojabaterias.ui.viewmodel.InvoiceDetailViewModel
import br.com.lojabaterias.ui.viewmodel.InvoiceFormViewModel
import br.com.lojabaterias.ui.viewmodel.InvoiceTab
import br.com.lojabaterias.ui.viewmodel.InvoicesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Distribuidoras mais usadas (atalhos no formulário da nota). */
private val SUPPLIERS = listOf("Heliar do Rio", "Oeste Rio Distribuidora Moura", "PCR Baterias Baterax", "Barra Nota 10")

// ------------------------------------------------------------------ Lista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { InvoicesViewModel(it.repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmPay by remember { mutableStateOf<BillRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notas fiscais") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova nota") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { DebtCard(s.debt.open, s.debt.openCount, s.debt.overdue, s.debt.overdueCount, s.debt.dueSoon, s.debt.dueSoonCount, s.waitingCount) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    InvoiceTab.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = s.tab == t,
                            onClick = { vm.setTab(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, InvoiceTab.entries.size),
                            icon = {},
                        ) { FitText(t.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            when (s.tab) {
                InvoiceTab.BILLS -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !s.showPaid, onClick = { vm.setShowPaid(false) }, label = { Text("A pagar") })
                            FilterChip(selected = s.showPaid, onClick = { vm.setShowPaid(true) }, label = { Text("Pagos") })
                        }
                    }
                    if (!s.loading && s.bills.isEmpty()) {
                        item { EmptyState(if (s.showPaid) "Nenhum boleto pago ainda." else "Nenhum boleto a pagar. 🎉") }
                    }
                    items(s.bills, key = { it.bill.id }) { row ->
                        BillCard(
                            row = row,
                            today = s.today,
                            onOpen = { onOpen(row.invoice.id) },
                            onPay = { confirmPay = row },
                            onUndo = { vm.setPaid(row.bill.id, false) },
                        )
                    }
                }
                InvoiceTab.NOTES -> {
                    if (!s.loading && s.invoices.isEmpty()) {
                        item {
                            EmptyState("Nenhuma nota lançada. Toque em \"Nova nota\" quando chegar uma nota do fornecedor.")
                        }
                    }
                    items(s.invoices, key = { it.invoice.id }) { inv -> InvoiceCard(inv) { onOpen(inv.invoice.id) } }
                }
            }
        }
    }

    confirmPay?.let { row ->
        ConfirmDialog(
            title = "Boleto pago?",
            text = "Nota ${row.invoice.number} • parcela ${row.index} de ${row.count}\n" +
                "Vencimento ${Periods.formatDate(row.bill.dueDate)} • ${Money.format(row.bill.amount)}",
            confirmLabel = "Sim, paguei",
            onConfirm = {
                vm.setPaid(row.bill.id, true)
                confirmPay = null
            },
            onDismiss = { confirmPay = null },
        )
    }
}

@Composable
private fun DebtCard(open: Long, openCount: Int, overdue: Long, overdueCount: Int, soon: Long, soonCount: Int, waiting: Int) {
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onPrimary
            Text("Devemos aos fornecedores", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
            FitText(Money.format(open), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Text(
                "$openCount boleto${if (openCount == 1) "" else "s"} a pagar",
                style = MaterialTheme.typography.bodyMedium,
                color = on,
            )
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Vencidos", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                    FitText(
                        if (overdueCount == 0) "Nenhum" else "$overdueCount • ${Money.format(overdue)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = on,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Vencem em 7 dias", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                    FitText(
                        if (soonCount == 0) "Nenhum" else "$soonCount • ${Money.format(soon)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = on,
                    )
                }
            }
            if (waiting > 0) {
                Text(
                    "$waiting nota${if (waiting == 1) "" else "s"} aguardando as baterias chegarem",
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** "Vence hoje", "Vence em 3 dias", "Venceu há 2 dias". */
private fun dueLabel(due: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days == 0L -> "Vence hoje"
        days == 1L -> "Vence amanhã"
        days > 1 -> "Vence em $days dias"
        days == -1L -> "Venceu ontem"
        else -> "Venceu há ${-days} dias"
    }
}

@Composable
private fun BillCard(row: BillRow, today: LocalDate, onOpen: () -> Unit, onPay: () -> Unit, onUndo: () -> Unit) {
    val b = row.bill
    val due = Periods.toLocalDateTime(b.dueDate).toLocalDate()
    val statusColor = when {
        b.isPaid -> profitColor()
        due.isBefore(today) -> dangerColor()
        !due.isAfter(today.plusDays(7)) -> warningColor()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AppCard(onClick = onOpen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(Money.format(b.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Nota ${row.invoice.number}" + (if (row.invoice.supplier.isNotBlank()) " • ${row.invoice.supplier}" else "") +
                        " • parcela ${row.index}/${row.count}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (b.isPaid) "Pago em ${Periods.formatDate(b.paidAt!!)}" else "${Periods.formatDate(b.dueDate)} • ${dueLabel(due, today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (b.isPaid) {
                TextButton(onClick = onUndo) { Text("Desfazer") }
            } else {
                FilledTonalButton(onClick = onPay) { Text("Paguei") }
            }
        }
    }
}

@Composable
private fun InvoiceCard(inv: InvoiceWithBills, onClick: () -> Unit) {
    val i = inv.invoice
    val units = i.items.sumOf { it.quantity }
    AppCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Nota ${i.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(i.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                (if (i.supplier.isNotBlank()) "${i.supplier} • " else "") + "${Periods.formatDate(i.issueDate)} • $units bateria${if (units == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusText(
                    if (i.isReceived) "✓ Baterias chegaram" else "⏳ Aguardando baterias",
                    if (i.isReceived) profitColor() else warningColor(),
                )
                StatusText(
                    if (inv.isFullyPaid) "✓ Paga" else "${inv.paidCount}/${inv.bills.size} boletos pagos",
                    if (inv.isFullyPaid) profitColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
}

// ---------------------------------------------------------------- Detalhe

@Composable
fun InvoiceDetailScreen(invoiceId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "invoice-$invoiceId") { InvoiceDetailViewModel(it.repository, invoiceId) }
    val loaded by vm.invoice.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var askReceive by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askPay by remember { mutableStateOf<Long?>(null) }

    SubScreen(
        title = "Nota fiscal",
        onBack = onBack,
        actions = { TextButton(onClick = onEdit) { Text("Editar") } },
    ) { inner ->
        val data = loaded
        val inv = data?.value
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            inv == null -> Box(Modifier.padding(inner)) { EmptyState("Nota não encontrada.") }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val i = inv.invoice
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nota ${i.number}", style = MaterialTheme.typography.headlineSmall)
                        if (i.supplier.isNotBlank()) Text(i.supplier, style = MaterialTheme.typography.bodyLarge)
                        InfoRow("Data da nota", Periods.formatDate(i.issueDate))
                        InfoRow("Valor total", Money.format(i.total), bold = true)
                        InfoRow("Falta pagar", Money.format(inv.openAmount), valueColor = if (inv.openAmount > 0) warningColor() else profitColor())
                        i.note?.let { InfoRow("Observação", it) }
                    }
                }

                SectionTitle("1. Chegada das baterias")
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        if (i.isReceived) {
                            Text("✓ Chegaram em ${i.receivedAt?.let { Periods.formatDate(it) } ?: "-"}", color = profitColor(), fontWeight = FontWeight.SemiBold)
                            i.receivedNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                            TextButton(onClick = vm::markWaiting, modifier = Modifier.padding(top = 4.dp)) { Text("Ainda não chegaram (desfazer)") }
                        } else {
                            Text("⏳ Aguardando as baterias chegarem", color = warningColor(), fontWeight = FontWeight.SemiBold)
                            FilledTonalButton(onClick = { askReceive = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("As baterias chegaram")
                            }
                        }
                        Text(
                            "O estoque não muda sozinho: continue contando e ajustando o estoque normalmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                SectionTitle("Baterias da nota")
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val items = i.items
                        items.forEach { line -> InfoRow("${line.quantity}× ${line.model}", "${Money.format(line.unitCost)} cada • ${Money.format(line.subtotal)}") }
                        val extras = i.total - items.sumOf { it.subtotal }
                        if (extras != 0L) InfoRow("Frete, impostos e outros", Money.format(extras))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        InfoRow("Total (${items.sumOf { it.quantity }} baterias)", Money.format(i.total), bold = true)
                    }
                }

                SectionTitle("2. Boletos")
                inv.sortedBills.forEachIndexed { k, b ->
                    AppCard {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Parcela ${k + 1} de ${inv.bills.size}", style = MaterialTheme.typography.titleSmall)
                                Text(Money.format(b.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val today = LocalDate.now()
                                val due = Periods.toLocalDateTime(b.dueDate).toLocalDate()
                                Text(
                                    if (b.isPaid) "Pago em ${Periods.formatDate(b.paidAt!!)}" else "Vence ${Periods.formatDate(b.dueDate)} • ${dueLabel(due, today)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        b.isPaid -> profitColor()
                                        due.isBefore(today) -> dangerColor()
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            if (b.isPaid) {
                                TextButton(onClick = { vm.setPaid(b.id, false) }) { Text("Desfazer") }
                            } else {
                                FilledTonalButton(onClick = { askPay = b.id }) { Text("Paguei") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Excluir nota", color = dangerColor())
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (askReceive) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askReceive = false },
            title = { Text("As baterias chegaram?") },
            text = {
                Column {
                    Text("Se faltou ou voltou alguma bateria, anote aqui (opcional).")
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        label = { Text("Ex.: faltou 1 BE50D") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.markReceived(note)
                    askReceive = false
                }) { Text("Confirmar chegada") }
            },
            dismissButton = { TextButton(onClick = { askReceive = false }) { Text("Voltar") } },
        )
    }
    askPay?.let { id ->
        ConfirmDialog(
            title = "Boleto pago?",
            text = "Marca este boleto como pago hoje.",
            confirmLabel = "Sim, paguei",
            onConfirm = {
                vm.setPaid(id, true)
                askPay = null
            },
            onDismiss = { askPay = null },
        )
    }
    if (askDelete) {
        ConfirmDialog(
            title = "Excluir nota?",
            text = "A nota e todos os boletos dela serão apagados. O estoque não muda.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = {
                askDelete = false
                vm.delete(onBack)
            },
            onDismiss = { askDelete = false },
        )
    }
}

// ------------------------------------------------------------- Formulário

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceFormScreen(invoiceId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "invoiceform-$invoiceId") { InvoiceFormViewModel(it.repository, invoiceId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    LaunchedEffect(s.done) { if (s.done) onDone() }
    var picking by remember { mutableStateOf(false) }

    SubScreen(
        title = if (invoiceId == null) "Nova nota fiscal" else "Editar nota",
        onBack = onBack,
        bottomBar = {
            if (!s.loading) {
                BottomActionBar {
                    if (s.bills.isNotEmpty() && s.billsDifference != 0L) {
                        Text(
                            if (s.billsDifference > 0) "Faltam ${Money.format(s.billsDifference)} nos boletos para fechar o total"
                            else "Os boletos passam ${Money.format(-s.billsDifference)} do total",
                            color = dangerColor(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    PrimaryActionButton(
                        text = if (s.saving) "Salvando..." else if (invoiceId == null) "Lançar nota" else "Salvar",
                        onClick = vm::save,
                        enabled = !s.saving,
                    )
                }
            }
        },
    ) { inner ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreen
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("1. Dados da nota")
            OutlinedTextField(
                value = s.number,
                onValueChange = { v -> vm.update { it.copy(number = v.take(30)) } },
                label = { Text("Número da nota") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.supplier,
                onValueChange = { v -> vm.update { it.copy(supplier = v.take(60)) } },
                label = { Text("Fornecedor (toque numa opção ou digite)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPLIERS.forEach { name ->
                    FilterChip(
                        selected = s.supplier.equals(name, ignoreCase = true),
                        onClick = { vm.update { it.copy(supplier = name) } },
                        label = { Text(name) },
                    )
                }
            }
            DateButton("Data da nota", s.issueDate) { d -> vm.update { it.copy(issueDate = d) } }

            SectionTitle("2. Baterias que vieram na nota")
            s.items.forEach { item ->
                AppCard {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.model, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removeItem(item.key) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover ${item.model}")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quantidade", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            QuantityStepper(item.quantity, { q -> vm.updateItem(item.key) { it.copy(quantity = q) } }, max = 999)
                        }
                        MoneyField(
                            value = item.unitCost,
                            onValueChange = { v -> vm.updateItem(item.key) { it.copy(unitCost = v) } },
                            label = "Valor de cada bateria na nota",
                            modifier = Modifier.padding(top = 8.dp),
                            supportingText = "Subtotal: ${Money.format(item.subtotal)}",
                        )
                    }
                }
            }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Adicionar bateria", modifier = Modifier.padding(start = 8.dp))
            }
            MoneyField(
                value = s.extras,
                onValueChange = { v -> vm.update { it.copy(extras = v) } },
                label = "Frete, impostos e outros (opcional)",
            )
            AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    InfoRow("Baterias", "${s.units} un. • ${Money.format(s.itemsTotal)}")
                    if (s.extras > 0) InfoRow("Outros valores", Money.format(s.extras))
                    InfoRow("Total da nota", Money.format(s.total), bold = true)
                }
            }
            if (s.items.any { it.productId != null }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.updateCosts, onCheckedChange = { v -> vm.update { it.copy(updateCosts = v) } })
                    Text(
                        "Atualizar o custo dessas baterias no estoque com os valores desta nota",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionTitle("3. Boletos")
            BillGenerator(enabled = s.total > 0, default = s.issueDate.plusDays(30)) { count, first, interval ->
                vm.generateBills(count, first, interval)
            }
            s.bills.sortedBy { it.dueDate }.forEachIndexed { k, b ->
                AppCard {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Parcela ${k + 1}" + if (b.isPaid) " • paga" else "",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (b.isPaid) profitColor() else Color.Unspecified,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.removeBill(b.key) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover parcela ${k + 1}")
                            }
                        }
                        DateButton("Vencimento", b.dueDate) { d -> vm.updateBill(b.key) { it.copy(dueDate = d) } }
                        MoneyField(
                            value = b.amount,
                            onValueChange = { v -> vm.updateBill(b.key) { it.copy(amount = v) } },
                            label = "Valor do boleto",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            if (s.bills.isNotEmpty()) {
                TextButton(onClick = vm::addBill) { Text("+ Adicionar outro boleto") }
            }

            SectionTitle("4. As baterias já chegaram?")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Ainda não", true to "Já chegaram").forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = s.received == v,
                        onClick = { vm.update { it.copy(received = v) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = {},
                    ) { FitText(label, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Text(
                "Notas antigas, de baterias que já chegaram: marque \"Já chegaram\" e coloque só os boletos que faltam pagar. " +
                    "O estoque não muda em nenhum caso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = s.note,
                onValueChange = { v -> vm.update { it.copy(note = v.take(300)) } },
                label = { Text("Observação (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (picking) {
        ProductPickerDialog(
            title = "Qual bateria veio na nota?",
            products = products,
            onPick = {
                vm.addItem(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Monta os boletos de uma vez: quantidade, primeiro vencimento e intervalo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillGenerator(enabled: Boolean, default: LocalDate, onGenerate: (Int, LocalDate, Int) -> Unit) {
    var count by remember { mutableIntStateOf(1) }
    var first by remember(default) { mutableStateOf(default) }
    var interval by remember { mutableIntStateOf(30) }
    AppCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Montar os boletos", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantos boletos?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                QuantityStepper(count, { count = it }, max = 24)
            }
            DateButton("Primeiro vencimento", first) { first = it }
            if (count > 1) {
                Text("Um boleto a cada:", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 15, 21, 28, 30).forEach { d ->
                        FilterChip(selected = interval == d, onClick = { interval = d }, label = { Text("$d dias") })
                    }
                }
            }
            FilledTonalButton(onClick = { onGenerate(count, first, interval) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (count == 1) "Criar 1 boleto" else "Dividir em $count boletos iguais")
            }
            if (!enabled) {
                Text(
                    "Adicione as baterias primeiro para saber o total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
