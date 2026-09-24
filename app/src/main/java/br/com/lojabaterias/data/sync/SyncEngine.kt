package br.com.lojabaterias.data.sync

import androidx.room.withTransaction
import br.com.lojabaterias.data.AppDatabase
import br.com.lojabaterias.data.SyncTables
import org.json.JSONArray
import org.json.JSONObject

/** Guarda o "cursor" da última sincronização (momento do servidor). */
interface CursorStore {
    var cursor: String?
}

/**
 * Este celular tem dados próprios (criados antes da sincronização) e a nuvem já tem dados de outro celular.
 * Misturar os dois causaria duplicidades/conflitos.
 */
class SyncConflictException(message: String) : Exception(message)

data class SyncReport(val pushed: Int, val pulled: Int)

/**
 * Sincronização "offline primeiro":
 * 1. envia as alterações locais pendentes (dirty) e as exclusões (tombstones);
 * 2. recebe as alterações feitas na nuvem por outros celulares desde a última vez;
 * 3. recalcula o estoque como soma das movimentações.
 *
 * Conflitos: vence a alteração mais recente (updated_at). Exclusão vence edição.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val remote: RemoteApi,
    private val cursorStore: CursorStore,
) {
    private val dao = db.syncDao()

    suspend fun sync(token: String): SyncReport {
        val firstSync = cursorStore.cursor == null
        return if (firstSync) {
            val data = remote.pull(token, null)
            if (hasAnyData(data) && dao.legacyDirtyCount() > 0) {
                throw SyncConflictException(
                    "Este celular tem dados próprios e a nuvem já tem dados de outro celular. " +
                        "Para usar os dados da nuvem, apague os dados deste celular (Menu > Backup e sincronização)."
                )
            }
            val pulled = apply(data)
            val pushed = push(token)
            SyncReport(pushed, pulled)
        } else {
            val pushed = push(token)
            val pulled = apply(remote.pull(token, cursorStore.cursor))
            SyncReport(pushed, pulled)
        }
    }

    private fun hasAnyData(data: JSONObject): Boolean =
        TABLES.any { (data.optJSONArray(it)?.length() ?: 0) > 0 }

    // ------------------------------------------------------------------ Envio

    private suspend fun push(token: String): Int {
        var count = 0
        dao.dirtyProducts().let { list ->
            send(token, SyncTables.PRODUCTS, list.map { RemoteMapper.toJson(it) })
            list.forEach { dao.cleanProduct(it.id, it.updatedAt) }
            count += list.size
        }
        dao.dirtySales().let { list ->
            send(token, SyncTables.SALES, list.map { RemoteMapper.toJson(it) })
            list.forEach { dao.cleanSale(it.id, it.updatedAt) }
            count += list.size
        }
        dao.dirtySaleItems().let { list ->
            send(token, SyncTables.SALE_ITEMS, list.map { RemoteMapper.toJson(it) })
            list.forEach { dao.cleanSaleItem(it.id, it.updatedAt) }
            count += list.size
        }
        dao.dirtyStockMovements().let { list ->
            send(token, SyncTables.STOCK_MOVEMENTS, list.map { RemoteMapper.toJson(it) })
            list.forEach { dao.cleanStockMovement(it.id, it.updatedAt) }
            count += list.size
        }
        dao.dirtyScrapPrices().let { list ->
            send(token, SyncTables.SCRAP_PRICES, list.map { RemoteMapper.toJson(it) })
            list.forEach { dao.cleanScrapPrice(it.id, it.updatedAt) }
            count += list.size
        }
        dao.dirtyScrapMovements().let { list ->
            send(token, SyncTables.SCRAP_MOVEMENTS, list.map { RemoteMapper.toJson(it) })
            list.forEach { dao.cleanScrapMovement(it.id, it.updatedAt) }
            count += list.size
        }
        val tombstones = dao.tombstones()
        if (tombstones.isNotEmpty()) {
            send(
                token,
                DELETIONS,
                tombstones.map {
                    JSONObject().put("table_name", it.tableName).put("record_id", it.recordId).put("deleted_at", it.deletedAt)
                },
            )
            dao.clearTombstones(tombstones.maxOf { it.id })
            count += tombstones.size
        }
        return count
    }

    private suspend fun send(token: String, table: String, rows: List<JSONObject>) {
        rows.chunked(CHUNK).forEach { chunk -> remote.upsert(token, table, JSONArray(chunk)) }
    }

    // --------------------------------------------------------------- Recebimento

    /** Aplica as alterações vindas da nuvem. Retorna quantos registros foram recebidos. */
    private suspend fun apply(data: JSONObject): Int {
        var count = 0
        db.withTransaction {
            data.rows(SyncTables.PRODUCTS).forEach { o ->
                val r = RemoteMapper.product(o)
                val local = dao.product(r.id)
                if (local == null || !local.dirty || local.updatedAt <= r.updatedAt) {
                    // Mantém o estoque local até o recálculo abaixo.
                    dao.upsertProduct(r)
                    if (dao.product(r.id) == null) {
                        // Mesmo modelo cadastrado em dois celulares ao mesmo tempo: guarda com outro nome.
                        var n = 2
                        var name = "${r.model} ($n)"
                        while (dao.productByModel(name) != null) name = "${r.model} (${++n})"
                        dao.upsertProduct(r.copy(model = name))
                    }
                    count++
                }
            }
            data.rows(SyncTables.SALES).forEach { o ->
                val r = RemoteMapper.sale(o)
                val local = dao.sale(r.id)
                if (local == null || !local.dirty || local.updatedAt <= r.updatedAt) {
                    dao.upsertSale(r)
                    count++
                }
            }
            data.rows(SyncTables.SALE_ITEMS).forEach { o ->
                val r = RemoteMapper.saleItem(o)
                val local = dao.saleItem(r.id)
                if ((local == null || !local.dirty || local.updatedAt <= r.updatedAt) && dao.sale(r.saleId) != null) {
                    dao.upsertSaleItem(r)
                    count++
                }
            }
            data.rows(SyncTables.STOCK_MOVEMENTS).forEach { o ->
                val r = RemoteMapper.stockMovement(o)
                val local = dao.stockMovement(r.id)
                if (local == null || !local.dirty || local.updatedAt <= r.updatedAt) {
                    dao.upsertStockMovement(r)
                    count++
                }
            }
            data.rows(SyncTables.SCRAP_PRICES).forEach { o ->
                val r = RemoteMapper.scrapPrice(o)
                val local = dao.scrapPrice(r.id)
                if (local == null || !local.dirty || local.updatedAt <= r.updatedAt) {
                    dao.upsertScrapPrice(r)
                    if (dao.scrapPrice(r.id) == null) {
                        // Mesma amperagem cadastrada em outro celular: fica a versão da nuvem.
                        dao.scrapPriceByAmperage(r.amperage)?.let { dao.deleteScrapPrice(it.id) }
                        dao.upsertScrapPrice(r)
                    }
                    count++
                }
            }
            data.rows(SyncTables.SCRAP_MOVEMENTS).forEach { o ->
                val r = RemoteMapper.scrapMovement(o)
                val local = dao.scrapMovement(r.id)
                if (local == null || !local.dirty || local.updatedAt <= r.updatedAt) {
                    dao.upsertScrapMovement(r)
                    count++
                }
            }
            data.rows(DELETIONS).forEach { o ->
                val id = o.getLong("record_id")
                when (o.getString("table_name")) {
                    SyncTables.PRODUCTS -> dao.deleteProduct(id)
                    SyncTables.SALES -> dao.deleteSale(id)
                    SyncTables.SALE_ITEMS -> dao.deleteSaleItem(id)
                    SyncTables.STOCK_MOVEMENTS -> dao.deleteStockMovement(id)
                    SyncTables.SCRAP_PRICES -> dao.deleteScrapPrice(id)
                    SyncTables.SCRAP_MOVEMENTS -> dao.deleteScrapMovement(id)
                }
                count++
            }
            dao.recomputeStock()
        }
        data.optString("now").takeIf { it.isNotBlank() }?.let { cursorStore.cursor = it }
        return count
    }

    private fun JSONObject.rows(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    companion object {
        const val DELETIONS = "deletions"
        private const val CHUNK = 500
        private val TABLES = listOf(
            SyncTables.PRODUCTS, SyncTables.SALES, SyncTables.SALE_ITEMS,
            SyncTables.STOCK_MOVEMENTS, SyncTables.SCRAP_PRICES, SyncTables.SCRAP_MOVEMENTS,
        )
    }
}
