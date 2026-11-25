package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.BonusCost
import br.com.lojabaterias.data.InvoiceItem
import br.com.lojabaterias.data.InvoiceWithBills
import br.com.lojabaterias.data.bonusFor
import br.com.lojabaterias.data.items
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.HeroStat
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.Segmented
import br.com.lojabaterias.ui.components.StatusPill
import br.com.lojabaterias.ui.components.TextInput
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.BillRow
import br.com.lojabaterias.ui.viewmodel.ImportResult
import br.com.lojabaterias.ui.viewmodel.InvoiceDetailViewModel
import br.com.lojabaterias.ui.viewmodel.InvoiceFormViewModel
import br.com.lojabaterias.ui.viewmodel.InvoiceTab
import br.com.lojabaterias.ui.viewmodel.InvoicesState
import br.com.lojabaterias.ui.viewmodel.InvoicesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import br.com.lojabaterias.ui.viewmodel.purchaseOptions
import br.com.lojabaterias.ui.viewmodel.suggestedPurchase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Distribuidoras mais usadas (atalhos no formulário da nota). */
private val SUPPLIERS = listOf("Heliar do Rio", "Oeste Rio Distribuidora Moura", "PCR Baterias Baterax", "Barra Nota 10")

// ------------------------------------------------------------------ Lista

