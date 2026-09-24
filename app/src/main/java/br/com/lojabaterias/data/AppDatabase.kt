package br.com.lojabaterias.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Product::class,
        Sale::class,
        SaleItem::class,
        StockMovement::class,
        ScrapPrice::class,
        ScrapMovement::class,
        Tombstone::class,
        ChargeService::class,
        WarrantyClaim::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun movementDao(): MovementDao
    abstract fun scrapDao(): ScrapDao
    abstract fun syncDao(): SyncDao
    abstract fun chargeDao(): ChargeDao
    abstract fun warrantyDao(): WarrantyDao

    companion object {
        const val NAME = "loja_baterias.db"

        /**
         * v1 → v2: controle de sucatas.
         * Apenas ADICIONA colunas e tabelas; nenhum dado existente é alterado ou removido.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `amperage` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `scrap_returned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `scrap_amperage` INTEGER")
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `scrap_missing` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `scrap_charge` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scrap_prices` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amperage` INTEGER NOT NULL, " +
                        "`value` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_scrap_prices_amperage` ON `scrap_prices` (`amperage`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scrap_movements` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date_time` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`amperage` INTEGER NOT NULL, " +
                        "`quantity` INTEGER NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`sale_id` INTEGER, " +
                        "`note` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scrap_movements_date_time` ON `scrap_movements` (`date_time`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scrap_movements_amperage` ON `scrap_movements` (`amperage`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scrap_movements_sale_id` ON `scrap_movements` (`sale_id`)"
                )
            }
        }

        private val SYNCED_TABLES = listOf(
            "products", "sales", "sale_items", "stock_movements", "scrap_prices", "scrap_movements",
        )

        /**
         * v2 → v3: sincronização entre celulares.
         * Adiciona controle de alterações (updated_at/dirty) e a tabela de exclusões pendentes.
         * Todos os dados existentes ficam marcados para envio (dirty = 1).
         * Também concilia o estoque com as movimentações (o estoque passa a ser a soma delas).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in SYNCED_TABLES) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tombstones` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`table_name` TEXT NOT NULL, " +
                        "`record_id` INTEGER NOT NULL, " +
                        "`deleted_at` INTEGER NOT NULL)"
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO stock_movements (product_id, date_time, type, quantity, stock_after, sale_id, note, " +
                        "unit_cost, updated_at, dirty) " +
                        "SELECT p.id, $now, 'ADJUSTMENT', " +
                        "p.stock - COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.product_id = p.id), 0), " +
                        "p.stock, NULL, 'Conciliação automática (sincronização)', NULL, $now, 1 " +
                        "FROM products p WHERE p.stock != " +
                        "COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.product_id = p.id), 0)"
                )
            }
        }

        /** v3 → v4: baterias na carga e garantias (apenas novas tabelas). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `charge_services` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customer_name` TEXT NOT NULL, " +
                        "`phone` TEXT NOT NULL, `battery_description` TEXT NOT NULL, `received_at` INTEGER NOT NULL, " +
                        "`price` INTEGER NOT NULL, `paid` INTEGER NOT NULL, `paid_at` INTEGER, `payment_method` TEXT, " +
                        "`loan_product_id` INTEGER, `loan_model` TEXT, `loan_movement_id` INTEGER, `loan_return_movement_id` INTEGER, `status` TEXT NOT NULL, " +
                        "`delivered_at` INTEGER, `note` TEXT, `updated_at` INTEGER NOT NULL DEFAULT 0, " +
                        "`dirty` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_charge_services_received_at` ON `charge_services` (`received_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_charge_services_status` ON `charge_services` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `warranty_claims` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sale_id` INTEGER, `created_at` INTEGER NOT NULL, " +
                        "`customer_name` TEXT NOT NULL, `returned_product_id` INTEGER, `returned_model` TEXT NOT NULL, " +
                        "`defective` INTEGER NOT NULL, `replacement_product_id` INTEGER, `replacement_model` TEXT, " +
                        "`replacement_cost` INTEGER NOT NULL, `out_movement_id` INTEGER, `difference_amount` INTEGER NOT NULL, " +
                        "`difference_method` TEXT, `status` TEXT NOT NULL, `collected_at` INTEGER, `resolved_at` INTEGER, " +
                        "`factory_product_id` INTEGER, `factory_model` TEXT, `in_movement_id` INTEGER, `refusal_notes` TEXT, " +
                        "`used_destination` TEXT, `used_destination_at` INTEGER, `used_sale_value` INTEGER NOT NULL, " +
                        "`scrap_movement_id` INTEGER, `note` TEXT, `updated_at` INTEGER NOT NULL DEFAULT 0, " +
                        "`dirty` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_warranty_claims_sale_id` ON `warranty_claims` (`sale_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_warranty_claims_status` ON `warranty_claims` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_warranty_claims_created_at` ON `warranty_claims` (`created_at`)")
            }
        }

        /**
         * v4 → v5: taxa da maquininha em cada venda.
         * Aplica as taxas padrão (crédito 7%, débito 2%) às vendas já registradas e recalcula o lucro.
         * As vendas alteradas são marcadas para sincronizar.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `card_fee` INTEGER NOT NULL DEFAULT 0")
                val now = System.currentTimeMillis()
                val credit = br.com.lojabaterias.domain.CardFees.DEFAULT_CREDIT_BPS
                val debit = br.com.lojabaterias.domain.CardFees.DEFAULT_DEBIT_BPS
                // Mesmo arredondamento do app: (valor × pontos-base + 5000) / 10000
                db.execSQL(
                    "UPDATE sales SET card_fee = (final_amount * $credit + 5000) / 10000, " +
                        "gross_profit = final_amount - total_cost - (final_amount * $credit + 5000) / 10000, " +
                        "updated_at = $now, dirty = 1 WHERE payment_method = 'CREDITO' AND final_amount > 0"
                )
                db.execSQL(
                    "UPDATE sales SET card_fee = (final_amount * $debit + 5000) / 10000, " +
                        "gross_profit = final_amount - total_cost - (final_amount * $debit + 5000) / 10000, " +
                        "updated_at = $now, dirty = 1 WHERE payment_method = 'DEBITO' AND final_amount > 0"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
