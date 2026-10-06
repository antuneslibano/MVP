package br.com.lojabaterias.data

import br.com.lojabaterias.domain.Report
import br.com.lojabaterias.domain.ReportCalculator

/** Posição de estoque de um modelo. */
data class StockRow(
    val model: String,
    val amperage: Int,
    val stock: Int,
    val minStock: Int,
    val cost: Long,
    val pricePix: Long,
    /** Valor a preço de custo pelos lotes (null = custo × estoque). */
    val layeredValue: Long? = null,
) {
    val valueAtCost: Long get() = layeredValue ?: (cost * stock)
    val valueAtPix: Long get() = pricePix * stock
    val isOut: Boolean get() = stock <= 0
    val isLow: Boolean get() = stock in 1..minStock
}

/** Estoque de sucatas de uma amperagem, com valor de tabela. */
data class ScrapStockRow(
    val amperage: Int,
    val quantity: Int,
    /** Valor de tabela por unidade (null se a amperagem não está na tabela). */
    val unitValue: Long?,
) {
    val totalValue: Long get() = (unitValue ?: 0) * quantity
}

/** Movimentação do estoque de baterias no período. */
data class StockPeriodSummary(
    val entriesQuantity: Int = 0,
    /** Custo das entradas (quantidade × custo unitário informado). */
    val entriesCost: Long = 0,
    val initialQuantity: Int = 0,
    val adjustmentsIn: Int = 0,
    val adjustmentsOut: Int = 0,
    val soldUnits: Int = 0,
)

/** Baterias na carga: recebidas no período e situação atual. */
data class ChargePeriodSummary(
    val received: Int = 0,
    val charged: Long = 0,
    val paid: Long = 0,
    val unpaid: Long = 0,
    /** Situação atual (independe do período). */
    val openNow: Int = 0,
    val loansOutNow: Int = 0,
    val unpaidTotalNow: Long = 0,
)

/** Quantidade de um modelo (ex.: 4× BEP60D). */
data class ModelCount(val model: String, val count: Int)

/** Garantias trocadas, extras ganhadas e baterias do Vitor no período, por modelo. */
data class WarrantyPeriodSummary(
    val exchanged: List<ModelCount> = emptyList(),
    val extras: List<ModelCount> = emptyList(),
    val vitor: List<ModelCount> = emptyList(),
    /** Total pago ao Vitor pelas baterias registradas no período. */
    val vitorPaid: Long = 0,
) {
    val exchangedTotal: Int get() = exchanged.sumOf { it.count }
    val extrasTotal: Int get() = extras.sumOf { it.count }
    val vitorTotal: Int get() = vitor.sumOf { it.count }

    companion object {
        fun byModel(list: List<WarrantyClaim>): List<ModelCount> =
            list.groupingBy { it.returnedModel }.eachCount()
                .map { (model, count) -> ModelCount(model, count) }
                .sortedWith(compareByDescending<ModelCount> { it.count }.thenBy { it.model })

        fun from(claims: List<WarrantyClaim>) = WarrantyPeriodSummary(
            exchanged = byModel(claims.filter { it.isExchange }),
            extras = byModel(claims.filter { it.isExtra }),
            vitor = byModel(claims.filter { it.isVitor }),
            vitorPaid = claims.filter { it.isVitor }.sumOf { it.replacementCost },
        )
    }
}

/** Baterias vendidas num dia, juntando os modelos iguais. */
data class DaySales(
    val date: java.time.LocalDate,
    val units: Int,
    val revenue: Long,
    /** Quantidade por modelo, do mais vendido para o menos vendido. */
    val models: List<ModelCount>,
)

/** Boleto pago no período, com a nota a que pertence. */
data class PaidBill(val bill: InvoiceBill, val invoice: Invoice)

/** Notas fiscais e boletos: o que aconteceu no período e a situação atual. */
data class InvoicePeriodSummary(
    /** Notas lançadas no período (pela data da nota). */
    val issued: List<InvoiceWithBills> = emptyList(),
    /** Notas cujas baterias chegaram no período. */
    val received: List<InvoiceWithBills> = emptyList(),
    /** Boletos pagos no período. */
    val paid: List<PaidBill> = emptyList(),
    /** Situação atual (independe do período). */
    val debt: SupplierDebt = SupplierDebt(),
    val waitingNow: Int = 0,
) {
    val issuedTotal: Long get() = issued.sumOf { it.invoice.total }
    val receivedUnits: Int get() = received.sumOf { inv -> inv.invoice.items.sumOf { it.received ?: it.quantity } }
    val paidTotal: Long get() = paid.sumOf { it.bill.amount }
    /** Bonificações (notas sem boletos) cujas baterias chegaram no período. */
    val bonus: List<InvoiceWithBills> get() = received.filter { it.isBonus }
    val bonusUnits: Int get() = bonus.sumOf { inv -> inv.invoice.items.sumOf { it.received ?: it.quantity } }
    val bonusValue: Long get() = bonus.sumOf { it.invoice.total }
}

