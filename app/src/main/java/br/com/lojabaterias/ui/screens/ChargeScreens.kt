package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.ChargeService
import br.com.lojabaterias.data.ChargeStatus
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateTimeSelector
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PaymentMethodChips
import br.com.lojabaterias.ui.components.PaymentMethodDialog
import br.com.lojabaterias.ui.components.PhoneActions
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.ChargeDetailViewModel
import br.com.lojabaterias.ui.viewmodel.ChargeFormViewModel
import br.com.lojabaterias.ui.viewmodel.ChargeTab
import br.com.lojabaterias.ui.viewmodel.ChargesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

// ----------------------------------------------------------------------------- Lista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargesScreen(onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { ChargesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Baterias na carga") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Receber bateria") },
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
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Na loja", style = MaterialTheme.typography.labelMedium)
                            Text(state.openCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Empréstimos", style = MaterialTheme.typography.labelMedium)
                            Text(state.loansOut.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1.4f)) {
                            Text("A receber", style = MaterialTheme.typography.labelMedium)
                            Text(
                                Money.format(state.unpaidOpenTotal),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (state.unpaidOpenTotal > 0) warningColor() else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ChargeTab.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = state.tab == t,
                            onClick = { vm.setTab(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, ChargeTab.entries.size),
                        ) { Text(t.label) }
                    }
                }
            }
            item { SearchField(value = state.query, onValueChange = vm::setQuery, placeholder = "Buscar cliente ou telefone...") }
            if (!state.loading && state.list.isEmpty()) {
                item { EmptyState("Nenhuma bateria aqui.\nToque em \"Receber bateria\" para registrar.") }
            }
            items(state.list, key = { it.id }) { c -> ChargeRow(c) { onOpen(c.id) } }
        }
    }
}

