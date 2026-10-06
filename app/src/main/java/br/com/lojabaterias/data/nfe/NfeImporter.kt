package br.com.lojabaterias.data.nfe

import android.content.Context
import android.net.Uri
import br.com.lojabaterias.data.BusinessException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Abre o arquivo escolhido (PDF ou XML da nota) e lê os dados. Funciona sem internet. */
object NfeImporter {

    suspend fun read(context: Context, uri: Uri): NfeData = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BusinessException("Não foi possível abrir o arquivo")
        if (bytes.size > 20 * 1024 * 1024) throw BusinessException("Arquivo grande demais para uma nota fiscal")
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        when {
            head.startsWith("%PDF") -> readPdf(context, bytes)
            else -> {
                val text = String(bytes, Charsets.UTF_8)
                if (!NfeXmlParser.looksLikeNfe(text)) {
                    throw BusinessException("Esse arquivo não parece ser o PDF nem o XML de uma nota fiscal")
                }
                runCatching { NfeXmlParser.parse(text) }.getOrElse { throw BusinessException("Não foi possível ler o XML da nota") }
            }
        }
    }

    private fun readPdf(context: Context, bytes: ByteArray): NfeData {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = runCatching {
            PDDocument.load(bytes).use { doc ->
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        }.getOrElse { throw BusinessException("Não foi possível ler o PDF (ele pode estar protegido ou danificado)") }
        if (text.count { it.isLetterOrDigit() } < 50) {
            throw BusinessException(
                "Esse PDF parece ser uma foto ou digitalização, sem texto para ler. Use o PDF original ou o XML da nota."
            )
        }
        return DanfeTextParser.parse(text)
    }
}
