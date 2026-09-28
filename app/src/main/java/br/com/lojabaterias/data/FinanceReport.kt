package br.com.lojabaterias.data

import br.com.lojabaterias.domain.DateRange
import br.com.lojabaterias.domain.ModelStats
import br.com.lojabaterias.domain.PaymentStats
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.ReportCalculator
import java.time.LocalDate
import java.time.ZoneId

enum class FinancePeriod(val label: String) {
    MONTH("Mês"),
    YEAR("Ano"),
    ALL("Tudo"),
}

/** Uma barra do gráfico de evolução (um dia ou um mês). */
data class FinanceBucket(
    val label: String,
    val fullLabel: String,
    val revenue: Long,
    val profit: Long,
)

/** Resultado de uma bateria extra (ganhada) vendida, por modelo. */
data class ExtraModelStats(val model: String, val sold: Int, val saleValue: Long, val profit: Long)

/** Baterias extras: quantas foram vendidas, por quanto e quanto deram de lucro. */
data class ExtrasSummary(
    val received: Int = 0,
    val sold: Int = 0,
    val saleValue: Long = 0,
    /** Custo zero: o lucro é o valor de venda menos a taxa da maquininha. */
    val profit: Long = 0,
    val byModel: List<ExtraModelStats> = emptyList(),
)

/** Tudo o que entrou e saiu num período. */
data class FinanceSummary(
    val revenue: Long = 0,
    /** Custo das baterias vendidas (sem o casco). */
    val batteryCost: Long = 0,
    /** Casco cobrado: entra no faturamento e também no custo (serve para repor o casco). */
    val cascoCost: Long = 0,
    val fees: Long = 0,
    val discounts: Long = 0,
    val expenses: Long = 0,
    val salesCount: Int = 0,
    val units: Int = 0,
    val chargesPaid: Long = 0,
    val scrapSold: Long = 0,
    val scrapPurchased: Long = 0,
    val vouchersPaid: Long = 0,
    /** Boletos de notas fiscais pagos no período (não entram no lucro: o custo já sai nas vendas). */
    val supplierPaid: Long = 0,
    val byPayment: List<PaymentStats> = emptyList(),
    val expensesByCategory: List<Pair<String, Long>> = emptyList(),
    val topModels: List<ModelStats> = emptyList(),
) {
    /** Lucro das vendas = faturamento − custo − casco − taxas. */
    val grossProfit: Long get() = revenue - batteryCost - cascoCost - fees
    /** Lucro líquido = lucro das vendas − despesas. */
    val netProfit: Long get() = grossProfit - expenses
    /** Tudo o que saiu do faturamento (custo, casco, taxas e despesas). */
    val totalOut: Long get() = batteryCost + cascoCost + fees + expenses
    val averageTicket: Long get() = if (salesCount > 0) revenue / salesCount else 0
    /** Margem: quanto sobra (lucro líquido) de cada R$ 100 vendidos. */
    val marginPercent: Int? get() = if (revenue > 0) (netProfit * 100 / revenue).toInt() else null
    /** Resultado geral = lucro líquido + carga recebida + sucatas vendidas − sucatas compradas. */
    val generalResult: Long get() = netProfit + chargesPaid + scrapSold - scrapPurchased
}

