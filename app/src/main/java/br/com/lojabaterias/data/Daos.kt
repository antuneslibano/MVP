package br.com.lojabaterias.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY model COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Query("SELECT * FROM products WHERE model = :model COLLATE NOCASE LIMIT 1")
    suspend fun findByModel(model: String): Product?

    @Query("SELECT * FROM products ORDER BY id")
    suspend fun getAll(): List<Product>

    @Insert
    suspend fun insert(product: Product): Long

    @Insert
    suspend fun insertAll(products: List<Product>)

    @Update
    suspend fun update(product: Product)

    @Query("UPDATE products SET stock = :stock WHERE id = :id")
    suspend fun updateStock(id: Long, stock: Int)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertItem(item: SaleItem): Long

    @Insert
    suspend fun insertSales(sales: List<Sale>)

    @Insert
    suspend fun insertItems(items: List<SaleItem>)

    @Update
    suspend fun updateSale(sale: Sale)

    @Update
    suspend fun updateItem(item: SaleItem)

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getWithItems(id: Long): SaleWithItems?

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :id")
    fun observeWithItems(id: Long): Flow<SaleWithItems?>

    @Transaction
    @Query("SELECT * FROM sales ORDER BY date_time DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE date_time >= :start AND date_time < :end ORDER BY date_time DESC, id DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<SaleWithItems>>

    @Transaction
    @Query(
        "SELECT * FROM sales WHERE status = 'ACTIVE' AND date_time >= :start AND date_time < :end " +
            "ORDER BY date_time DESC"
    )
    fun observeActiveInRange(start: Long, end: Long): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE status = 'ACTIVE' ORDER BY date_time")
    suspend fun getActiveWithItems(): List<SaleWithItems>

    @Query(
        "SELECT COUNT(*) AS count, COALESCE(SUM(final_amount), 0) AS revenue, " +
            "COALESCE(SUM(gross_profit), 0) AS profit, " +
            "COALESCE((SELECT SUM(i.quantity) FROM sale_items i INNER JOIN sales s2 ON s2.id = i.sale_id " +
            "WHERE s2.status = 'ACTIVE' AND s2.date_time >= :start AND s2.date_time < :end), 0) AS units " +
            "FROM sales WHERE status = 'ACTIVE' AND date_time >= :start AND date_time < :end"
    )
    fun observeSummary(start: Long, end: Long): Flow<PeriodSummary>

    @Query("SELECT * FROM sales ORDER BY id")
    suspend fun getAllSales(): List<Sale>

    @Query("SELECT * FROM sale_items ORDER BY id")
    suspend fun getAllItems(): List<SaleItem>

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM sale_items WHERE sale_id = :saleId")
    suspend fun deleteItemsForSale(saleId: Long)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteSale(saleId: Long)

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()
}

data class MovementWithModel(
    @Embedded val movement: StockMovement,
    val model: String?,
)

@Dao
interface MovementDao {
    @Insert
    suspend fun insert(movement: StockMovement): Long

    @Insert
    suspend fun insertAll(movements: List<StockMovement>)

    @Query(
        "SELECT m.*, p.model AS model FROM stock_movements m " +
            "LEFT JOIN products p ON p.id = m.product_id " +
            "WHERE m.product_id = :productId ORDER BY m.date_time DESC, m.id DESC"
    )
    fun observeForProduct(productId: Long): Flow<List<MovementWithModel>>

    @Query(
        "SELECT m.*, p.model AS model FROM stock_movements m " +
            "LEFT JOIN products p ON p.id = m.product_id " +
            "ORDER BY m.date_time DESC, m.id DESC LIMIT :limit"
    )
    fun observeRecent(limit: Int): Flow<List<MovementWithModel>>

    @Query("SELECT * FROM stock_movements ORDER BY id")
    suspend fun getAll(): List<StockMovement>

