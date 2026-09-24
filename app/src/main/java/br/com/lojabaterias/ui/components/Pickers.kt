package br.com.lojabaterias.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.ui.theme.dangerColor

/** Diálogo para escolher uma bateria do estoque, com busca. */
@Composable
fun ProductPickerDialog(
    title: String,
    products: List<Product>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
    onlyInStock: Boolean = false,
    highlightId: Long? = null,
) {
    var query by remember { mutableStateOf("") }
    val list = products
        .filter { !onlyInStock || it.stock > 0 }
        .filter { query.isBlank() || it.model.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<Product> { it.id == highlightId }.thenBy { it.model.lowercase() })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Buscar modelo...")
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    if (list.isEmpty()) {
                        item {
                            Text(
                                if (onlyInStock) "Nenhuma bateria com estoque." else "Nenhuma bateria encontrada.",
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(p) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (p.amperage > 0) "${p.amperage}Ah • " else "") + "PIX ${Money.format(p.pricePix)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "estoque ${p.stock}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (p.stock <= 0) dangerColor() else MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

/** Chips para escolher a forma de pagamento. */
@Composable
fun PaymentMethodChips(selected: PaymentMethod?, onSelect: (PaymentMethod) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaymentMethod.entries.forEach { m ->
            FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(m.label) })
        }
    }
}

/** Diálogo simples para escolher a forma de pagamento. */
@Composable
fun PaymentMethodDialog(title: String, onPick: (PaymentMethod) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { m ->
                    OutlinedButton(onClick = { onPick(m) }, modifier = Modifier.fillMaxWidth()) { Text(m.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Botões "Ligar" e "WhatsApp" para um telefone. */
@Composable
fun PhoneActions(phone: String, modifier: Modifier = Modifier) {
    val digits = phone.filter { it.isDigit() }
    if (digits.length < 8) return
    val context = LocalContext.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
        }) { Text("Ligar") }
        OutlinedButton(onClick = {
            val full = if (digits.length <= 11) "55$digits" else digits
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$full"))) }
        }) { Text("WhatsApp") }
    }
}