/** Relatório completo de um período: vendas, estoque e sucatas. */
data class FullReport(
    val sales: Report = Report.EMPTY,
    /** Vendas válidas do período, mais recentes primeiro. */
    val activeSales: List<SaleWithItems> = emptyList(),
    val canceledSales: List<SaleWithItems> = emptyList(),
    val grossTotal: Long = 0,
    val discountTotal: Long = 0,
    // Estoque (posição atual)
    val stockRows: List<StockRow> = emptyList(),
    val stockPeriod: StockPeriodSummary = StockPeriodSummary(),
    val stockMovements: List<MovementWithModel> = emptyList(),
    // Sucatas
    val scrap: ScrapPeriodSummary = ScrapPeriodSummary(),
    val scrapStock: List<ScrapStockRow> = emptyList(),
    val scrapMovements: List<ScrapMovement> = emptyList(),
    // Carga e garantias
    val charges: ChargePeriodSummary = ChargePeriodSummary(),
    val chargesInPeriod: List<ChargeService> = emptyList(),
    val warranty: WarrantyPeriodSummary = WarrantyPeriodSummary(),
    // Despesas pagas no período
    val expenses: List<Expense> = emptyList(),
    // Notas fiscais e boletos
    val invoices: InvoicePeriodSummary = InvoicePeriodSummary(),
    // Retiradas dos sócios no período
    val withdrawals: List<Expense> = emptyList(),
    // Caixa (dinheiro de verdade) e extras vendidas no período
    val cash: FinanceSummary = FinanceSummary(),
    val extrasSold: ExtrasSummary = ExtrasSummary(),
    /** Baterias vendidas por dia (só os dias com venda), em ordem de data. */
    val daily: List<DaySales> = emptyList(),
) {
    /** Total de baterias por modelo no período. */
    val modelsTotal: List<ModelCount>
        get() = daily.flatMap { it.models }.groupBy { it.model }
            .map { (m, l) -> ModelCount(m, l.sumOf { it.count }) }
            .sortedWith(compareByDescending<ModelCount> { it.count }.thenBy { it.model })
    /** Média de baterias por dia, contando só os dias que tiveram venda. */
    val averagePerSalesDay: Double
        get() = if (daily.isEmpty()) 0.0 else daily.sumOf { it.units }.toDouble() / daily.size
    val withdrawalsTotal: Long get() = withdrawals.sumOf { it.amount }
    val withdrawalsByPartner: List<Pair<String, Long>>
        get() = withdrawals.groupBy { it.category }.map { (n, l) -> n to l.sumOf { it.amount } }.sortedByDescending { it.second }
    val expensesTotal: Long get() = expenses.sumOf { it.amount }
    /** Total por categoria, da maior para a menor. */
    val expensesByCategory: List<Pair<String, Long>>
        get() = expenses.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
    /** Lucro líquido = lucro bruto das vendas − despesas. */
    val netProfit: Long get() = sales.profit - expensesTotal
    val canceledAmount: Long get() = canceledSales.sumOf { it.sale.finalAmount }
    val stockUnits: Int get() = stockRows.sumOf { it.stock.coerceAtLeast(0) }
    val stockValueAtCost: Long get() = stockRows.sumOf { it.valueAtCost.coerceAtLeast(0) }
    val stockValueAtPix: Long get() = stockRows.sumOf { it.valueAtPix.coerceAtLeast(0) }
    val lowStock: List<StockRow> get() = stockRows.filter { it.isLow }
    val outOfStock: List<StockRow> get() = stockRows.filter { it.isOut }
    val scrapStockQuantity: Int get() = scrapStock.sumOf { it.quantity }
    val scrapStockValue: Long get() = scrapStock.sumOf { it.totalValue }

    companion object {
        fun build(
            salesInPeriod: List<SaleWithItems>,
            stockMovements: List<MovementWithModel>,
            scrapMovements: List<ScrapMovement>,
            products: List<Product>,
            scrapStock: List<ScrapStock>,
            scrapPrices: Map<Int, Long>,
            allCharges: List<ChargeService> = emptyList(),
            allWarranties: List<WarrantyClaim> = emptyList(),
            expenses: List<Expense> = emptyList(),
            allExpenses: List<Expense> = emptyList(),
            allInvoices: List<InvoiceWithBills> = emptyList(),
            today: java.time.LocalDate = java.time.LocalDate.now(),
            range: br.com.lojabaterias.domain.DateRange? = null,
            stockValues: Map<Long, Long> = emptyMap(),
        ): FullReport {
            fun inRange(t: Long?) = t != null && (range == null || t in range)
            val periodCharges = allCharges.filter { inRange(it.receivedAt) }
            val openCharges = allCharges.filter { it.isOpen }
            val periodClaims = allWarranties.filter { inRange(it.createdAt) }
            val all = range ?: br.com.lojabaterias.domain.DateRange(Long.MIN_VALUE, Long.MAX_VALUE)
            val withdrawals = allExpenses.filter { it.kind == ExpenseKind.WITHDRAWAL }
            val chargeSummary = ChargePeriodSummary(
                received = periodCharges.size,
                charged = periodCharges.sumOf { it.price },
                paid = periodCharges.filter { it.paid }.sumOf { it.price },
                unpaid = periodCharges.filter { !it.paid }.sumOf { it.price },
                openNow = openCharges.size,
                loansOutNow = openCharges.count { it.hasLoan },
                unpaidTotalNow = allCharges.filter { !it.paid }.sumOf { it.price },
            )
            val active = salesInPeriod.filter { !it.sale.isCanceled }
            val canceled = salesInPeriod.filter { it.sale.isCanceled }
            val report = ReportCalculator.build(active.map { StoreRepository.toReportSale(it) })
            val moves = stockMovements.map { it.movement }
            fun of(type: String) = moves.filter { it.type == type }
            val entries = of(MovementType.ENTRY)
            val adjustments = of(MovementType.ADJUSTMENT)
            return FullReport(
                sales = report,
                activeSales = active,
                canceledSales = canceled,
                grossTotal = active.sumOf { it.sale.grossAmount },
                discountTotal = active.sumOf { it.sale.discount },
                stockRows = products.map { StockRow(it.model, it.amperage, it.stock, it.minStock, it.cost, it.pricePix, stockValues[it.id]) },
                stockPeriod = StockPeriodSummary(
                    entriesQuantity = entries.sumOf { it.quantity },
                    entriesCost = entries.sumOf { it.quantity * (it.unitCost ?: 0) },
                    initialQuantity = of(MovementType.INITIAL).sumOf { it.quantity },
                    adjustmentsIn = adjustments.filter { it.quantity > 0 }.sumOf { it.quantity },
                    adjustmentsOut = -adjustments.filter { it.quantity < 0 }.sumOf { it.quantity },
                    soldUnits = report.unitsSold,
                ),
                stockMovements = stockMovements,
                scrap = ScrapPeriodSummary.from(active, scrapMovements),
                scrapStock = scrapStock.filter { it.quantity != 0 }
                    .map { ScrapStockRow(it.amperage, it.quantity, scrapPrices[it.amperage]) },
                scrapMovements = scrapMovements.filter { it.type != ScrapMovementType.VOUCHER_ISSUED },
                charges = chargeSummary,
                chargesInPeriod = periodCharges,
                warranty = WarrantyPeriodSummary.from(periodClaims),
                expenses = expenses.filter { it.kind == ExpenseKind.PAYMENT && inRange(it.date) },
                invoices = InvoicePeriodSummary(
                    issued = allInvoices.filter { inRange(it.invoice.issueDate) }.sortedBy { it.invoice.issueDate },
                    received = allInvoices.filter { it.invoice.isReceived && inRange(it.invoice.receivedAt) },
                    paid = allInvoices.flatMap { inv -> inv.bills.filter { inRange(it.paidAt) }.map { PaidBill(it, inv.invoice) } }
                        .sortedBy { it.bill.paidAt },
                    debt = SupplierDebt.from(allInvoices.flatMap { it.bills }, today),
                    waitingNow = allInvoices.count { !it.invoice.isReceived },
                ),
                withdrawals = withdrawals.filter { inRange(it.date) }.sortedBy { it.date },
                cash = FinanceReport.summarize(
                    all, active, allExpenses.filter { it.kind == ExpenseKind.PAYMENT }, scrapMovements, allCharges,
                    allInvoices.flatMap { it.bills }, withdrawals,
                ),
                extrasSold = FinanceReport.extrasSummary(allWarranties.filter { it.isExtra }, active, all),
                daily = dailySales(active),
            )
        }

        /** Agrupa as vendas por dia e, em cada dia, soma as baterias do mesmo modelo. */
        fun dailySales(sales: List<SaleWithItems>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<DaySales> =
            sales.groupBy { br.com.lojabaterias.domain.Periods.toLocalDateTime(it.sale.dateTime, zone).toLocalDate() }
                .toSortedMap()
                .map { (day, list) ->
                    val models = list.flatMap { it.items }.groupBy { it.modelSnapshot }
                        .map { (m, items) -> ModelCount(m, items.sumOf { it.quantity }) }
                        .sortedWith(compareByDescending<ModelCount> { it.count }.thenBy { it.model })
                    DaySales(day, models.sumOf { it.count }, list.sumOf { it.sale.finalAmount }, models)
                }
    }
}
