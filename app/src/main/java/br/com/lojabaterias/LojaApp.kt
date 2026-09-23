package br.com.lojabaterias

import android.app.Application
import br.com.lojabaterias.data.AppDatabase
import br.com.lojabaterias.data.BackupManager
import br.com.lojabaterias.data.StoreRepository

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
    val repository: StoreRepository by lazy { StoreRepository(database) }
    val backupManager: BackupManager by lazy { BackupManager(database) }
}
