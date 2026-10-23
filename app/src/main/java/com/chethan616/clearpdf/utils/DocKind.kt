package com.chethan616.clearpdf.utils

/**
 * The original document family, derived from a file name's extension. The app converts non-PDF
 * documents to a PDF for viewing (which loses the original type), but the original display name is
 * preserved (e.g. "budget.xlsx") — so the extension is a reliable way to recover the kind and show
 * the right editor tools / route to the right viewer.
 */
enum class DocKind { Pdf, Word, Excel, Ppt, Image, Markdown, Text, Other }

/** Classify a file by its name/extension. Null/unknown → [DocKind.Other]. */
fun docKindOf(fileName: String?): DocKind {
    val n = (fileName ?: "").lowercase().trim()
    return when {
        n.endsWith(".pdf") -> DocKind.Pdf
        n.endsWith(".docx") || n.endsWith(".doc") || n.endsWith(".odt") || n.endsWith(".rtf") -> DocKind.Word
        n.endsWith(".xlsx") || n.endsWith(".xls") -> DocKind.Excel
        n.endsWith(".pptx") || n.endsWith(".ppt") -> DocKind.Ppt
        n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ||
            n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".heic") -> DocKind.Image
        n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".mdown") || n.endsWith(".mkd") -> DocKind.Markdown
        n.substringAfterLast('.', "") in TextExtensions || n.substringAfterLast('/') in TextFileNames -> DocKind.Text
        else -> DocKind.Other
    }
}

/** Opened in the text viewer: plain text, logs, data and config formats, source code. */
private val TextExtensions = setOf(
    "txt", "text", "log", "out", "logcat", "json", "jsonl", "geojson", "har", "xml", "html", "htm", "xhtml", "svg",
    "plist", "xsd", "xsl", "kml", "gpx", "rss", "yaml", "yml", "ini", "conf", "cfg", "properties", "toml", "env",
    "csv", "tsv", "kt", "kts", "java", "js", "mjs", "ts", "jsx", "tsx", "c", "h", "cpp", "hpp", "cc", "cs", "go",
    "rs", "swift", "dart", "scala", "groovy", "gradle", "php", "css", "scss", "less", "sql", "py", "sh", "bash",
    "zsh", "rb", "pl", "r", "bat", "ps1", "srt", "vtt", "diff", "patch", "gitignore"
)
private val TextFileNames = setOf("dockerfile", "makefile", "license", "readme", "changelog")
