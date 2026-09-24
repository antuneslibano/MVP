package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.MovementType
import br.com.lojabaterias.data.MovementWithModel
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.StockBadge
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.viewmodel.ProductDetailViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun ProductDetailScreen(productId: Long, onEdit: () -> Unit, onSell: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "product-$productId") { ProductDetailViewModel(it.repository, productId) }
    val loaded by vm.product.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)

    var showEntry by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<MovementWithModel?>(null) }

    SubScreen(
        title = "Bateria",
        onBack = onBack,
        actions = { TextButton(onClick = onEdit) { Text("Editar") } },
    ) { inner ->
        val data = loaded
        val product = data?.value
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            product == null -> Box(Modifier.padding(inner)) { EmptyState("Bateria não encontrada.") }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { ProductHeader(product) }
                item {
                    PrimaryActionButton(
                        text = "Vender esta bateria",
                        onClick = onSell,
                        enabled = !product.isOutOfStock,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { showEntry = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) { Text("+ Entrada") }
                        FilledTonalButton(
                            onClick = { showAdjust = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) { Text("Ajustar estoque") }
                    }
                }
                item { SectionTitle("Histórico de movimentações") }
                if (movements.isEmpty()) {
                    item { EmptyState("Sem movimentações.") }
                }
                items(movements, key = { it.movement.id }) { m ->
                    MovementRow(
                        m,
                        showModel = false,
                        onDelete = if (MovementType.isDeletable(m.movement.type)) ({ toDelete = m }) else null,
                    )
                }
            }
        }
    }

    toDelete?.let { m ->
        DeleteMovementDialog(m, onConfirm = { vm.deleteMovement(m.movement.id) { toDelete = null } }, onDismiss = { toDelete = null })
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
private fun ProductHeader(product: Product) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(product.model, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        (if (product.amperage > 0) "${product.amperage}Ah • " else "") +
                            "Estoque mínimo: ${product.minStock}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StockBadge(product, large = true)
            }
            Spacer(Modifier.height(12.dp))
            InfoRow("Custo", Money.format(product.cost))
            InfoRow("Preço PIX / Dinheiro", Money.format(product.pricePix))
            InfoRow("Preço débito", Money.format(product.priceDebit))
            InfoRow("Preço crédito", Money.format(product.priceCredit))
            InfoRow(
                "Lucro no PIX",
                Money.format(product.pricePix - product.cost),
                valueColor = if (product.pricePix >= product.cost) profitColor() else dangerColor(),
            )
        }
    }
}

@Composable
fun DeleteMovementDialog(item: MovementWithModel, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val m = item.movement
    ConfirmDialog(
        title = "Excluir registro?",
        text = "${MovementType.label(m.type)} de ${if (m.quantity > 0) "+" else ""}${m.quantity} " +
            "(${item.model ?: "bateria excluída"}) em ${Periods.formatDateTime(m.dateTime)}. " +
            "O estoque será corrigido desfazendo esta movimentação.",
        confirmLabel = "Excluir",
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun MovementRow(item: MovementWithModel, showModel: Boolean, onDelete: (() -> Unit)? = null) {
    val m = item.movement
    AppCard {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showModel) "${item.model ?: "(excluído)"} • ${MovementType.label(m.type)}"
                    else MovementType.label(m.type),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    Periods.formatDateTime(m.dateTime) + (m.saleId?.let { " • Venda #$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                m.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (m.quantity > 0) "+" else "") + m.quantity,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (m.quantity >= 0) profitColor() else dangerColor(),
                )
                Text(
                    "saldo ${m.stockAfter}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(120)) },
        label = { Text("Observação (opcional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EntryDialog(product: Product, onConfirm: (Int, Long?, String) -> Unit, onDismiss: () -> Unit) {
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableLongStateOf(product.cost) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrada de estoque — ${product.model}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estoque atual: ${product.stock}")
                IntField(value = qty, onValueChange = { qty = it }, label = "Quantidade recebida")
                MoneyField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = "Custo unitário",
                    supportingText = "Altere se o custo de compra mudou",
                )
                NoteField(note) { note = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull() ?: 0
                onConfirm(q, if (cost != product.cost && cost > 0) cost else null, note)
            }) { Text("Registrar entrada") }
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
        title = { Text("Ajuste de estoque — ${product.model}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Informe a quantidade real contada na loja. Estoque atual no sistema: ${product.stock}.")
                IntField(value = stock, onValueChange = { stock = it }, label = "Quantidade real")
                NoteField(note) { note = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = stock.toIntOrNull()
                if (s != null) onConfirm(s, note)
            }) { Text("Salvar ajuste") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
