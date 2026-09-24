package br.com.lojabaterias.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import br.com.lojabaterias.data.AppDatabase
import br.com.lojabaterias.security.AccessControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class SyncState { DISABLED, WAITING_LOGIN, SYNCING, OK, OFFLINE, ERROR, CONFLICT }

data class SyncStatus(
    val state: SyncState = SyncState.WAITING_LOGIN,
    val lastSuccessAt: Long? = null,
    val message: String? = null,
    val pending: Int = 0,
)

/**
 * Coordena a sincronização com a nuvem:
 * - entra na conta da loja quando o app é desbloqueado;
 * - sincroniza ao abrir, a cada [INTERVAL_MS] com o app aberto e logo após cada alteração local;
 * - funciona sem internet (as alterações ficam pendentes até a próxima sincronização).
 */
class SyncManager(
    context: Context,
    private val db: AppDatabase,
    private val remote: RemoteApi?,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Senha da nuvem (derivada da senha do app); só em memória. */
    @Volatile private var password: String? = null
    private var loopJob: Job? = null
    private var debounceJob: Job? = null

    private val cursorStore = object : CursorStore {
        override var cursor: String?
            get() = prefs.getString(KEY_CURSOR, null)
            set(value) = prefs.edit().putString(KEY_CURSOR, value).apply()
    }

    private val engine = remote?.let { SyncEngine(db, it, cursorStore) }

    private val _status = MutableStateFlow(
        SyncStatus(
            state = if (remote == null) SyncState.DISABLED else SyncState.WAITING_LOGIN,
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0 },
        )
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Chamado quando a senha correta é digitada. */
    fun onUnlocked(pin: String) {
        password = AccessControl.cloudPassword(pin)
        requestSync(0)
    }

    /** App visível: sincroniza periodicamente. */
    fun onForeground() {
        if (engine == null) return
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                syncNow()
                delay(INTERVAL_MS)
            }
        }
    }

    /** App em segundo plano: para o ciclo, mas envia o que estiver pendente. */
    fun onBackground() {
        loopJob?.cancel()
        loopJob = null
        requestSync(0)
    }

    /** Pede uma sincronização em breve (agrupa várias alterações seguidas). */
    fun requestSync(delayMs: Long = 1_500) {
        if (engine == null) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    /** Sincroniza agora. Retorna true se deu certo. */
    suspend fun syncNow(): Boolean {
        val engine = engine ?: return false
        return mutex.withLock {
            val session = try {
                session()
            } catch (e: IOException) {
                setStatus(SyncState.OFFLINE, "Sem internet. As alterações ficam guardadas no celular.")
                return@withLock false
            } catch (e: Exception) {
                setStatus(SyncState.ERROR, "Falha ao entrar na nuvem: ${e.message}")
                return@withLock false
            }
            if (session == null) {
                setStatus(SyncState.WAITING_LOGIN, "Digite a senha do app para conectar à nuvem.")
                return@withLock false
            }
            _status.update { it.copy(state = SyncState.SYNCING) }
            try {
                runWithRetry(engine, session)
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()
                _status.value = SyncStatus(SyncState.OK, now, null, pending())
                true
            } catch (e: SyncConflictException) {
                setStatus(SyncState.CONFLICT, e.message)
                false
            } catch (e: IOException) {
                setStatus(SyncState.OFFLINE, "Sem internet. As alterações ficam guardadas no celular.")
                false
            } catch (e: RemoteException) {
                setStatus(SyncState.ERROR, explain(e))
                false
            } catch (e: Exception) {
                setStatus(SyncState.ERROR, "Erro na sincronização: ${e.message ?: e.javaClass.simpleName}")
                false
            }
        }
    }

    private suspend fun runWithRetry(engine: SyncEngine, session: Session) {
        try {
            engine.sync(session.accessToken)
        } catch (e: RemoteException) {
            if (e.code != 401) throw e
            // Token expirado/revogado: renova e tenta de novo uma vez.
            prefs.edit().remove(KEY_ACCESS).putLong(KEY_EXPIRES, 0).apply()
            val fresh = session() ?: throw e
            engine.sync(fresh.accessToken)
        }
    }

    /**
     * Apaga os dados deste celular e baixa tudo da nuvem (para celulares com dados antigos/de teste).
     */
    suspend fun wipeLocalAndDownload(): Boolean {
        mutex.withLock {
            val dao = db.syncDao()
            db.withTransaction {
                dao.wipeCharges()
                dao.wipeWarranties()
                dao.wipeScrapMovements()
                dao.wipeScrapPrices()
                dao.wipeStockMovements()
                dao.wipeSaleItems()
                dao.wipeSales()
                dao.wipeProducts()
                dao.deleteAllTombstones()
            }
            cursorStore.cursor = null
        }
        return syncNow()
    }

    private suspend fun session(): Session? {
        val api = remote ?: return null
        val access = prefs.getString(KEY_ACCESS, null)
        val expires = prefs.getLong(KEY_EXPIRES, 0)
        val refresh = prefs.getString(KEY_REFRESH, null)
        if (access != null && expires - 60_000 > System.currentTimeMillis() && refresh != null) {
            return Session(access, refresh, expires)
        }
        if (refresh != null) {
            try {
                return api.refresh(refresh).also { save(it) }
            } catch (e: RemoteException) {
                prefs.edit().remove(KEY_REFRESH).remove(KEY_ACCESS).apply()
            }
        }
        val pwd = password ?: return null
        return api.signIn(AccessControl.CLOUD_EMAIL, pwd).also { save(it) }
    }

    private fun save(s: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, s.accessToken)
            .putString(KEY_REFRESH, s.refreshToken)
            .putLong(KEY_EXPIRES, s.expiresAt)
            .apply()
    }

    private suspend fun pending(): Int = runCatching { db.syncDao().pendingCount() }.getOrDefault(0)

    private suspend fun setStatus(state: SyncState, message: String?) {
        val p = pending()
        _status.update { it.copy(state = state, message = message, pending = p) }
    }

    private fun explain(e: RemoteException): String = when {
        e.code == 400 && e.message.orEmpty().contains("Invalid login", ignoreCase = true) ->
            "A conta da loja na nuvem não aceitou a senha. Confira o usuário criado no Supabase."
        e.code == 404 || e.message.orEmpty().contains("pull_changes") || e.message.orEmpty().contains("does not exist") ->
            "O banco na nuvem ainda não foi configurado (execute o script SQL no Supabase)."
        e.code == 401 || e.code == 403 -> "Acesso negado pela nuvem (${e.message})."
        else -> "Erro na nuvem: ${e.message}"
    }

    companion object {
        private const val INTERVAL_MS = 30_000L
        private const val KEY_CURSOR = "cursor"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_LAST_SUCCESS = "last_success"
    }
}
