package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScrapTest {

    @Test
    fun guessesAmperageFromModel() {
        assertEquals(60, Scrap.guessAmperage("BEP60D"))
        assertEquals(60, Scrap.guessAmperage("M60GD"))
        assertEquals(45, Scrap.guessAmperage("BF45D"))
        assertEquals(100, Scrap.guessAmperage("M100HE"))
        assertNull(Scrap.guessAmperage("XPTO"))
    }

    @Test
    fun scrapChargeIsPartOfSaleTotal() {
        val t = SaleCalculator.compute(unitPrice = 25_000, quantity = 1, discount = 0, unitCost = 18_990, scrapCharge = 5_000)
        assertEquals(30_000L, t.finalAmount)
        assertEquals(11_010L, t.grossProfit)
    }

    @Test
    fun validatesScrapInfo() {
        assertNull(SaleCalculator.validateScrap(2, 2, 0, 60, 0))
        assertNull(SaleCalculator.validateScrap(2, 1, 1, 60, 5_000))
        assertNull(SaleCalculator.validateScrap(2, 0, 2, null, 0))
        assertNull(SaleCalculator.validateScrap(2, 0, 0, null, 0)) // não informado
        assertNotNull(SaleCalculator.validateScrap(2, 1, 0, 60, 0))
        assertNotNull(SaleCalculator.validateScrap(1, 1, 0, null, 0))
        assertNotNull(SaleCalculator.validateScrap(1, 1, 0, 60, 5_000))
    }
}
