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
import com.chethan616.clearpdf.utils.text.Grammar
import com.chethan616.clearpdf.utils.text.Grammars
import com.chethan616.clearpdf.utils.text.Highlighter
import com.chethan616.clearpdf.utils.text.LineState
import com.chethan616.clearpdf.utils.text.TextFormat
import com.chethan616.clearpdf.utils.text.TextHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a text file (.txt, .log, .json, .xml, .yaml, .ini, .csv, source code) for
 * [com.chethan616.clearpdf.ui.screen.TextViewerScreen]. Read-only. The highlighting follows the
 * file's type, or a look at its first lines for a `.txt`; the reader can pick another.
 */
class TextViewerViewModel : ViewModel() {

    /** What the list shows: one entry per screen line; a very long line is cut into pieces. */
    class Lines(
        val text: List<String>,
        /** 1-based line number, or 0 for the continuation of a cut line. */
        val numbers: IntArray,
        /** State at the start of each entry, for grammars whose rules span lines; else null. */
        val states: List<LineState>?,
        val longest: Int,
        val count: Int
    )

    data class State(
        val fileName: String = "",
        val sourceUri: Uri? = null,
        val localUri: Uri? = null,
        val grammar: Grammar? = null,
        val lines: Lines? = null,
        val formatted: Boolean = false,
        val canFormat: Boolean = false,
        /** The file was larger than [MaxBytes]; only its start is shown. */
        val truncated: Boolean = false,
        val isLoading: Boolean = true,
        val failed: Boolean = false
    )

    var state by mutableStateOf(State())
        private set

    private var started = false
    private var raw: String = ""
    private var pretty: String? = null
    private var pdf: Pair<String, Uri>? = null
    private var linesJob: Job? = null

    fun load(context: Context, uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = withContext(Dispatchers.IO) {
                runCatching { queryName(context, uri) }.getOrNull()
                    ?: RecentFilesManager.getRecents(context).firstOrNull { it.uriString == uri.toString() }?.name
                    ?: uri.lastPathSegment ?: "text.txt"
            }
            val read = withContext(Dispatchers.IO) {
                val readable = runCatching { LocalDocumentMirror.resolve(context, uri, name.substringAfterLast('.', "txt")) }.getOrDefault(uri)
                runCatching {
                    context.contentResolver.openInputStream(readable)?.use { input ->
                        val buf = input.readNBytesCompat(MaxBytes + 1)
                        buf to (buf.size > MaxBytes)
                    }
                }.getOrNull()
            }
            if (read == null) {
                state = State(fileName = name, sourceUri = uri, isLoading = false, failed = true)
                return@launch
            }
            val (bytes, truncated) = read
            val kept = if (truncated) bytes.copyOf(MaxBytes) else bytes
            val local = withContext(Dispatchers.IO) { runCatching { copyToCache(context, kept, name) }.getOrNull() }
            raw = withContext(Dispatchers.Default) { TextDecoding.decode(kept).replace("\r\n", "\n").replace('\r', '\n') }
            val grammar = Grammars.forFileName(name) ?: withContext(Dispatchers.Default) { Grammars.sniff(raw.lineSequence().take(60).toList()) }
            val lines = withContext(Dispatchers.Default) { buildLines(raw, grammar) }
            state = State(
                fileName = name, sourceUri = uri, localUri = local, grammar = grammar, lines = lines,
                canFormat = grammar == Grammars.Json || grammar == Grammars.Xml,
                truncated = truncated, isLoading = false
            )
            withContext(Dispatchers.IO) {
                runCatching {
                    RecentFilesManager.addRecent(context, RecentFile(name = name, uriString = uri.toString(), timestamp = System.currentTimeMillis(), sizeBytes = bytes.size.toLong()))
                }
            }
        }
    }

    fun setGrammar(grammar: Grammar?) {
        if (grammar == state.grammar) return
        pretty = null // formatted as JSON is not formatted as XML
        state = state.copy(grammar = grammar, canFormat = grammar == Grammars.Json || grammar == Grammars.Xml)
        if (state.formatted && !state.canFormat) state = state.copy(formatted = false)
        rebuild()
    }

    /** JSON and XML: re-indent (a minified file becomes readable) or back to the file as it is. */
    fun toggleFormat() {
        if (!state.canFormat) return
        state = state.copy(formatted = !state.formatted)
        rebuild()
    }

    private fun shownText(): String = if (!state.formatted) raw else {
        val g = state.grammar
        pretty?.takeIf { it.isNotEmpty() } ?: runCatching { if (g == Grammars.Xml) TextFormat.xml(raw) else TextFormat.json(raw) }.getOrDefault(raw)
    }

    private fun rebuild() {
        val grammar = state.grammar
        val formatted = state.formatted
        linesJob?.cancel()
        linesJob = viewModelScope.launch {
            val lines = withContext(Dispatchers.Default) {
                if (formatted && pretty == null) pretty = shownText()
                buildLines(if (formatted) pretty ?: raw else raw, grammar)
            }
            if (state.grammar == grammar && state.formatted == formatted) state = state.copy(lines = lines)
        }
    }

    /** The text as shown (formatted or not) as an A4 PDF with the same highlighting, for Print and "Open as PDF". */
    fun exportToPdf(context: Context, onDone: (Uri?) -> Unit) {
        val key = "${state.formatted}:${state.grammar?.id}"
        pdf?.let { (k, uri) -> if (k == key && File(uri.path.orEmpty()).exists()) return onDone(uri) }
        if (state.lines == null) return onDone(null)
        val stem = state.fileName.substringBeforeLast('.').ifBlank { "Text" }
        val grammar = state.grammar
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                val text = withContext(Dispatchers.Default) { shownText() }
                val file = File(context.cacheDir, "converted_pdfs/${System.currentTimeMillis()}/${stem.replace('/', '_')}.pdf")
                val html = TextHtml.page(text, grammar, state.fileName)
                if (HtmlPagePdf.render(context.applicationContext, html, file)) Uri.fromFile(file) else null
            }
            if (out != null) pdf = key to out
            onDone(out)
        }
    }

    private fun buildLines(text: String, grammar: Grammar?): Lines {
        val src = text.split('\n').let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }
        val out = ArrayList<String>(src.size)
        val numbers = ArrayList<Int>(src.size)
        var longest = 0
        src.forEachIndexed { i, line ->
            // A minified file is one enormous line; pieces keep each list row cheap to lay out.
            if (line.length <= Piece) { out.add(line); numbers.add(i + 1) }
            else {
                var p = 0
                while (p < line.length) {
                    out.add(line.substring(p, minOf(line.length, p + Piece)))
                    numbers.add(if (p == 0) i + 1 else 0)
                    p += Piece
                }
            }
            longest = maxOf(longest, minOf(line.length, Piece))
        }
        val states = grammar?.let { Highlighter.lineStates(it, out) }
        return Lines(out, numbers.toIntArray(), states, longest, src.size)
    }

    private fun copyToCache(context: Context, bytes: ByteArray, name: String): Uri {
        val dir = File(context.cacheDir, "docs/${System.currentTimeMillis()}").apply { mkdirs() }
        val file = File(dir, name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "text.txt" })
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun queryName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        }

    companion object {
        /** A phone viewer, not a log server: past this, the start of the file is shown. */
        const val MaxBytes = 24 * 1024 * 1024
        private const val Piece = 4000
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). Shared with the Markdown viewer. */
internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val r = read(buf, 0, minOf(buf.size, limit - total))
        if (r < 0) break
        out.write(buf, 0, r)
        total += r
    }
    return out.toByteArray()
}
