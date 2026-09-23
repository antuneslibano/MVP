package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun formatsBrazilianCurrency() {
        assertEquals("R$ 0,00", Money.format(0))
        assertEquals("R$ 0,05", Money.format(5))
        assertEquals("R$ 350,00", Money.format(35_000))
        assertEquals("R$ 1.234,56", Money.format(123_456))
        assertEquals("R$ 1.000.000,00", Money.format(100_000_000))
        assertEquals("-R$ 40,00", Money.format(-4_000))
    }

    @Test
    fun parsesDigitsAsCents() {
        assertEquals(50_000L, Money.centsFromDigits("50000"))
        assertEquals(50_000L, Money.centsFromDigits("500,00"))
        assertEquals(123_456L, Money.centsFromDigits("R$ 1.234,56"))
        assertEquals(0L, Money.centsFromDigits(""))
        assertEquals(5L, Money.centsFromDigits("0,05"))
    }
}
