package dev.wizpor

/** Instant, rule-based tidy-up of a transcript. Pure Kotlin so it is unit-testable. */
object Cleanup {
    private val FILLERS = setOf("um", "umm", "uh", "uhh", "uhm", "er", "erm", "ah", "ahh", "hmm", "hm", "mm", "mmm")
    private val SCRATCH = Regex("""(?i)\bscratch that\b[.,!?]*""")
    private val SENTENCE_END = Regex("""[.!?]\s""")

    fun tidy(text: String, words: List<String> = emptyList(), removeFillers: Boolean = true): String {
        var t = text.trim()
        if (t.isEmpty()) return ""
        t = applyScratchThat(t)
        if (removeFillers) t = dropFillers(t)
        t = capitalise(t)
        t = applyWords(t, words)
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    /** "A. B. Scratch that. C." -> "A. C." (removes the sentence before the phrase). */
    internal fun applyScratchThat(input: String): String {
        var t = input
        while (true) {
            val m = SCRATCH.find(t) ?: break
            val before = t.substring(0, m.range.first).trimEnd()
            // Drop the sentence that `before` ends with.
            val body = before.trimEnd('.', '!', '?', ',', ' ')
            val cut = SENTENCE_END.findAll(body).lastOrNull()?.range?.first?.plus(1) ?: 0
            val kept = body.substring(0, cut).trimEnd()
            val after = t.substring(m.range.last + 1).trimStart()
            t = if (kept.isEmpty()) after else "$kept $after"
        }
        return t.trim()
    }

    private fun core(token: String) = token.trim { !it.isLetterOrDigit() }.lowercase()

    internal fun dropFillers(input: String): String {
        val out = ArrayList<String>()
        for (tok in input.split(Regex("""\s+"""))) {
            if (core(tok) in FILLERS) {
                // Keep sentence-ending punctuation by moving it to the previous token.
                val end = tok.takeLastWhile { it in ".!?" }
                if (out.isNotEmpty()) {
                    if (end.isNotEmpty()) {
                        out[out.size - 1] = out.last().trimEnd(',', ';', ':') + end
                    } else if (!tok.endsWith(",")) {
                        // "I think, uh we" -> "I think we"
                        out[out.size - 1] = out.last().trimEnd(',')
                    }
                }
                continue
            }
            out.add(tok)
        }
        return out.joinToString(" ")
    }

    internal fun capitalise(input: String): String {
        val sb = StringBuilder(input)
        var startOfSentence = true
        for (i in sb.indices) {
            val c = sb[i]
            if (startOfSentence && c.isLetter()) {
                sb.setCharAt(i, c.uppercaseChar())
                startOfSentence = false
            } else if (c in ".!?") {
                startOfSentence = i + 1 < sb.length && sb[i + 1].isWhitespace()
            } else if (!c.isWhitespace()) {
                startOfSentence = false
            }
        }
        return sb.toString()
    }

    private fun key(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    /** Replace 1-3 word sequences that spell a custom word, e.g. "use effect" -> "useEffect". */
    internal fun applyWords(input: String, words: List<String>): String {
        val targets = words.map { it.trim() }.filter { key(it).length >= 2 }.associateBy { key(it) }
        if (targets.isEmpty()) return input
        val toks = input.split(" ").filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0
        while (i < toks.size) {
            var matched = false
            for (n in 3 downTo 1) {
                if (i + n > toks.size) continue
                val window = toks.subList(i, i + n)
                val w = targets[window.joinToString("") { key(it) }] ?: continue
                val lead = window.first().takeWhile { !it.isLetterOrDigit() }
                val trail = window.last().takeLastWhile { !it.isLetterOrDigit() }
                out.add(lead + w + trail)
                i += n
                matched = true
                break
            }
            if (!matched) out.add(toks[i++])
        }
        return out.joinToString(" ")
    }
}