    @Query(
        "SELECT m.*, p.model AS model FROM stock_movements m " +
            "LEFT JOIN products p ON p.id = m.product_id " +
            "WHERE m.date_time >= :start AND m.date_time < :end ORDER BY m.date_time DESC, m.id DESC"
    )
    fun observeInRange(start: Long, end: Long): Flow<List<MovementWithModel>>

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getById(id: Long): StockMovement?

    @Query("DELETE FROM stock_movements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM stock_movements WHERE sale_id = :saleId")
    suspend fun deleteForSale(saleId: Long)

    @Query("DELETE FROM stock_movements WHERE product_id = :productId")
    suspend fun deleteForProduct(productId: Long)

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAll()
}

@Dao
interface ScrapDao {
    // ----- Tabela de valores
    @Query("SELECT * FROM scrap_prices ORDER BY amperage")
    fun observePrices(): Flow<List<ScrapPrice>>

    @Query("SELECT * FROM scrap_prices ORDER BY amperage")
    suspend fun getPrices(): List<ScrapPrice>

    @Query("SELECT * FROM scrap_prices WHERE amperage = :amperage LIMIT 1")
    suspend fun findPrice(amperage: Int): ScrapPrice?

    @Insert
    suspend fun insertPrice(price: ScrapPrice): Long

    @Insert
    suspend fun insertPrices(prices: List<ScrapPrice>)

    @Update
    suspend fun updatePrice(price: ScrapPrice)

    @Query("DELETE FROM scrap_prices WHERE id = :id")
    suspend fun deletePrice(id: Long)

    @Query("DELETE FROM scrap_prices")
    suspend fun deleteAllPrices()

    // ----- Movimentações / estoque
    @Insert
    suspend fun insertMovement(movement: ScrapMovement): Long

    @Insert
    suspend fun insertMovements(movements: List<ScrapMovement>)

    @Query(
        "SELECT amperage, SUM(quantity) AS quantity FROM scrap_movements " +
            "GROUP BY amperage HAVING SUM(quantity) != 0 ORDER BY amperage"
    )
    fun observeStock(): Flow<List<ScrapStock>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM scrap_movements WHERE amperage = :amperage")
    suspend fun stockOf(amperage: Int): Int

    @Query("SELECT * FROM scrap_movements ORDER BY date_time DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScrapMovement>>

    @Query(
        "SELECT COALESCE(-SUM(quantity), 0) AS quantity, COALESCE(SUM(amount), 0) AS amount " +
            "FROM scrap_movements WHERE type = 'SOLD' AND date_time >= :start AND date_time < :end"
    )
    fun observeSold(start: Long, end: Long): Flow<ScrapSoldSummary>

    @Query("SELECT * FROM scrap_movements ORDER BY id")
    suspend fun getAllMovements(): List<ScrapMovement>

    @Query(
        "SELECT * FROM scrap_movements WHERE date_time >= :start AND date_time < :end " +
            "ORDER BY date_time DESC, id DESC"
    )
    fun observeInRange(start: Long, end: Long): Flow<List<ScrapMovement>>

    @Query("SELECT * FROM scrap_movements WHERE id = :id")
    suspend fun getMovement(id: Long): ScrapMovement?

    @Query("DELETE FROM scrap_movements WHERE id = :id")
    suspend fun deleteMovement(id: Long)

    @Query("DELETE FROM scrap_movements WHERE sale_id = :saleId")
    suspend fun deleteForSale(saleId: Long)

    @Query("DELETE FROM scrap_movements")
    suspend fun deleteAllMovements()
}

/** Consultas usadas pela sincronização com a nuvem. */
@Dao
interface SyncDao {
    // ----- Pendências (alterações locais ainda não enviadas)
    @Query("SELECT * FROM products WHERE dirty = 1") suspend fun dirtyProducts(): List<Product>
    @Query("SELECT * FROM sales WHERE dirty = 1") suspend fun dirtySales(): List<Sale>
    @Query("SELECT * FROM sale_items WHERE dirty = 1") suspend fun dirtySaleItems(): List<SaleItem>
    @Query("SELECT * FROM stock_movements WHERE dirty = 1") suspend fun dirtyStockMovements(): List<StockMovement>
    @Query("SELECT * FROM scrap_prices WHERE dirty = 1") suspend fun dirtyScrapPrices(): List<ScrapPrice>
    @Query("SELECT * FROM scrap_movements WHERE dirty = 1") suspend fun dirtyScrapMovements(): List<ScrapMovement>

