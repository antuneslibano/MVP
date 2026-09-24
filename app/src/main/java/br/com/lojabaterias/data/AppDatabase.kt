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
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun movementDao(): MovementDao
    abstract fun scrapDao(): ScrapDao

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

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
