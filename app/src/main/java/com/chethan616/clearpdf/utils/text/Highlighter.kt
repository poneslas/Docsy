package com.chethan616.clearpdf.utils.text

/**
 * A small TextMate-style highlighter: a grammar is an ordered list of rules, each a single-line
 * [Match] or a [Region] with begin/end patterns and rules of its own (a comment, a tag, a string
 * that runs over several lines). At every position the rule whose match starts first wins, ties
 * going to the rule listed first, and the end of the enclosing region beats them all — the same
 * resolution TextMate and VS Code use.
 *
 * Tokens carry TextMate scope names (`comment.line`, `string.quoted.double.json`, `entity.name.tag`),
 * so the rules translate almost one to one from `.tmLanguage` grammars and VS Code style themes
 * map onto them. Lines are tokenized one at a time; the open regions at the end of a line are its
 * [LineState], which the next line starts from.
 */
class Grammar(val id: String, val rules: List<Rule>) {
    /** Whether any rule can stay open past the end of a line, so lines depend on the ones above. */
    val multiline: Boolean = rules.any { it.hasRegion() }
}

sealed class Rule {
    internal abstract fun hasRegion(): Boolean
}

/** [scope] covers the whole match; [captures] cover groups inside it (group → scope). */
class Match(pattern: String, val scope: String? = null, val captures: Map<Int, String> = emptyMap()) : Rule() {
    val regex = Regex(pattern)
    override fun hasRegion() = false
}

/**
 * Text from [begin] to [end], possibly over many lines. [scope] covers all of it, [rules] apply
 * inside; [beginCaptures] / [endCaptures] style groups of the delimiters.
 */
class Region(
    begin: String,
    end: String,
    val scope: String? = null,
    val rules: List<Rule> = emptyList(),
    val beginCaptures: Map<Int, String> = emptyMap(),
    val endCaptures: Map<Int, String> = emptyMap()
) : Rule() {
    val begin = Regex(begin)
    val end = Regex(end)
    override fun hasRegion() = true
}

/** The regions still open at the end of a line, innermost last. Shared, never mutated. */
class LineState internal constructor(internal val stack: List<Region>) {
    companion object { val Initial = LineState(emptyList()) }
}

/** A run of a line with one scope. Runs don't overlap and are in order; unstyled text has no run. */
data class Token(val start: Int, val end: Int, val scope: String)

class LineTokens(val tokens: List<Token>, val endState: LineState)

object Highlighter {

    fun tokenize(grammar: Grammar, line: String, state: LineState = LineState.Initial): LineTokens {
        val n = line.length
        // Painting per character, last write wins, then folding into runs: captures painted after
        // their match's scope sit on top of it, which is what nesting means here.
        val paint = arrayOfNulls<String>(n)
        fun paint(from: Int, to: Int, scope: String?) {
            if (scope == null) return
            for (i in from.coerceAtLeast(0) until to.coerceAtMost(n)) paint[i] = scope
        }
        fun paintGroups(m: MatchResult, captures: Map<Int, String>) {
            for ((g, scope) in captures) {
                val r = m.groups[g]?.range ?: continue
                paint(r.first, r.last + 1, scope)
            }
        }

        val stack = ArrayList(state.stack)
        var pos = 0
        var guard = 0
        while (pos <= n && guard++ < n * 4 + 16) {
            val top = stack.lastOrNull()
            val rules = top?.rules ?: grammar.rules
            val endMatch = top?.end?.find(line, pos)
            var best: MatchResult? = endMatch
            var bestRule: Rule? = null
            for (rule in rules) {
                val m = when (rule) {
                    is Match -> rule.regex.find(line, pos)
                    is Region -> rule.begin.find(line, pos)
                } ?: continue
                // Empty matches can't move the scanner; only a region's end may be empty.
                if (m.range.isEmpty()) continue
                val b = best
                if (b == null || m.range.first < b.range.first) { best = m; bestRule = rule }
            }
            val m = best
            if (m == null) {
                paint(pos, n, top?.scope)
                break
            }
            paint(pos, m.range.first, top?.scope)
            val next = m.range.last + 1
            when {
                bestRule == null -> { // the region's end
                    paint(m.range.first, next, top!!.scope)
                    paintGroups(m, top.endCaptures)
                    stack.removeAt(stack.lastIndex)
                }
                bestRule is Match -> {
                    paint(m.range.first, next, top?.scope)
                    paint(m.range.first, next, bestRule.scope)
                    paintGroups(m, bestRule.captures)
                }
                bestRule is Region -> {
                    paint(m.range.first, next, bestRule.scope)
                    paintGroups(m, bestRule.beginCaptures)
                    stack.add(bestRule)
                }
            }
            if (next == pos && bestRule != null) break
            pos = next
        }

        val tokens = ArrayList<Token>()
        var i = 0
        while (i < n) {
            val s = paint[i]
            var j = i + 1
            while (j < n && paint[j] == s) j++
            if (s != null) tokens.add(Token(i, j, s))
            i = j
        }
        return LineTokens(tokens, if (stack == state.stack) state else LineState(stack.toList()))
    }

    /** The state at the start of every line: only grammars with regions need it computed. */
    fun lineStates(grammar: Grammar, lines: List<String>): List<LineState>? {
        if (!grammar.multiline) return null
        val out = ArrayList<LineState>(lines.size)
        var s = LineState.Initial
        for (l in lines) {
            out.add(s)
            s = tokenize(grammar, l, s).endState
        }
        return out
    }
}
