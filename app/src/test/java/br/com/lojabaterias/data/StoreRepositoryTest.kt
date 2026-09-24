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

    @Test
    fun deleteActiveSale_restoresStockAndRemovesEverything() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 2, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(2, 0, 60, 0))
        assertEquals(6, repo.getProduct(id)!!.stock)
        assertEquals(2, scrapStock(60))
        repo.deleteSale(saleId)
        assertEquals(8, repo.getProduct(id)!!.stock)
        assertEquals(0, scrapStock(60))
        assertEquals(null, repo.getSale(saleId))
        val range = br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)
        assertTrue(repo.observeStockMovementsInRange(range).first().none { it.movement.saleId == saleId })
        assertEquals(0, repo.observeSummary(range).first().count)
    }

    @Test
    fun deleteCanceledSale_doesNotChangeStockAgain() = runBlocking {
        val id = newProduct()
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.cancelSale(saleId)
        repo.deleteSale(saleId)
        assertEquals(8, repo.getProduct(id)!!.stock)
        assertEquals(0, scrapStock(60))
    }

    @Test
    fun deleteStockEntry_revertsStock_andSaleMovementsAreProtected() = runBlocking {
        val id = newProduct(stock = 2)
        repo.addStock(id, 5, null, null)
        assertEquals(7, repo.getProduct(id)!!.stock)
        val range = br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)
        val entry = repo.observeStockMovementsInRange(range).first().first { it.movement.type == MovementType.ENTRY }
        repo.deleteStockMovement(entry.movement.id)
        assertEquals(2, repo.getProduct(id)!!.stock)

        repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        val saleMove = repo.observeStockMovementsInRange(range).first().first { it.movement.type == MovementType.SALE }
        expectBusinessError { repo.deleteStockMovement(saleMove.movement.id) }
        // Excluir o estoque inicial (2) com só 1 em estoque deixaria negativo
        val initial = repo.observeStockMovementsInRange(range).first().first { it.movement.type == MovementType.INITIAL }
        expectBusinessError { repo.deleteStockMovement(initial.movement.id) }
        assertEquals(1, repo.getProduct(id)!!.stock)
    }

    @Test
    fun buyAndDeleteScrapMovements() = runBlocking {
        repo.buyScrap(60, 4, 12_000, "fornecedor")
        assertEquals(4, scrapStock(60))
        repo.sellScrap(60, 3, 15_000, null)
        val range = br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)
        val moves = repo.observeScrapMovementsInRange(range).first()
        val summary = ScrapPeriodSummary.from(emptyList(), moves)
        assertEquals(4, summary.purchasedQuantity)
        assertEquals(12_000L, summary.purchasedAmount)
        assertEquals(3, summary.soldQuantity)
        assertEquals(3_000L, summary.netAmount)
        // excluir a compra deixaria o estoque negativo (-3)
        val purchase = moves.first { it.type == ScrapMovementType.PURCHASE }
        expectBusinessError { repo.deleteScrapMovement(purchase.id) }
        val sold = moves.first { it.type == ScrapMovementType.SOLD }
        repo.deleteScrapMovement(sold.id)
        assertEquals(4, scrapStock(60))
    }

    @Test
    fun fullReport_coversSalesStockAndScrap() = runBlocking {
        val id = newProduct()
        repo.saveScrapPrice(0, 60, 5_000)
        repo.addStock(id, 2, null, null)
        val s1 = repo.registerSale(id, 2, PaymentMethod.PIX, 25_000, 1_000, 1_000, ScrapInput(1, 1, 60, 5_000))
        val s2 = repo.registerSale(id, 1, PaymentMethod.CREDITO, 28_000, 0, 2_000, ScrapInput(1, 0, 60, 0))
        repo.cancelSale(s2)
        val range = br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)
        val report = FullReport.build(
            repo.observeSales(range).first(),
            repo.observeStockMovementsInRange(range).first(),
            repo.observeScrapMovementsInRange(range).first(),
            repo.observeProducts().first(),
            repo.observeScrapStock().first(),
            mapOf(60 to 5_000L),
        )
        assertEquals(1, report.sales.salesCount)
        assertEquals(2, report.sales.unitsSold)
        assertEquals(54_000L, report.sales.revenue) // 50.000 - 1.000 + 5.000
        assertEquals(1_000L, report.discountTotal)
        assertEquals(1, report.canceledSales.size)
        assertEquals(2, report.stockPeriod.entriesQuantity)
        assertEquals(8, report.stockUnits) // 8 + 2 - 2 (a venda cancelada devolveu 1)
        assertEquals(1, report.scrap.returnedInSales)
        assertEquals(1, report.scrap.missingInSales)
        assertEquals(1, report.scrapStockQuantity)
        assertEquals(5_000L, report.scrapStockValue)
        assertTrue(s1 > 0)
    }

    @Test
    fun charge_withLoan_movesStockAndReturnsOnDelivery() = runBlocking {
        val id = newProduct(stock = 3)
        val chargeId = repo.createCharge("João", "(11) 99999-0000", "Moura 60", 1_000, 2_000, false, null, id, null)
        assertEquals(2, repo.getProduct(id)!!.stock)
        repo.deliverCharge(chargeId, PaymentMethod.PIX)
        val c = repo.getCharge(chargeId)!!
        assertEquals(ChargeStatus.DELIVERED, c.status)
        assertTrue(c.paid)
        assertEquals(3, repo.getProduct(id)!!.stock)
        // excluir desfaz empréstimo e devolução (estoque fica igual)
        repo.deleteCharge(chargeId)
        assertEquals(3, repo.getProduct(id)!!.stock)
        assertEquals(null, repo.getCharge(chargeId))
    }

    @Test
    fun charge_deleteWhileLoaned_restoresStock() = runBlocking {
        val id = newProduct(stock = 1)
        val chargeId = repo.createCharge("Maria", "", "", 1_000, 1_500, true, PaymentMethod.DINHEIRO, id, null)
        assertEquals(0, repo.getProduct(id)!!.stock)
        expectBusinessError { repo.createCharge("Outro", "", "", 1_000, 1_500, false, null, id, null) }
        repo.deleteCharge(chargeId)
        assertEquals(1, repo.getProduct(id)!!.stock)
    }

    @Test
    fun warranty_fullFactoryFlow() = runBlocking {
        val same = newProduct(stock = 4)
        val other = repo.saveProduct(
            Product(id = 0, model = "M60GD", cost = 30_000, pricePix = 45_000, priceDebit = 46_000, priceCredit = 48_000, stock = 2, amperage = 60)
        )
        val saleId = repo.registerSale(same, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        assertEquals(3, repo.getProduct(same)!!.stock)

        // Cliente volta, bateria ruim, troca pela mesma
        val w1 = repo.createWarranty(saleId, "Ana", same, "BEP60D", true, same, 0, null, null)
        assertEquals(2, repo.getProduct(same)!!.stock)
        assertEquals(WarrantyStatus.AWAITING_PICKUP, repo.observeWarranty(w1).first()!!.status)

        // Fábrica recolhe, oferece outra e recusamos, depois repõe com o M60GD (aceito)
        repo.markWarrantiesCollected(listOf(w1))
        repo.warrantyOfferRefused(w1, "Z50D", "amperagem menor")
        repo.warrantyReplaced(w1, other)
        val done = repo.observeWarranty(w1).first()!!
        assertEquals(WarrantyStatus.REPLACED, done.status)
        assertTrue(done.refusalNotes!!.contains("Z50D"))
        assertEquals(3, repo.getProduct(other)!!.stock)

        // Segunda troca: por bateria melhor com diferença; fábrica nega; vira sucata
        val w2 = repo.createWarranty(saleId, "Ana", same, "BEP60D", true, other, 20_000, PaymentMethod.PIX, null)
        assertEquals(2, repo.getProduct(other)!!.stock)
        repo.markWarrantiesCollected(listOf(w2))
        repo.warrantyDenied(w2)
        assertTrue(repo.observeWarranty(w2).first()!!.isUsedInShop)
        repo.setUsedDestination(w2, UsedDestination.SCRAP, 0, 60)
        assertEquals(2, scrapStock(60)) // 1 da venda + 1 da garantia negada
        assertEquals(false, repo.observeWarranty(w2).first()!!.isUsedInShop)

        // Teste sem defeito: nada muda no estoque
        repo.createWarranty(saleId, "", same, "BEP60D", false, null, 0, null, null)
        assertEquals(2, repo.getProduct(same)!!.stock)

        // Excluir a primeira garantia desfaz a saída e a reposição
        repo.deleteWarranty(w1)
        assertEquals(3, repo.getProduct(same)!!.stock)
        assertEquals(1, repo.getProduct(other)!!.stock)
    }

    @Test
    fun warranty_withoutStock_isRejected() = runBlocking {
        val id = newProduct(stock = 0)
        expectBusinessError { repo.createWarranty(null, "", id, "BEP60D", true, id, 0, null, null) }
    }
}
