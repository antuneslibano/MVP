package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.StatusPill
import br.com.lojabaterias.ui.components.StockBadge
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.StockViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun StockScreen(onOpenProduct: (Long) -> Unit, onNewProduct: () -> Unit) {
    val vm = appViewModel { StockViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    MainPage(fabLabel = "Nova bateria", onFab = onNewProduct) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 12.dp, bottom = FabSpace),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SearchField(value = state.query, onValueChange = vm::setQuery, placeholder = "Buscar modelo")
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${state.totalUnits} baterias · ${state.totalModels} modelos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.lowCount > 0) StatusPill("${state.lowCount} baixo", warningColor())
                    if (state.zeroCount > 0) StatusPill("${state.zeroCount} zerado", dangerColor())
                }
            }
            if (!state.loading && state.products.isEmpty()) {
                item {
                    EmptyState(if (state.query.isBlank()) "Nenhuma bateria cadastrada." else "Nenhum modelo encontrado.")
                }
            }
            items(state.products, key = { it.id }) { product ->
                StockRow(product, onClick = { onOpenProduct(product.id) })
            }
        }
    }
}

@Composable
private fun StockRow(product: Product, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    product.model,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (product.amperage > 0) "${product.amperage}Ah · " else "") + "PIX ${Money.format(product.pricePix)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StockBadge(product)
        }
    }
}
