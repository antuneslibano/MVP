package br.com.lojabaterias.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.CardFees
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.DateTimeSelector
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.Segmented
import br.com.lojabaterias.ui.components.StockBadge
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.SaleFormState
import br.com.lojabaterias.ui.viewmodel.SaleFormViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun SaleFormScreen(saleId: Long?, productId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "saleform-$saleId-$productId") { SaleFormViewModel(it.repository, saleId, productId) }
    val form by vm.form.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)

    LaunchedEffect(form.done) { if (form.done) onDone() }

    val canChangeProduct = !form.isEdit && productId == null
    BackHandler(enabled = form.hasSelection && canChangeProduct) { vm.clearProduct() }

    when {
        form.loading -> SubScreen(title = "Editar venda", onBack = onBack) { inner ->
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        !form.hasSelection -> ProductPicker(vm, onBack)
        else -> SaleDetailsForm(
            vm = vm,
            form = form,
            canChangeProduct = canChangeProduct,
            onBack = if (canChangeProduct) vm::clearProduct else onBack,
        )
    }
}

@Composable
private fun ProductPicker(vm: SaleFormViewModel, onBack: () -> Unit) {
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    SubScreen(title = "Nova venda", onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
                .imePadding(),
        ) {
            SearchField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = "Qual bateria?",
                focusRequester = focus,
                modifier = Modifier.padding(horizontal = Gaps.screen),
            )
            LazyColumn(
                contentPadding = PaddingValues(Gaps.screen),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (results.isEmpty()) {
                    item { EmptyState(if (query.isBlank()) "Nenhuma bateria cadastrada." else "Nenhum modelo encontrado.") }
                }
                items(results, key = { it.id }) { product ->
                    ProductPickRow(product) { vm.selectProduct(product) }
                }
            }
        }
    }
}

@Composable
private fun ProductPickRow(product: Product, onClick: () -> Unit) {
    val enabled = !product.isOutOfStock
    AppCard(
        onClick = if (enabled) onClick else null,
        modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(product.model, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "PIX ${Money.format(product.pricePix)} · Créd. ${Money.format(product.priceCredit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StockBadge(product)
        }
    }
}

