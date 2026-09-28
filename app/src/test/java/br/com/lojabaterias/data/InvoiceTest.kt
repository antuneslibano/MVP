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
            note = null, updateCosts = true,
        )
        val inv = repo.observeInvoice(id).first()!!
        assertEquals(2, inv.bills.size)
        assertEquals(310_000L, inv.openAmount)
        assertEquals(10, inv.invoice.items.single().quantity)
        // Lançar a nota não mexe no estoque; o custo foi atualizado
        assertEquals(5, repo.getProduct(pid)!!.stock)
        assertEquals(31_000L, repo.getProduct(pid)!!.cost)

        repo.setInvoiceBillPaid(inv.sortedBills.first().id, true)
        // Chegaram 9 das 10: entram 9 no estoque
        repo.markInvoiceReceived(id, "caixa amassada", listOf(9), addToStock = true)
        val after = repo.observeInvoice(id).first()!!
        assertEquals(155_000L, after.openAmount)
        assertTrue(after.invoice.isReceived)
        assertEquals("Faltou: 1 BE50D • caixa amassada", after.invoice.receivedNote)
        assertEquals(14, repo.getProduct(pid)!!.stock)
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
    fun itemFromSubtotal() {
        val a = InvoiceItem.fromSubtotal("M100QD", 4, 282_664)
        assertEquals(70_666L, a.unitCost)
        assertEquals(282_664L, a.subtotal)
        val b = InvoiceItem.fromSubtotal("BE50D", 3, 100_000)
        assertEquals(33_333L, b.unitCost)
        assertEquals(100_000L, b.subtotal) // o total da nota continua exato
        val decoded = InvoiceItems.decode(InvoiceItems.encode(listOf(b))).single()
        assertEquals(100_000L, decoded.subtotal)
    }
}
