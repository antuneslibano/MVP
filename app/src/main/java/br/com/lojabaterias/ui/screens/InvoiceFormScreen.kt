package br.com.lojabaterias.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.InvoiceWithBills
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.StatusPill
import br.com.lojabaterias.ui.components.TextInput
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.InvoiceFormState
import br.com.lojabaterias.ui.viewmodel.InvoiceFormViewModel
import br.com.lojabaterias.ui.viewmodel.ItemDraft
import br.com.lojabaterias.ui.viewmodel.appViewModel
import br.com.lojabaterias.ui.viewmodel.purchaseOptions
import br.com.lojabaterias.ui.viewmodel.suggestedPurchase

/** Os passos da nota: uma pergunta por tela. */
private enum class NoteStep(val label: String, val question: String, val help: String) {
    START("Começar", "Como você quer lançar a nota?", "Com o arquivo, o app lê e preenche tudo. Você só confere."),
    DATA("Dados", "De quem é a nota?", "Fica no topo da nota: o número, quem vendeu e a data de emissão."),
    ITEMS(
        "Baterias", "Quais baterias vieram?",
        "Para cada modelo: quantidade, valor da linha e desconto. Se alguma veio de brinde na mesma nota, marque \"Bonificação\".",
    ),
    PAYMENT("Pagamento", "Como esta nota vai ser paga?", "Boletos aparecem na aba Notas, com aviso perto do vencimento."),
    ARRIVAL("Chegada", "As baterias já chegaram na loja?", "Quando chegarem, elas entram no estoque com o custo desta nota."),
    REVIEW("Conferir", "Está tudo certo?", "Toque em Alterar para corrigir qualquer parte."),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceFormScreen(invoiceId: Long?, onDone: () -> Unit, onBack: () -> Unit, autoImport: Boolean = false) {
    val vm = appViewModel(key = "invoiceform-$invoiceId") { InvoiceFormViewModel(it.repository, invoiceId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val all by vm.allInvoices.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    LaunchedEffect(s.done) { if (s.done) onDone() }

    val steps = if (invoiceId == null) NoteStep.entries.toList()
    else listOf(NoteStep.DATA, NoteStep.ITEMS, NoteStep.PAYMENT, NoteStep.REVIEW)
    var index by rememberSaveable { mutableIntStateOf(if (invoiceId == null) 0 else steps.lastIndex) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    fun goTo(target: NoteStep) { index = steps.indexOf(target).coerceAtLeast(0) }

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
    // Nota lida do arquivo: vai direto para a conferência
    LaunchedEffect(s.importResult) {
        if (s.importResult != null && step == NoteStep.START) goTo(NoteStep.REVIEW)
    }

    val purchase = if (s.billsMode == false) suggestedPurchase(s, all) else null
    val error = stepError(step, s, purchase, steps)
    BackHandler(enabled = index > 0 && invoiceId == null) { index-- }

    SubScreen(
        title = if (invoiceId == null) "Nova nota" else "Editar nota",
        onBack = { if (index > 0 && invoiceId == null) index-- else onBack() },
        bottomBar = {
            if (!s.loading && step != NoteStep.START) {
                BottomActionBar {
                    if (error != null) {
                        Text(
                            "Falta: $error",
                            color = if (step == NoteStep.REVIEW) dangerColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    EqualRow {
                        if (index > 0) {
                            OutlinedButton(
                                onClick = { index-- },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                            ) { Text("Voltar") }
                        }
                        Button(
                            onClick = { if (step == NoteStep.REVIEW) vm.save() else index++ },
                            enabled = error == null && !s.saving,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(2f).heightIn(min = 54.dp),
                        ) {
                            Text(
                                when {
                                    step != NoteStep.REVIEW -> "Continuar"
                                    s.saving -> "Salvando..."
                                    invoiceId == null -> "Lançar nota"
                                    else -> "Salvar"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepHeader(step, index, steps.size)
            when (step) {
                NoteStep.START -> StartStep(
                    importing = s.importing,
                    onImport = { importer.launch(NFE_TYPES) },
                    onManual = { index++ },
                )
                NoteStep.DATA -> DataStep(s, vm)
                NoteStep.ITEMS -> ItemsStep(s, vm, onAdd = { picking = true }, onAssign = { assigning = it })
                NoteStep.PAYMENT -> PaymentStep(s, vm, all, purchase, onChoosePurchase = { choosingPurchase = true })
                NoteStep.ARRIVAL -> ArrivalStep(s, vm)
                NoteStep.REVIEW -> ReviewStep(s, vm, purchase, showArrival = invoiceId == null, onEdit = { goTo(it) })
            }
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

/** O que falta para seguir (null = pode continuar). Na conferência, mostra o primeiro problema de qualquer passo. */
private fun stepError(step: NoteStep, s: InvoiceFormState, purchase: InvoiceWithBills?, steps: List<NoteStep>): String? = when (step) {
    NoteStep.DATA -> if (s.number.isBlank()) "digitar o número da nota" else null
    NoteStep.ITEMS -> {
        val unmatched = s.items.firstOrNull { it.unmatched != null }
        val noValue = s.items.firstOrNull { !it.bonus && it.subtotal <= 0 && s.billsMode != false }
        when {
            s.items.isEmpty() -> "adicionar as baterias da nota"
            unmatched != null -> "escolher do estoque a bateria \"${unmatched.unmatched}\""
            noValue != null && s.billsMode != false -> "o valor de ${noValue.model}"
            else -> null
        }
    }
    NoteStep.PAYMENT -> when {
        s.billsMode == null -> "escolher se tem boletos ou se é bonificação"
        s.billsMode == true && s.items.isNotEmpty() && s.items.all { it.bonus } -> "pelo menos uma bateria comprada (não bonificação)"
        s.billsMode == true && s.bills.isEmpty() -> "montar os boletos"
        s.billsMode == true && s.bills.any { it.amount <= 0 } -> "o valor de todos os boletos"
        s.billsMode == true && s.billsDifference > 0 -> "${Money.format(s.billsDifference)} nos boletos para fechar o total"
        s.billsMode == true && s.billsDifference < 0 -> "tirar ${Money.format(-s.billsDifference)}: os boletos passam do total"
        s.billsMode == false && purchase == null -> "escolher a nota de compra que a bonificação abate"
        s.billsMode == false && s.total <= 0 -> "o valor das baterias da bonificação"
        else -> null
    }
    NoteStep.REVIEW -> steps.filter { it != NoteStep.REVIEW }.firstNotNullOfOrNull { stepError(it, s, purchase, steps) }
    else -> null
}

@Composable
private fun StepHeader(step: NoteStep, index: Int, count: Int) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Passo ${index + 1} de $count · ${step.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        LinearProgressIndicator(
            progress = { (index + 1f) / count },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Text(step.question, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp))
        Hint(step.help, Modifier.padding(top = 2.dp))
    }
}

/** Cartão de escolha única (com bolinha). */
@Composable
private fun ChoiceCard(title: String, description: String, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.primaryContainer else scheme.surface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) StatusPill(badge, profitColor(), Modifier.padding(start = 8.dp))
                }
                Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ Passos

@Composable
private fun StartStep(importing: Boolean, onImport: () -> Unit, onManual: () -> Unit) {
    ChoiceCard(
        title = if (importing) "Lendo a nota..." else "Importar PDF ou XML",
        description = "Escolha o arquivo que o fornecedor mandou. O XML é lido sem erro.",
        selected = false,
        onClick = { if (!importing) onImport() },
        badge = "mais rápido",
    )
    ChoiceCard(
        title = "Digitar à mão",
        description = "Preencha olhando a nota em papel, passo a passo.",
        selected = false,
        onClick = onManual,
    )
    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataStep(s: InvoiceFormState, vm: InvoiceFormViewModel) {
    OutlinedTextField(
        value = s.number,
        onValueChange = { v -> vm.update { it.copy(number = v.take(30)) } },
        label = { Text("Número da nota") },
        placeholder = { Text("Ex.: 123456") },
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
    DateButton("Data de emissão", s.issueDate) { d -> vm.update { it.copy(issueDate = d) } }
}

@Composable
private fun ItemsStep(s: InvoiceFormState, vm: InvoiceFormViewModel, onAdd: () -> Unit, onAssign: (Long) -> Unit) {
    s.items.forEach { item -> ItemCard(item, canMarkBonus = s.billsMode != false, vm = vm, onAssign = { onAssign(item.key) }) }
    OutlinedButton(
        onClick = onAdd,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(if (s.items.isEmpty()) "Adicionar bateria" else "Adicionar outro modelo", modifier = Modifier.padding(start = 8.dp))
    }
    if (s.items.isNotEmpty()) {
        MoneyField(
            value = s.extras,
            onValueChange = { v -> vm.update { it.copy(extras = v) } },
            label = "Frete, impostos e outros (se tiver)",
        )
        PanelCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            val free = s.items.filter { it.bonus }.sumOf { it.quantity }
            InfoRow("Baterias", "${s.units}" + if (free > 0) " ($free de bonificação)" else "")
            if (s.extras > 0) InfoRow("Frete e outros", Money.format(s.extras))
            InfoRow("Total a pagar", Money.format(s.total), bold = true)
        }
        if (s.items.any { it.bonus }) {
            PanelCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Custo de cada bateria no estoque", style = MaterialTheme.typography.titleSmall)
                Hint(
                    "O que foi pago é dividido por todas, as compradas e as de bonificação.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                InNoteBonusLines(s.items.map { it.toInvoiceItem() })
            }
        }
    }
}

@Composable
private fun ItemCard(item: ItemDraft, canMarkBonus: Boolean, vm: InvoiceFormViewModel, onAssign: () -> Unit) {
    PanelCard(containerColor = if (item.bonus) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (item.bonus) "🎁 " else "") + if (item.unmatched != null) "Bateria não reconhecida" else item.model,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.unmatched != null) warningColor() else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.removeItem(item.key) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover ${item.model}")
            }
        }
        if (item.unmatched != null) {
            Hint("Na nota está escrito: ${item.unmatched}")
            FilledTonalButton(onClick = onAssign, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Escolher do estoque")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("Quantidade", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            QuantityStepper(item.quantity, { q -> vm.updateItem(item.key) { it.copy(quantity = q) } }, max = 999)
        }
        if (canMarkBonus) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Bonificação", style = MaterialTheme.typography.bodyLarge)
                    Hint("Veio de brinde nesta nota (não é paga)")
                }
                Switch(checked = item.bonus, onCheckedChange = { on -> vm.updateItem(item.key) { it.copy(bonus = on) } })
            }
        }
        if (item.bonus) {
            MoneyField(
                value = item.grossTotal,
                onValueChange = { v -> vm.updateItem(item.key) { it.copy(grossTotal = v) } },
                label = "Valor na nota (se tiver)",
                supportingText = "Só para dividir o custo. Sem valor, usa o preço das compradas.",
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            EqualRow(Modifier.padding(top = 8.dp)) {
                MoneyField(
                    value = item.grossTotal,
                    onValueChange = { v -> vm.updateItem(item.key) { it.copy(grossTotal = v) } },
                    label = "Valor da linha",
                    modifier = Modifier.weight(1f),
                )
                MoneyField(
                    value = item.discountTotal,
                    onValueChange = { v -> vm.updateItem(item.key) { it.copy(discountTotal = v) } },
                    label = "Desconto",
                    modifier = Modifier.weight(1f),
                )
            }
            if (item.subtotal > 0) {
                Text(
                    "${Money.format(item.subtotal)} ÷ ${item.quantity} = ${Money.format(item.unitCost)} cada",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PaymentStep(
    s: InvoiceFormState,
    vm: InvoiceFormViewModel,
    all: List<InvoiceWithBills>,
    purchase: InvoiceWithBills?,
    onChoosePurchase: () -> Unit,
) {
    ChoiceCard(
        title = "Com boletos",
        description = "A loja vai pagar esta nota, à vista ou em parcelas.",
        selected = s.billsMode == true,
        onClick = { vm.setBillsMode(true) },
    )
    ChoiceCard(
        title = "Bonificação",
        description = "A nota inteira veio de graça: nada a pagar. Ela baixa o custo de uma nota de compra.",
        selected = s.billsMode == false,
        onClick = { vm.setBillsMode(false) },
    )

    when (s.billsMode) {
        true -> {
            PanelCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                InfoRow("Total a pagar", Money.format(s.total), bold = true)
                if (s.bills.isNotEmpty()) {
                    val ok = s.billsDifference == 0L
                    InfoRow(
                        "Nos boletos",
                        Money.format(s.billsTotal) + if (ok) " ✓" else "",
                        valueColor = if (ok) profitColor() else warningColor(),
                    )
                }
            }
            Hint(if (s.bills.isEmpty()) "Escolha quantos boletos e o primeiro vencimento." else "Ajuste datas e valores se precisar.")
            BillGenerator(enabled = s.total > 0, default = s.issueDate.plusDays(30)) { count, first, interval ->
                vm.generateBills(count, first, interval)
            }
            s.bills.sortedBy { it.dueDate }.forEachIndexed { k, b ->
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Parcela ${k + 1} de ${s.bills.size}" + if (b.isPaid) " · paga" else "",
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
            if (s.bills.isNotEmpty()) TextButton(onClick = vm::addBill) { Text("+ Outro boleto") }
        }
        false -> {
            PanelCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Qual nota de compra ela abate?", style = MaterialTheme.typography.titleSmall)
                Hint(
                    "O valor pago na nota de compra passa a ser dividido também por estas baterias.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (purchase == null) {
                    Text(
                        "Nenhuma escolhida ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = warningColor(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Text(
                        "Nota ${purchase.invoice.number}" +
                            (if (purchase.invoice.supplier.isNotBlank()) " · ${purchase.invoice.supplier}" else "") +
                            " · ${Periods.formatDate(purchase.invoice.issueDate)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    val draft = s.items.map { it.copy(bonus = false).toInvoiceItem() }
                    BonusCostLines(purchase, bonusesOf(purchase, all).filter { it.invoice.id != s.id }, highlight = null, draft = draft)
                }
                FilledTonalButton(onClick = onChoosePurchase, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (purchase == null) "Escolher nota de compra" else "Trocar nota")
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun ArrivalStep(s: InvoiceFormState, vm: InvoiceFormViewModel) {
    ChoiceCard(
        title = "Ainda não chegaram",
        description = "Quando chegarem, abra a nota e toque em \"As baterias chegaram\". Elas entram no estoque.",
        selected = !s.received,
        onClick = { vm.update { it.copy(received = false) } },
    )
    ChoiceCard(
        title = "Já estão no estoque",
        description = "Nota antiga: as baterias já foram contadas. O estoque não muda.",
        selected = s.received,
        onClick = { vm.update { it.copy(received = true) } },
    )
}

@Composable
private fun ReviewStep(
    s: InvoiceFormState,
    vm: InvoiceFormViewModel,
    purchase: InvoiceWithBills?,
    showArrival: Boolean,
    onEdit: (NoteStep) -> Unit,
) {
    val warnings = s.importResult?.warnings.orEmpty()
    s.importResult?.let { r ->
        PanelCard(containerColor = if (warnings.isEmpty()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer) {
            Text("Lido do ${r.source}", style = MaterialTheme.typography.titleSmall, color = profitColor())
            if (warnings.isEmpty()) Hint("Tudo encontrado. Só confira.")
            warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodyMedium, color = warningColor()) }
        }
    }

    ReviewSection("Dados", onEdit = { onEdit(NoteStep.DATA) }) {
        Text(if (s.number.isBlank()) "Sem número" else "Nota ${s.number}", style = MaterialTheme.typography.titleMedium)
        Hint(listOf(s.supplier.ifBlank { "Sem fornecedor" }, s.issueDate.format(Periods.DATE)).joinToString(" · "))
    }

    ReviewSection("Baterias", onEdit = { onEdit(NoteStep.ITEMS) }) {
        if (s.items.isEmpty()) Hint("Nenhuma bateria.")
        val costs = if (s.items.any { it.bonus }) {
            br.com.lojabaterias.data.BonusCost.spreadNote(s.items.map { it.toInvoiceItem() }, emptyList()).purchase
        } else {
            s.items.map { it.unitCost }
        }
        s.items.zip(costs).forEach { (i, cost) ->
            InfoRow((if (i.bonus) "🎁 " else "") + "${i.quantity}× ${i.model}", "${Money.format(cost)} cada")
        }
        if (s.extras > 0) InfoRow("Frete e outros", Money.format(s.extras))
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow(if (s.billsMode == false) "Valor (bonificação)" else "Total a pagar", Money.format(s.total), bold = true)
    }

    ReviewSection("Pagamento", onEdit = { onEdit(NoteStep.PAYMENT) }) {
        when (s.billsMode) {
            true -> {
                if (s.bills.isEmpty()) Hint("Sem boletos ainda.")
                s.bills.sortedBy { it.dueDate }.forEachIndexed { k, b ->
                    InfoRow("${k + 1}ª · ${b.dueDate.format(Periods.DATE)}" + if (b.isPaid) " (paga)" else "", Money.format(b.amount))
                }
            }
            false -> Text(
                "Bonificação" + (purchase?.let { " · abate a nota ${it.invoice.number}" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
            )
            null -> Hint("Não escolhido.")
        }
    }

    if (showArrival) {
        ReviewSection("Chegada", onEdit = { onEdit(NoteStep.ARRIVAL) }) {
            Text(
                if (s.received) "Já estão no estoque (não mexe no estoque)" else "Ainda não chegaram",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    OutlinedTextField(
        value = s.note,
        onValueChange = { v -> vm.update { it.copy(note = v.take(300)) } },
        label = { Text("Observação (opcional)") },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReviewSection(title: String, onEdit: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit) { Text("Alterar") }
        }
        content()
    }
}
