package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFeesTest {

    @Test
    fun defaultRates() {
        val f = CardFees.DEFAULT
        assertEquals(3_780L, f.feeFor(PaymentMethod.CREDITO, 54_000)) // 7% de R$ 540,00
        assertEquals(1_020L, f.feeFor(PaymentMethod.DEBITO, 51_000))  // 2% de R$ 510,00
        assertEquals(0L, f.feeFor(PaymentMethod.PIX, 50_000))
        assertEquals(0L, f.feeFor(PaymentMethod.DINHEIRO, 50_000))
    }

    @Test
    fun roundsToNearestCent() {
        assertEquals(0L, CardFees.feeOf(7, 700))    // 0,49 centavo -> 0
        assertEquals(35L, CardFees.feeOf(499, 700)) // 34,93 -> 35
    }

    @Test
    fun profitDiscountsFee() {
        val t = SaleCalculator.compute(unitPrice = 54_000, quantity = 1, discount = 0, unitCost = 35_000, feeBps = 700)
        assertEquals(54_000L, t.finalAmount)
        assertEquals(3_780L, t.cardFee)
        assertEquals(54_000L - 35_000L - 3_780L, t.grossProfit)
    }

    @Test
    fun percentText() {
        assertEquals("7%", CardFees.formatPercent(700))
        assertEquals("2,5%", CardFees.formatPercent(250))
        assertEquals(700, CardFees.parsePercent("7"))
        assertEquals(250, CardFees.parsePercent("2,5"))
        assertEquals(399, CardFees.parsePercent("3.99%"))
        assertNull(CardFees.parsePercent("abc"))
        assertNull(CardFees.parsePercent("80"))
    }
}
