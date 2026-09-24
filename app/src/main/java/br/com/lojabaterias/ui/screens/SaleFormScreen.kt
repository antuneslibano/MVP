package br.com.lojabaterias.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.SectionTitle
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
                placeholder = "Buscar modelo...",
                focusRequester = focus,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (results.isEmpty()) {
                    item {
                        EmptyState(
                            if (query.isBlank()) "Nenhuma bateria cadastrada.\nCadastre na aba Estoque."
                            else "Nenhum modelo encontrado para \"$query\"."
                        )
                    }
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
                Text(product.model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    (if (product.amperage > 0) "${product.amperage}Ah • " else "") +
                        "PIX ${Money.format(product.pricePix)} • Créd. ${Money.format(product.priceCredit)}",
                    style = MaterialTheme.typography.bodyMedium,
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
                        else -> "Confirmar venda • ${Money.format(totals?.finalAmount ?: 0)}"
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
                .padding(horizontal = 16.dp),
        ) {
            // Produto
            AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            form.model,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            if (form.product == null) "Produto excluído do estoque"
                            else "Disponível: ${form.available}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (canChangeProduct) {
                        TextButton(onClick = vm::clearProduct) { Text("Trocar") }
                    }
                }
            }

            SectionTitle("Forma de pagamento")
            val methods = PaymentMethod.entries
            methods.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
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

            SectionTitle("Quantidade")
            QuantityStepper(
                value = form.quantity,
                onValueChange = vm::setQuantity,
                min = 1,
                max = maxOf(1, form.available),
            )

            ScrapSection(vm, form)

            SectionTitle("Valores")
            val tablePrice = form.product?.prices?.priceFor(form.method)
            MoneyField(
                value = form.unitPrice,
                onValueChange = vm::setUnitPrice,
                label = "Preço unitário",
                supportingText = if (tablePrice != null && tablePrice != form.unitPrice) {
                    "Preço de tabela (${form.method.label}): ${Money.format(tablePrice)}"
                } else {
                    null
                },
            )
            Spacer(Modifier.height(8.dp))
            MoneyField(value = form.discount, onValueChange = vm::setDiscount, label = "Desconto (opcional)")

            SectionTitle("Data e hora")
            DateTimeSelector(millis = form.dateTime, onChange = vm::setDateTime)
            if (!form.dateTimeEdited) {
                Text(
                    "Será registrada com o horário do momento da confirmação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionTitle("Resumo")
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    if (totals != null) {
                        InfoRow("Valor bruto", Money.format(totals.grossAmount))
                        if (totals.discount > 0) InfoRow("Desconto", "-" + Money.format(totals.discount))
                        if (totals.scrapCharge > 0) InfoRow("Sucata faltante", "+" + Money.format(totals.scrapCharge))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        InfoRow("Total", Money.format(totals.finalAmount), bold = true)
                        InfoRow("Custo", Money.format(totals.totalCost))
                        if (totals.cardFee > 0) {
                            InfoRow(
                                "Taxa da maquininha (${CardFees.formatPercent(form.fees.rateFor(form.method))})",
                                "-" + Money.format(totals.cardFee),
                            )
                        }
                        InfoRow("Lucro bruto", Money.format(totals.grossProfit), valueColor = moneyResultColor(totals.grossProfit))
                    } else {
                        Text("Confira quantidade, preço e desconto.")
                    }
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
        modifier = modifier.heightIn(min = 68.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primary else scheme.surface,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        ),
        border = if (selected) null else BorderStroke(1.dp, scheme.outline),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(method.label, style = MaterialTheme.typography.titleMedium)
            if (price != null) {
                Text(Money.format(price), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScrapSection(vm: SaleFormViewModel, form: SaleFormState) {
    val prices by vm.scrapPrices.collectAsStateWithLifecycle()
    SectionTitle("Sucata")
    if (form.scrapLegacy) {
        Text(
            "Venda registrada antes do controle de sucatas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val left = form.scrapReturned > 0
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = left,
            onClick = { vm.setScrapLeft(true) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Deixou sucata") }
        SegmentedButton(
            selected = !left,
            onClick = { vm.setScrapLeft(false) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Sem sucata") }
    }
    Spacer(Modifier.height(8.dp))

    if (left) {
        if (form.quantity > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quantas deixou?", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                QuantityStepper(
                    value = form.scrapReturned,
                    onValueChange = vm::setScrapReturned,
                    min = 1,
                    max = form.quantity,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        IntField(
            value = form.scrapAmperage,
            onValueChange = vm::setScrapAmperage,
            label = "Amperagem da sucata deixada (Ah)",
            supportingText = form.scrapAmperage.toIntOrNull()?.let { a ->
                prices[a]?.let { "Valor de tabela da sucata ${a}Ah: ${Money.format(it)}" }
            } ?: "Confira a amperagem escrita na sucata",
        )
        val options = (prices.keys + listOfNotNull(form.batteryAmperage.takeIf { it > 0 })).toSortedSet()
        if (options.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { a ->
                    FilterChip(
                        selected = form.scrapAmperage == a.toString(),
                        onClick = { vm.setScrapAmperage(a.toString()) },
                        label = { Text("${a}Ah") },
                    )
                }
            }
        }
    }

    if (form.scrapMissing > 0) {
        Spacer(Modifier.height(8.dp))
        val suggested = vm.suggestedScrapCharge(form)
        MoneyField(
            value = form.scrapCharge,
            onValueChange = vm::setScrapCharge,
            label = "Cobrar pela sucata (${Scrap.units(form.scrapMissing)} faltando)",
            supportingText = when {
                form.batteryAmperage <= 0 -> "Informe a amperagem no cadastro da bateria para sugerir o valor"
                prices[form.batteryAmperage] == null ->
                    "Sem valor para ${form.batteryAmperage}Ah na tabela de sucatas (menu > Tabela de sucatas)"
                else -> "Tabela ${form.batteryAmperage}Ah: ${Money.format(prices[form.batteryAmperage] ?: 0)} por sucata"
            },
        )
        if (form.scrapChargeEdited && form.scrapCharge != suggested && suggested > 0) {
            TextButton(onClick = vm::resetScrapCharge) { Text("Usar valor da tabela (${Money.format(suggested)})") }
        }
    }
}
