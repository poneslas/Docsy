package com.chethan616.clearpdf.utils.text

/**
 * The grammars the text viewer knows, with TextMate scope names so VS Code style colours apply.
 *
 * Sources (see THIRD_PARTY_NOTICES.md): the log rules follow emilast's "Log File Highlighter"
 * `log.tmLanguage` (MIT), which VS Code ships as its own log grammar; JSON, XML and INI follow
 * the VS Code grammars (MIT) and textmate/ini.tmbundle; YAML follows Prism's `yaml` component
 * (MIT) with VS Code's YAML scope names. Rules are translated, not copied: TextMate's Oniguruma
 * regexes are rewritten where Java's engine differs (unbounded look-behind, `\G`).
 */
object Grammars {

    val Log = Grammar("log", listOf(
        // Levels. emilast's `(?<=^[\s\d\p]*)\bV\b` (a lone letter after the timestamp, as logcat
        // writes it) needs an unbounded look-behind; a bounded one does the same on real lines.
        Match("""\b([Tt]race|TRACE)\b:?""", "comment log.verbose"),
        Match("""(?i)\[(verbose|verb|vrb|vb|v)\]""", "comment log.verbose"),
        Match("""(?<=^[\s\d\p{Punct}]{0,64})\bV\b""", "comment log.verbose"),
        Match("""\b(DEBUG|Debug)\b|(?i)\b(debug):""", "markup.changed log.debug"),
        Match("""(?i)\[(debug|dbug|dbg|de|d)\]""", "markup.changed log.debug"),
        Match("""(?<=^[\s\d\p{Punct}]{0,64})\bD\b""", "markup.changed log.debug"),
        Match("""\b(HINT|INFO|INFORMATION|Info|NOTICE|II)\b|(?i)\b(info|information):""", "markup.inserted log.info"),
        Match("""(?i)\[(information|info|inf|in|i)\]""", "markup.inserted log.info"),
        Match("""(?<=^[\s\d\p{Punct}]{0,64})\bI\b""", "markup.inserted log.info"),
        Match("""\b(WARNING|WARN|Warn|WW)\b|(?i)\b(warning):""", "markup.deleted log.warning"),
        Match("""(?i)\[(warning|warn|wrn|wn|w)\]""", "markup.deleted log.warning"),
        Match("""(?<=^[\s\d\p{Punct}]{0,64})\bW\b""", "markup.deleted log.warning"),
        Match("""\b(ALERT|CRITICAL|EMERGENCY|ERROR|FAILURE|FAIL|Fatal|FATAL|Error|EE)\b|(?i)\b(error):""", "strong log.error"),
        Match("""(?i)\[(error|eror|err|er|e|fatal|fatl|ftl|fa|f)\]""", "strong log.error"),
        Match("""(?<=^[\s\d\p{Punct}]{0,64})\b[EFA]\b""", "strong log.error"),
        // Dates and times. The logcat "MM-DD hh:mm" form comes first, before it reads as a MAC.
        Match("""\b\d{4}-\d{2}-\d{2}(?=T|\b)""", "comment log.date"),
        Match("""\b\d{2}-\d{2}(?=\s+\d{1,2}:\d{2})""", "comment log.date"),
        Match("""(?<=^|\s)\d{2}[^\w\s]\d{2}[^\w\s]\d{4}\b""", "comment log.date"),
        Match("""T?\d{1,2}:\d{2}(:\d{2}([.,]\d+)?)?(Z| ?[+-]\d{1,2}:\d{2})?\b""", "comment log.date"),
        Match("""T\d{2}\d{2}(\d{2}([.,]\d+)?)?(Z| ?[+-]\d{1,2}\d{2})?\b""", "comment log.date"),
        // Identifiers and literals.
        Match("""\b[0-9a-fA-F]{8}-?([0-9a-fA-F]{4}-?){3}[0-9a-fA-F]{12}\b""", "constant.language log.constant"),
        Match("""\b([0-9a-fA-F]{40}|[0-9a-fA-F]{10}|[0-9a-fA-F]{7})\b""", "constant.language log.constant"),
        Match("""\b([0-9a-fA-F]{2,}[:-])+[0-9a-fA-F]{2,}+\b""", "constant.language log.constant"),
        Match("""\b([0-9]+|true|false|null)\b""", "constant.language log.constant"),
        Match("""\b(0x[a-fA-F0-9]+)\b""", "constant.language log.constant"),
        Match(""""[^"]*"""", "string log.string"),
        Match("""(?<!\w)'[^']*'""", "string log.string"),
        // Exceptions: the type name, and a whole "at …" stack-frame line.
        Match("""\b([a-zA-Z.]*Exception)\b""", "log.exceptiontype"),
        Match("""^[\t ]*at[\t ].*$""", "log.exception"),
        Match("""\b[a-z]+://\S+\b/?""", "markup.underline.link log.constant"),
        Match("""(?<![\w/\\])([\w-]+\.)+([\w-])+(?![\w/\\])""", "log.namespace")
    ))

    private const val DqString = """"(?:[^"\\]|\\.)*""""

    val Json = Grammar("json", listOf(
        Region("""/\*""", """\*/""", "comment.block.json"),
        Match("""//.*$""", "comment.line.double-slash.json"),
        Match("""$DqString(?=\s*:)""", "support.type.property-name.json"),
        Match(DqString, "string.quoted.double.json"),
        Match("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""", "constant.numeric.json"),
        Match("""\b(?:true|false|null)\b""", "constant.language.json"),
        Match("""[{}\[\],:]""", "punctuation.json")
    ))

    private val xmlAttributes = listOf(
        Match("""(?:([-\w.]+)(:))?([-\w.:]+)(?=\s*=)""", captures = mapOf(
            1 to "entity.other.attribute-name.namespace.xml", 2 to "punctuation.separator.namespace.xml",
            3 to "entity.other.attribute-name.xml"
        )),
        Region("\"", "\"", "string.quoted.double.xml"),
        Region("'", "'", "string.quoted.single.xml")
    )
    private val xmlEntity = Match("""&(?:[:a-zA-Z_][:a-zA-Z0-9_.-]*|#[0-9]+|#x[0-9a-fA-F]+);""", "constant.character.entity.xml")

    val Xml = Grammar("xml", listOf(
        Region("<!--", "-->", "comment.block.xml"),
        Region("""<!\[CDATA\[""", """\]\]>""", "string.unquoted.cdata.xml"),
        Region(
            """(<\?)\s*([-\w]+)""", """(\?>)""", "meta.tag.preprocessor.xml", xmlAttributes,
            beginCaptures = mapOf(1 to "punctuation.definition.tag.xml", 2 to "entity.name.tag.xml"),
            endCaptures = mapOf(1 to "punctuation.definition.tag.xml")
        ),
        Region(
            """(<!)(DOCTYPE|ENTITY|ELEMENT|ATTLIST)\b""", ">", "meta.tag.sgml.doctype.xml",
            listOf(Region("\"", "\"", "string.quoted.double.xml")),
            beginCaptures = mapOf(1 to "punctuation.definition.tag.xml", 2 to "keyword.other.doctype.xml")
        ),
        Region(
            """(</?)(?:([-\w.]+)(:))?([-\w.:]+)""", """(/?>)""", "meta.tag.xml", xmlAttributes,
            beginCaptures = mapOf(
                1 to "punctuation.definition.tag.xml", 2 to "entity.name.tag.namespace.xml",
                3 to "punctuation.separator.namespace.xml", 4 to "entity.name.tag.xml"
            ),
            endCaptures = mapOf(1 to "punctuation.definition.tag.xml")
        ),
        xmlEntity
    ))

    val Yaml = Grammar("yaml", listOf(
        Match("""(?:^|(?<=\s))#.*$""", "comment.line.number-sign.yaml"),
        Match("""^(?:---|\.\.\.)(?=\s|$)""", "entity.other.document.yaml"),
        Match(
            """^(\s*)(-\s+)?("(?:[^"\\]|\\.)*"|'(?:[^']|'')*'|[^\s#'"\-{\[][^#:]*?|-[^\s#:][^#:]*?)(:)(?=\s|$)""",
            captures = mapOf(2 to "punctuation.definition.block.sequence.item.yaml", 3 to "entity.name.tag.yaml", 4 to "punctuation.separator.key-value.yaml")
        ),
        Match("""^\s*(-)(?=\s|$)""", captures = mapOf(1 to "punctuation.definition.block.sequence.item.yaml")),
        Match(""""(?:[^"\\]|\\.)*"|'(?:[^']|'')*'""", "string.quoted.yaml"),
        Match("""(?<=^|[\s:\-\[,])[&*][^\s,\[\]{}]+""", "variable.other.anchor.yaml"),
        Match("""(?<=^|\s)!!?[^\s]*""", "storage.type.tag.yaml"),
        Match("""(?<=[:\-]\s{1,8})[|>][-+]?\d?(?=\s*(?:#|$))""", "keyword.control.flow.block-scalar.yaml"),
        Match(
            """(?<=[:\-\[,]\s{0,8}|^\s{0,64})(?:true|false|yes|no|on|off|null|True|False|TRUE|FALSE|Null|NULL|~)(?=\s*(?:#|,|\]|\}|$))""",
            "constant.language.yaml"
        ),
        Match(
            """(?<=[:\-\[,]\s{0,8}|^\s{0,64})[-+]?(?:\d[\d_]*(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?(?=\s*(?:#|,|\]|\}|$))""",
            "constant.numeric.yaml"
        )
    ))

    val Ini = Grammar("ini", listOf(
        Match("""^\s*[;#!].*$""", "comment.line.ini"),
        Match("""^\s*(\[\[?)([^\]]*)(\]\]?)""", captures = mapOf(
            1 to "punctuation.definition.entity.ini", 2 to "entity.name.section.group-title.ini", 3 to "punctuation.definition.entity.ini"
        )),
        Match("""^\s*([^=:\s\[;#!][^=:]*?)\s*([=:])""", captures = mapOf(
            1 to "keyword.other.definition.ini", 2 to "punctuation.separator.key-value.ini"
        )),
        Match(""""(?:[^"\\]|\\.)*"|'[^']*'""", "string.quoted.ini"),
        Match("""(?<=[=:]\s{0,8})(?:true|false|yes|no|on|off)\b""", "constant.language.ini"),
        Match("""(?<=[=:]\s{0,8})[-+]?\d+(?:\.\d+)?(?=\s*$)""", "constant.numeric.ini")
    ))

    val Csv = Grammar("csv", listOf(
        Match(""""(?:[^"]|"")*"""", "string.quoted.csv"),
        Match("""(?<=^|[,;\t])\s*[-+]?\d[\d\s]*(?:[.,]\d+)?\s*(?=[,;\t]|$)""", "constant.numeric.csv"),
        Match("""[,;\t]""", "punctuation.separator.csv")
    ))

    private val codeKeywords = listOf(
        "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case", "default", "when",
        "try", "catch", "finally", "throw", "throws", "new", "class", "interface", "enum", "object", "struct",
        "fun", "func", "function", "fn", "val", "var", "let", "const", "import", "package", "export", "from",
        "as", "is", "in", "public", "private", "protected", "internal", "static", "final", "abstract", "override",
        "open", "sealed", "data", "suspend", "void", "int", "long", "float", "double", "char", "bool", "boolean",
        "byte", "short", "async", "await", "yield", "typealias", "extends", "implements", "where", "match",
        "impl", "trait", "mod", "use", "pub", "mut", "go", "defer", "chan", "select", "range", "type",
        "namespace", "using", "template", "typename", "extern", "inline", "virtual", "operator", "sizeof",
        "lateinit", "companion", "init", "constructor", "get", "set", "typeof", "instanceof", "delete", "of"
    ).joinToString("|")

    /** C, Kotlin, Java, JavaScript, Go, Rust, Swift, C#… and CSS: comments, strings, keywords, numbers. */
    val Code = Grammar("code", listOf(
        Region("""/\*""", """\*/""", "comment.block"),
        Match("""//.*$""", "comment.line.double-slash"),
        Match(""""(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`[^`]*`""", "string.quoted"),
        Match("""@[A-Za-z_][\w.]*""", "storage.type.annotation"),
        Match("""\b(?:$codeKeywords)\b""", "keyword.control"),
        Match("""\b(?:true|false|null|nil|undefined|this|self|super|it)\b""", "constant.language"),
        Match("""(?<![\w.])(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[eE][-+]?\d+)?)[LlFfDdUu]*\b""", "constant.numeric"),
        Match("""\b[A-Z][A-Za-z0-9_]*\b""", "entity.name.type"),
        Match("""\b[a-zA-Z_]\w*(?=\s*\()""", "entity.name.function")
    ))

    private val scriptKeywords = listOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is", "lambda",
        "import", "from", "as", "with", "try", "except", "finally", "raise", "pass", "break", "continue", "yield",
        "global", "nonlocal", "assert", "del", "async", "await", "fi", "then", "do", "done", "esac", "case",
        "function", "local", "export", "readonly", "source", "alias", "unset", "shift", "exit", "echo", "end",
        "begin", "unless", "until", "module", "require", "elsif", "self", "sub", "my", "use"
    ).joinToString("|")

    /** Python, shell, Ruby, Perl: `#` comments, `$variables`, triple-quoted strings. */
    val Script = Grammar("script", listOf(
        Region("\"\"\"", "\"\"\"", "string.quoted.triple"),
        Region("'''", "'''", "string.quoted.triple"),
        Match("""(?:^|(?<=\s))#.*$""", "comment.line.number-sign"),
        Match(""""(?:[^"\\]|\\.)*"|'[^']*'|`[^`]*`""", "string.quoted"),
        Match("\\\$\\{?[A-Za-z_]\\w*\\}?|\\\$[0-9@#?*\$!-]", "variable.other"),
        Match("""@[A-Za-z_][\w.]*""", "storage.type.annotation"),
        Match("""\b(?:$scriptKeywords)\b""", "keyword.control"),
        Match("""\b(?:True|False|None|true|false|nil)\b""", "constant.language"),
        Match("""(?<![\w.])(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?)\b""", "constant.numeric"),
        Match("""\b[a-zA-Z_]\w*(?=\()""", "entity.name.function")
    ))

    val all: List<Grammar> = listOf(Log, Json, Xml, Yaml, Ini, Csv, Code, Script)

    fun byId(id: String?): Grammar? = all.firstOrNull { it.id == id }

    /** A fenced code block's language, as Markdown files write it (```json, ```yml, ```sh…). */
    fun forLanguage(lang: String?): Grammar? = when (lang?.lowercase()?.trim()) {
        null, "" -> null
        "json", "jsonc", "json5", "jsonl", "geojson" -> Json
        "xml", "html", "htm", "svg", "xhtml", "plist", "xsd", "xsl", "vue" -> Xml
        "yaml", "yml" -> Yaml
        "ini", "toml", "conf", "cfg", "properties", "env", "dotenv", "editorconfig", "gitconfig" -> Ini
        "csv", "tsv" -> Csv
        "log", "logcat" -> Log
        "py", "python", "sh", "bash", "zsh", "shell", "console", "rb", "ruby", "pl", "perl", "r", "dockerfile", "makefile", "cmake" -> Script
        "kotlin", "kt", "kts", "java", "js", "javascript", "jsx", "ts", "typescript", "tsx", "c", "h", "cpp", "c++",
        "cc", "hpp", "cs", "csharp", "go", "rust", "rs", "swift", "dart", "scala", "groovy", "gradle", "php",
        "css", "scss", "less", "objc", "m", "proto", "sql", "graphql", "gql" -> Code
        else -> null
    }

    /** The grammar for a file name; null for plain text. */
    fun forFileName(name: String): Grammar? {
        val n = name.lowercase().trim()
        val base = n.substringAfterLast('/')
        return when {
            base == "dockerfile" || base == "makefile" || base == "cmakelists.txt" -> Script
            base.startsWith(".env") || base == ".gitconfig" || base == ".editorconfig" || base == ".npmrc" -> Ini
            base.endsWith(".logcat") || base.endsWith(".log") || base.endsWith(".out") -> Log
            else -> forLanguage(base.substringAfterLast('.', ""))
        }
    }

    private val logLine = Regex("""^\s*(?:\[?\d{2,4}[-/.]\d{2}[-/.]\d{2,4}|\d{2}-\d{2}\s+\d{1,2}:\d{2}|\[?\d{1,2}:\d{2}:\d{2}|[VDIWEF]/\S)""")

    /** For a `.txt` or a nameless file: a quick look at the first lines decides log / JSON / XML. */
    fun sniff(lines: List<String>): Grammar? {
        val head = lines.asSequence().filter { it.isNotBlank() }.take(40).toList()
        if (head.isEmpty()) return null
        val first = head.first().trimStart()
        if (first.startsWith("<?xml") || (first.startsWith("<") && head.last().trimEnd().endsWith(">"))) return Xml
        if ((first.startsWith("{") || first.startsWith("[")) && head.last().trimEnd().let { it.endsWith("}") || it.endsWith("]") || it.endsWith(",") }) return Json
        val logLike = head.count { logLine.containsMatchIn(it) }
        return if (logLike * 10 >= head.size * 4) Log else null
    }
}
