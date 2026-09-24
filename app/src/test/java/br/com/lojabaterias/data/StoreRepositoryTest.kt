package br.com.lojabaterias.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.lojabaterias.domain.PaymentMethod
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreRepositoryTest {

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

    private suspend fun newProduct(stock: Int = 8, amperage: Int = 60): Long = repo.saveProduct(
        Product(
            model = "BEP60D",
            cost = 18_990,
            pricePix = 25_000,
            priceDebit = 26_000,
            priceCredit = 28_000,
            stock = stock,
            amperage = amperage,
        )
    )

    private suspend fun scrapStock(amperage: Int): Int =
        repo.observeScrapStock().first().firstOrNull { it.amperage == amperage }?.quantity ?: 0

    private suspend fun expectBusinessError(block: suspend () -> Unit) {
        try {
            block()
            fail("Esperava BusinessException")
        } catch (e: BusinessException) {
            // ok
        }
    }

    @Test
    fun saleWithScrapReturned_addsScrapStock() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        val sale = repo.getSale(saleId)!!.sale
        assertEquals(25_000L, sale.finalAmount)
        assertEquals(6_010L, sale.grossProfit)
        assertEquals(1, scrapStock(60))
        assertEquals(7, repo.getProduct(id)!!.stock)
    }

    @Test
    fun saleWithoutScrap_chargesScrapValue() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(0, 1, 0, 5_000))
        val sale = repo.getSale(saleId)!!.sale
        assertEquals(30_000L, sale.finalAmount)
        assertEquals(30_000L - 18_990L, sale.grossProfit)
        assertEquals(0, scrapStock(60))
    }

    @Test
    fun partialScrap_twoBatteriesOneScrap() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 2, PaymentMethod.CREDITO, 28_000, 0, 1_000, ScrapInput(1, 1, 45, 5_000))
        val sale = repo.getSale(saleId)!!.sale
        assertEquals(61_000L, sale.finalAmount)
        assertEquals(1, scrapStock(45))
        assertEquals(6, repo.getProduct(id)!!.stock)
        val summary = repo.observeSummary(br.com.lojabaterias.domain.DateRange(0, 10_000)).first()
        assertEquals(1, summary.count)
        assertEquals(2, summary.units)
    }

    @Test
    fun invalidScrapCombination_isRejected() = runBlocking {
        val id = newProduct()
        expectBusinessError { repo.registerSale(id, 2, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0)) }
        expectBusinessError { repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 0, 0)) }
        assertEquals(8, repo.getProduct(id)!!.stock)
    }

    @Test
    fun cancelSale_restoresStockAndRemovesScrap() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.cancelSale(saleId)
        assertEquals(8, repo.getProduct(id)!!.stock)
        assertEquals(0, scrapStock(60))
        val summary = repo.observeSummary(br.com.lojabaterias.domain.DateRange(0, 10_000)).first()
        assertEquals(0, summary.count)
        assertEquals(0L, summary.revenue)
    }

    @Test
    fun cancelAfterScrapSold_neverNegative() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.sellScrap(60, 1, 4_000, null)
        repo.cancelSale(saleId)
        assertEquals(0, scrapStock(60))
    }

    @Test
    fun editSale_movesScrapBetweenAmperages() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.updateSale(saleId, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 45, 0))
        assertEquals(0, scrapStock(60))
        assertEquals(1, scrapStock(45))
        repo.updateSale(saleId, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(0, 1, 0, 5_000))
        assertEquals(0, scrapStock(45))
        assertEquals(30_000L, repo.getSale(saleId)!!.sale.finalAmount)
    }

    @Test
    fun sellScrap_validatesStock() = runBlocking {
        repo.addScrap(60, 3, "compra")
        expectBusinessError { repo.sellScrap(60, 4, 10_000, null) }
        repo.sellScrap(60, 2, 8_000, null)
        assertEquals(1, scrapStock(60))
        val sold = repo.observeScrapSold(br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)).first()
        assertEquals(2, sold.quantity)
        assertEquals(8_000L, sold.amount)
        repo.adjustScrap(60, 5, null)
        assertEquals(5, scrapStock(60))
    }

    @Test
    fun historicalCost_isKeptWhenProductCostChanges() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.addStock(id, 5, 20_000, null)
        val sale = repo.getSale(saleId)!!
        assertEquals(18_990L, sale.items.first().unitCost)
        assertEquals(6_010L, sale.sale.grossProfit)
        assertEquals(20_000L, repo.getProduct(id)!!.cost)
    }

    @Test
    fun scrapPriceTable_uniqueAmperage() = runBlocking {
        repo.saveScrapPrice(0, 60, 5_000)
        expectBusinessError { repo.saveScrapPrice(0, 60, 6_000) }
        val prices = repo.observeScrapPrices().first()
        assertEquals(1, prices.size)
        assertTrue(prices.first().value == 5_000L)
    }
}
