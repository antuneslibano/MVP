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
    fun charge_withLoan_doesNotTouchStock() = runBlocking {
        val id = newProduct(stock = 3)
        val chargeId = repo.createCharge("João", "(11) 99999-0000", "B45D", 1_000, 2_000, false, null, true, "Levou 01 Júpiter")
        assertEquals(3, repo.getProduct(id)!!.stock)
        assertTrue(repo.getCharge(chargeId)!!.hasLoan)
        repo.deliverCharge(chargeId, PaymentMethod.PIX)
        val c = repo.getCharge(chargeId)!!
        assertEquals(ChargeStatus.DELIVERED, c.status)
        assertTrue(c.paid)
        assertEquals(3, repo.getProduct(id)!!.stock)
        // edição pode desmarcar o empréstimo
        repo.updateCharge(chargeId, "João", "", "B45D", 1_000, 2_000, true, PaymentMethod.PIX, null, loaned = false)
        assertEquals(false, repo.getCharge(chargeId)!!.hasLoan)
        repo.deleteCharge(chargeId)
        assertEquals(null, repo.getCharge(chargeId))
        assertEquals(3, repo.getProduct(id)!!.stock)
    }

    @Test
    fun warrantyExchange_onlyCounts_withoutTouchingStock() = runBlocking {
        val id = newProduct(stock = 4)
        repo.registerExchange(id, 3)
        assertEquals(4, repo.getProduct(id)!!.stock)
        val all = repo.observeWarranties().first()
        assertEquals(3, all.size)
        assertTrue(all.all { it.status == WarrantyStatus.EXCHANGE && it.returnedModel == "BEP60D" })
        assertEquals(listOf(ModelCount("BEP60D", 3)), WarrantyPeriodSummary.from(all).exchanged)

        repo.deleteWarranty(all.first().id)
        assertEquals(2, repo.observeWarranties().first().size)
        assertEquals(4, repo.getProduct(id)!!.stock)
    }

    @Test
    fun extra_entersStockAndIsSoldWithZeroCost() = runBlocking {
        val id = newProduct(stock = 2)
        repo.registerExtra(id, 1)
        assertEquals(3, repo.getProduct(id)!!.stock)
        assertEquals(1, repo.freeExtraCount(id))

        // Venda de 2: uma sai de graça (extra), a outra com o custo normal
        val saleId = repo.registerSale(id, 2, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(2, 0, 60, 0))
        assertEquals(18_990L, repo.getSale(saleId)!!.sale.totalCost)
        assertEquals(0, repo.freeExtraCount(id))
        assertEquals(1, repo.freeExtraCount(id, saleId))
        val extra = repo.observeWarranties().first().single()
        assertEquals(saleId, extra.saleId)
        expectBusinessError { repo.deleteWarranty(extra.id) }

        // Editar a venda para 1 unidade: a extra continua nela, custo zero
        repo.updateSale(saleId, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        assertEquals(0L, repo.getSale(saleId)!!.sale.totalCost)

        // Cancelar devolve a extra
        repo.cancelSale(saleId)
        assertEquals(1, repo.freeExtraCount(id))
        assertEquals(3, repo.getProduct(id)!!.stock)

        // Excluir a extra (ainda no estoque) tira a bateria do estoque
        repo.deleteWarranty(extra.id)
        assertEquals(2, repo.getProduct(id)!!.stock)
        assertEquals(0, repo.freeExtraCount(id))
    }

    @Test
    fun lostExtras_areRebuiltFromStock_withoutCountingTwice() = runBlocking {
        val id = newProduct(stock = 2)
        repo.registerExtra(id, 2)
        val saleId = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        assertEquals(0L, repo.getSale(saleId)!!.sale.totalCost)

        // Os registros das extras somem (a entrada no estoque e a venda continuam)
        db.warrantyDao().deleteAll()
        assertEquals(0, repo.freeExtraCount(id))

        repo.repairExtras()
        val extras = repo.observeWarranties().first()
        assertEquals(2, extras.size)
        assertTrue(extras.all { it.isExtra && it.returnedModel == "BEP60D" })
        assertEquals(1, extras.count { it.saleId == saleId })
        assertEquals(1, repo.freeExtraCount(id))
        assertEquals(3, repo.getProduct(id)!!.stock)

        // Rodar de novo não duplica
        repo.repairExtras()
        assertEquals(2, repo.observeWarranties().first().size)
    }

    @Test
    fun scrapVoucher_isPaidWhenCustomerBringsTheCasco() = runBlocking {
        val id = newProduct(stock = 4)
        // Sem vale: o casco fica pago e não aparece nos vales
        val noVoucher = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(0, 1, 0, 3_000))
        expectBusinessError { repo.payVoucher(noVoucher, 1, 60) }
        // Cliente levou 2 baterias sem deixar sucata, pagou R$ 60,00 pelos cascos e levou vale
        val saleId = repo.registerSale(id, 2, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(0, 2, 0, 6_000, voucher = true))
        assertTrue(repo.hasVoucher(saleId))
        assertEquals(false, repo.hasVoucher(noVoucher))
        suspend fun open() = Vouchers.open(
            repo.observeSales(br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)).first(),
            repo.observeScrapMovementsInRange(br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)).first(),
        )
        assertEquals(6_000L, open().single().value)

        // Trouxe 1 casco: devolve R$ 30,00, o vale continua com 1
        repo.payVoucher(saleId, 1, 60)
        assertEquals(1, open().single().remaining)
        assertEquals(1, scrapStock(60))
        expectBusinessError { repo.payVoucher(saleId, 2, 60) }

        // Trouxe o outro: vale quitado
        repo.payVoucher(saleId, 1, 60)
        assertTrue(open().isEmpty())
        assertEquals(2, scrapStock(60))
        val paid = repo.observeScrapMovementsInRange(br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)).first()
            .filter { it.type == ScrapMovementType.VOUCHER_PAID }
        assertEquals(6_000L, paid.sumOf { it.amount })
        expectBusinessError { repo.payVoucher(saleId, 1, 60) }
    }

    @Test
    fun cardFees_areDiscountedFromProfit() = runBlocking {
        val id = newProduct()
        val credit = repo.registerSale(id, 1, PaymentMethod.CREDITO, 28_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        val debit = repo.registerSale(id, 1, PaymentMethod.DEBITO, 26_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        val pix = repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.getSale(credit)!!.sale.let {
            assertEquals(1_960L, it.cardFee)
            assertEquals(28_000L - 18_990L - 1_960L, it.grossProfit)
        }
        repo.getSale(debit)!!.sale.let {
            assertEquals(520L, it.cardFee)
            assertEquals(26_000L - 18_990L - 520L, it.grossProfit)
        }
        assertEquals(0L, repo.getSale(pix)!!.sale.cardFee)
        // Editar para PIX remove a taxa
        repo.updateSale(credit, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        repo.getSale(credit)!!.sale.let {
            assertEquals(0L, it.cardFee)
            assertEquals(25_000L - 18_990L, it.grossProfit)
        }
    }
}
