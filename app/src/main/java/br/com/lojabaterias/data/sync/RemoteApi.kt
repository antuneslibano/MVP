package br.com.lojabaterias.data.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sessão autenticada na nuvem. */
data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long)

/** Erro de comunicação com a nuvem (HTTP). [code] = status HTTP. */
class RemoteException(val code: Int, message: String) : Exception(message)

/** Operações na nuvem usadas pela sincronização (implementada pelo Supabase; falsa nos testes). */
interface RemoteApi {
    suspend fun signIn(email: String, password: String): Session
    suspend fun refresh(refreshToken: String): Session

    /**
     * Alterações feitas na nuvem desde [since] (null = tudo).
     * Retorna {"now": cursor, "products": [...], ..., "deletions": [...]}.
     */
    suspend fun pull(token: String, since: String?): JSONObject

    /** Insere/atualiza linhas (pela chave "id", ou table_name+record_id em deletions). */
    suspend fun upsert(token: String, table: String, rows: JSONArray)
}

/** Implementação usando a API REST do Supabase (PostgREST + Auth), sem bibliotecas extras. */
class SupabaseApi(private val baseUrl: String, private val anonKey: String) : RemoteApi {

    override suspend fun signIn(email: String, password: String): Session =
        session(post("/auth/v1/token?grant_type=password", null, JSONObject().put("email", email).put("password", password).toString()))

    override suspend fun refresh(refreshToken: String): Session =
        session(post("/auth/v1/token?grant_type=refresh_token", null, JSONObject().put("refresh_token", refreshToken).toString()))

    override suspend fun pull(token: String, since: String?): JSONObject =
        JSONObject(post("/rest/v1/rpc/pull_changes", token, JSONObject().put("since", since ?: JSONObject.NULL).toString()))

    override suspend fun upsert(token: String, table: String, rows: JSONArray) {
        if (rows.length() == 0) return
        val conflict = if (table == "deletions") "?on_conflict=table_name,record_id" else ""
        post(
            "/rest/v1/$table$conflict",
            token,
            rows.toString(),
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    private fun session(body: String): Session {
        val o = JSONObject(body)
        val expiresIn = o.optLong("expires_in", 3600)
        return Session(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + expiresIn * 1000,
        )
    }

    private fun post(path: String, token: String?, body: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw RemoteException(code, describeError(code, text))
            return text
        } catch (e: RemoteException) {
            throw e
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun describeError(code: Int, body: String): String {
        val msg = runCatching {
            val o = JSONObject(body)
            o.optString("msg").ifBlank { o.optString("message") }.ifBlank { o.optString("error_description") }
                .ifBlank { o.optString("error") }
        }.getOrNull().orEmpty().ifBlank { body.take(200) }
        return "HTTP $code: $msg"
    }
}
