package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import br.com.lojabaterias.ui.viewmodel.ImportResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.Role
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
import br.com.lojabaterias.ui.viewmodel.InvoicesState
import br.com.lojabaterias.ui.viewmodel.InvoicesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Distribuidoras mais usadas (atalhos no formulário da nota). */
private val SUPPLIERS = listOf("Heliar do Rio", "Oeste Rio Distribuidora Moura", "PCR Baterias Baterax", "Barra Nota 10")

// ------------------------------------------------------------------ Lista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(onNew: () -> Unit, onImport: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { InvoicesViewModel(it.repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmPay by remember { mutableStateOf<BillRow?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notas fiscais") },
                actions = { TextButton(onClick = { showHelp = true }) { Text("? Como funciona") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { choosing = true },
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
            item {
                // O quadro de cima acompanha o que está sendo visto
                when {
                    s.tab == InvoiceTab.NOTES -> NotesSummaryCard(s)
                    s.showPaid -> PaidSummaryCard(s)
                    else -> DebtCard(s.debt.open, s.debt.openCount, s.debt.overdue, s.debt.overdueCount, s.debt.dueSoon, s.debt.dueSoonCount, s.waitingCount)
                }
            }
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
                            AppCard {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Nenhuma nota lançada ainda", style = MaterialTheme.typography.titleMedium)
                                    HowItWorks(Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                    items(s.invoices, key = { it.invoice.id }) { inv -> InvoiceCard(inv) { onOpen(inv.invoice.id) } }
                }
            }
        }
    }

    if (choosing) {
        NewNoteDialog(
            onImport = { choosing = false; onImport() },
            onManual = { choosing = false; onNew() },
            onDismiss = { choosing = false },
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Como funcionam as notas") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { HowItWorks() } },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendi") } },
        )
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