data class FinanceReport(
    val period: FinancePeriod = FinancePeriod.MONTH,
    val label: String = "",
    val current: FinanceSummary = FinanceSummary(),
    /** Período anterior (para comparar). Nulo em "Tudo". */
    val previous: FinanceSummary? = null,
    val previousLabel: String = "",
    val buckets: List<FinanceBucket> = emptyList(),
    val extrasPeriod: ExtrasSummary = ExtrasSummary(),
    val extrasAllTime: ExtrasSummary = ExtrasSummary(),
    /** Situação atual (independe do período). */
    val extrasInStock: Int = 0,
    val extrasInStockValue: Long = 0,
    val stockAtCost: Long = 0,
    val stockAtPix: Long = 0,
    val scrapStockValue: Long = 0,
    val toReceive: Long = 0,
    /** Boletos de fornecedor ainda não pagos. */
    val supplierDebt: SupplierDebt = SupplierDebt(),
) {
    companion object {
        private val MONTHS = listOf(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
        )

        private fun monthName(d: LocalDate) = "${MONTHS[d.monthValue - 1]} de ${d.year}"
        private fun shortMonth(d: LocalDate) = MONTHS[d.monthValue - 1].take(3)

        /** Início do período (mês ou ano) deslocado por [offset]. */
        fun startOf(period: FinancePeriod, today: LocalDate, offset: Int): LocalDate = when (period) {
            FinancePeriod.MONTH -> today.withDayOfMonth(1).plusMonths(offset.toLong())
            FinancePeriod.YEAR -> today.withDayOfYear(1).plusYears(offset.toLong())
            FinancePeriod.ALL -> LocalDate.of(2000, 1, 1)
        }

        fun label(period: FinancePeriod, today: LocalDate, offset: Int): String {
            val start = startOf(period, today, offset)
            return when (period) {
                FinancePeriod.MONTH -> monthName(start)
                FinancePeriod.YEAR -> "Ano de ${start.year}"
                FinancePeriod.ALL -> "Desde o começo"
            }
        }

        fun build(
            period: FinancePeriod,
            offset: Int,
            today: LocalDate,
            allSales: List<SaleWithItems>,
            expenses: List<Expense>,
            scrapMovements: List<ScrapMovement>,
            charges: List<ChargeService>,
            warranties: List<WarrantyClaim>,
            products: List<Product>,
            scrapStock: List<ScrapStock>,
            scrapPrices: Map<Int, Long>,
            zone: ZoneId = ZoneId.systemDefault(),
            invoiceBills: List<InvoiceBill> = emptyList(),
        ): FinanceReport {
            val sales = allSales.filter { !it.sale.isCanceled }
            val payments = expenses.filter { it.kind == ExpenseKind.PAYMENT }
            fun millis(d: LocalDate) = Periods.toMillis(d, zone)
            fun rangeOf(p: FinancePeriod, off: Int): DateRange = when (p) {
                FinancePeriod.MONTH -> startOf(p, today, off).let { DateRange(millis(it), millis(it.plusMonths(1))) }
                FinancePeriod.YEAR -> startOf(p, today, off).let { DateRange(millis(it), millis(it.plusYears(1))) }
                FinancePeriod.ALL -> DateRange(Long.MIN_VALUE, Long.MAX_VALUE)
            }
            fun summary(range: DateRange) = summarize(range, sales, payments, scrapMovements, charges, invoiceBills)

            val range = rangeOf(period, offset)
            val start = startOf(period, today, offset)

            // Evolução: dia a dia no mês, mês a mês no ano, últimos 12 meses em "Tudo".
            val buckets = when (period) {
                FinancePeriod.MONTH -> (0 until start.lengthOfMonth()).map { i ->
                    val d = start.plusDays(i.toLong())
                    val r = DateRange(millis(d), millis(d.plusDays(1)))
                    val s = sales.filter { it.sale.dateTime in r }
                    FinanceBucket(
                        label = d.dayOfMonth.toString(),
                        fullLabel = "Dia ${Periods.DATE.format(d)}",
                        revenue = s.sumOf { it.sale.finalAmount },
                        profit = s.sumOf { it.sale.finalAmount - it.sale.totalCost - it.sale.cardFee },
                    )
                }
                FinancePeriod.YEAR, FinancePeriod.ALL -> {
                    val first = if (period == FinancePeriod.YEAR) start else today.withDayOfMonth(1).minusMonths(11)
                    (0 until 12).map { i ->
                        val m = first.plusMonths(i.toLong())
                        val s = summary(DateRange(millis(m), millis(m.plusMonths(1))))
                        FinanceBucket(
                            label = if (period == FinancePeriod.ALL && m.monthValue == 1) "${shortMonth(m)}/${m.year % 100}" else shortMonth(m),
                            fullLabel = monthName(m),
                            revenue = s.revenue,
                            profit = s.netProfit,
                        )
                    }
                }
            }

            val extras = warranties.filter { it.isExtra }
            val productsById = products.associateBy { it.id }
            val inStock = extras.filter { it.saleId == null }

            return FinanceReport(
                period = period,
                label = label(period, today, offset),
                current = summary(range),
                previous = if (period == FinancePeriod.ALL) null else summary(rangeOf(period, offset - 1)),
                previousLabel = when (period) {
                    FinancePeriod.MONTH -> MONTHS[start.minusMonths(1).monthValue - 1].lowercase()
                    FinancePeriod.YEAR -> start.minusYears(1).year.toString()
                    FinancePeriod.ALL -> ""
                },
                buckets = buckets,
                extrasPeriod = extrasSummary(extras, sales, range),
                extrasAllTime = extrasSummary(extras, sales, DateRange(Long.MIN_VALUE, Long.MAX_VALUE)),
                extrasInStock = inStock.size,
                extrasInStockValue = inStock.sumOf { w -> w.returnedProductId?.let { productsById[it]?.pricePix } ?: 0L },
                stockAtCost = products.sumOf { (it.cost * it.stock).coerceAtLeast(0) },
                stockAtPix = products.sumOf { (it.pricePix * it.stock).coerceAtLeast(0) },
                scrapStockValue = scrapStock.sumOf { (scrapPrices[it.amperage] ?: 0L) * it.quantity.coerceAtLeast(0) },
                toReceive = charges.filter { !it.paid }.sumOf { it.price },
                supplierDebt = SupplierDebt.from(invoiceBills, today, zone),
            )
        }

        internal fun summarize(
            range: DateRange,
            activeSales: List<SaleWithItems>,
            expensePayments: List<Expense>,
            scrapMovements: List<ScrapMovement>,
            charges: List<ChargeService>,
            invoiceBills: List<InvoiceBill> = emptyList(),
        ): FinanceSummary {
            val sales = activeSales.filter { it.sale.dateTime in range }
            val report = ReportCalculator.build(sales.map { StoreRepository.toReportSale(it) })
            val casco = sales.sumOf { it.sale.scrapCharge }
            val exp = expensePayments.filter { it.date in range }
            val scrap = ScrapPeriodSummary.from(emptyList(), scrapMovements.filter { it.dateTime in range })
            return FinanceSummary(
                revenue = report.revenue,
                batteryCost = report.cost - casco,
                cascoCost = casco,
                fees = report.fees,
                discounts = sales.sumOf { it.sale.discount },
                expenses = exp.sumOf { it.amount },
                salesCount = report.salesCount,
                units = report.unitsSold,
                chargesPaid = charges.filter { it.paid && (it.paidAt ?: it.receivedAt) in range }.sumOf { it.price },
                scrapSold = scrap.soldAmount,
                scrapPurchased = scrap.purchasedAmount,
                vouchersPaid = scrap.voucherPaidAmount,
                supplierPaid = invoiceBills.filter { it.paidAt != null && it.paidAt in range }.sumOf { it.amount },
                byPayment = report.byPayment,
                expensesByCategory = exp.groupBy { it.category }
                    .map { (c, l) -> c to l.sumOf { it.amount } }
                    .sortedByDescending { it.second },
                topModels = report.topByProfit,
            )
        }

        /**
         * Extras vendidas nas vendas do período: o valor de venda é a parte da venda (sem o casco)
         * que cabe a cada bateria extra; o lucro é esse valor menos a parte da taxa da maquininha.
         */
        internal fun extrasSummary(extras: List<WarrantyClaim>, activeSales: List<SaleWithItems>, range: DateRange): ExtrasSummary {
            val salesById = activeSales.associateBy { it.sale.id }
            val perModel = LinkedHashMap<String, ExtraModelStats>()
            for ((saleId, claims) in extras.filter { it.saleId != null }.groupBy { it.saleId!! }) {
                val s = salesById[saleId] ?: continue
                if (s.sale.dateTime !in range) continue
                for ((productId, list) in claims.groupBy { it.returnedProductId }) {
                    val item = s.items.firstOrNull { it.productId == productId } ?: s.items.firstOrNull() ?: continue
                    if (item.quantity <= 0) continue
                    val base = s.sale.finalAmount - s.sale.scrapCharge
                    val subtotals = s.items.sumOf { it.subtotal }
                    val itemRevenue = if (s.items.size == 1 || subtotals == 0L) base else base * item.subtotal / subtotals
                    val n = list.size.coerceAtMost(item.quantity)
                    val value = itemRevenue * n / item.quantity
                    val fee = if (s.sale.finalAmount > 0) s.sale.cardFee * value / s.sale.finalAmount else 0
                    val old = perModel[item.modelSnapshot] ?: ExtraModelStats(item.modelSnapshot, 0, 0, 0)
                    perModel[item.modelSnapshot] = old.copy(
                        sold = old.sold + n,
                        saleValue = old.saleValue + value,
                        profit = old.profit + value - fee,
                    )
                }
            }
            val models = perModel.values.sortedByDescending { it.profit }
            return ExtrasSummary(
                received = extras.count { it.createdAt in range },
                sold = models.sumOf { it.sold },
                saleValue = models.sumOf { it.saleValue },
                profit = models.sumOf { it.profit },
                byModel = models,
            )
        }
    }
}
