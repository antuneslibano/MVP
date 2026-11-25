package br.com.lojabaterias.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.theme.warningColor
import kotlinx.coroutines.flow.Flow

/** Espaçamento padrão das telas. */
object Gaps {
    val screen = 16.dp
    val item = 10.dp
}

/** Exibe mensagens curtas (Toast) emitidas pelo ViewModel. */
@Composable
fun ToastEffect(messages: Flow<String>) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}

// ------------------------------------------------------------------ Campos

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
        shape = MaterialTheme.shapes.medium,
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
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo de texto livre de uma linha. */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 120,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        label = { Text(label) },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
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
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
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
            modifier = Modifier.size(48.dp),
        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
        // O número também pode ser digitado (ex.: 70), sem precisar tocar várias vezes no + / −.
        var text by remember(value) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val digits = new.filter { it.isDigit() }.take(5)
                text = digits
                digits.toIntOrNull()?.let { n ->
                    val v = n.coerceIn(min, maxOf(min, max))
                    if (v != value) onValueChange(v)
                    if (v != n) text = v.toString()
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .width(84.dp)
                .onFocusChanged { if (!it.isFocused) text = value.toString() },
        )
        FilledTonalIconButton(
            onClick = { if (value < max) onValueChange(value + 1) },
            enabled = value < max,
            modifier = Modifier.size(48.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Aumentar") }
    }
}

/** Escolha única em botões segmentados (texto que diminui para caber). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { FitText(label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

// --------------------------------------------------------------- Estrutura

/** Título grande das telas principais, com ações à direita. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Gaps.screen, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.invoke()
        }
        actions()
    }
}

/** Título de seção, com link opcional à direita ("Ver todas"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp)
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Mantido por compatibilidade: o mesmo que [SectionHeader] sem ação. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = SectionHeader(text, modifier)

/** Cartão plano com borda fina. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val shape = MaterialTheme.shapes.large
    val border = if (containerColor == MaterialTheme.colorScheme.surface) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { content() }
    }
}

/** Cartão com padding interno e conteúdo em coluna. */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier, onClick = onClick, containerColor = containerColor) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Cartão de destaque (fundo da cor principal) com o número mais importante da tela. */
@Composable
fun HeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    AppCard(modifier = modifier, onClick = onClick, containerColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            val on = MaterialTheme.colorScheme.onPrimary
            Text(label, style = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Start), color = on.copy(alpha = 0.8f))
            FitText(value, style = MaterialTheme.typography.headlineLarge, color = on, fontWeight = FontWeight.Bold)
            footer()
        }
    }
}

/** Par rótulo/valor dentro do [HeroCard]. */
@Composable
fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.onPrimary
    Column(modifier.padding(end = 8.dp)) {
        FitText(label, style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.75f))
        FitText(value, style = MaterialTheme.typography.titleMedium, color = on)
    }
}

/** Pequeno bloco "rótulo / valor" para grades de métricas. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    caption: String? = null,
) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            FitText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            FitText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
            if (caption != null) {
                FitText(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Etiqueta colorida curta ("baixo", "vencido", "cancelada"). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.13f), shape = CircleShape, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Bolinha colorida (status). */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, CircleShape))
}

/** Navegação entre períodos: ‹ Novembro 2026 ›. */
@Composable
fun PeriodSwitcher(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = canPrevious) {
            if (canPrevious) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Anterior")
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canNext) {
            if (canNext || canPrevious) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Próximo",
                    tint = if (canNext) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                )
            }
        }
        trailing()
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
    // O rótulo ocupa no máximo pouco mais da metade da linha; o valor fica com o resto e quebra alinhado à direita.
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        content = {
            Text(
                label,
                style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                color = valueColor,
                textAlign = TextAlign.End,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val labelP = measurables[0].measure(Constraints(maxWidth = (width * 0.58f).toInt()))
        val valueWidth = (width - labelP.width - gap).coerceAtLeast(0)
        val valueP = measurables[1].measure(Constraints(minWidth = valueWidth, maxWidth = valueWidth))
        val height = maxOf(labelP.height, valueP.height)
        layout(width, height) {
            labelP.placeRelative(0, (height - labelP.height) / 2)
            valueP.placeRelative(width - valueWidth, (height - valueP.height) / 2)
        }
    }
}

/**
 * Texto de uma linha que diminui a fonte até caber (valores em R$, rótulos curtos, abas).
 * Evita quebra no meio de números e palavras quando a fonte do celular está grande.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth && scale > 0.5f) scale *= 0.9f else ready = true
        },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
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

// ------------------------------------------------------------------ Linhas

/** Selo de estoque com destaque para baixo/zerado. */
@Composable
fun StockBadge(product: Product, large: Boolean = false) {
    val (bg, fg) = when {
        product.isOutOfStock -> dangerColor().copy(alpha = 0.12f) to dangerColor()
        product.isLowStock -> warningColor().copy(alpha = 0.14f) to warningColor()
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = bg, shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .widthIn(min = if (large) 64.dp else 52.dp)
                .padding(horizontal = 10.dp, vertical = if (large) 8.dp else 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                product.stock.toString(),
                style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
            Text(
                when {
                    product.isOutOfStock -> "zerado"
                    product.isLowStock -> "baixo"
                    else -> "un."
                },
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
        }
    }
}

/** Linha de venda para listas (Início e Vendas). */
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
                    color = if (s.isCanceled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                Text(
                    (if (showDate) Periods.formatDateTime(s.dateTime) else Periods.formatTime(s.dateTime)) +
                        " · " + sale.paymentLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    StatusPill("cancelada", dangerColor())
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

/** Linha de lista genérica: título, detalhe e conteúdo à direita. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

/** Mensagem curta de apoio (cinza, pequena). Use pouco. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** Espaço vertical entre blocos de formulário. */
@Composable
fun FormGap() = Spacer(Modifier.height(Gaps.item))

/** Linhas lado a lado com o mesmo peso. */
@Composable
fun EqualRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gaps.item), content = content)
}