@Composable
private fun ChargeRow(c: ChargeService, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.customerName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Recebida ${Periods.formatDateTime(c.receivedAt)}" +
                        (c.batteryDescription.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c.hasLoan) {
                    Text(
                        "Emprestada: ${c.loanModel}" + if (c.status == ChargeStatus.DELIVERED) " (devolvida)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (c.isOpen) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(c.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (c.paid) "PAGO" else "NÃO PAGO",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (c.paid) profitColor() else dangerColor(),
                )
                Text(ChargeStatus.label(c.status), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ----------------------------------------------------------------------- Formulário

@Composable
fun ChargeFormScreen(chargeId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "chargeform-$chargeId") { ChargeFormViewModel(it.repository, chargeId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var pickLoan by remember { mutableStateOf(false) }

    LaunchedEffect(s.done) { if (s.done) onDone() }

    SubScreen(
        title = if (chargeId == null) "Receber bateria para carga" else "Editar carga",
        onBack = onBack,
        bottomBar = {
            if (!s.loading) {
                BottomActionBar {
                    PrimaryActionButton(
                        text = if (s.saving) "Salvando..." else if (chargeId == null) "Registrar recebimento" else "Salvar",
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
            SectionTitle("Cliente")
            OutlinedTextField(
                value = s.customerName,
                onValueChange = { v -> vm.update { it.copy(customerName = v.take(80)) } },
                label = { Text("Nome do cliente") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.phone,
                onValueChange = { v -> vm.update { it.copy(phone = v.filter { c -> c.isDigit() || c in " ()-+" }.take(20)) } },
                label = { Text("Telefone (WhatsApp)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.batteryDescription,
                onValueChange = { v -> vm.update { it.copy(batteryDescription = v.take(80)) } },
                label = { Text("Bateria do cliente (opcional)") },
                placeholder = { Text("Ex.: Moura 60Ah") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Recebimento")
            DateTimeSelector(millis = s.receivedAt, onChange = { v -> vm.update { it.copy(receivedAt = v) } })
            MoneyField(value = s.price, onValueChange = { v -> vm.update { it.copy(price = v) } }, label = "Valor cobrado pela carga")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Já foi pago?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = s.paid, onCheckedChange = { v -> vm.update { it.copy(paid = v) } })
            }
            if (s.paid) PaymentMethodChips(selected = s.method, onSelect = { m -> vm.update { it.copy(method = m) } })

            SectionTitle("Empréstimo de bateria da loja")
            if (s.isEdit) {
                Text(
                    s.existingLoanModel?.let { "Bateria emprestada: $it (o empréstimo não pode ser alterado aqui)" }
                        ?: "Sem empréstimo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Emprestou uma bateria da loja?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = s.loan, onCheckedChange = { v -> vm.update { it.copy(loan = v) } })
                }
                if (s.loan) {
                    OutlinedButton(onClick = { pickLoan = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(s.loanProduct?.let { "Emprestada: ${it.model} (trocar)" } ?: "Escolher a bateria emprestada")
                    }
                    Text(
                        "A bateria emprestada sai do estoque e volta automaticamente quando a carga for entregue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = s.note,
                onValueChange = { v -> vm.update { it.copy(note = v.take(200)) } },
                label = { Text("Observação (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickLoan) {
        ProductPickerDialog(
            title = "Bateria emprestada",
            products = products,
            onlyInStock = true,
            onPick = { p -> vm.update { it.copy(loanProduct = p) }; pickLoan = false },
            onDismiss = { pickLoan = false },
        )
    }
}

// --------------------------------------------------------------------------- Detalhe

@Composable
fun ChargeDetailScreen(chargeId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "charge-$chargeId") { ChargeDetailViewModel(it.repository, chargeId) }
    val loaded by vm.charge.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var askPay by remember { mutableStateOf(false) }
    var askDeliver by remember { mutableStateOf(false) }
    var askDeliverPay by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    SubScreen(
        title = "Bateria na carga",
        onBack = onBack,
        actions = { TextButton(onClick = onEdit) { Text("Editar") } },
    ) { inner ->
        val data = loaded
        val c = data?.value
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            c == null -> Box(Modifier.padding(inner)) { EmptyState("Registro não encontrado.") }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.customerName, style = MaterialTheme.typography.headlineSmall)
                        if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyLarge)
                        PhoneActions(c.phone, Modifier.padding(top = 8.dp))
                    }
                }
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        InfoRow("Situação", ChargeStatus.label(c.status))
                        InfoRow("Recebida em", Periods.formatDateTime(c.receivedAt))
                        c.deliveredAt?.let { InfoRow("Entregue em", Periods.formatDateTime(it)) }
                        if (c.batteryDescription.isNotBlank()) InfoRow("Bateria do cliente", c.batteryDescription)
                        InfoRow("Valor", Money.format(c.price), bold = true)
                        InfoRow(
                            "Pagamento",
                            if (c.paid) "Pago" + (c.paymentMethod?.let { " (${PaymentMethod.fromName(it).label})" } ?: "") else "Não pago",
                            valueColor = if (c.paid) profitColor() else dangerColor(),
                        )
                        InfoRow(
                            "Empréstimo",
                            c.loanModel?.let { it + if (c.loanReturnMovementId != null) " (devolvida)" else " (com o cliente)" } ?: "Não",
                        )
                        c.note?.let { InfoRow("Observação", it) }
                    }
                }
                if (c.isOpen) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(text = "Entregar ao cliente", onClick = { askDeliver = true })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!c.paid) {
                            FilledTonalButton(onClick = { askPay = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                                Text("Registrar pagamento")
                            }
                        }
                        if (c.status == ChargeStatus.IN_SHOP) {
                            FilledTonalButton(onClick = vm::markReady, modifier = Modifier.weight(1f).height(52.dp)) {
                                Text("Marcar pronta")
                            }
                        }
                    }
                } else if (!c.paid) {
                    FilledTonalButton(onClick = { askPay = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Registrar pagamento")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { askDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Excluir registro") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val c = loaded?.value
    if (askPay) {
        PaymentMethodDialog("Forma de pagamento", onPick = { vm.markPaid(it); askPay = false }, onDismiss = { askPay = false })
    }
    if (askDeliver && c != null) {
        ConfirmDialog(
            title = "Entregar ao cliente?",
            text = buildString {
                append("A bateria de ${c.customerName} será marcada como entregue.")
                if (c.hasLoan) append(" A bateria emprestada (${c.loanModel}) volta para o estoque.")
                if (!c.paid) append(" A carga ainda não foi paga: na próxima etapa informe se recebeu agora.")
            },
            confirmLabel = "Entregar",
            onConfirm = {
                askDeliver = false
                if (c.paid) vm.deliver(null) else askDeliverPay = true
            },
            onDismiss = { askDeliver = false },
        )
    }
    if (askDeliverPay) {
        AlertDialog(
            onDismissRequest = { askDeliverPay = false },
            title = { Text("O cliente pagou agora?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escolha a forma de pagamento:")
                    PaymentMethod.entries.forEach { m ->
                        OutlinedButton(
                            onClick = { vm.deliver(m); askDeliverPay = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pagou: ${m.label}") }
                    }
                    TextButton(
                        onClick = { vm.deliver(null); askDeliverPay = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Entregar sem receber (fica a receber)", color = dangerColor()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askDeliverPay = false }) { Text("Voltar") } },
        )
    }
    if (askDelete) {
        ConfirmDialog(
            title = "Excluir registro?",
            text = "O registro de carga será apagado. Se havia bateria emprestada, o estoque é corrigido.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { askDelete = false; vm.delete(onBack) },
            onDismiss = { askDelete = false },
        )
    }
}
