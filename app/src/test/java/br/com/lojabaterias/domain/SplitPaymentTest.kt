package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitPaymentTest {
    private val be50d = PriceTable(pix = 21_000, debit = 22_000, credit = 24_000)

    @Test
    fun suggestionsForTheRestOfThePayment() {
        val paid = listOf(PaymentMethod.DINHEIRO to 10_000L)
        // Pelo preço à vista falta R$ 110; pelo preço do crédito, R$ 140; proporcional, R$ 125,71
        assertEquals(11_000L, SplitPayment.remaining(be50d.pix, 10_000))
        assertEquals(14_000L, SplitPayment.remaining(be50d.credit, 10_000))
        assertEquals(12_571L, SplitPayment.proportional(be50d, 1, 0, paid, PaymentMethod.CREDITO))
    }

    @Test
    fun totalIsWhatWasPaidAndFeeOnlyOnCard() {
        val parts = listOf(PaymentMethod.DINHEIRO to 10_000L, PaymentMethod.CREDITO to 14_000L)
        val t = SplitPayment.compute(21_000, 1, 15_000, 0, parts, { if (it == PaymentMethod.CREDITO) 700 else 0 })
        assertEquals(24_000L, t.finalAmount)
        assertEquals(980L, t.cardFee) // 7% de R$ 140
        assertEquals(0L, t.discount)
        assertEquals(24_000L - 15_000L - 980L, t.grossProfit)

        // Cobrou menos que o à vista: vira desconto
        val cheap = SplitPayment.compute(21_000, 1, 15_000, 0, listOf(PaymentMethod.PIX to 10_000L, PaymentMethod.DINHEIRO to 10_000L), { 0 })
        assertEquals(1_000L, cheap.discount)
        assertEquals(20_000L, cheap.finalAmount)
    }

    @Test
    fun validation() {
        assertEquals("Adicione pelo menos duas formas de pagamento", SplitPayment.validate(listOf(PaymentMethod.PIX to 1L)))
        assertNull(SplitPayment.validate(listOf(PaymentMethod.PIX to 1L, PaymentMethod.CREDITO to 1L)))
    }
}