    @Query(
        "SELECT (SELECT COUNT(*) FROM products WHERE dirty = 1) + (SELECT COUNT(*) FROM sales WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM sale_items WHERE dirty = 1) + (SELECT COUNT(*) FROM stock_movements WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM scrap_prices WHERE dirty = 1) + (SELECT COUNT(*) FROM scrap_movements WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM charge_services WHERE dirty = 1) + (SELECT COUNT(*) FROM warranty_claims WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM expenses WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM tombstones)"
    )
    suspend fun pendingCount(): Int

    /** Dados criados antes da sincronização (IDs pequenos) que ainda não foram enviados. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM products WHERE dirty = 1 AND id < 1000000000) + " +
            "(SELECT COUNT(*) FROM sales WHERE dirty = 1 AND id < 1000000000)"
    )
    suspend fun legacyDirtyCount(): Int

    @Query("SELECT (SELECT COUNT(*) FROM products) + (SELECT COUNT(*) FROM sales) + (SELECT COUNT(*) FROM scrap_movements)")
    suspend fun localDataCount(): Int

    @Query("SELECT * FROM charge_services WHERE dirty = 1") suspend fun dirtyCharges(): List<ChargeService>
    @Query("SELECT * FROM warranty_claims WHERE dirty = 1") suspend fun dirtyWarranties(): List<WarrantyClaim>
    @Query("UPDATE charge_services SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanCharge(id: Long, updatedAt: Long)
    @Query("UPDATE warranty_claims SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanWarranty(id: Long, updatedAt: Long)
    @Query("SELECT * FROM charge_services WHERE id = :id") suspend fun charge(id: Long): ChargeService?
    @Query("SELECT * FROM warranty_claims WHERE id = :id") suspend fun warranty(id: Long): WarrantyClaim?
    @Upsert suspend fun upsertCharge(c: ChargeService)
    @Upsert suspend fun upsertWarranty(w: WarrantyClaim)
    @Query("DELETE FROM charge_services WHERE id = :id") suspend fun deleteCharge(id: Long)
    @Query("DELETE FROM warranty_claims WHERE id = :id") suspend fun deleteWarranty(id: Long)
    @Query("SELECT id FROM charge_services") suspend fun allChargeIds(): List<Long>
    @Query("SELECT id FROM warranty_claims") suspend fun allWarrantyIds(): List<Long>
    @Query("DELETE FROM charge_services") suspend fun wipeCharges()
    @Query("DELETE FROM warranty_claims") suspend fun wipeWarranties()

    @Query("SELECT * FROM expenses WHERE dirty = 1") suspend fun dirtyExpenses(): List<Expense>
    @Query("UPDATE expenses SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanExpense(id: Long, updatedAt: Long)
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun expense(id: Long): Expense?
    @Upsert suspend fun upsertExpense(e: Expense)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun deleteExpense(id: Long)
    @Query("SELECT id FROM expenses") suspend fun allExpenseIds(): List<Long>
    @Query("DELETE FROM expenses") suspend fun wipeExpenses()

    // ----- Marcar como enviado (só se não mudou durante o envio)
    @Query("UPDATE products SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanProduct(id: Long, updatedAt: Long)
    @Query("UPDATE sales SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanSale(id: Long, updatedAt: Long)
    @Query("UPDATE sale_items SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanSaleItem(id: Long, updatedAt: Long)
    @Query("UPDATE stock_movements SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanStockMovement(id: Long, updatedAt: Long)
    @Query("UPDATE scrap_prices SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanScrapPrice(id: Long, updatedAt: Long)
    @Query("UPDATE scrap_movements SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun cleanScrapMovement(id: Long, updatedAt: Long)

    // ----- Leitura por ID (para decidir se aplica a versão da nuvem)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: Long): Product?
    @Query("SELECT * FROM products WHERE model = :model COLLATE NOCASE LIMIT 1") suspend fun productByModel(model: String): Product?
    @Query("SELECT * FROM sales WHERE id = :id") suspend fun sale(id: Long): Sale?
    @Query("SELECT * FROM sale_items WHERE id = :id") suspend fun saleItem(id: Long): SaleItem?
    @Query("SELECT * FROM stock_movements WHERE id = :id") suspend fun stockMovement(id: Long): StockMovement?
    @Query("SELECT * FROM scrap_prices WHERE id = :id") suspend fun scrapPrice(id: Long): ScrapPrice?
    @Query("SELECT * FROM scrap_prices WHERE amperage = :amperage LIMIT 1") suspend fun scrapPriceByAmperage(amperage: Int): ScrapPrice?
    @Query("SELECT * FROM scrap_movements WHERE id = :id") suspend fun scrapMovement(id: Long): ScrapMovement?

    // ----- Gravar a versão da nuvem
    @Upsert suspend fun upsertProduct(p: Product)
    @Upsert suspend fun upsertSale(s: Sale)
    @Upsert suspend fun upsertSaleItem(i: SaleItem)
    @Upsert suspend fun upsertStockMovement(m: StockMovement)
    @Upsert suspend fun upsertScrapPrice(p: ScrapPrice)
    @Upsert suspend fun upsertScrapMovement(m: ScrapMovement)

    @Query("DELETE FROM products WHERE id = :id") suspend fun deleteProduct(id: Long)
    @Query("DELETE FROM sales WHERE id = :id") suspend fun deleteSale(id: Long)
    @Query("DELETE FROM sale_items WHERE id = :id") suspend fun deleteSaleItem(id: Long)
    @Query("DELETE FROM stock_movements WHERE id = :id") suspend fun deleteStockMovement(id: Long)
    @Query("DELETE FROM scrap_prices WHERE id = :id") suspend fun deleteScrapPrice(id: Long)
    @Query("DELETE FROM scrap_movements WHERE id = :id") suspend fun deleteScrapMovement(id: Long)

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movements WHERE product_id = :productId")
    suspend fun movementSum(productId: Long): Int

    /** Estoque = soma das movimentações (evita conflito quando dois celulares vendem ao mesmo tempo). */
    @Query("UPDATE products SET stock = (SELECT COALESCE(SUM(m.quantity), 0) FROM stock_movements m WHERE m.product_id = products.id)")
    suspend fun recomputeStock()

