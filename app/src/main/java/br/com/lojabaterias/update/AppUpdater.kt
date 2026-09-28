package br.com.lojabaterias.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import br.com.lojabaterias.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Versão publicada no GitHub (Release). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState()
    data class Installing(val info: UpdateInfo) : UpdateState()
    /** O Android precisa que o usuário permita instalar apps a partir deste app. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState()
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState()
}

/**
 * Atualização do app pelo próprio app:
 * consulta a Release mais recente no GitHub, baixa o APK e instala (1 toque de confirmação,
 * ou nenhum a partir do Android 12 depois que o app já se atualizou uma vez).
 */
class AppUpdater(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    /** Verifica se há versão nova. Sem [force], no máximo a cada [CHECK_INTERVAL_MS]. */
    fun check(force: Boolean = false) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.Installing) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (!force && System.currentTimeMillis() - last < CHECK_INTERVAL_MS && s !is UpdateState.Error) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val info = fetchLatest()
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (info != null && info.versionCode > currentVersionCode) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (force) UpdateState.Error("Não foi possível verificar: ${e.message ?: "sem conexão"}") else UpdateState.Idle
            }
        }
    }

    /** Baixa e instala a versão nova. */
    fun downloadAndInstall(info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        _state.value = UpdateState.Downloading(info, 0)
        scope.launch {
            try {
                val file = download(info)
                _state.value = UpdateState.Installing(info)
                install(file)
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Falha na atualização: ${e.message ?: e.javaClass.simpleName}", info)
            }
        }
    }

    /** Abre a tela do Android para permitir que este app instale atualizações. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Chamado pelo [UpdateReceiver] quando a instalação falha. */
    fun onInstallFailed(message: String) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = UpdateState.Error("A instalação não foi concluída: $message", info)
    }

    fun dismissError() {
        if (_state.value is UpdateState.Error) _state.value = UpdateState.Idle
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ArtDasBaterias-App")
        }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val code = tag.substringAfterLast('-').toIntOrNull() ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
            }
            return UpdateInfo(
                versionCode = code,
                versionName = o.optString("name").ifBlank { "1.0.$code" },
                apkUrl = url ?: return null,
                notes = o.optString("body"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun download(info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "art-das-baterias-${info.versionCode}.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "ArtDasBaterias-App")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastProgress = -1
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != lastProgress) {
                                lastProgress = p
                                _state.value = UpdateState.Downloading(info, p)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        // Confere se o arquivo é mesmo uma versão mais nova deste app.
        val archive = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw IllegalStateException("arquivo baixado inválido")
        if (archive.packageName != context.packageName) throw IllegalStateException("APK de outro aplicativo")
        return file
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // A partir do Android 12, se este app for o instalador, atualiza sem pedir confirmação.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("app.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/antuneslibano/MVP/releases/latest"
        private const val KEY_LAST_CHECK = "last_check"
        private const val CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L
    }
}
