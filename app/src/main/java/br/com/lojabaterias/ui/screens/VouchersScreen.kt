package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.Voucher
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.viewmodel.VouchersViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

/** Vales de casco: vendas em que o cliente pagou o casco e pode trazê-lo depois para receber o valor de volta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen() {
    val vm = appViewModel { VouchersViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var paying by remember { mutableStateOf<Voucher?>(null) }
    var toUndo by remember { mutableStateOf<ScrapMovement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vales de casco") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Toda venda com o casco cobrado vira um vale. Quando o cliente trouxer o casco, toque em " +
                        "\"Pagar vale\": o valor devolvido fica registrado no dia e o casco entra nas sucatas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Vales em aberto",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        FitText(
                            "${state.openCascos} casco(s) • ${Money.format(state.openValue)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (!state.loading && state.open.isEmpty()) item { EmptyState("Nenhum vale em aberto.") }
            items(state.open, key = { it.sale.sale.id }) { v -> OpenVoucherRow(v) { paying = v } }

            if (state.paid.isNotEmpty()) {
                item { SectionTitle("Vales pagos") }
                items(state.paid, key = { it.id }) { m -> PaidVoucherRow(m) { toUndo = m } }
            }
        }
    }

    paying?.let { v ->
        PayVoucherDialog(
            voucher = v,
            defaultAmperage = state.amperageBySale[v.sale.sale.id] ?: 0,
            onConfirm = { q, amp -> vm.pay(v, q, amp) { paying = null } },
            onDismiss = { paying = null },
        )
    }
    toUndo?.let { m ->
        ConfirmDialog(
            title = "Desfazer pagamento?",
            text = "O vale de ${Money.format(m.amount)} volta a ficar em aberto e ${m.quantity} casco(s) " +
                "${m.amperage}Ah saem do estoque de sucatas.",
            confirmLabel = "Desfazer",
            destructive = true,
            onConfirm = { vm.undo(m); toUndo = null },
            onDismiss = { toUndo = null },
        )
    }
}

@Composable
private fun OpenVoucherRow(v: Voucher, onPay: () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        v.sale.modelsLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Venda de ${Periods.formatDate(v.sale.sale.dateTime)} • ${v.remaining} casco(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(v.value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(onClick = onPay, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Pagar vale (cliente trouxe o casco)")
            }
        }
    }
}

@Composable
private fun PaidVoucherRow(m: ScrapMovement, onUndo: () -> Unit) {
    AppCard {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    "${Money.format(m.amount)} devolvido",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${Periods.formatDateTime(m.dateTime)} • ${m.quantity} casco(s) ${m.amperage}Ah",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUndo) {
                Icon(Icons.Filled.Delete, contentDescription = "Desfazer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PayVoucherDialog(voucher: Voucher, defaultAmperage: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var quantity by remember { mutableIntStateOf(voucher.remaining) }
    var amperage by remember { mutableStateOf(defaultAmperage.takeIf { it > 0 }?.toString() ?: "") }
    val value = if (quantity == voucher.remaining) voucher.value else voucher.unitValue * quantity
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pagar vale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (voucher.remaining > 1) {
                    Text("Quantos cascos o cliente trouxe?")
                    QuantityStepper(value = quantity, onValueChange = { quantity = it }, min = 1, max = voucher.remaining)
                }
                IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Amperagem do casco (Ah)")
                Text("Devolver ao cliente: ${Money.format(value)}", style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(quantity, amperage.toIntOrNull() ?: 0) }) { Text("Pagar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
