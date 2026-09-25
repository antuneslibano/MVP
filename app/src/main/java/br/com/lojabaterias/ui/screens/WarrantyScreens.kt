package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.UsedDestination
import br.com.lojabaterias.data.WarrantyClaim
import br.com.lojabaterias.data.WarrantyStatus
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DatePickerModal
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PaymentMethodChips
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.WarrantiesViewModel
import br.com.lojabaterias.ui.viewmodel.WarrantyDetailViewModel
import br.com.lojabaterias.ui.viewmodel.WarrantyFormViewModel
import br.com.lojabaterias.ui.viewmodel.WarrantyTab
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** "há 3 meses", "há 12 dias". */
fun elapsedLabel(since: Long, now: Long = System.currentTimeMillis()): String {
    val days = TimeUnit.MILLISECONDS.toDays(now - since).coerceAtLeast(0)
    return when {
        days < 1 -> "hoje"
        days < 60 -> "há $days dia(s)"
        else -> "há ${days / 30} meses"
    }
}

// ----------------------------------------------------------------------------- Lista

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantiesScreen(onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { WarrantiesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmCollectAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garantias") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova troca / teste") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            ScrollableTabRow(
                selectedTabIndex = state.tab.ordinal,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                WarrantyTab.entries.forEach { t ->
                    val count = when (t) {
                        WarrantyTab.PICKUP -> state.pickupCount
                        WarrantyTab.FACTORY -> state.factoryCount
                        WarrantyTab.USED -> state.usedCount
                        WarrantyTab.HISTORY -> null
                    }
                    Tab(
                        selected = state.tab == t,
                        onClick = { vm.setTab(t) },
                        text = { Text(if (count != null) "${t.label} ($count)" else t.label) },
                    )
                }
            }
            val searching = state.query.isNotBlank()
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SearchField(value = state.query, onValueChange = vm::setQuery, placeholder = "Buscar nº de série, modelo ou cliente...")
                }
                item {
                    if (searching) {
                        Text(
                            "Resultado da busca em todas as seções (${state.list.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TabHelp(state.tab)
                    }
                }
                if (!searching && state.tab == WarrantyTab.PICKUP && state.list.size > 1) {
                    item {
                        FilledTonalButton(
                            onClick = { confirmCollectAll = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("A fábrica recolheu todas (${state.list.size})") }
                    }
                }
                if (!state.loading && state.list.isEmpty()) {
                    item { EmptyState(if (searching) "Nenhuma troca encontrada." else "Nada nesta seção.") }
                }
                items(state.list, key = { it.id }) { w -> WarrantyRow(w) { onOpen(w.id) } }
            }
        }
    }

    if (confirmCollectAll) {
        ConfirmDialog(
            title = "A fábrica recolheu todas?",
            text = "As ${state.list.size} baterias passam para \"Na fábrica\". Se foi só parte, marque uma a uma.",
            confirmLabel = "Sim, recolheu todas",
            onConfirm = {
                confirmCollectAll = false
                vm.collect(state.list.map { it.id })
            },
            onDismiss = { confirmCollectAll = false },
        )
    }
}

