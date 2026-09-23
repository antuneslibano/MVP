package br.com.lojabaterias.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("DELETE FROM stock_movements WHERE product_id = :productId")
    suspend fun deleteForProduct(productId: Long)

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAll()
}