@Composable
private fun PaidSummaryCard(s: InvoicesState) {
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onSecondaryContainer
            Text("Já pagamos aos fornecedores", style = MaterialTheme.typography.bodyMedium, color = on)
            FitText(Money.format(s.paidBillsTotal), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Text(
                "${s.paidBillsCount} boleto${if (s.paidBillsCount == 1) "" else "s"} pago${if (s.paidBillsCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = on,
            )
            Text(
                "Neste mês: ${Money.format(s.paidThisMonth)}",
                style = MaterialTheme.typography.titleMedium,
                color = on,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun NotesSummaryCard(s: InvoicesState) {
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onSecondaryContainer
            Text("Notas pagas", style = MaterialTheme.typography.bodyMedium, color = on)
            FitText(Money.format(s.paidNotesTotal), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            val paying = s.invoices.size - s.bonusCount
            Text(
                "${s.paidNotesCount} de $paying nota${if (paying == 1) "" else "s"} com todos os boletos pagos" +
                    if (s.bonusCount > 0) " • 🎁 ${s.bonusCount} bonificaç${if (s.bonusCount == 1) "ão" else "ões"} (${Money.format(s.bonusTotal)})" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = on,
            )
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Em aberto", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                    FitText("${s.openNotesCount} • falta ${Money.format(s.debt.open)}", style = MaterialTheme.typography.titleMedium, color = on)
                }
                Column(Modifier.weight(1f)) {
                    Text("Aguardando baterias", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                    FitText(s.waitingCount.toString(), style = MaterialTheme.typography.titleMedium, color = on)
                }
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
                    (if (inv.isBonus) "🎁 " else "") + "Nota ${i.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(i.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                (if (inv.isBonus) "Bonificação • " else "") + (if (i.supplier.isNotBlank()) "${i.supplier} • " else "") +
                    "${Periods.formatDate(i.issueDate)} • $units bateria${if (units == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NoteProgress(inv, Modifier.padding(top = 10.dp))
            val (next, done) = nextStep(inv, LocalDate.now())
            StatusText(next, if (done) profitColor() else MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
}

/** Os 3 passos de uma nota: lançar, receber as baterias e pagar os boletos. */
@Composable
fun HowItWorks(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HelpStep(1, "Lance a nota", "Toque em \"Nova nota\". Importe o PDF ou o XML que o fornecedor mandou (o app preenche tudo) ou digite à mão.")
        HelpStep(2, "Marque a chegada", "Quando as baterias chegarem, abra a nota e toque em \"As baterias chegaram\". Elas entram no estoque com o custo da nota.")
        HelpStep(3, "Pague os boletos", "Na aba Boletos aparecem os vencimentos. Ao pagar, toque em \"Paguei\". O boleto sai do caixa, mas não do lucro: o custo da bateria já sai na venda.")
        HelpStep(0, "🎁 Nota sem boletos = bonificação", "O fornecedor deu as baterias: não há nada a pagar. Elas entram no estoque com custo R$ 0, então tudo o que a venda trouxer é lucro.")
    }
}

@Composable
private fun HelpStep(n: Int, title: String, text: String) {
    Row {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) { Text(if (n > 0) "$n" else "★", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Andamento da nota: Lançada → Chegou → Paga. */
@Composable
private fun NoteProgress(inv: InvoiceWithBills, modifier: Modifier = Modifier) {
    val i = inv.invoice
    val steps = listOf(
        "Lançada" to true,
        (if (i.isReceived) "Chegou" else "Chegada") to i.isReceived,
        when {
            inv.isBonus -> "Bonificação" to i.isReceived
            inv.isFullyPaid -> "Paga" to true
            else -> "Pagos ${inv.paidCount}/${inv.bills.size}" to false
        },
    )
    val doneColor = profitColor()
    val todoColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { k, (label, done) ->
            if (k > 0) {
                Box(
                    Modifier.weight(1f).padding(top = 11.dp).height(2.dp)
                        .background(if (done) doneColor else todoColor)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (done) doneColor else todoColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done) "✓" else "${k + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (done) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** O que falta fazer nesta nota (e se já está tudo certo). */
private fun nextStep(inv: InvoiceWithBills, today: LocalDate): Pair<String, Boolean> {
    if (!inv.invoice.isReceived) return "Próximo passo: marcar a chegada das baterias" to false
    if (inv.isBonus) return "✓ Tudo certo: bonificação no estoque com custo zero (nada a pagar)" to true
    val bills = inv.sortedBills
    val next = bills.firstOrNull { !it.isPaid } ?: return "✓ Tudo certo: baterias no estoque e nota paga" to true
    val due = Periods.toLocalDateTime(next.dueDate).toLocalDate()
    return "Próximo passo: pagar a parcela ${bills.indexOf(next) + 1} de ${bills.size} " +
        "(${Money.format(next.amount)}, ${dueLabel(due, today).lowercase()})" to false
}

/** Nova nota: importar o arquivo (preenche sozinho) ou digitar à mão. */
@Composable
private fun NewNoteDialog(onImport: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova nota fiscal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppCard(onClick = onImport, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📄 Importar PDF ou XML", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Escolha o arquivo que o fornecedor mandou. O app lê e preenche tudo; você só confere.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                AppCard(onClick = onManual) {
                    Column(Modifier.padding(16.dp)) {
                        Text("✍️ Digitar à mão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Preencha os dados olhando a nota em papel.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}

// ---------------------------------------------------------------- Detalhe

@Composable
fun InvoiceDetailScreen(invoiceId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "invoice-$invoiceId") { InvoiceDetailViewModel(it.repository, invoiceId) }
    val loaded by vm.invoice.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var askReceive by remember { mutableStateOf(false) }
    var askUndo by remember { mutableStateOf(false) }
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
                        if (inv.isBonus) Text("🎁 Bonificação (sem boletos)", color = profitColor(), fontWeight = FontWeight.SemiBold)
                        if (i.supplier.isNotBlank()) Text(i.supplier, style = MaterialTheme.typography.bodyLarge)
                        InfoRow("Data da nota", Periods.formatDate(i.issueDate))
                        InfoRow("Valor total", Money.format(i.total), bold = true)
                        if (inv.isBonus) {
                            InfoRow("Falta pagar", "Nada (bonificação)", valueColor = profitColor())
                        } else {
                            InfoRow("Falta pagar", Money.format(inv.openAmount), valueColor = if (inv.openAmount > 0) warningColor() else profitColor())
                        }
                        i.note?.let { InfoRow("Observação", it) }
                        NoteProgress(inv, Modifier.padding(top = 12.dp))
                    }
                }

                // O que fazer agora, com o botão certo
                val (next, allDone) = nextStep(inv, LocalDate.now())
                AppCard(containerColor = if (allDone) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text(next, style = MaterialTheme.typography.titleSmall)
                        val nextBill = inv.sortedBills.firstOrNull { !it.isPaid }
                        when {
                            !i.isReceived -> FilledTonalButton(onClick = { askReceive = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("As baterias chegaram")
                            }
                            nextBill != null -> FilledTonalButton(onClick = { askPay = nextBill.id }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("Paguei esta parcela")
                            }
                        }
                    }
                }

                SectionTitle("1. Chegada das baterias")
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        if (i.isReceived) {
                            Text("✓ Chegaram em ${i.receivedAt?.let { Periods.formatDate(it) } ?: "-"}", color = profitColor(), fontWeight = FontWeight.SemiBold)
                            i.receivedNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                            TextButton(onClick = { askUndo = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Ainda não chegaram (desfazer)") }
                        } else {
                            Text("⏳ Aguardando as baterias chegarem", color = warningColor(), fontWeight = FontWeight.SemiBold)
                            FilledTonalButton(onClick = { askReceive = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("As baterias chegaram")
                            }
                        }
                        Text(
                            if (i.isReceived) {
                                if (i.items.any { it.movementId != null }) "As baterias que chegaram entraram no estoque (veja em Movimentações)."
                                else "Esta nota não mexeu no estoque."
                            } else {
                                "Quando chegarem, toque no botão acima: as baterias entram no estoque automaticamente" +
                                    if (inv.isBonus) " com custo R$ 0 (bonificação)." else "."
                            },
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
                        items.forEach { line ->
                            InfoRow("${line.quantity}× ${line.model}", "${Money.format(line.unitCost)} cada • ${Money.format(line.subtotal)}")
                            if (line.discountTotal > 0 && line.grossTotal != null) {
                                Text(
                                    "${Money.format(line.grossTotal)} − desconto de ${Money.format(line.discountTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val got = line.received
                            if (i.isReceived && got != null) {
                                Text(
                                    (if (got == line.quantity) "✓ chegaram todas" else "⚠ chegaram $got de ${line.quantity}") +
                                        if (line.movementId != null) " • entrou no estoque" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (got == line.quantity) profitColor() else warningColor(),
                                )
                            }
                        }
                        val extras = i.total - items.sumOf { it.subtotal }
                        if (extras != 0L) InfoRow("Frete, impostos e outros", Money.format(extras))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        InfoRow("Total (${items.sumOf { it.quantity }} baterias)", Money.format(i.total), bold = true)
                    }
                }

                SectionTitle("2. Boletos")
                if (inv.isBonus) {
                    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "🎁 Sem boletos: é bonificação. Não há nada a pagar e as baterias entram no estoque com custo R$ 0 " +
                                "(o valor da venda delas é todo lucro). Se a nota tiver boletos, toque em Editar e adicione.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
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

    val current = loaded?.value
    if (askReceive && current != null) {
        ReceiveDialog(
            inv = current,
            onConfirm = { note, qty, stock ->
                vm.markReceived(note, qty, stock)
                askReceive = false
            },
            onDismiss = { askReceive = false },
        )
    }
    if (askUndo) {
        ConfirmDialog(
            title = "Desfazer a chegada?",
            text = "A nota volta para \"aguardando baterias\" e as baterias que entraram no estoque por ela saem do estoque.",
            confirmLabel = "Desfazer",
            onConfirm = {
                vm.markWaiting()
                askUndo = false
            },
            onDismiss = { askUndo = false },
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
            text = "A nota e todos os boletos dela serão apagados. Se as baterias entraram no estoque por ela, saem do estoque.",
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
fun InvoiceFormScreen(invoiceId: Long?, onDone: () -> Unit, onBack: () -> Unit, autoImport: Boolean = false) {
    val vm = appViewModel(key = "invoiceform-$invoiceId") { InvoiceFormViewModel(it.repository, invoiceId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    LaunchedEffect(s.done) { if (s.done) onDone() }
    var picking by remember { mutableStateOf(false) }
    // Linha lida da nota que está sendo ligada a uma bateria do estoque
    var assigning by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(context, uri)
    }
    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoImport) {
        if (autoImport && !autoLaunched) {
            autoLaunched = true
            importer.launch(NFE_TYPES)
        }
    }

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
            if (!s.isEdit) {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📄 Tem o PDF ou o XML da nota?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "O app lê o arquivo e preenche tudo: número, fornecedor, data, baterias, descontos e boletos. Depois é só conferir.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(
                            onClick = { importer.launch(NFE_TYPES) },
                            enabled = !s.importing,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(if (s.importing) "Lendo a nota..." else "Importar PDF ou XML") }
                        Text(
                            "Dica: o XML costuma vir junto com o PDF no e-mail do fornecedor, e é lido sem erro.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            s.importResult?.let { ImportResultCard(it) }

            SectionTitle("1. Dados da nota")
            Hint("Ficam no topo da nota: o número, quem vendeu (fornecedor) e a data de emissão.")
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
            Hint("Para cada modelo: a quantidade, o subtotal sem desconto e o desconto daquela linha. O app calcula o custo de cada bateria.")
            s.items.forEach { item ->
                AppCard {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (item.unmatched != null) "⚠ Bateria não reconhecida" else item.model,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (item.unmatched != null) warningColor() else Color.Unspecified,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { vm.removeItem(item.key) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover ${item.model}")
                            }
                        }
                        if (item.unmatched != null) {
                            Text("Na nota: ${item.unmatched}", style = MaterialTheme.typography.bodyMedium)
                            FilledTonalButton(onClick = { assigning = item.key }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text("Escolher bateria do estoque")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quantidade", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            QuantityStepper(item.quantity, { q -> vm.updateItem(item.key) { it.copy(quantity = q) } }, max = 999)
                        }
                        MoneyField(
                            value = item.grossTotal,
                            onValueChange = { v -> vm.updateItem(item.key) { it.copy(grossTotal = v) } },
                            label = "Subtotal sem desconto (todas desse modelo)",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        MoneyField(
                            value = item.discountTotal,
                            onValueChange = { v -> vm.updateItem(item.key) { it.copy(discountTotal = v) } },
                            label = "Desconto (no subtotal, opcional)",
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            "Com desconto: ${Money.format(item.subtotal)} ÷ ${item.quantity} = ${Money.format(item.unitCost)} cada",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
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
            Text(
                if (s.bills.isEmpty()) "Sem boletos (bonificação): quando as baterias chegarem, entram no estoque com custo R$ 0."
                else "Quando as baterias chegarem, o custo de cada uma no estoque passa a ser o valor com desconto desta nota.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("3. Boletos")
            Hint("As parcelas que vocês vão pagar. Elas aparecem na aba Boletos, com aviso quando estiverem perto de vencer.")
            if (s.bills.isEmpty()) {
                AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🎁 Sem boletos = bonificação", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Se a nota não tem boletos, o fornecedor deu as baterias: nada a pagar, e elas entram no estoque com custo R$ 0. " +
                                "Se a nota tem boletos, monte abaixo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
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

            if (!s.isEdit) {
                SectionTitle("4. As baterias já chegaram?")
                listOf(
                    false to "Ainda não: vão entrar no estoque quando eu marcar que chegaram",
                    true to "Nota antiga: já chegaram antes (não mexe no estoque)",
                ).forEach { (v, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = s.received == v, role = Role.RadioButton, onClick = { vm.update { it.copy(received = v) } })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.received == v, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (s.received) {
                    Text(
                        "Use para notas de antes, de baterias que já estão no estoque: coloque só os boletos que faltam pagar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = s.note,
                onValueChange = { v -> vm.update { it.copy(note = v.take(300)) } },
                label = { Text("Observação (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    assigning?.let { key ->
        ProductPickerDialog(
            title = "Qual bateria do estoque é esta?",
            products = products,
            onPick = {
                vm.assignProduct(key, it)
                assigning = null
            },
            onDismiss = { assigning = null },
        )
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

/** Tipos de arquivo aceitos ao importar a nota (PDF ou XML; alguns apps mandam XML sem tipo). */
private val NFE_TYPES = arrayOf("application/pdf", "text/xml", "application/xml", "application/octet-stream")

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** O que foi lido do arquivo e o que conferir. */
@Composable
private fun ImportResultCard(r: ImportResult) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Text("✓ Lido do ${r.source}", style = MaterialTheme.typography.titleSmall, color = profitColor())
            if (r.found.isNotEmpty()) Text(r.found.joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
            if (r.warnings.isEmpty()) {
                Text(
                    "Tudo encontrado. Confira os dados abaixo e toque em \"Lançar nota\".",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text("Confira:", style = MaterialTheme.typography.labelLarge, color = warningColor(), modifier = Modifier.padding(top = 8.dp))
                r.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium, color = warningColor()) }
            }
        }
    }
}

/** Chegada das baterias: quantas vieram de cada modelo e se entram no estoque. */
@Composable
private fun ReceiveDialog(inv: InvoiceWithBills, onConfirm: (String, List<Int>, Boolean) -> Unit, onDismiss: () -> Unit) {
    val items = inv.invoice.items
    val qty = remember { mutableStateListOf(*items.map { it.quantity }.toTypedArray()) }
    var toStock by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("As baterias chegaram?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Confira quantas chegaram de cada uma (se faltou alguma, diminua).", style = MaterialTheme.typography.bodyMedium)
                items.forEachIndexed { k, item ->
                    Column {
                        Text("${item.model} (na nota: ${item.quantity})", style = MaterialTheme.typography.titleSmall)
                        QuantityStepper(qty[k], { qty[k] = it }, min = 0, max = item.quantity * 2)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = toStock, role = Role.Switch, onValueChange = { toStock = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (inv.bills.isEmpty()) "Dar entrada no estoque (bonificação: custo R$ 0)" else "Dar entrada no estoque com o custo da nota", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = toStock, onCheckedChange = null)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Observação (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note, qty.toList(), toStock) }) { Text("Confirmar chegada") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
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
