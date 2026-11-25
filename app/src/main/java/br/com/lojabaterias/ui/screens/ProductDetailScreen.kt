package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.CostLayer
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.StatTile
import br.com.lojabaterias.ui.components.StockBadge
import br.com.lojabaterias.ui.components.TextInput
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.ProductDetailViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun ProductDetailScreen(productId: Long, onEdit: () -> Unit, onSell: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "product-$productId") { ProductDetailViewModel(it.repository, productId) }
    val loaded by vm.product.collectAsStateWithLifecycle()
    val layers by vm.layers.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)

    var showEntry by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }

    SubScreen(
        title = loaded?.value?.model ?: "Bateria",
        onBack = onBack,
        actions = { TextButton(onClick = onEdit) { Text("Editar") } },
        bottomBar = {
            val product = loaded?.value
            if (product != null) {
                BottomActionBar {
                    PrimaryActionButton(text = "Vender", onClick = onSell, enabled = !product.isOutOfStock)
                }
            }
        },
    ) { inner ->
        val data = loaded
        val product = data?.value
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            product == null -> Box(Modifier.padding(inner)) { EmptyState("Bateria não encontrada.") }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Gaps.screen),
                verticalArrangement = Arrangement.spacedBy(Gaps.item),
            ) {
                StockCard(product)
                EqualRow {
                    FilledTonalButton(
                        onClick = { showEntry = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("+ Entrada") }
                    FilledTonalButton(
                        onClick = { showAdjust = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Ajustar") }
                }
                PricesSection(product, layers)
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val product = loaded?.value
    if (showEntry && product != null) {
        EntryDialog(
            product = product,
            onConfirm = { qty, cost, note -> vm.addStock(qty, cost, note) { showEntry = false } },
            onDismiss = { showEntry = false },
        )
    }
    if (showAdjust && product != null) {
        AdjustDialog(
            product = product,
            onConfirm = { newStock, note -> vm.adjustStock(newStock, note) { showAdjust = false } },
            onDismiss = { showAdjust = false },
        )
    }
}

@Composable
private fun StockCard(product: Product) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(product.model, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(
                        product.amperage.takeIf { it > 0 }?.let { "${it}Ah" },
                        "alerta em ${product.minStock}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StockBadge(product, large = true)
        }
    }
}

@Composable
private fun PricesSection(product: Product, layers: List<CostLayer>) {
    SectionHeader("Preços")
    EqualRow {
        StatTile("PIX / dinheiro", Money.format(product.pricePix), Modifier.weight(1f))
        StatTile("Débito", Money.format(product.priceDebit), Modifier.weight(1f))
        StatTile("Crédito", Money.format(product.priceCredit), Modifier.weight(1f))
    }
    // Custo pelos lotes: a próxima bateria a sair é a do lote mais antigo
    val nextCost = layers.firstOrNull()?.unitCost ?: product.cost
    PanelCard {
        InfoRow(if (layers.size > 1) "Custo (próxima a sair)" else "Custo", Money.format(nextCost))
        InfoRow("Lucro no PIX", Money.format(product.pricePix - nextCost), valueColor = moneyResultColor(product.pricePix - nextCost))
        if (layers.size > 1) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Lotes (saem de cima para baixo)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            layers.forEach { l -> InfoRow("${l.quantity} × ${Money.format(l.unitCost)}", Money.format(l.total)) }
        }
    }
}

@Composable
private fun EntryDialog(product: Product, onConfirm: (Int, Long?, String) -> Unit, onDismiss: () -> Unit) {
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableLongStateOf(product.cost) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrada · ${product.model}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(value = qty, onValueChange = { qty = it }, label = "Quantidade", supportingText = "Em estoque: ${product.stock}")
                MoneyField(value = cost, onValueChange = { cost = it }, label = "Custo unitário")
                TextInput(note, { note = it }, "Observação (opcional)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull() ?: 0
                onConfirm(q, if (cost != product.cost && cost > 0) cost else null, note)
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AdjustDialog(product: Product, onConfirm: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var stock by remember { mutableStateOf(product.stock.toString()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar · ${product.model}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(value = stock, onValueChange = { stock = it }, label = "Quantidade contada", supportingText = "No sistema: ${product.stock}")
                TextInput(note, { note = it }, "Observação (opcional)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = stock.toIntOrNull()
                if (s != null) onConfirm(s, note)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