@Composable
private fun TabHelp(tab: WarrantyTab) {
    val text = when (tab) {
        WarrantyTab.PICKUP -> "Baterias com defeito trocadas, esperando a fábrica recolher. Quando recolherem, toque na bateria e marque \"Fábrica recolheu\"."
        WarrantyTab.FACTORY -> "Baterias que a fábrica levou. Quando trouxerem a reposição (mesma ou outra), ou negarem a garantia, registre na bateria."
        WarrantyTab.USED -> "Garantias negadas: a bateria usada voltou para a loja. Registre o destino (sucata, vendida ou descartada)."
        WarrantyTab.HISTORY -> "Atendimentos finalizados: testes sem defeito, reposições recebidas e usadas com destino."
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WarrantyRow(w: WarrantyClaim, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    w.returnedModel + (w.replacementModel?.let { " → $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    Periods.formatDate(w.createdAt) +
                        (w.returnedSerial?.let { " • série $it" } ?: "") +
                        (w.customerName.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (w.refusalNotes != null && w.status == WarrantyStatus.AT_FACTORY) {
                    Text("Houve reposição recusada", style = MaterialTheme.typography.bodySmall, color = warningColor())
                }
            }
            Text(
                if (w.status == WarrantyStatus.DENIED) UsedDestination.label(w.usedDestination) else WarrantyStatus.label(w.status),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 110.dp),
                color = when (w.status) {
                    WarrantyStatus.REPLACED, WarrantyStatus.NO_DEFECT -> profitColor()
                    WarrantyStatus.DENIED -> dangerColor()
                    else -> warningColor()
                },
            )
        }
    }
}

// ----------------------------------------------------------- Passo a passo da troca

@Composable
private fun StepHeader(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 84.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primary else scheme.surface,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        ),
        border = if (selected) null else BorderStroke(1.dp, scheme.outline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(date?.format(Periods.DATE) ?: "Escolher data")
        }
    }
    if (open) {
        DatePickerModal(initial = date ?: LocalDate.now(), onPick = onPick, onDismiss = { open = false })
    }
}