@Composable
fun InvoicesScreen(onNew: () -> Unit, onImport: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { InvoicesViewModel(it.repository) }
    val s by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmPay by remember { mutableStateOf<BillRow?>(null) }
    var choosing by remember { mutableStateOf(false) }

    MainPage(fabLabel = "Nova nota", onFab = { choosing = true }) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 12.dp, bottom = FabSpace),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Segmented(
                    options = InvoiceTab.entries.map { it.label },
                    selected = s.tab.ordinal,
                    onSelect = { vm.setTab(InvoiceTab.entries[it]) },
                )
            }
            item {
                when {
                    s.tab == InvoiceTab.NOTES -> NotesSummaryCard(s)
                    s.showPaid -> PaidSummaryCard(s)
                    else -> DebtCard(s)
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
                        item { EmptyState(if (s.showPaid) "Nenhum boleto pago." else "Nenhum boleto a pagar.") }
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
                        item { EmptyState("Nenhuma nota lançada.") }
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
    confirmPay?.let { row ->
        ConfirmDialog(
            title = "Boleto pago?",
            text = "Nota ${row.invoice.number} · ${row.index}/${row.count} · ${Money.format(row.bill.amount)}",
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
private fun DebtCard(s: InvoicesState) {
    val d = s.debt
    HeroCard(label = "Devemos aos fornecedores", value = Money.format(d.open)) {
        Row(Modifier.padding(top = 12.dp)) {
            HeroStat("Vencidos", if (d.overdueCount == 0) "—" else "${d.overdueCount} · ${Money.format(d.overdue)}", Modifier.weight(1f))
            HeroStat("Em 7 dias", if (d.dueSoonCount == 0) "—" else "${d.dueSoonCount} · ${Money.format(d.dueSoon)}", Modifier.weight(1f))
        }
        if (s.waitingCount > 0) {
            Text(
                "${s.waitingCount} nota${plural(s.waitingCount)} aguardando chegada",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun PaidSummaryCard(s: InvoicesState) {
    HeroCard(label = "Já pagamos", value = Money.format(s.paidBillsTotal)) {
        Row(Modifier.padding(top = 12.dp)) {
            HeroStat("Boletos", s.paidBillsCount.toString(), Modifier.weight(1f))
            HeroStat("Neste mês", Money.format(s.paidThisMonth), Modifier.weight(1f))
        }
    }
}

@Composable
private fun NotesSummaryCard(s: InvoicesState) {
    HeroCard(label = "Notas pagas", value = Money.format(s.paidNotesTotal)) {
        Row(Modifier.padding(top = 12.dp)) {
            HeroStat("Em aberto", s.openNotesCount.toString(), Modifier.weight(1f))
            HeroStat("Aguardando", s.waitingCount.toString(), Modifier.weight(1f))
            if (s.bonusCount > 0) HeroStat("Bonificações", s.bonusCount.toString(), Modifier.weight(1f))
        }
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"

/** "Vence hoje", "Vence em 3 dias", "Venceu há 2 dias". */
private fun dueLabel(due: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days == 0L -> "vence hoje"
        days == 1L -> "vence amanhã"
        days > 1 -> "vence em $days dias"
        days == -1L -> "venceu ontem"
        else -> "venceu há ${-days} dias"
    }
}

@Composable
private fun dueColor(isPaid: Boolean, due: LocalDate, today: LocalDate): Color = when {
    isPaid -> profitColor()
    due.isBefore(today) -> dangerColor()
    !due.isAfter(today.plusDays(7)) -> warningColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun BillCard(row: BillRow, today: LocalDate, onOpen: () -> Unit, onPay: () -> Unit, onUndo: () -> Unit) {
    val b = row.bill
    val due = Periods.toLocalDateTime(b.dueDate).toLocalDate()
    val color = dueColor(b.isPaid, due, today)
    AppCard(onClick = onOpen) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(Money.format(b.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Nota ${row.invoice.number}" + (if (row.invoice.supplier.isNotBlank()) " · ${row.invoice.supplier}" else "") +
                        " · ${row.index}/${row.count}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (b.isPaid) "Pago em ${Periods.formatDate(b.paidAt!!)}" else "${Periods.formatDate(b.dueDate)} · ${dueLabel(due, today)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (b.isPaid) {
                TextButton(onClick = onUndo) { Text("Desfazer") }
            } else {
                FilledTonalButton(onClick = onPay, shape = MaterialTheme.shapes.medium) { Text("Paguei") }
            }
        }
    }
}

@Composable
private fun InvoiceCard(inv: InvoiceWithBills, onClick: () -> Unit) {
    val i = inv.invoice
    val units = i.items.sumOf { it.quantity }
    PanelCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nota ${i.number}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (inv.isBonus) StatusPill("bonificação", profitColor(), Modifier.padding(end = 8.dp))
            Text(Money.format(i.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            listOfNotNull(i.supplier.takeIf { it.isNotBlank() }, Periods.formatDate(i.issueDate), "$units bat.").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NoteProgress(inv, Modifier.padding(top = 12.dp))
        val (next, done) = nextStep(inv, LocalDate.now())
        Text(
            next,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) profitColor() else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
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
                    Modifier.weight(1f).padding(top = 9.dp).height(2.dp)
                        .background(if (done) doneColor else todoColor),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (done) doneColor else todoColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done) "✓" else "${k + 1}",
                        style = MaterialTheme.typography.labelSmall,
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
    if (!inv.invoice.isReceived) return "Aguardando as baterias chegarem" to false
    if (inv.isBonus) return "Concluída" to true
    val bills = inv.sortedBills
    val next = bills.firstOrNull { !it.isPaid } ?: return "Concluída" to true
    val due = Periods.toLocalDateTime(next.dueDate).toLocalDate()
    return "Parcela ${bills.indexOf(next) + 1}/${bills.size} · ${Money.format(next.amount)} · ${dueLabel(due, today)}" to false
}

/** Nova nota: importar o arquivo (preenche sozinho) ou digitar à mão. */
@Composable
private fun NewNoteDialog(onImport: () -> Unit, onManual: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova nota fiscal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PanelCard(onClick = onImport, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Importar PDF ou XML", style = MaterialTheme.typography.titleMedium)
                    Hint("O app preenche tudo", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                PanelCard(onClick = onManual) {
                    Text("Digitar à mão", style = MaterialTheme.typography.titleMedium)
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
    val all by vm.allInvoices.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var askReceive by remember { mutableStateOf(false) }
    var askUndo by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askPay by remember { mutableStateOf<Long?>(null) }

    SubScreen(
        title = loaded?.value?.invoice?.number?.let { "Nota $it" } ?: "Nota fiscal",
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
                    .padding(horizontal = Gaps.screen),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val i = inv.invoice
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Money.format(i.total), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                listOfNotNull(i.supplier.takeIf { it.isNotBlank() }, Periods.formatDate(i.issueDate)).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (inv.isBonus) StatusPill("bonificação", profitColor())
                    }
                    if (!inv.isBonus && inv.openAmount > 0) {
                        InfoRow("Falta pagar", Money.format(inv.openAmount), valueColor = warningColor(), modifier = Modifier.padding(top = 6.dp))
                    }
                    i.note?.let { Hint(it, Modifier.padding(top = 4.dp)) }
                    NoteProgress(inv, Modifier.padding(top = 12.dp))
                }

                // Próximo passo, com o botão certo
                val nextBill = inv.sortedBills.firstOrNull { !it.isPaid }
                when {
                    !i.isReceived -> PrimaryActionButton("As baterias chegaram", onClick = { askReceive = true })
                    nextBill != null && !inv.isBonus -> PrimaryActionButton(
                        "Paguei a parcela ${inv.sortedBills.indexOf(nextBill) + 1} · ${Money.format(nextBill.amount)}",
                        onClick = { askPay = nextBill.id },
                    )
                }

                SectionHeader("Baterias")
                PanelCard {
                    val items = i.items
                    items.forEach { line ->
                        InfoRow("${line.quantity}× ${line.model}", "${Money.format(line.unitCost)} cada")
                        val got = line.received
                        if (i.isReceived && got != null && got != line.quantity) {
                            Hint("Chegaram $got de ${line.quantity}", color = warningColor())
                        }
                    }
                    val extras = i.total - items.sumOf { it.subtotal }
                    if (extras != 0L) InfoRow("Frete e outros", Money.format(extras))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow("Total", Money.format(i.total), bold = true)
                    if (i.isReceived) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                "Chegaram em ${i.receivedAt?.let { Periods.formatDate(it) } ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = profitColor(),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { askUndo = true }) { Text("Desfazer") }
                        }
                        i.receivedNote?.let { Hint(it) }
                    }
                }

                if (inv.isBonus) {
                    val purchase = all.firstOrNull { it.invoice.id == i.bonusFor }
                    SectionHeader("Custo com a bonificação")
                    PanelCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        if (purchase == null) {
                            Hint("Sem nota de compra ligada. Toque em Editar para escolher.", color = warningColor())
                        } else {
                            BonusCostLines(purchase, bonusesOf(purchase, all), highlight = i.id)
                        }
                    }
                } else {
                    val linked = bonusesOf(inv, all)
                    if (linked.isNotEmpty()) {
                        SectionHeader("Custo com a bonificação")
                        PanelCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            BonusCostLines(inv, linked, highlight = null)
                        }
                    }
                    SectionHeader("Boletos")
                    val today = LocalDate.now()
                    inv.sortedBills.forEachIndexed { k, b ->
                        val due = Periods.toLocalDateTime(b.dueDate).toLocalDate()
                        AppCard {
                            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("${k + 1}/${inv.bills.size} · ${Money.format(b.amount)}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (b.isPaid) "Pago em ${Periods.formatDate(b.paidAt!!)}" else "${Periods.formatDate(b.dueDate)} · ${dueLabel(due, today)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = dueColor(b.isPaid, due, today),
                                    )
                                }
                                if (b.isPaid) {
                                    TextButton(onClick = { vm.setPaid(b.id, false) }) { Text("Desfazer") }
                                } else {
                                    FilledTonalButton(onClick = { askPay = b.id }, shape = MaterialTheme.shapes.medium) { Text("Paguei") }
                                }
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
            text = "As baterias que entraram por esta nota saem do estoque.",
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
            text = "Marca como pago hoje.",
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
            text = "A nota e os boletos serão apagados. Baterias que entraram por ela saem do estoque.",
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
    val all by vm.allInvoices.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    LaunchedEffect(s.done) { if (s.done) onDone() }
    var picking by remember { mutableStateOf(false) }
    var choosingPurchase by remember { mutableStateOf(false) }
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
        title = if (invoiceId == null) "Nova nota" else "Editar nota",
        onBack = onBack,
        bottomBar = {
            if (!s.loading) {
                BottomActionBar {
                    if (s.bills.isNotEmpty() && s.billsDifference != 0L) {
                        Text(
                            if (s.billsDifference > 0) "Faltam ${Money.format(s.billsDifference)} nos boletos"
                            else "Boletos passam ${Money.format(-s.billsDifference)} do total",
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
                .padding(horizontal = Gaps.screen),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!s.isEdit) {
                FilledTonalButton(
                    onClick = { importer.launch(NFE_TYPES) },
                    enabled = !s.importing,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (s.importing) "Lendo a nota..." else "Importar PDF ou XML") }
            }
            s.importResult?.let { ImportResultCard(it) }

            SectionHeader("Dados")
            OutlinedTextField(
                value = s.number,
                onValueChange = { v -> vm.update { it.copy(number = v.take(30)) } },
                label = { Text("Número") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            TextInput(
                value = s.supplier,
                onValueChange = { v -> vm.update { it.copy(supplier = v) } },
                label = "Fornecedor",
                maxLength = 60,
                capitalization = KeyboardCapitalization.Words,
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
            DateButton("Data", s.issueDate) { d -> vm.update { it.copy(issueDate = d) } }

            SectionHeader("Baterias")
            s.items.forEach { item ->
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (item.unmatched != null) "Não reconhecida" else item.model,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (item.unmatched != null) warningColor() else Color.Unspecified,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.removeItem(item.key) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remover ${item.model}")
                        }
                    }
                    if (item.unmatched != null) {
                        Hint("Na nota: ${item.unmatched}")
                        FilledTonalButton(onClick = { assigning = item.key }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("Escolher do estoque")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Quantidade", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        QuantityStepper(item.quantity, { q -> vm.updateItem(item.key) { it.copy(quantity = q) } }, max = 999)
                    }
                    EqualRow(Modifier.padding(top = 8.dp)) {
                        MoneyField(
                            value = item.grossTotal,
                            onValueChange = { v -> vm.updateItem(item.key) { it.copy(grossTotal = v) } },
                            label = "Subtotal",
                            modifier = Modifier.weight(1f),
                        )
                        MoneyField(
                            value = item.discountTotal,
                            onValueChange = { v -> vm.updateItem(item.key) { it.copy(discountTotal = v) } },
                            label = "Desconto",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "${Money.format(item.unitCost)} cada",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            OutlinedButton(onClick = { picking = true }, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Adicionar bateria", modifier = Modifier.padding(start = 8.dp))
            }
            MoneyField(
                value = s.extras,
                onValueChange = { v -> vm.update { it.copy(extras = v) } },
                label = "Frete e outros (opcional)",
            )
            PanelCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                InfoRow("Baterias", "${s.units} · ${Money.format(s.itemsTotal)}")
                if (s.extras > 0) InfoRow("Outros", Money.format(s.extras))
                InfoRow("Total da nota", Money.format(s.total), bold = true)
            }

            SectionHeader("Boletos")
            if (s.bills.isEmpty()) {
                val purchase = suggestedPurchase(s, all)
                PanelCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Sem boletos = bonificação", style = MaterialTheme.typography.titleSmall)
                    if (purchase == null) {
                        Hint("Escolha a nota de compra que ela abate.", color = warningColor())
                    } else {
                        Text(
                            "Abate a nota ${purchase.invoice.number}" +
                                (if (purchase.invoice.supplier.isNotBlank()) " (${purchase.invoice.supplier})" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        val draft = s.items.map { InvoiceItem.fromTotals(it.model, it.quantity, it.grossTotal, it.discountTotal, it.productId) }
                        BonusCostLines(purchase, bonusesOf(purchase, all).filter { it.invoice.id != s.id }, highlight = null, draft = draft)
                    }
                    TextButton(onClick = { choosingPurchase = true }) {
                        Text(if (purchase == null) "Escolher nota de compra" else "Trocar nota de compra")
                    }
                }
            }
            BillGenerator(enabled = s.total > 0, default = s.issueDate.plusDays(30)) { count, first, interval ->
                vm.generateBills(count, first, interval)
            }
            s.bills.sortedBy { it.dueDate }.forEachIndexed { k, b ->
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Parcela ${k + 1}" + if (b.isPaid) " · paga" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (b.isPaid) profitColor() else Color.Unspecified,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.removeBill(b.key) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remover parcela ${k + 1}")
                        }
                    }
                    EqualRow {
                        DateButton("Vence", b.dueDate, Modifier.weight(1f)) { d -> vm.updateBill(b.key) { it.copy(dueDate = d) } }
                    }
                    MoneyField(
                        value = b.amount,
                        onValueChange = { v -> vm.updateBill(b.key) { it.copy(amount = v) } },
                        label = "Valor",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (s.bills.isNotEmpty()) {
                TextButton(onClick = vm::addBill) { Text("+ Outro boleto") }
            }

            if (!s.isEdit) {
                SectionHeader("As baterias já chegaram?")
                listOf(
                    false to "Ainda não",
                    true to "Já estão no estoque (nota antiga)",
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
            }
            OutlinedTextField(
                value = s.note,
                onValueChange = { v -> vm.update { it.copy(note = v.take(300)) } },
                label = { Text("Observação (opcional)") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (choosingPurchase) {
        PurchasePickerDialog(
            options = purchaseOptions(s, all),
            onPick = { id ->
                vm.update { it.copy(bonusFor = id) }
                choosingPurchase = false
            },
            onDismiss = { choosingPurchase = false },
        )
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

/** Bonificações ligadas a uma nota de compra. */
private fun bonusesOf(purchase: InvoiceWithBills, all: List<InvoiceWithBills>): List<InvoiceWithBills> =
    all.filter { it.isBonus && it.invoice.bonusFor == purchase.invoice.id && it.invoice.id != purchase.invoice.id }

/**
 * Custo de cada bateria com a bonificação: as da compra (antes → depois) e as ganhas.
 * [draft] é a bonificação que está sendo lançada; [highlight] marca a bonificação aberta na tela.
 */
@Composable
private fun BonusCostLines(
    purchase: InvoiceWithBills,
    bonuses: List<InvoiceWithBills>,
    highlight: Long?,
    draft: List<InvoiceItem>? = null,
) {
    val bonusItems = bonuses.map { b -> b.invoice.items.map { if (b.invoice.isReceived) it else it.copy(received = null) } } +
        listOfNotNull(draft)
    val costs = BonusCost.spread(purchase.invoice.items, bonusItems)
    purchase.invoice.items.zip(costs.purchase).forEach { (line, cost) ->
        InfoRow("${line.quantity}× ${line.model}", "${Money.format(line.unitCost)} → ${Money.format(cost)}")
    }
    val labels: List<Pair<Long?, String>> =
        bonuses.map { Pair<Long?, String>(it.invoice.id, it.invoice.number) } + if (draft != null) listOf(Pair<Long?, String>(null, "esta")) else emptyList()
    bonusItems.zip(costs.bonuses).forEachIndexed { k, (lines, list) ->
        val (id, number) = labels[k]
        lines.zip(list).forEach { (line, cost) ->
            InfoRow(
                "${line.received ?: line.quantity}× ${line.model} (${if (number == "esta") "esta" else "nota $number"})",
                Money.format(cost),
                bold = id == highlight || id == null,
            )
        }
    }
}

/** Escolher a nota de compra que a bonificação abate. */
@Composable
private fun PurchasePickerDialog(options: List<InvoiceWithBills>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Qual nota ela abate?") },
        text = {
            if (options.isEmpty()) {
                Text("Nenhuma nota de compra lançada.")
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { o ->
                        val i = o.invoice
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(i.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text("Nota ${i.number} · ${Money.format(i.total)}", style = MaterialTheme.typography.titleSmall)
                            Hint(
                                (if (i.supplier.isNotBlank()) "${i.supplier} · " else "") + Periods.formatDate(i.issueDate) + " · " +
                                    i.items.joinToString { "${it.quantity}× ${it.model}" },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

/** Tipos de arquivo aceitos ao importar a nota (PDF ou XML; alguns apps mandam XML sem tipo). */
private val NFE_TYPES = arrayOf("application/pdf", "text/xml", "application/xml", "application/octet-stream")

/** O que foi lido do arquivo e o que conferir. */
@Composable
private fun ImportResultCard(r: ImportResult) {
    PanelCard {
        Text("Lido do ${r.source}", style = MaterialTheme.typography.titleSmall, color = profitColor())
        if (r.found.isNotEmpty()) Hint(r.found.joinToString(" · "))
        r.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium, color = warningColor()) }
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
        title = { Text("Chegada das baterias") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { k, item ->
                    Column {
                        Text("${item.model} (nota: ${item.quantity})", style = MaterialTheme.typography.titleSmall)
                        QuantityStepper(qty[k], { qty[k] = it }, min = 0, max = item.quantity * 2)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = toStock, role = Role.Switch, onValueChange = { toStock = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dar entrada no estoque", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = toStock, onCheckedChange = null)
                }
                TextInput(note, { note = it }, "Observação (opcional)", maxLength = 200)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note, qty.toList(), toStock) }) { Text("Confirmar") } },
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
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantos boletos?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            QuantityStepper(count, { count = it }, max = 24)
        }
        DateButton("1º vencimento", first, Modifier.padding(top = 8.dp)) { first = it }
        if (count > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(7, 14, 15, 21, 28, 30).forEach { d ->
                    FilterChip(selected = interval == d, onClick = { interval = d }, label = { Text("$d dias") })
                }
            }
        }
        FilledTonalButton(
            onClick = { onGenerate(count, first, interval) },
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(if (count == 1) "Criar boleto" else "Dividir em $count")
        }
    }
}