    // ----- Exclusões pendentes
    @Insert suspend fun insertTombstone(t: Tombstone)
    @Insert suspend fun insertTombstones(t: List<Tombstone>)
    @Query("SELECT * FROM tombstones ORDER BY id") suspend fun tombstones(): List<Tombstone>
    @Query("DELETE FROM tombstones WHERE id <= :maxId") suspend fun clearTombstones(maxId: Long)
    @Query("DELETE FROM tombstones") suspend fun deleteAllTombstones()

    // ----- IDs para registrar exclusões em cascata
    @Query("SELECT id FROM sale_items WHERE sale_id = :saleId") suspend fun saleItemIds(saleId: Long): List<Long>
    @Query("SELECT id FROM stock_movements WHERE sale_id = :saleId") suspend fun stockMovementIdsForSale(saleId: Long): List<Long>
    @Query("SELECT id FROM scrap_movements WHERE sale_id = :saleId") suspend fun scrapMovementIdsForSale(saleId: Long): List<Long>
    @Query("SELECT id FROM stock_movements WHERE product_id = :productId") suspend fun stockMovementIdsForProduct(productId: Long): List<Long>
    @Query("SELECT id FROM products") suspend fun allProductIds(): List<Long>
    @Query("SELECT id FROM sales") suspend fun allSaleIds(): List<Long>
    @Query("SELECT id FROM sale_items") suspend fun allSaleItemIds(): List<Long>
    @Query("SELECT id FROM stock_movements") suspend fun allStockMovementIds(): List<Long>
    @Query("SELECT id FROM scrap_prices") suspend fun allScrapPriceIds(): List<Long>
    @Query("SELECT id FROM scrap_movements") suspend fun allScrapMovementIds(): List<Long>

