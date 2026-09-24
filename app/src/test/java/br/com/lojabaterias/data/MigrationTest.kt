package br.com.lojabaterias.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Garante que a atualização do banco (versão 1 → 2) preserva todos os dados existentes
 * e que o esquema resultante é exatamente o esperado pelo Room (o Room valida ao abrir).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    /** Esquema da versão 1, idêntico ao gerado pelo Room na primeira versão do app. */
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`model` TEXT NOT NULL, `cost` INTEGER NOT NULL, `price_pix` INTEGER NOT NULL, " +
            "`price_debit` INTEGER NOT NULL, `price_credit` INTEGER NOT NULL, `stock` INTEGER NOT NULL, " +
            "`min_stock` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_products_model` ON `products` (`model`)",
        "CREATE TABLE IF NOT EXISTS `sales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date_time` INTEGER NOT NULL, `payment_method` TEXT NOT NULL, `gross_amount` INTEGER NOT NULL, " +
            "`discount` INTEGER NOT NULL, `final_amount` INTEGER NOT NULL, `total_cost` INTEGER NOT NULL, " +
            "`gross_profit` INTEGER NOT NULL, `status` TEXT NOT NULL, `canceled_at` INTEGER)",
        "CREATE INDEX IF NOT EXISTS `index_sales_date_time` ON `sales` (`date_time`)",
        "CREATE INDEX IF NOT EXISTS `index_sales_status` ON `sales` (`status`)",
        "CREATE TABLE IF NOT EXISTS `sale_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`sale_id` INTEGER NOT NULL, `product_id` INTEGER NOT NULL, `model_snapshot` TEXT NOT NULL, " +
            "`quantity` INTEGER NOT NULL, `unit_price` INTEGER NOT NULL, `unit_cost` INTEGER NOT NULL, " +
            "`subtotal` INTEGER NOT NULL, FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_sale_items_sale_id` ON `sale_items` (`sale_id`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_items_product_id` ON `sale_items` (`product_id`)",
        "CREATE TABLE IF NOT EXISTS `stock_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`product_id` INTEGER NOT NULL, `date_time` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
            "`quantity` INTEGER NOT NULL, `stock_after` INTEGER NOT NULL, `sale_id` INTEGER, `note` TEXT, " +
            "`unit_cost` INTEGER)",
        "CREATE INDEX IF NOT EXISTS `index_stock_movements_product_id` ON `stock_movements` (`product_id`)",
        "CREATE INDEX IF NOT EXISTS `index_stock_movements_date_time` ON `stock_movements` (`date_time`)",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1-identity')",
    )

    @Test
    fun migrate1To5_keepsAllData() {
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            v1Schema.forEach { raw.execSQL(it) }
            raw.execSQL(
                "INSERT INTO products (id, model, cost, price_pix, price_debit, price_credit, stock, min_stock, created_at) " +
                    "VALUES (1, 'BEP60D', 18990, 25000, 26000, 28000, 7, 2, 1000)"
            )
            raw.execSQL(
                "INSERT INTO sales (id, date_time, payment_method, gross_amount, discount, final_amount, total_cost, " +
                    "gross_profit, status, canceled_at) VALUES (1, 2000, 'PIX', 50000, 0, 50000, 37980, 12020, 'ACTIVE', NULL)"
            )
            raw.execSQL(
                "INSERT INTO sale_items (id, sale_id, product_id, model_snapshot, quantity, unit_price, unit_cost, subtotal) " +
                    "VALUES (1, 1, 1, 'BEP60D', 2, 25000, 18990, 50000)"
            )
            // venda no crédito (antes das taxas): R$ 280,00, custo R$ 189,90, lucro R$ 90,10
            raw.execSQL(
                "INSERT INTO sales (id, date_time, payment_method, gross_amount, discount, final_amount, total_cost, " +
                    "gross_profit, status, canceled_at) VALUES (2, 3000, 'CREDITO', 28000, 0, 28000, 18990, 9010, 'ACTIVE', NULL)"
            )
            raw.execSQL(
                "INSERT INTO stock_movements (id, product_id, date_time, type, quantity, stock_after, sale_id, note, unit_cost) " +
                    "VALUES (1, 1, 2000, 'SALE', -2, 7, 1, NULL, NULL)"
            )
            raw.version = 1
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        try {
            runBlocking {
                val product = db.productDao().getById(1)
                assertNotNull(product)
                assertEquals("BEP60D", product!!.model)
                assertEquals(7, product.stock)
                assertEquals(18990L, product.cost)
                assertEquals(0, product.amperage)

                val sale = db.saleDao().getWithItems(1)
                assertNotNull(sale)
                assertEquals(50000L, sale!!.sale.finalAmount)
                assertEquals(12020L, sale.sale.grossProfit)
                assertEquals(0, sale.sale.scrapReturned)
                assertEquals(0, sale.sale.scrapMissing)
                assertEquals(0L, sale.sale.scrapCharge)
                assertNull(sale.sale.scrapAmperage)
                assertEquals(2, sale.quantity)

                // v5: taxa de 7% aplicada à venda antiga no crédito, lucro recalculado; PIX sem taxa
                assertEquals(0L, sale.sale.cardFee)
                val credit = db.saleDao().getWithItems(2)!!.sale
                assertEquals(1_960L, credit.cardFee)
                assertEquals(28_000L - 18_990L - 1_960L, credit.grossProfit)
                assertTrue(credit.dirty)

                // v3: tudo marcado para envio à nuvem
                assertTrue(product.dirty)
                assertTrue(sale.sale.dirty)
                // v3: estoque conciliado com as movimentações (7 em estoque, só havia a venda de -2)
                val moves = db.movementDao().getAll()
                assertEquals(2, moves.size)
                assertEquals(7, moves.sumOf { it.quantity })
                assertEquals(MovementType.ADJUSTMENT, moves.last().type)
                assertTrue(db.scrapDao().getPrices().isEmpty())
                assertTrue(db.scrapDao().getAllMovements().isEmpty())
                assertTrue(db.syncDao().tombstones().isEmpty())
                // v4: tabelas de carga e garantias criadas vazias
                assertTrue(db.chargeDao().getAll().isEmpty())
                assertTrue(db.warrantyDao().getAll().isEmpty())
                // o recálculo pela soma das movimentações mantém o estoque
                db.syncDao().recomputeStock()
                assertEquals(7, db.productDao().getById(1)!!.stock)
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
