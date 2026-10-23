package com.chethan616.clearpdf.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chethan616.clearpdf.data.repository.LocalDocumentMirror
import com.chethan616.clearpdf.data.repository.RecentFile
import com.chethan616.clearpdf.data.repository.RecentFilesManager
import com.chethan616.clearpdf.utils.HtmlPagePdf
import com.chethan616.clearpdf.utils.TextDecoding
import com.chethan616.clearpdf.utils.markdown.MarkdownHtml
import com.chethan616.clearpdf.utils.markdown.MarkdownReader
import com.chethan616.clearpdf.utils.markdown.MdDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a Markdown file for [com.chethan616.clearpdf.ui.screen.MarkdownViewerScreen]. Read-only:
 * the file is copied to the cache once (so Share and the PDF don't depend on the sender's grant
 * lasting), decoded, and parsed off the main thread.
 */
class MarkdownViewerViewModel : ViewModel() {

    data class State(
        val fileName: String = "",
        val sourceUri: Uri? = null,
        /** The cached copy, for Share. */
        val localUri: Uri? = null,
        val doc: MdDocument? = null,
        val isLoading: Boolean = true,
        val failed: Boolean = false
    )

    var state by mutableStateOf(State())
        private set

    private var started = false
    private var pdf: Uri? = null

    fun load(context: Context, uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = withContext(Dispatchers.IO) {
                runCatching { queryName(context, uri) }.getOrNull()
                    ?: RecentFilesManager.getRecents(context).firstOrNull { it.uriString == uri.toString() }?.name
                    ?: uri.lastPathSegment ?: "README.md"
            }
            val loaded = withContext(Dispatchers.IO) {
                val readable = runCatching { LocalDocumentMirror.resolve(context, uri, "md") }.getOrDefault(uri)
                val bytes = runCatching { context.contentResolver.openInputStream(readable)?.use { it.readBytes() } }.getOrNull()
                    ?: return@withContext null
                val local = runCatching { copyToCache(context, bytes, name) }.getOrNull()
                val doc = withContext(Dispatchers.Default) { runCatching { MarkdownReader.read(TextDecoding.decode(bytes)) }.getOrNull() }
                Triple(doc, local, bytes.size.toLong())
            }
            val doc = loaded?.first
            state = State(fileName = name, sourceUri = uri, localUri = loaded?.second, doc = doc, isLoading = false, failed = doc == null)
            if (doc != null) withContext(Dispatchers.IO) {
                runCatching {
                    RecentFilesManager.addRecent(
                        context,
                        RecentFile(name = name, uriString = uri.toString(), timestamp = System.currentTimeMillis(), sizeBytes = loaded.third)
                    )
                }
            }
        }
    }

    /** The document as an A4 PDF, for Print and "Open as PDF". Made once, then reused. */
    fun exportToPdf(context: Context, onDone: (Uri?) -> Unit) {
        pdf?.let { if (File(it.path.orEmpty()).exists()) return onDone(it) }
        val doc = state.doc ?: return onDone(null)
        val stem = state.fileName.substringBeforeLast('.').ifBlank { "Document" }
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "converted_pdfs/${System.currentTimeMillis()}/${stem.replace('/', '_')}.pdf")
                val html = MarkdownHtml.render(doc, stem)
                if (HtmlPagePdf.render(context.applicationContext, html, file)) Uri.fromFile(file) else null
            }
            pdf = out
            onDone(out)
        }
    }

    private fun copyToCache(context: Context, bytes: ByteArray, name: String): Uri {
        // A folder per copy keeps the file's own name, which is what the share target shows.
        val dir = File(context.cacheDir, "docs/${System.currentTimeMillis()}").apply { mkdirs() }
        val safe = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "document.md" }
        val file = File(dir, safe)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun queryName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        }
}
