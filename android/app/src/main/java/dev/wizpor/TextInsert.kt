package dev.wizpor

/** Pure helper: insert dictated text into existing field text at the selection. */
object TextInsert {
    data class Result(val text: String, val cursor: Int)

    fun insert(existing: String, selStart: Int, selEnd: Int, dictated: String): Result {
        if (dictated.isEmpty()) return Result(existing, selEnd.coerceIn(0, existing.length))
        var s = if (selStart < 0) existing.length else selStart.coerceIn(0, existing.length)
        var e = if (selEnd < 0) existing.length else selEnd.coerceIn(0, existing.length)
        if (s > e) { val t = s; s = e; e = t }
        val before = existing.substring(0, s)
        val after = existing.substring(e)
        var ins = dictated
        if (before.isNotEmpty() && !before.last().isWhitespace() && ins.first().isLetterOrDigit()) ins = " $ins"
        if (after.isNotEmpty() && !after.first().isWhitespace() && after.first() !in ".,!?;:)") ins = "$ins "
        return Result(before + ins + after, before.length + ins.length)
    }
}
