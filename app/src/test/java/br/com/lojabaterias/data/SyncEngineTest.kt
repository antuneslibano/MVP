package br.com.lojabaterias.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.lojabaterias.data.sync.CursorStore
import br.com.lojabaterias.data.sync.RemoteApi
import br.com.lojabaterias.data.sync.Session
import br.com.lojabaterias.data.sync.SyncConflictException
import br.com.lojabaterias.data.sync.SyncEngine
import br.com.lojabaterias.domain.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Nuvem falsa que imita o comportamento do Supabase (tabelas, exclusões e cursor). */
private class FakeCloud : RemoteApi {
    private var clock = 0L
    private val tables = mutableMapOf<String, MutableMap<Long, JSONObject>>()
    private val deletions = mutableMapOf<String, JSONObject>()

    override suspend fun signIn(email: String, password: String) = Session("t", "r", Long.MAX_VALUE)
    override suspend fun refresh(refreshToken: String) = Session("t", "r", Long.MAX_VALUE)

    override suspend fun upsert(token: String, table: String, rows: JSONArray) {
        for (i in 0 until rows.length()) {
            val o = JSONObject(rows.getJSONObject(i).toString())
            o.put("_v", ++clock)
            if (table == "deletions") {
                val t = o.getString("table_name")
                val id = o.getLong("record_id")
                deletions["$t:$id"] = o
                tables[t]?.remove(id)
            } else {
                val id = o.getLong("id")
                if (deletions.containsKey("$table:$id")) continue // não ressuscita excluídos
                tables.getOrPut(table) { mutableMapOf() }[id] = o
            }
        }
    }

    override suspend fun pull(token: String, since: String?): JSONObject {
        val s = since?.toLong() ?: -1L
        val result = JSONObject().put("now", clock.toString())
        for (t in listOf("products", "sales", "sale_items", "stock_movements", "scrap_prices", "scrap_movements")) {
            result.put(t, JSONArray(tables[t].orEmpty().values.filter { it.getLong("_v") > s }))
        }
        result.put("deletions", JSONArray(deletions.values.filter { it.getLong("_v") > s }))
        return result
    }
}

private class MemCursor : CursorStore {
    override var cursor: String? = null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cloud = FakeCloud()
    private val dbs = mutableListOf<AppDatabase>()

    private inner class Phone {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { dbs += it }
        val repo = StoreRepository(db)
        val engine = SyncEngine(db, cloud, MemCursor())
        suspend fun sync() = engine.sync("t")
        suspend fun product(model: String) = repo.observeProducts().first().first { it.model == model }
        suspend fun salesCount() = repo.observeSales(br.com.lojabaterias.domain.DateRange(0, Long.MAX_VALUE)).first().size
        suspend fun scrap(amperage: Int) =
            repo.observeScrapStock().first().firstOrNull { it.amperage == amperage }?.quantity ?: 0
    }

    @After
    fun tearDown() = dbs.forEach { it.close() }

    private suspend fun Phone.newProduct(stock: Int = 5): Long = repo.saveProduct(
        Product(id = 0, model = "BEP60D", cost = 18_990, pricePix = 25_000, priceDebit = 26_000, priceCredit = 28_000, stock = stock, amperage = 60)
    )

    @Test
    fun simultaneousSales_onTwoPhones_areBothCounted() = runBlocking {
        val a = Phone()
        val b = Phone()
        val id = a.newProduct(stock = 5)
        a.sync()
        b.sync()
        assertEquals(5, b.product("BEP60D").stock)

        // As duas vendem ao mesmo tempo, sem ter sincronizado entre si
        a.repo.registerSale(id, 1, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(1, 0, 60, 0))
        b.repo.registerSale(id, 1, PaymentMethod.CREDITO, 28_000, 0, 2_000, ScrapInput(0, 1, 0, 5_000))
        a.sync()
        b.sync()
        a.sync()

        assertEquals(3, a.product("BEP60D").stock)
        assertEquals(3, b.product("BEP60D").stock)
        assertEquals(2, a.salesCount())
        assertEquals(2, b.salesCount())
        assertEquals(1, b.scrap(60))
    }

    @Test
    fun deletionAndCancel_propagateToOtherPhone() = runBlocking {
        val a = Phone()
        val b = Phone()
        val id = a.newProduct(stock = 5)
        val saleId = a.repo.registerSale(id, 2, PaymentMethod.PIX, 25_000, 0, 1_000, ScrapInput(2, 0, 60, 0))
        a.sync()
        b.sync()
        assertEquals(3, b.product("BEP60D").stock)
        assertEquals(2, b.scrap(60))

        // B exclui a venda; A deve ver a exclusão
        b.repo.deleteSale(saleId)
        b.sync()
        a.sync()
        assertNull(a.repo.getSale(saleId))
        assertEquals(5, a.product("BEP60D").stock)
        assertEquals(0, a.scrap(60))
    }

    @Test
    fun lastEditWins() = runBlocking {
        val a = Phone()
        val b = Phone()
        a.newProduct()
        a.sync()
        b.sync()
        val pa = a.product("BEP60D")
        a.repo.saveProduct(pa.copy(pricePix = 26_000, updatedAt = 1))
        a.sync()
        Thread.sleep(5)
        val pb = b.product("BEP60D")
        b.repo.saveProduct(pb.copy(pricePix = 27_000))
        b.sync()
        a.sync()
        assertEquals(27_000L, a.product("BEP60D").pricePix)
        assertEquals(27_000L, b.product("BEP60D").pricePix)
    }

    @Test
    fun phoneWithOwnOldData_doesNotMixWithCloud() = runBlocking {
        val a = Phone()
        a.newProduct()
        a.sync()

        val c = Phone()
        // dado criado antes da sincronização (ID pequeno, autoincremento antigo)
        c.db.productDao().insert(
            Product(id = 1, model = "M40FD", cost = 1, pricePix = 2, priceDebit = 2, priceCredit = 2, stock = 1)
        )
        try {
            c.sync()
            fail("Esperava conflito")
        } catch (e: SyncConflictException) {
            // ok
        }
        // Depois de apagar os dados locais, baixa tudo da nuvem
        c.db.syncDao().wipeProducts()
        c.sync()
        assertEquals(5, c.product("BEP60D").stock)
    }

    @Test
    fun scrapTableAndPurchases_sync() = runBlocking {
        val a = Phone()
        val b = Phone()
        a.repo.saveScrapPrice(0, 60, 5_000)
        a.repo.buyScrap(60, 3, 9_000, null)
        a.sync()
        b.sync()
        assertEquals(3, b.scrap(60))
        assertEquals(5_000L, b.repo.observeScrapPrices().first().single().value)
    }
}
