package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import br.com.lojabaterias.data.WarrantyStatus
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.SectionTitle
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
    onWarranty: () -> Unit = {},
    onOpenWarranty: (Long) -> Unit = {},
) {
    val vm = appViewModel(key = "sale-$saleId") { SaleDetailViewModel(it.repository, saleId) }
    val loaded by vm.sale.collectAsStateWithLifecycle()
    val claims by vm.warranties.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    SubScreen(title = "Venda #$saleId", onBack = onBack) { inner ->
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
                        .padding(horizontal = 16.dp),
                ) {
                    if (s.isCanceled) {
                        AppCard(containerColor = dangerColor().copy(alpha = 0.12f)) {
                            Text(
                                "Venda cancelada" + (s.canceledAt?.let { " em ${Periods.formatDateTime(it)}" } ?: "") +
                                    ". Não entra nos relatórios e o estoque foi devolvido.",
                                color = dangerColor(),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    AppCard {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow("Data/hora", Periods.formatDateTime(s.dateTime))
                            InfoRow("Pagamento", s.payment.label)
                        }
                    }

                    SectionTitle("Itens")
                    sw.items.forEach { item ->
                        AppCard {
                            Column(Modifier.padding(16.dp)) {
                                Text(item.modelSnapshot, style = MaterialTheme.typography.titleLarge)
                                InfoRow("Quantidade", item.quantity.toString())
                                InfoRow("Preço unitário", Money.format(item.unitPrice))
                                InfoRow("Custo unitário (na venda)", Money.format(item.unitCost))
                                InfoRow("Subtotal", Money.format(item.subtotal))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    SectionTitle("Valores")
                    AppCard {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow("Valor bruto", Money.format(s.grossAmount))
                            InfoRow("Desconto", Money.format(s.discount))
                            if (s.scrapCharge > 0) InfoRow("Sucata faltante", Money.format(s.scrapCharge))
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            InfoRow("Valor final", Money.format(s.finalAmount), bold = true)
                            InfoRow("Custo", Money.format(s.totalCost))
                            if (s.cardFee > 0) InfoRow("Taxa da maquininha", "-" + Money.format(s.cardFee))
                            InfoRow("Lucro bruto", Money.format(s.grossProfit), valueColor = moneyResultColor(s.grossProfit))
                        }
                    }

                    SectionTitle("Garantia")
                    AppCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Vendida ${elapsedLabel(s.dateTime)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (claims.isEmpty()) {
                                Text(
                                    "Nenhuma troca ou teste de garantia registrado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            claims.forEach { w ->
                                TextButton(onClick = { onOpenWarranty(w.id) }) {
                                    Text(
                                        "${Periods.formatDate(w.createdAt)}: ${WarrantyStatus.label(w.status)}" +
                                            (w.replacementModel?.let { " (trocada por $it)" } ?: ""),
                                    )
                                }
                            }
                            if (!s.isCanceled) {
                                FilledTonalButton(
                                    onClick = onWarranty,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                ) { Text("Garantia: testar / trocar bateria") }
                            }
                        }
                    }

                    SectionTitle("Sucata")
                    AppCard {
                        Column(Modifier.padding(16.dp)) {
                            if (!s.hasScrapInfo) {
                                Text(
                                    "Não informada (venda anterior ao controle de sucatas).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                InfoRow(
                                    "Deixadas pelo cliente",
                                    if (s.scrapReturned > 0) "${s.scrapReturned} • ${Scrap.format(s.scrapAmperage)}" else "0",
                                )
                                InfoRow("Não deixadas", s.scrapMissing.toString())
                                if (s.scrapMissing > 0) InfoRow("Valor cobrado", Money.format(s.scrapCharge))
                            }
                        }
                    }

                    if (!s.isCanceled) {
                        Spacer(Modifier.height(20.dp))
                        PrimaryActionButton(text = "Editar venda", onClick = onEdit)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            OutlinedButton(
                                onClick = { confirmCancel = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                            ) { Text("Cancelar venda") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Excluir venda do histórico", color = dangerColor()) }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir venda?",
            text = "A venda será apagada definitivamente do histórico e dos relatórios, como se nunca tivesse existido. " +
                "Se ela estava ativa, as baterias voltam para o estoque e a sucata recebida sai do estoque de sucatas. " +
                "Para apenas desfazer uma venda mantendo o registro, use \"Cancelar venda\".",
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
            text = "O produto volta para o estoque e a venda deixa de contar no faturamento, custo e lucro. " +
                "Se o cliente deixou sucata, ela sai do estoque de sucatas (devolvida ao cliente).",
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