@Composable
private fun SaleDetailsForm(
    vm: SaleFormViewModel,
    form: SaleFormState,
    canChangeProduct: Boolean,
    onBack: () -> Unit,
) {
    val totals = form.totals
    val error = form.validationError

    SubScreen(
        title = if (form.isEdit) "Editar venda" else "Nova venda",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                if (error != null) {
                    Text(
                        error,
                        color = dangerColor(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                PrimaryActionButton(
                    text = when {
                        form.saving -> "Salvando..."
                        form.isEdit -> "Salvar alterações"
                        else -> "Confirmar · ${Money.format(totals?.finalAmount ?: 0)}"
                    },
                    onClick = vm::confirm,
                    enabled = error == null && !form.saving,
                )
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gaps.screen),
        ) {
            // Bateria
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(form.model, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            if (form.product == null) "Excluída do estoque" else "${form.available} em estoque",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    if (canChangeProduct) {
                        TextButton(onClick = vm::clearProduct) { Text("Trocar") }
                    }
                }
            }

            SectionHeader("Pagamento")
            if (!form.split) {
                PaymentMethod.entries.chunked(2).forEach { row ->
                    EqualRow(Modifier.padding(bottom = 8.dp)) {
                        row.forEach { method ->
                            PaymentOption(
                                method = method,
                                price = form.product?.prices?.priceFor(method),
                                selected = form.method == method,
                                onClick = { vm.selectMethod(method) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Dividir em mais de uma forma", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = form.split, onCheckedChange = vm::setSplit)
            }

            SectionHeader("Quantidade")
            QuantityStepper(
                value = form.quantity,
                onValueChange = vm::setQuantity,
                min = 1,
                max = maxOf(1, form.available),
            )

            ScrapSection(vm, form)

            if (form.split) {
                SplitSection(vm, form)
            } else {
                SectionHeader("Valores")
                EqualRow {
                    MoneyField(value = form.unitPrice, onValueChange = vm::setUnitPrice, label = "Preço", modifier = Modifier.weight(1f))
                    MoneyField(value = form.discount, onValueChange = vm::setDiscount, label = "Desconto", modifier = Modifier.weight(1f))
                }
            }

            SectionHeader("Data e hora")
            DateTimeSelector(millis = form.dateTime, onChange = vm::setDateTime)

            SectionHeader("Resumo")
            PanelCard {
                if (totals != null) {
                    InfoRow("Valor", Money.format(totals.grossAmount))
                    if (totals.discount > 0) InfoRow("Desconto", "-" + Money.format(totals.discount))
                    if (totals.scrapCharge > 0) InfoRow("Casco", "+" + Money.format(totals.scrapCharge))
                    if (totals.cardFee > 0) {
                        InfoRow("Taxa ${CardFees.formatPercent(form.fees.rateFor(form.method))}", "-" + Money.format(totals.cardFee))
                    }
                    InfoRow("Custo", Money.format(totals.totalCost))
                    if (form.freeUnits > 0) InfoRow("Extras (custo zero)", "${form.freeUnits} un.")
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow("Total", Money.format(totals.finalAmount), bold = true)
                    InfoRow("Lucro", Money.format(totals.grossProfit), valueColor = moneyResultColor(totals.grossProfit))
                } else {
                    Hint("Confira quantidade, preço e desconto.")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PaymentOption(
    method: PaymentMethod,
    price: Long?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primary else scheme.surface,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        ),
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(method.label, style = MaterialTheme.typography.titleSmall)
            if (price != null) FitText(Money.format(price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScrapSection(vm: SaleFormViewModel, form: SaleFormState) {
    SectionHeader("Sucata")
    if (form.scrapLegacy) {
        Hint("Não informada nesta venda.")
        return
    }
    val left = form.scrapReturned > 0
    Segmented(
        options = listOf("Deixou sucata", "Não deixou"),
        selected = if (left) 0 else 1,
        onSelect = { vm.setScrapLeft(it == 0) },
    )
    Spacer(Modifier.height(10.dp))

    if (left) {
        if (form.quantity > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantas?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                QuantityStepper(value = form.scrapReturned, onValueChange = vm::setScrapReturned, min = 1, max = form.quantity)
            }
            Spacer(Modifier.height(8.dp))
        }
        IntField(value = form.scrapAmperage, onValueChange = vm::setScrapAmperage, label = "Amperagem da sucata (Ah)")
    }

    if (form.scrapMissing > 0) {
        if (left) Spacer(Modifier.height(8.dp))
        MoneyField(
            value = form.scrapCharge,
            onValueChange = vm::setScrapCharge,
            label = "Cobrar casco (${Scrap.units(form.scrapMissing)})",
        )
    }
}

/** Pagamento dividido: uma linha por forma de pagamento, com atalhos do quanto falta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitSection(vm: SaleFormViewModel, form: SaleFormState) {
    SectionHeader("Formas de pagamento")
    form.parts.forEachIndexed { index, part ->
        PanelCard(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}ª forma", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (form.parts.size > 2) {
                    TextButton(onClick = { vm.removePart(index) }) { Text("Remover") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaymentMethod.entries.forEach { m ->
                    FilterChip(selected = part.method == m, onClick = { vm.setPartMethod(index, m) }, label = { Text(m.label) })
                }
            }
            MoneyField(value = part.amount, onValueChange = { vm.setPartAmount(index, it) }, label = "Valor")
            val suggestions = vm.suggestions(form, index).filter { it.amount != part.amount }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    suggestions.forEach { sg ->
                        AssistChip(
                            onClick = { vm.setPartAmount(index, sg.amount) },
                            label = { Text("${sg.label}: ${Money.format(sg.amount)}") },
                        )
                    }
                }
            }
        }
    }
    if (form.parts.size < PaymentMethod.entries.size) {
        TextButton(onClick = vm::addPart) { Text("+ Outra forma") }
    }
    PanelCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        form.parts.filter { it.amount > 0 }.forEach { InfoRow(it.method.label, Money.format(it.amount)) }
        InfoRow("Total cobrado", Money.format(form.partsTotal), bold = true)
    }
}
