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
            received = false, note = null, updateCosts = true,
        )
        val inv = repo.observeInvoice(id).first()!!
        assertEquals(2, inv.bills.size)
        assertEquals(310_000L, inv.openAmount)
        assertEquals(10, inv.invoice.items.single().quantity)
        // Estoque continua manual; o custo foi atualizado
        assertEquals(5, repo.getProduct(pid)!!.stock)
        assertEquals(31_000L, repo.getProduct(pid)!!.cost)

        repo.setInvoiceBillPaid(inv.sortedBills.first().id, true)
        repo.markInvoiceReceived(id, "faltou 1")
        val after = repo.observeInvoice(id).first()!!
        assertEquals(155_000L, after.openAmount)
        assertTrue(after.invoice.isReceived)
        assertEquals("faltou 1", after.invoice.receivedNote)

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
            received = true, note = null,
        )
        val edited = repo.observeInvoice(id).first()!!
        assertEquals(3, edited.bills.size)
        assertEquals(1, edited.paidCount)
        assertEquals("faltou 1", edited.invoice.receivedNote)

        repo.deleteInvoice(id)
        assertEquals(0, repo.observeInvoices().first().size)
        assertEquals(0, repo.observeInvoiceBills().first().size)
    }

    @Test
    fun saveInvoice_rejectsBillsThatDontMatchTotal() = runBlocking {
        try {
            repo.saveInvoice(
                id = null, number = "1", supplier = "", issueDate = 0,
                items = listOf(InvoiceItem("BE50D", 1, 30_000)), total = 30_000,
                bills = listOf(BillDraft(dueDate = 0, amount = 20_000)), received = false, note = null,
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
}
