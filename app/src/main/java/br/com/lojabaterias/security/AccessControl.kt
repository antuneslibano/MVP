package br.com.lojabaterias.security

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Senha de acesso ao aplicativo.
 *
 * A senha NÃO fica escrita no código: guardamos apenas o hash (para conferir a senha digitada)
 * e a senha cifrada com uma chave derivada da resposta da pergunta secreta.
 * Assim ela só pode ser revelada por quem souber a resposta.
 */
object AccessControl {

    const val SECURITY_QUESTION = "Qual era nome da sua calopsita?"

    private const val PIN_HASH = "fb360d43ce77b8e695243e2216e6dcae95b99f13c3acc5d6c888830087b8b418"
    private const val PIN_CIPHER = "18f94219fdc8fb6d"

    /** Confere a senha digitada. */
    fun checkPin(pin: String): Boolean = sha256Hex("artdasbaterias:pin:$pin") == PIN_HASH

    /** Se a resposta estiver correta, devolve a senha; senão, null. Ignora maiúsculas, acentos e espaços nas pontas. */
    fun recoverPin(answer: String): String? {
        val key = sha256("artdasbaterias:calopsita:${normalize(answer)}")
        val cipher = PIN_CIPHER.chunked(2).map { it.toInt(16).toByte() }
        val plain = String(ByteArray(cipher.size) { i -> (cipher[i].toInt() xor key[i].toInt()).toByte() }, Charsets.UTF_8)
        return plain.takeIf { checkPin(it) }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun sha256(text: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(text: String): String = sha256(text).joinToString("") { "%02x".format(it) }
}
