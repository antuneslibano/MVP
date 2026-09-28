package br.com.lojabaterias.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FinanceReportTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 9, 28)
    private fun at(d: LocalDate) = d.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun sale(
        id: Long, date: LocalDate, qty: Int, unit: Long, cost: Long,
        fee: Long = 0, casco: Long = 0, canceled: Boolean = false, productId: Long = 1,
    ): SaleWithItems {
        val final = unit * qty + casco
        return SaleWithItems(
            Sale(
                id = id, dateTime = at(date), paymentMethod = "PIX", grossAmount = final, discount = 0,
                finalAmount = final, totalCost = cost + casco, grossProfit = final - cost - casco - fee,
                status = if (canceled) SaleStatus.CANCELED else SaleStatus.ACTIVE,
                scrapCharge = casco, cardFee = fee,
            ),
            listOf(SaleItem(id = id * 10, saleId = id, productId = productId, modelSnapshot = "BE50D", quantity = qty, unitPrice = unit, unitCost = cost / qty, subtotal = unit * qty)),
        )
    }

    private fun extra(id: Long, date: LocalDate, saleId: Long? = null) = WarrantyClaim(
        id = id, saleId = saleId, createdAt = at(date), returnedProductId = 1, returnedModel = "BE50D",
        defective = false, status = WarrantyStatus.EXTRA,
    )

    private fun expense(id: Long, date: LocalDate, amount: Long, category: String = "Aluguel") = Expense(
        id = id, kind = ExpenseKind.PAYMENT, category = category, description = "", amount = amount, date = at(date),
    )

    private fun build(
        period: FinancePeriod = FinancePeriod.MONTH,
        offset: Int = 0,
        sales: List<SaleWithItems> = emptyList(),
        expenses: List<Expense> = emptyList(),
        warranties: List<WarrantyClaim> = emptyList(),
    ) = FinanceReport.build(
        period, offset, today, sales, expenses, emptyList(), emptyList(), warranties,
        emptyList(), emptyList(), emptyMap(), zone,
    )

    @Test
    fun monthTotalsAndNetProfit() {
        val r = build(
            sales = listOf(
                sale(1, today, 1, 50_000, 30_000, fee = 1_000, casco = 5_000),
                sale(2, today.minusMonths(1), 1, 40_000, 30_000),
                sale(3, today, 1, 99_000, 1, canceled = true),
            ),
            expenses = listOf(expense(4, today, 8_000), expense(5, today.minusMonths(1), 2_000)),
        ).current
        assertEquals(55_000L, r.revenue)
        assertEquals(30_000L, r.batteryCost)
        assertEquals(5_000L, r.cascoCost)
        assertEquals(1_000L, r.fees)
        assertEquals(19_000L, r.grossProfit)
        assertEquals(11_000L, r.netProfit)
        assertEquals(1, r.salesCount)
    }

    @Test
    fun buckets() {
        val month = build(sales = listOf(sale(1, today, 1, 50_000, 30_000)))
        assertEquals(30, month.buckets.size)
        assertEquals(50_000L, month.buckets[27].revenue)
        assertEquals(20_000L, month.buckets[27].profit)

        val year = build(FinancePeriod.YEAR, sales = listOf(sale(1, today, 1, 50_000, 30_000)), expenses = listOf(expense(2, today, 5_000)))
        assertEquals(12, year.buckets.size)
        assertEquals(15_000L, year.buckets[8].profit)
        assertEquals("Set", year.buckets[8].label)

        val all = build(FinancePeriod.ALL)
        assertEquals(12, all.buckets.size)
        assertNull(all.previous)
    }

    @Test
    fun extrasSaleValueAndProfit() {
        val sales = listOf(
            // 2 baterias, uma delas extra (custo zero), com casco e taxa
            sale(1, today, 2, 50_000, 30_000, fee = 2_000, casco = 10_000),
            sale(2, today.minusMonths(2), 1, 40_000, 0),
            sale(3, today, 1, 45_000, 0, canceled = true),
        )
        val claims = listOf(
            extra(10, today.minusMonths(3), saleId = 1),
            extra(11, today.minusMonths(3), saleId = 2),
            extra(12, today, saleId = null),
        )
        val r = build(sales = sales, warranties = claims)
        // Venda 1: (110.000 − 10.000 de casco) / 2 = 50.000 por bateria; taxa proporcional = 2.000 × 50.000 / 110.000 = 909
        assertEquals(1, r.extrasPeriod.sold)
        assertEquals(50_000L, r.extrasPeriod.saleValue)
        assertEquals(50_000L - 909, r.extrasPeriod.profit)
        assertEquals(1, r.extrasPeriod.received)
        assertEquals(2, r.extrasAllTime.sold)
        assertEquals(90_000L, r.extrasAllTime.saleValue)
        assertEquals(90_000L - 909, r.extrasAllTime.profit)
        assertEquals(1, r.extrasInStock)
    }

    @Test
    fun cashFlowAndPosition() {
        val opening = Expense(id = 50, kind = ExpenseKind.OPENING, category = "Caixa", description = "", amount = 100_000, date = at(today.withDayOfMonth(1)))
        val withdrawal = Expense(id = 51, kind = ExpenseKind.WITHDRAWAL, category = "João", description = "", amount = 5_000, date = at(today))
        val oldSale = sale(9, today.minusMonths(2), 1, 99_000, 1) // antes do saldo inicial: não conta no caixa
        val bills = listOf(
            InvoiceBill(id = 60, invoiceId = 1, dueDate = at(today.minusDays(3)), amount = 30_000, paidAt = at(today)),
            InvoiceBill(id = 61, invoiceId = 1, dueDate = at(today.plusDays(10)), amount = 20_000),
            InvoiceBill(id = 62, invoiceId = 1, dueDate = at(today.plusDays(60)), amount = 40_000),
        )
        val r = FinanceReport.build(
            FinancePeriod.MONTH, 0, today,
            listOf(sale(1, today, 1, 50_000, 30_000, fee = 1_000), oldSale),
            listOf(expense(2, today, 8_000), opening, withdrawal),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), zone,
            invoiceBills = bills,
        )
        val c = r.current
        assertEquals(49_000L, c.cashIn)
        assertEquals(38_000L, c.cashOut)
        assertEquals(5_000L, c.withdrawals)
        assertEquals(6_000L, c.cashResult)
        // Lucro não muda com boletos nem retiradas
        assertEquals(11_000L, c.netProfit)
        assertEquals(106_000L, r.cash.now)
        assertEquals(20_000L, r.cash.upcomingBills)
        assertEquals(86_000L, r.cash.safeToWithdraw)
        assertEquals(listOf("João"), r.partners)
    }
}
