package br.com.lojabaterias.security

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Entrar com a digital (opcional, desligado por padrão).
 *
 * A senha do app também libera a nuvem, então a digital não apenas "pula" a tela: ao ativar, a senha é
 * cifrada com uma chave do Android Keystore que só pode ser usada depois de confirmar a digital.
 * Ao entrar com a digital, a senha é decifrada e o app segue como se ela tivesse sido digitada.
 * Se uma digital nova for cadastrada no celular, a chave é invalidada e é preciso ativar de novo.
 */
object BiometricLock {

    private const val KEY_ALIAS = "art_das_baterias_pin"
    private const val PREFS = "lock_prefs"
    private const val P_WANTED = "bio_wanted"
    private const val P_DATA = "bio_pin"
    private const val P_IV = "bio_iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** O celular tem leitor de digital com digital cadastrada (Android 10 ou mais novo). */
    fun isSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val bm = context.getSystemService(BiometricManager::class.java) ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            bm.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** A opção está ligada (pode ainda faltar confirmar a digital pela primeira vez). */
    fun isWanted(context: Context): Boolean = prefs(context).getBoolean(P_WANTED, false)

    /** A digital já foi confirmada e a senha está guardada. */
    fun isReady(context: Context): Boolean = isWanted(context) && prefs(context).getString(P_DATA, null) != null

    fun setWanted(context: Context, on: Boolean) {
        if (on) prefs(context).edit().putBoolean(P_WANTED, true).apply() else disable(context)
    }

    /** Desliga e apaga a senha guardada e a chave. */
    fun disable(context: Context) {
        prefs(context).edit().remove(P_WANTED).remove(P_DATA).remove(P_IV).apply()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /** Pede a digital e guarda a [pin] cifrada. [onDone] recebe true se deu certo, ou false com o motivo. */
    fun enroll(activity: Activity, pin: String, onDone: (Boolean, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return onDone(false, "Este Android não tem digital")
        val cipher = try {
            runCatching { keyStore().deleteEntry(KEY_ALIAS) }
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: Exception) {
            return onDone(false, "Não foi possível ativar a digital")
        }
        authenticate(activity, cipher, "Ativar a digital", onSuccess = { c ->
            try {
                val data = c.doFinal(pin.toByteArray(Charsets.UTF_8))
                prefs(activity).edit()
                    .putBoolean(P_WANTED, true)
                    .putString(P_DATA, Base64.encodeToString(data, Base64.NO_WRAP))
                    .putString(P_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
                    .apply()
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, "Não foi possível ativar a digital")
            }
        }, onError = { msg -> onDone(false, msg) })
    }

    /** Pede a digital e devolve a senha guardada em [onPin]. [onError] recebe null quando o usuário escolheu a senha. */
    fun unlock(activity: Activity, onPin: (String) -> Unit, onError: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return onError(null)
        val p = prefs(activity)
        val data = p.getString(P_DATA, null)
        val iv = p.getString(P_IV, null)
        if (data == null || iv == null) return onError(null)
        val cipher = try {
            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: throw KeyPermanentlyInvalidatedException()
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable(activity)
            return onError("As digitais do celular mudaram. Entre com a senha e ative a digital de novo.")
        } catch (e: Exception) {
            return onError("Não foi possível usar a digital agora")
        }
        authenticate(activity, cipher, "Entrar com a digital", onSuccess = { c ->
            val pin = runCatching { String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8) }.getOrNull()
            if (pin != null) onPin(pin) else onError("Não foi possível usar a digital agora")
        }, onError = onError)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun authenticate(
        activity: Activity,
        cipher: Cipher,
        title: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String?) -> Unit,
    ) {
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("Art das Baterias")
            .setNegativeButton("Usar a senha", executor) { _, _ -> onError(null) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c != null) onSuccess(c) else onError("Não foi possível usar a digital agora")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val canceled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                onError(if (canceled) null else errString.toString())
            }
        }
        runCatching {
            builder.build().authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), executor, callback)
        }.onFailure { onError("Não foi possível usar a digital agora") }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(builder.build()) }
            .generateKey()
    }
}
