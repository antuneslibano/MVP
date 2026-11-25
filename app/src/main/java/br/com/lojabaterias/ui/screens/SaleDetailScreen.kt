package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.StatusPill
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.SaleDetailViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun SaleDetailScreen(
    saleId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val vm = appViewModel(key = "sale-$saleId") { SaleDetailViewModel(it.repository, saleId) }
    val loaded by vm.sale.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canceled = loaded?.value?.sale?.isCanceled == true

    SubScreen(
        title = "Venda #$saleId",
        onBack = onBack,
        actions = {
            if (loaded?.value != null && !canceled) TextButton(onClick = onEdit) { Text("Editar") }
        },
    ) { inner ->
        val data = loaded
        when {
            data == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            data.value == null -> Box(Modifier.padding(inner)) { EmptyState("Venda não encontrada.") }
            else -> {
                val sw = requireNotNull(data.value)
                val s = sw.sale
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Gaps.screen),
                    verticalArrangement = Arrangement.spacedBy(Gaps.item),
                ) {
                    if (s.isCanceled) {
                        StatusPill(
                            "Cancelada" + (s.canceledAt?.let { " em ${Periods.formatDateTime(it)}" } ?: ""),
                            dangerColor(),
                        )
                    }
                    HeroCard(label = Periods.formatDateTime(s.dateTime), value = Money.format(s.finalAmount)) {
                        val on = MaterialTheme.colorScheme.onPrimary
                        Text(
                            if (sw.isSplit) sw.paymentParts.joinToString(" + ") { (m, amount) -> "${m.label} ${Money.format(amount)}" }
                            else s.payment.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = on.copy(alpha = 0.85f),
                        )
                    }

                    SectionHeader("Baterias")
                    sw.items.forEach { item ->
                        PanelCard {
                            Text("${item.quantity}× ${item.modelSnapshot}", style = MaterialTheme.typography.titleMedium)
                            InfoRow("Preço", Money.format(item.unitPrice))
                            InfoRow("Custo", Money.format(item.unitCost))
                        }
                    }

                    SectionHeader("Valores")
                    PanelCard {
                        InfoRow("Valor", Money.format(s.grossAmount))
                        if (s.discount > 0) InfoRow("Desconto", "-" + Money.format(s.discount))
                        if (s.scrapCharge > 0) InfoRow("Casco", "+" + Money.format(s.scrapCharge))
                        if (s.cardFee > 0) InfoRow("Taxa da maquininha", "-" + Money.format(s.cardFee))
                        InfoRow("Custo", Money.format(s.totalCost))
                        if (extras.isNotEmpty()) InfoRow("Extras (custo zero)", "${extras.size} un.")
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        InfoRow("Lucro", Money.format(s.grossProfit), bold = true, valueColor = moneyResultColor(s.grossProfit))
                    }

                    if (s.hasScrapInfo) {
                        SectionHeader("Sucata")
                        PanelCard {
                            InfoRow(
                                "Deixou",
                                if (s.scrapReturned > 0) "${s.scrapReturned} · ${Scrap.format(s.scrapAmperage)}" else "Nenhuma",
                            )
                            if (s.scrapMissing > 0) InfoRow("Faltou", "${s.scrapMissing} · ${Money.format(s.scrapCharge)}")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    if (!s.isCanceled) {
                        OutlinedButton(
                            onClick = { confirmCancel = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp),
                        ) { Text("Cancelar venda") }
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Excluir do histórico", color = dangerColor()) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir venda?",
            text = "Ela some do histórico e dos relatórios. Se estava ativa, as baterias voltam ao estoque.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmCancel) {
        ConfirmDialog(
            title = "Cancelar venda?",
            text = "As baterias voltam ao estoque e a venda deixa de contar no faturamento.",
            confirmLabel = "Cancelar venda",
            destructive = true,
            onConfirm = {
                confirmCancel = false
                vm.cancel()
            },
            onDismiss = { confirmCancel = false },
        )
    }
}