    /** Apaga todos os dados locais (usado para baixar tudo da nuvem num celular novo). */
    @Query("DELETE FROM scrap_movements") suspend fun wipeScrapMovements()
    @Query("DELETE FROM scrap_prices") suspend fun wipeScrapPrices()
    @Query("DELETE FROM stock_movements") suspend fun wipeStockMovements()
    @Query("DELETE FROM sale_items") suspend fun wipeSaleItems()
    @Query("DELETE FROM sales") suspend fun wipeSales()
    @Query("DELETE FROM products") suspend fun wipeProducts()
}

@Dao
interface ChargeDao {
    @Query("SELECT * FROM charge_services ORDER BY received_at DESC")
    fun observeAll(): Flow<List<ChargeService>>

    @Query("SELECT * FROM charge_services WHERE id = :id")
    fun observeById(id: Long): Flow<ChargeService?>

    @Query("SELECT * FROM charge_services WHERE id = :id")
    suspend fun getById(id: Long): ChargeService?

    @Query("SELECT * FROM charge_services WHERE received_at >= :start AND received_at < :end ORDER BY received_at DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<ChargeService>>

    @Query("SELECT * FROM charge_services ORDER BY id")
    suspend fun getAll(): List<ChargeService>

    @Insert suspend fun insert(c: ChargeService)
    @Insert suspend fun insertAll(list: List<ChargeService>)
    @Update suspend fun update(c: ChargeService)

    @Query("DELETE FROM charge_services WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM charge_services") suspend fun deleteAll()
}

@Dao
interface WarrantyDao {
    @Query("SELECT * FROM warranty_claims ORDER BY created_at DESC")
    fun observeAll(): Flow<List<WarrantyClaim>>

    @Query("SELECT * FROM warranty_claims WHERE id = :id")
    fun observeById(id: Long): Flow<WarrantyClaim?>

    @Query("SELECT * FROM warranty_claims WHERE id = :id")
    suspend fun getById(id: Long): WarrantyClaim?

    @Query("SELECT * FROM warranty_claims WHERE sale_id = :saleId ORDER BY created_at DESC")
    fun observeForSale(saleId: Long): Flow<List<WarrantyClaim>>

    @Query("SELECT * FROM warranty_claims ORDER BY id")
    suspend fun getAll(): List<WarrantyClaim>

    @Insert suspend fun insert(w: WarrantyClaim)
    @Insert suspend fun insertAll(list: List<WarrantyClaim>)
    @Update suspend fun update(w: WarrantyClaim)

    @Query("DELETE FROM warranty_claims WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM warranty_claims") suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE kind = 'PAYMENT' AND date >= :start AND date < :end ORDER BY date DESC")
    fun observePaymentsInRange(start: Long, end: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT * FROM expenses ORDER BY id")
    suspend fun getAll(): List<Expense>

    @Insert suspend fun insert(e: Expense)
    @Insert suspend fun insertAll(list: List<Expense>)
    @Update suspend fun update(e: Expense)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM expenses") suspend fun deleteAll()
}
