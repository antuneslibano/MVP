package br.com.lojabaterias

import android.app.Application
import br.com.lojabaterias.data.AppDatabase
import br.com.lojabaterias.data.BackupManager
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.sync.SupabaseApi
import br.com.lojabaterias.data.sync.SyncManager
import br.com.lojabaterias.update.AppUpdater

class LojaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Injeção de dependências manual (simples e sem bibliotecas extras). */
class AppContainer(val app: Application) {
    val database: AppDatabase by lazy { AppDatabase.build(app) }

    val syncManager: SyncManager by lazy {
        val remote = if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else {
            null
        }
        SyncManager(app, database, remote)
    }

    /** Toda alteração local dispara a sincronização. */
    val repository: StoreRepository by lazy { StoreRepository(database) { syncManager.requestSync() } }
    val updater: AppUpdater by lazy { AppUpdater(app) }
    val backupManager: BackupManager by lazy { BackupManager(database) { syncManager.requestSync() } }
}
