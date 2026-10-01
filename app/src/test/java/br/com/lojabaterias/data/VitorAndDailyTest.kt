package br.com.lojabaterias.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.lojabaterias.domain.CostLayer
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VitorAndDailyTest {

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

    private suspend fun product(model: String, stock: Int = 0, cost: Long = 30_000) = repo.saveProduct(
        Product(model = model, cost = cost, pricePix = 40_000, priceDebit = 41_000, priceCredit = 43_000, stock = stock)
    )

    @Test
    fun vitorBatteriesEnterStockWithTheirCost() = runBlocking {
        val he = product("HE60DD")
        repo.registerVitor(he, 2) // R$ 150 cada (padrão)
        assertEquals(2, repo.getProduct(he)!!.stock)
        assertEquals(listOf(CostLayer(2, 15_000)), repo.costLayers(he))

        // Venda de uma por R$ 400: lucro de R$ 250
        val saleId = repo.registerSale(he, 1, PaymentMethod.PIX, 40_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        val sale = repo.getSale(saleId)!!.sale
        assertEquals(15_000L, sale.totalCost)
        assertEquals(25_000L, sale.grossProfit)

        // Resumo do mês: 2 do Vitor, R$ 300 pagos; não contam como troca em garantia
        val summary = WarrantyPeriodSummary.from(repo.observeWarranties().first())
        assertEquals(2, summary.vitorTotal)
        assertEquals(30_000L, summary.vitorPaid)
        assertEquals(0, summary.exchangedTotal)

        // Excluir um registro tira do estoque
        repo.deleteWarranty(repo.observeWarranties().first().first().id)
        assertEquals(0, repo.getProduct(he)!!.stock)
    }

    @Test
    fun vitorAcceptsOnlyHeliar() = runBlocking {
        val be = product("BE50D")
        try {
            repo.registerVitor(be, 1)
            fail("Esperava BusinessException")
        } catch (e: BusinessException) {
            // ok
        }
        repo.registerVitor(product("he50ed"), 1, unitPaid = 12_000) // minúscula também vale; valor editável
        assertEquals(12_000L, repo.observeWarranties().first().single().replacementCost)
    }

    @Test
    fun dailySalesGroupsModelsPerDay() = runBlocking {
        val zone = ZoneId.systemDefault()
        val a = product("BEP60D", stock = 20)
        val b = product("BE50D", stock = 20)
        val d1 = Periods.toMillis(LocalDate.of(2026, 9, 21), zone) + 10 * 3_600_000L
        val d2 = Periods.toMillis(LocalDate.of(2026, 9, 22), zone) + 10 * 3_600_000L
        repo.registerSale(a, 2, PaymentMethod.PIX, 40_000, 0, d1, ScrapInput(2, 0, 60, 0))
        repo.registerSale(a, 2, PaymentMethod.PIX, 40_000, 0, d1 + 60_000, ScrapInput(2, 0, 60, 0))
        repo.registerSale(b, 1, PaymentMethod.PIX, 40_000, 0, d1 + 120_000, ScrapInput(1, 0, 50, 0))
        repo.registerSale(b, 3, PaymentMethod.PIX, 40_000, 0, d2, ScrapInput(3, 0, 50, 0))

        val sales = repo.observeSales(br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)).first()
        val f = FullReport.build(sales, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        assertEquals(2, f.daily.size)
        val day1 = f.daily[0]
        assertEquals(LocalDate.of(2026, 9, 21), day1.date)
        assertEquals(5, day1.units)
        assertEquals(listOf(ModelCount("BEP60D", 4), ModelCount("BE50D", 1)), day1.models)
        assertEquals(3, f.daily[1].units)
        assertEquals(4.0, f.averagePerSalesDay, 0.001) // 8 baterias em 2 dias com venda
        assertEquals(listOf(ModelCount("BE50D", 4), ModelCount("BEP60D", 4)), f.modelsTotal) // empate: ordem alfabética
        assertEquals("Seg, 21/09", Labels.dayTitle(day1.date))
        assertEquals("4", Labels.oneDecimal(4.0))
        assertEquals("10,5", Labels.oneDecimal(10.5))
    }
}
