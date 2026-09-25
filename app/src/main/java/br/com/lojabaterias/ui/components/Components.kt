package br.com.lojabaterias.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.theme.warningColor
import kotlinx.coroutines.flow.Flow

/** Exibe mensagens curtas (Toast) emitidas pelo ViewModel. */
@Composable
fun ToastEffect(messages: Flow<String>) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}

/** Campo monetário com máscara de centavos: digitar "50000" resulta em "500,00". */
@Composable
fun MoneyField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    supportingText: String? = null,
) {
    val text = Money.formatNumber(value)
    OutlinedTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { onValueChange(Money.centsFromDigits(it.text)) },
        label = { Text(label) },
        prefix = { Text("R$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo numérico inteiro (quantidades). */
@Composable
fun IntField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = modifier
            .fillMaxWidth()
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    )
}

@Composable
fun QuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = Int.MAX_VALUE,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { if (value > min) onValueChange(value - 1) },
            enabled = value > min,
            modifier = Modifier.size(52.dp),
        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        FilledTonalIconButton(
            onClick = { if (value < max) onValueChange(value + 1) },
            enabled = value < max,
            modifier = Modifier.size(52.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Aumentar") }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { content() }
    }
}

/** Linha "rótulo .... valor". */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    bold: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) dangerColor() else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}

/** Selo de estoque com destaque para baixo/zerado. */
@Composable
fun StockBadge(product: Product, large: Boolean = false) {
    val (bg, fg) = when {
        product.isOutOfStock -> dangerColor().copy(alpha = 0.14f) to dangerColor()
        product.isLowStock -> warningColor().copy(alpha = 0.16f) to warningColor()
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = if (large) 18.dp else 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                product.stock.toString(),
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
            Text(
                when {
                    product.isOutOfStock -> "zerado"
                    product.isLowStock -> "baixo"
                    else -> "em estoque"
                },
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
        }
    }
}

/** Linha de venda para listas (dashboard e histórico). */
@Composable
fun SaleRow(sale: SaleWithItems, onClick: () -> Unit, showDate: Boolean = true) {
    val s = sale.sale
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${sale.quantity}× ${sale.modelsLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    (if (showDate) Periods.formatDateTime(s.dateTime) else Periods.formatTime(s.dateTime)) +
                        " • " + s.payment.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!s.isCanceled) {
                    Text(
                        "Custo ${Money.format(s.totalCost)} • Lucro ${Money.format(s.grossProfit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(s.finalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (s.isCanceled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                if (s.isCanceled) {
                    Text("CANCELADA", style = MaterialTheme.typography.labelSmall, color = dangerColor())
                } else {
                    Text(
                        (if (s.grossProfit >= 0) "+" else "") + Money.format(s.grossProfit),
                        style = MaterialTheme.typography.labelMedium,
                        color = moneyResultColor(s.grossProfit),
                    )
                }
            }
        }
    }
}
