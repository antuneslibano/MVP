package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SaleCalculatorTest {

    private val prices = PriceTable(pix = 50_000, debit = 51_000, credit = 54_000)

    @Test
    fun priceFollowsPaymentMethod() {
        assertEquals(50_000L, prices.priceFor(PaymentMethod.PIX))
        assertEquals(51_000L, prices.priceFor(PaymentMethod.DEBITO))
        assertEquals(54_000L, prices.priceFor(PaymentMethod.CREDITO))
        assertEquals(50_000L, prices.priceFor(PaymentMethod.DINHEIRO))
    }

    @Test
    fun computesProfitWithHistoricalCost() {
        val t = SaleCalculator.compute(unitPrice = 50_000, quantity = 1, discount = 0, unitCost = 35_000)
        assertEquals(50_000L, t.finalAmount)
        assertEquals(35_000L, t.totalCost)
        assertEquals(15_000L, t.grossProfit)
    }

    @Test
    fun computesWithQuantityAndDiscount() {
        val t = SaleCalculator.compute(unitPrice = 54_000, quantity = 2, discount = 3_000, unitCost = 35_000)
        assertEquals(108_000L, t.grossAmount)
        assertEquals(105_000L, t.finalAmount)
        assertEquals(70_000L, t.totalCost)
        assertEquals(35_000L, t.grossProfit)
    }

    @Test
    fun validatesInput() {
        assertNull(SaleCalculator.validate(50_000, 2, 0, availableStock = 8))
        assertNotNull(SaleCalculator.validate(50_000, 9, 0, availableStock = 8))
        assertNotNull(SaleCalculator.validate(50_000, 0, 0, availableStock = 8))
        assertNotNull(SaleCalculator.validate(0, 1, 0, availableStock = 8))
        assertNotNull(SaleCalculator.validate(50_000, 1, 60_000, availableStock = 8))
    }
}
