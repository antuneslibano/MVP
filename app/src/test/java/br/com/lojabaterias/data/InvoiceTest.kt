package br.com.lojabaterias.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoiceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: StoreRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = StoreRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun product(): Long = repo.saveProduct(
        Product(model = "BE50D", cost = 30_000, pricePix = 45_000, priceDebit = 46_000, priceCredit = 48_000, stock = 5)
    )

    @Test
    fun saveInvoice_keepsStock_andTracksBills() = runBlocking {
        val pid = product()
        val id = repo.saveInvoice(
            id = null, number = "1234", supplier = "Fábrica", issueDate = 1_000,
            items = listOf(InvoiceItem("BE50D", 10, 31_000, pid)),
            total = 310_000,
            bills = listOf(BillDraft(dueDate = 2_000, amount = 155_000), BillDraft(dueDate = 3_000, amount = 155_000)),
            note = null,
        )
        val inv = repo.observeInvoice(id).first()!!
        assertEquals(2, inv.bills.size)
        assertEquals(310_000L, inv.openAmount)
        assertEquals(10, inv.invoice.items.single().quantity)
        // Lançar a nota não mexe no estoque nem no custo
        assertEquals(5, repo.getProduct(pid)!!.stock)
        assertEquals(30_000L, repo.getProduct(pid)!!.cost)

        repo.setInvoiceBillPaid(inv.sortedBills.first().id, true)
        // Chegaram 9 das 10: entram 9 no estoque
        repo.markInvoiceReceived(id, "caixa amassada", listOf(9), addToStock = true)
        val after = repo.observeInvoice(id).first()!!
        assertEquals(155_000L, after.openAmount)
        assertTrue(after.invoice.isReceived)
        assertEquals("Faltou: 1 BE50D • caixa amassada", after.invoice.receivedNote)
        assertEquals(14, repo.getProduct(pid)!!.stock)
        // Na chegada entra um lote novo com o custo da nota; o lote antigo continua com o custo antigo
        assertEquals(30_000L, repo.getProduct(pid)!!.cost)
        assertEquals(
            listOf(br.com.lojabaterias.domain.CostLayer(5, 30_000), br.com.lojabaterias.domain.CostLayer(9, 31_000)),
            repo.costLayers(pid),
        )
        assertEquals(5 * 30_000L + 9 * 31_000L, repo.observeStockValues().first()[pid])
        assertEquals(9, after.invoice.items.single().received)

        // Não dá para trocar as baterias de uma nota que já entrou no estoque
        try {
            repo.saveInvoice(
                id = id, number = "1234", supplier = "Fábrica", issueDate = 1_000,
                items = listOf(InvoiceItem("BE50D", 8, 31_000, pid)), total = 248_000,
                bills = listOf(BillDraft(dueDate = 2_000, amount = 248_000)), note = null,
            )
            fail("Esperava BusinessException")
        } catch (e: BusinessException) {
            // ok
        }

        // Editar mantendo o boleto pago e trocando o outro por dois
        val paid = after.sortedBills.first()
        repo.saveInvoice(
            id = id, number = "1234", supplier = "Fábrica", issueDate = 1_000,
            items = listOf(InvoiceItem("BE50D", 10, 31_000, pid)),
            total = 310_000,
            bills = listOf(
                BillDraft(paid.id, paid.dueDate, paid.amount, paid.paidAt),
                BillDraft(dueDate = 4_000, amount = 100_000),
                BillDraft(dueDate = 5_000, amount = 55_000),
            ),
            note = null,
        )
        val edited = repo.observeInvoice(id).first()!!
        assertEquals(3, edited.bills.size)
        assertEquals(1, edited.paidCount)
        assertTrue(edited.invoice.isReceived)
        assertEquals(9, edited.invoice.items.single().received)

        // Desfazer a chegada tira do estoque; marcar de novo sem estoque não mexe
        repo.markInvoiceWaiting(id)
        assertEquals(5, repo.getProduct(pid)!!.stock)
        repo.markInvoiceReceived(id, null, addToStock = false)
        assertEquals(5, repo.getProduct(pid)!!.stock)
        repo.markInvoiceWaiting(id)
        repo.markInvoiceReceived(id, null)
        assertEquals(15, repo.getProduct(pid)!!.stock)

        // Excluir a nota tira do estoque o que entrou por ela
        repo.deleteInvoice(id)
        assertEquals(5, repo.getProduct(pid)!!.stock)
        assertEquals(0, repo.observeInvoices().first().size)
        assertEquals(0, repo.observeInvoiceBills().first().size)
    }

    @Test
    fun saveInvoice_rejectsBillsThatDontMatchTotal() = runBlocking {
        try {
            repo.saveInvoice(
                id = null, number = "1", supplier = "", issueDate = 0,
                items = listOf(InvoiceItem("BE50D", 1, 30_000)), total = 30_000,
                bills = listOf(BillDraft(dueDate = 0, amount = 20_000)), note = null,
            )
            fail("Esperava BusinessException")
        } catch (e: BusinessException) {
            assertTrue(e.message!!.contains("diferente"))
        }
    }

    @Test
    fun supplierDebt() {
        val today = LocalDate.of(2026, 10, 10)
        fun at(d: LocalDate) = br.com.lojabaterias.domain.Periods.toMillis(d)
        val bills = listOf(
            InvoiceBill(id = 1, invoiceId = 1, dueDate = at(today.minusDays(1)), amount = 100),
            InvoiceBill(id = 2, invoiceId = 1, dueDate = at(today), amount = 200),
            InvoiceBill(id = 3, invoiceId = 1, dueDate = at(today.plusDays(7)), amount = 300),
            InvoiceBill(id = 4, invoiceId = 1, dueDate = at(today.plusDays(20)), amount = 400),
            InvoiceBill(id = 5, invoiceId = 1, dueDate = at(today.minusDays(5)), amount = 500, paidAt = 1),
        )
        val d = SupplierDebt.from(bills, today)
        assertEquals(1000L, d.open)
        assertEquals(100L, d.overdue)
        assertEquals(500L, d.dueSoon)
        assertEquals(2, d.dueSoonCount)
        assertEquals(listOf(334L, 333L, 333L), SupplierDebt.split(1000, 3))
    }

    @Test
    fun itemWithLineDiscount() {
        // 4 M100QD: subtotal 2.826,64 sem desconto, desconto de 56,53 na linha
        val a = InvoiceItem.fromTotals("M100QD", 4, 282_664, 5_653)
        assertEquals(277_011L, a.subtotal) // o total da nota bate no centavo
        assertEquals(69_253L, a.unitCost) // 2.770,11 ÷ 4 = 692,53 (arredondado)
        val decoded = InvoiceItems.decode(InvoiceItems.encode(listOf(a))).single()
        assertEquals(282_664L, decoded.grossTotal)
        assertEquals(5_653L, decoded.discountTotal)
        assertEquals(277_011L, decoded.subtotal)
        // Linha salva na versão com desconto por bateria
        val old = InvoiceItems.decode("""[{"model":"M100QD","quantity":4,"unit_cost":65013,"list_price":70666,"unit_discount":5653}]""").single()
        assertEquals(282_664L, old.grossTotal)
        assertEquals(22_612L, old.discountTotal)
        assertEquals(260_052L, old.subtotal)
    }

    @Test
    fun saleUsesOldestLotFirst() = runBlocking {
        val pid = product() // 5 a R$ 300,00
        repo.addStock(pid, 5, 28_000, null) // mais 5 a R$ 280,00
        val saleId = repo.registerSale(pid, 6, br.com.lojabaterias.domain.PaymentMethod.PIX, 45_000, 0, 1_000, ScrapInput(6, 0, 60, 0))
        val sale = repo.getSale(saleId)!!
        // 5 × 300 + 1 × 280 = 1.780 → 296,67 cada
        assertEquals(29_667L, sale.items.single().unitCost)
        // Sobraram 4 do lote de R$ 280
        assertEquals(listOf(br.com.lojabaterias.domain.CostLayer(4, 28_000)), repo.costLayers(pid))
    }

    @Test
    fun reportIncludesInvoicesBillsAndWithdrawals() = runBlocking {
        val pid = product()
        val id = repo.saveInvoice(
            id = null, number = "4200", supplier = "PCR Baterias Baterax", issueDate = 1_000,
            items = listOf(InvoiceItem.fromTotals("BE50D", 4, 282_664, 5_653, pid)),
            total = 277_011,
            bills = listOf(BillDraft(dueDate = 2_000, amount = 177_011), BillDraft(dueDate = 3_000, amount = 100_000)),
            note = null,
        )
        repo.setInvoiceBillPaid(repo.observeInvoice(id).first()!!.sortedBills.first().id, true, at = 5_000)
        repo.markInvoiceReceived(id, null, at = 6_000)
        repo.addWithdrawal("João", 50_000, 7_000)
        val f = FullReport.build(
            salesInPeriod = emptyList(), stockMovements = emptyList(), scrapMovements = emptyList(),
            products = repo.observeProducts().first(), scrapStock = emptyList(), scrapPrices = emptyMap(),
            range = br.com.lojabaterias.domain.DateRange(0, 10_000),
            allExpenses = repo.observeExpenses().first(),
            allInvoices = repo.observeInvoices().first(),
        )
        assertEquals(1, f.invoices.issued.size)
        assertEquals(277_011L, f.invoices.issuedTotal)
        assertEquals(1, f.invoices.received.size)
        assertEquals(4, f.invoices.receivedUnits)
        assertEquals(177_011L, f.invoices.paidTotal)
        assertEquals(100_000L, f.invoices.debt.open)
        assertEquals(50_000L, f.withdrawalsTotal)
        assertEquals(177_011L, f.cash.supplierPaid)
        assertEquals(-177_011L - 50_000L, f.cash.cashResult)
    }

    @Test
    fun bonusSpreadsPurchaseCost() {
        // 10 × R$ 380 + 1 de bonificação: R$ 3.800 ÷ 11 = R$ 345,45 cada
        val bought = listOf(InvoiceItem("M100", 10, 38_000, productId = 1, grossTotal = 380_000))
        val r = BonusCost.spread(bought, listOf(listOf(InvoiceItem("M100", 1, 38_000, productId = 1, grossTotal = 38_000))))
        assertEquals(listOf(34_545L), r.purchase)
        assertEquals(listOf(listOf(34_545L)), r.bonuses)
        // Bonificação sem valor na nota: usa o valor da mesma bateria na compra
        val noValue = BonusCost.spread(bought, listOf(listOf(InvoiceItem("M100", 1, 0, productId = 1))))
        assertEquals(listOf(34_545L), noValue.purchase)
        assertEquals(listOf(listOf(34_545L)), noValue.bonuses)
        // Sem bonificação nada muda
        assertEquals(listOf(38_000L), BonusCost.spread(bought, emptyList()).purchase)
    }

    @Test
    fun invoiceWithoutBills_isBonus_andLowersPurchaseCost() = runBlocking {
        val pid = product()
        val purchase = repo.saveInvoice(
            id = null, number = "100", supplier = "Oeste Rio Distribuidora Moura", issueDate = 1_000,
            items = listOf(InvoiceItem.fromTotals("BE50D", 10, 380_000, 0, pid)),
            total = 380_000, bills = listOf(BillDraft(dueDate = 2_000, amount = 380_000)), note = null,
        )
        // Bonificação precisa dizer qual nota abate
        try {
            repo.saveInvoice(
                id = null, number = "101", supplier = "Oeste Rio Distribuidora Moura", issueDate = 1_000,
                items = listOf(InvoiceItem.fromTotals("BE50D", 1, 38_000, 0, pid)), total = 38_000,
                bills = emptyList(), note = null,
            )
            fail("Esperava BusinessException")
        } catch (e: BusinessException) {
            assertTrue(e.message!!.contains("nota de compra"))
        }
        val bonus = repo.saveInvoice(
            id = null, number = "101", supplier = "Oeste Rio Distribuidora Moura", issueDate = 1_000,
            items = listOf(InvoiceItem.fromTotals("BE50D", 1, 38_000, 0, pid)), total = 38_000,
            bills = emptyList(), note = null, bonusFor = purchase,
        )
        val inv = repo.observeInvoice(bonus).first()!!
        assertTrue(inv.isBonus)
        assertEquals(0L, inv.openAmount)

        repo.markInvoiceReceived(purchase, null, at = 5_000)
        assertEquals(
            listOf(br.com.lojabaterias.domain.CostLayer(5, 30_000), br.com.lojabaterias.domain.CostLayer(10, 38_000)),
            repo.costLayers(pid),
        )
        // A bonificação chegou: as 11 dividem os R$ 3.800
        repo.markInvoiceReceived(bonus, null, at = 6_000)
        assertEquals(16, repo.getProduct(pid)!!.stock)
        assertEquals(
            listOf(br.com.lojabaterias.domain.CostLayer(5, 30_000), br.com.lojabaterias.domain.CostLayer(11, 34_545)),
            repo.costLayers(pid),
        )
        assertEquals(5 * 30_000L + 11 * 34_545L, repo.observeStockValues().first()[pid])

        // A nota de compra não pode ser excluída com a bonificação ligada
        try {
            repo.deleteInvoice(purchase)
            fail("Esperava BusinessException")
        } catch (e: BusinessException) {
            assertTrue(e.message!!.contains("bonificação"))
        }

        val f = FullReport.build(
            salesInPeriod = emptyList(), stockMovements = emptyList(), scrapMovements = emptyList(),
            products = repo.observeProducts().first(), scrapStock = emptyList(), scrapPrices = emptyMap(),
            range = br.com.lojabaterias.domain.DateRange(0, 10_000),
            allExpenses = repo.observeExpenses().first(),
            allInvoices = repo.observeInvoices().first(),
        )
        assertEquals(1, f.invoices.bonusUnits)
        assertEquals(38_000L, f.invoices.bonusValue)
        assertEquals(380_000L, f.invoices.debt.open)

        // Desfazer a chegada da bonificação volta o custo da compra
        repo.markInvoiceWaiting(bonus)
        assertEquals(
            listOf(br.com.lojabaterias.domain.CostLayer(5, 30_000), br.com.lojabaterias.domain.CostLayer(10, 38_000)),
            repo.costLayers(pid),
        )
        repo.deleteInvoice(bonus)
        repo.deleteInvoice(purchase)
        assertEquals(5, repo.getProduct(pid)!!.stock)
    }
}
