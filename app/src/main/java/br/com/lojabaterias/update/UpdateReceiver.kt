package br.com.lojabaterias.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import br.com.lojabaterias.LojaApp

/** Recebe o resultado da instalação da atualização. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // O Android pede confirmação: abre a tela "Deseja atualizar este app?".
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // o app é reiniciado na versão nova
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "cancelada"
                (context.applicationContext as? LojaApp)?.container?.updater?.onInstallFailed(msg)
            }
        }
    }

    companion object {
        const val ACTION = "br.com.lojabaterias.UPDATE_INSTALL_STATUS"
    }
}
