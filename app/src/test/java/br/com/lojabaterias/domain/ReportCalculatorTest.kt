package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCalculatorTest {

    private fun sale(model: String, method: PaymentMethod, qty: Int, unit: Long, cost: Long, discount: Long = 0): ReportSale {
        val t = SaleCalculator.compute(unit, qty, discount, cost)
        return ReportSale(method, t.grossAmount, t.discount, t.finalAmount, t.totalCost,
            listOf(ReportItem(model, qty, t.grossAmount, t.totalCost)))
    }

    @Test
    fun emptyReport() {
        assertEquals(Report.EMPTY, ReportCalculator.build(emptyList()))
    }

    @Test
    fun aggregatesTotals() {
        val sales = listOf(
            sale("M60GD", PaymentMethod.PIX, 1, 50_000, 35_000),
            sale("M60GD", PaymentMethod.CREDITO, 2, 54_000, 35_000, discount = 4_000),
            sale("M40FD", PaymentMethod.DINHEIRO, 1, 40_000, 30_000),
        )
        val r = ReportCalculator.build(sales)
        assertEquals(50_000L + 104_000L + 40_000L, r.revenue)
        assertEquals(35_000L + 70_000L + 30_000L, r.cost)
        assertEquals(r.revenue - r.cost, r.profit)
        assertEquals(3, r.salesCount)
        assertEquals(4, r.unitsSold)
        assertEquals(r.revenue / 3, r.averageTicket)

        val top = r.topByQuantity.first()
        assertEquals("M60GD", top.model)
        assertEquals(3, top.quantity)
        assertEquals(154_000L, top.revenue)
        assertEquals(154_000L - 105_000L, top.profit)

        assertEquals(PaymentMethod.CREDITO, r.byPayment.first().method)
        assertEquals(3, r.byPayment.size)
    }

    @Test
    fun allocationSumsToFinalAmount() {
        val s = ReportSale(
            PaymentMethod.PIX, grossAmount = 1_000, discount = 1, finalAmount = 999, totalCost = 0,
            items = listOf(
                ReportItem("A", 1, 333, 0),
                ReportItem("B", 1, 333, 0),
                ReportItem("C", 1, 334, 0),
            ),
        )
        assertEquals(999L, ReportCalculator.allocateDiscount(s).sum())
    }
}