@Composable
private fun SerialField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.uppercase().take(40)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
fun WarrantyFormScreen(saleId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "warrantyform-$saleId") { WarrantyFormViewModel(it.repository, saleId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var pickReturned by remember { mutableStateOf(false) }
    var pickReplacement by remember { mutableStateOf(false) }

    LaunchedEffect(s.done) { if (s.done) onDone() }

    SubScreen(
        title = "Troca em garantia",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryActionButton(
                    text = when {
                        s.saving -> "Salvando..."
                        s.defective == false -> "Registrar teste (sem troca)"
                        else -> "Confirmar troca"
                    },
                    onClick = vm::confirm,
                    enabled = s.canConfirm && !s.saving,
                )
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
        ) {
            // 1. Venda e bateria
            StepHeader(1, "Bateria que o cliente trouxe")
            val sale = s.sale
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("Modelo", style = MaterialTheme.typography.labelLarge)
                    Text(s.returnedModel.ifBlank { "Escolha o modelo" }, style = MaterialTheme.typography.headlineSmall)
                    if (sale != null) {
                        Text(
                            "Venda de ${Periods.formatDate(sale.sale.dateTime)} • ${sale.quantity} un. • ${Money.format(sale.sale.finalAmount)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        TextButton(onClick = { pickReturned = true }) {
                            Text(if (s.returnedModel.isBlank()) "Escolher o modelo da bateria" else "Trocar o modelo")
                        }
                    }
                }
            }
            SerialField(s.returnedSerial, vm::setReturnedSerial, "Nº de série da bateria do cliente")
            DateField(
                label = "Data da venda (no papel da garantia)",
                date = s.saleDate,
                onPick = vm::setSaleDate,
                modifier = Modifier.padding(top = 8.dp),
            )
            s.saleDate?.let { d ->
                Text(
                    "Vendida ${elapsedLabel(d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = s.customerName,
                onValueChange = { vm.setCustomer(it.take(80)) },
                label = { Text("Nome do cliente (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            // 2. Teste
            StepHeader(2, "Resultado do teste")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCard(
                    "Está RUIM",
                    "Fazer a troca",
                    selected = s.defective == true,
                    onClick = { vm.setDefective(true) },
                    modifier = Modifier.weight(1f),
                )
                ChoiceCard(
                    "Está BOA",
                    "Sem defeito, sem troca",
                    selected = s.defective == false,
                    onClick = { vm.setDefective(false) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (s.defective == true) {
                // 3. Bateria nova
                StepHeader(3, "Bateria nova entregue ao cliente")
                val rep = s.replacement
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        if (rep == null) {
                            Text(
                                "Sem estoque da mesma bateria. Escolha outra semelhante ou melhor.",
                                color = warningColor(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(rep.model, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (rep.id == s.returnedProductId) "Mesma bateria • estoque ${rep.stock}"
                                else "Bateria diferente • estoque ${rep.stock}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        OutlinedButton(onClick = { pickReplacement = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (rep == null) "Escolher bateria" else "Trocar por outra bateria")
                        }
                    }
                }
                SerialField(s.replacementSerial, vm::setReplacementSerial, "Nº de série da bateria nova")
                DateField(
                    label = "Data da troca",
                    date = s.exchangeDate,
                    onPick = vm::setExchangeDate,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (s.returnedSerial.isBlank() || s.saleDate == null || s.replacementSerial.isBlank()) {
                    Text(
                        "Para confirmar a troca, preencha os dois números de série e a data da venda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor(),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // 4. Diferença
                StepHeader(4, "O cliente pagou diferença?")
                val suggested = vm.suggestedDifference(s)
                MoneyField(
                    value = s.difference,
                    onValueChange = vm::setDifference,
                    label = "Diferença paga (deixe 0,00 se não pagou)",
                    supportingText = if (suggested > 0) "Diferença de preço sugerida: ${Money.format(suggested)}" else null,
                )
                if (suggested > 0 && s.difference != suggested) {
                    TextButton(onClick = { vm.setDifference(suggested) }) { Text("Usar ${Money.format(suggested)}") }
                }
                if (s.difference > 0) {
                    PaymentMethodChips(selected = s.differenceMethod, onSelect = vm::setMethod, modifier = Modifier.padding(top = 4.dp))
                }
            }

            OutlinedTextField(
                value = s.note,
                onValueChange = { vm.setNote(it.take(200)) },
                label = { Text("Observação (opcional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )

            // Resumo didático
            if (s.defective != null) {
                StepHeader(if (s.defective == true) 5 else 3, "Confira o que vai acontecer")
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (s.defective == true) {
                            Text("• Sai do estoque: 1× ${s.replacement?.model ?: "(escolha a bateria nova)"} (nova, entregue ao cliente em ${s.exchangeDate.format(Periods.DATE)})")
                            Text("• A bateria do cliente (${s.returnedModel}) vai para \"Aguardando recolha\" da fábrica")
                            if (s.difference > 0) Text("• Diferença recebida: ${Money.format(s.difference)} (${s.differenceMethod.label})")
                        } else {
                            Text("• Fica registrado que a bateria ${s.returnedModel} foi testada e está boa")
                            Text("• Nada muda no estoque")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickReturned) {
        ProductPickerDialog(
            title = "Bateria que o cliente trouxe",
            products = products,
            onPick = { vm.setReturned(it); pickReturned = false },
            onDismiss = { pickReturned = false },
        )
    }
    if (pickReplacement) {
        ProductPickerDialog(
            title = "Bateria nova para o cliente",
            products = products,
            onlyInStock = true,
            highlightId = s.returnedProductId,
            onPick = { vm.setReplacement(it); pickReplacement = false },
            onDismiss = { pickReplacement = false },
        )
    }
}

// --------------------------------------------------------------------------- Detalhe

private enum class WDialog { NONE, COLLECTED, REPLACED_SAME, REPLACED_OTHER, REFUSED, DENIED, DESTINATION, DELETE }

@Composable
fun WarrantyDetailScreen(id: Long, onOpenSale: (Long) -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "warranty-$id") { WarrantyDetailViewModel(it.repository, id) }
    val loaded by vm.claim.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var dialog by remember { mutableStateOf(WDialog.NONE) }

    SubScreen(title = "Garantia", onBack = onBack) { inner ->
        val data = loaded
        val w = data?.value
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            w == null -> Box(Modifier.padding(inner)) { EmptyState("Atendimento não encontrado.") }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text(WarrantyStatus.label(w.status), style = MaterialTheme.typography.labelLarge)
                        Text(
                            w.returnedModel + (w.replacementModel?.let { " → $it" } ?: ""),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        w.saleId?.let {
                            TextButton(onClick = { onOpenSale(it) }) { Text("Ver venda") }
                        }
                    }
                }
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Linha do tempo", style = MaterialTheme.typography.titleSmall)
                        w.returnedSaleDate?.let { InfoRow("Venda (papel da garantia)", Periods.formatDate(it)) }
                        InfoRow(if (w.defective) "Troca" else "Teste", Periods.formatDate(w.createdAt))
                        if (w.customerName.isNotBlank()) InfoRow("Cliente", w.customerName)
                        w.returnedSerial?.let { InfoRow("Série da bateria do cliente", it, bold = true) }
                        w.replacementSerial?.let { InfoRow("Série da bateria nova", it, bold = true) }
                        InfoRow("Teste", if (w.defective) "Bateria ruim" else "Bateria boa (sem troca)")
                        w.replacementModel?.let { InfoRow("Entregue ao cliente", "$it (custo ${Money.format(w.replacementCost)})") }
                        if (w.differenceAmount > 0) InfoRow("Diferença paga", Money.format(w.differenceAmount))
                        w.collectedAt?.let { InfoRow("Recolhida pela fábrica", Periods.formatDateTime(it)) }
                        w.factoryModel?.let { InfoRow("Reposição recebida", it) }
                        if (w.status == WarrantyStatus.DENIED) InfoRow("Fábrica", "Negou a garantia", valueColor = dangerColor())
                        w.resolvedAt?.takeIf { w.status != WarrantyStatus.NO_DEFECT }?.let { InfoRow("Concluída em", Periods.formatDateTime(it)) }
                        if (w.status == WarrantyStatus.DENIED) {
                            InfoRow("Bateria usada", UsedDestination.label(w.usedDestination))
                            if (w.usedSaleValue > 0) InfoRow("Vendida por", Money.format(w.usedSaleValue))
                        }
                        w.refusalNotes?.let {
                            Text("Reposições recusadas:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        w.note?.let { InfoRow("Observação", it) }
                    }
                }

                // Próximo passo, conforme a situação
                when {
                    w.status == WarrantyStatus.AWAITING_PICKUP -> {
                        Text("Próximo passo: a fábrica recolher a bateria.", style = MaterialTheme.typography.bodyMedium)
                        PrimaryActionButton(text = "A fábrica recolheu", onClick = { dialog = WDialog.COLLECTED })
                    }
                    w.status == WarrantyStatus.AT_FACTORY -> {
                        Text("Próximo passo: registrar o que a fábrica fez.", style = MaterialTheme.typography.bodyMedium)
                        PrimaryActionButton(text = "Chegou a reposição (mesma bateria)", onClick = { dialog = WDialog.REPLACED_SAME })
                        FilledTonalButton(onClick = { dialog = WDialog.REPLACED_OTHER }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("Chegou OUTRA bateria e aceitamos")
                        }
                        FilledTonalButton(onClick = { dialog = WDialog.REFUSED }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("Trouxeram outra e RECUSAMOS")
                        }
                        OutlinedButton(
                            onClick = { dialog = WDialog.DENIED },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text("Fábrica negou a garantia (voltou usada)") }
                    }
                    w.isUsedInShop -> {
                        Text("Bateria usada na loja. Registre o destino quando houver.", style = MaterialTheme.typography.bodyMedium)
                        PrimaryActionButton(text = "Dar destino à bateria usada", onClick = { dialog = WDialog.DESTINATION })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { dialog = WDialog.DELETE },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Excluir atendimento") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val w = loaded?.value ?: return
    val close = { dialog = WDialog.NONE }
    when (dialog) {
        WDialog.COLLECTED -> ConfirmDialog(
            title = "A fábrica recolheu?",
            text = "A bateria ${w.returnedModel} passa para \"Na fábrica\".",
            confirmLabel = "Sim, recolheu",
            onConfirm = { vm.collected(); close() },
            onDismiss = close,
        )
        WDialog.REPLACED_SAME -> {
            val same = products.firstOrNull { it.id == w.returnedProductId }
            if (same == null) {
                ConfirmDialog(
                    title = "Modelo não cadastrado",
                    text = "A bateria ${w.returnedModel} não está cadastrada no estoque. Use \"Chegou OUTRA bateria\" e escolha o modelo recebido.",
                    confirmLabel = "Entendi",
                    onConfirm = close,
                    onDismiss = close,
                )
            } else {
                ConfirmDialog(
                    title = "Receber reposição",
                    text = "Entra no estoque: 1× ${same.model}. A garantia fica concluída.",
                    confirmLabel = "Confirmar",
                    onConfirm = { vm.replaced(same.id); close() },
                    onDismiss = close,
                )
            }
        }
        WDialog.REPLACED_OTHER -> ProductPickerDialog(
            title = "Qual bateria a fábrica entregou?",
            products = products,
            onPick = { vm.replaced(it.id); close() },
            onDismiss = close,
        )
        WDialog.REFUSED -> RefuseDialog(onConfirm = { offered, reason -> vm.refused(offered, reason); close() }, onDismiss = close)
        WDialog.DENIED -> ConfirmDialog(
            title = "Fábrica negou a garantia?",
            text = "A bateria ${w.returnedModel} volta para a loja como usada (seção \"Usadas\"). A bateria nova entregue ao cliente não é reposta.",
            confirmLabel = "Confirmar",
            destructive = true,
            onConfirm = { vm.denied(); close() },
            onDismiss = close,
        )
        WDialog.DESTINATION -> DestinationDialog(
            model = w.returnedModel,
            defaultAmperage = products.firstOrNull { it.id == w.returnedProductId }?.amperage?.takeIf { it > 0 }
                ?: Scrap.guessAmperage(w.returnedModel),
            onConfirm = { dest, value, amp -> vm.destination(dest, value, amp); close() },
            onDismiss = close,
        )
        WDialog.DELETE -> ConfirmDialog(
            title = "Excluir atendimento?",
            text = "O atendimento será apagado e o estoque corrigido (a bateria entregue volta, a reposição recebida sai).",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { close(); vm.delete(onBack) },
            onDismiss = close,
        )
        WDialog.NONE -> Unit
    }
}

@Composable
private fun RefuseDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var offered by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reposição recusada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A garantia continua pendente na fábrica. Fica registrado o que foi oferecido.")
                OutlinedTextField(
                    value = offered,
                    onValueChange = { offered = it.take(60) },
                    label = { Text("Bateria que trouxeram") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(120) },
                    label = { Text("Motivo (opcional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (offered.isNotBlank()) onConfirm(offered.trim(), reason) }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun DestinationDialog(model: String, defaultAmperage: Int?, onConfirm: (String, Long, Int?) -> Unit, onDismiss: () -> Unit) {
    var dest by remember { mutableStateOf(UsedDestination.SCRAP) }
    var value by remember { mutableLongStateOf(0L) }
    var amperage by remember { mutableStateOf(defaultAmperage?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Destino da bateria usada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Bateria: $model")
                listOf(UsedDestination.SCRAP, UsedDestination.SOLD, UsedDestination.DISCARDED).forEach { d ->
                    ChoiceCard(
                        UsedDestination.label(d),
                        when (d) {
                            UsedDestination.SCRAP -> "Entra no estoque de sucatas"
                            UsedDestination.SOLD -> "Informe o valor recebido"
                            else -> "Sem valor"
                        },
                        selected = dest == d,
                        onClick = { dest = d },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (dest == UsedDestination.SCRAP) {
                    IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Amperagem da sucata (Ah)")
                }
                if (dest == UsedDestination.SOLD) {
                    MoneyField(value = value, onValueChange = { value = it }, label = "Valor recebido")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dest, value, amperage.toIntOrNull()) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
