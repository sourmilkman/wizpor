package dev.wizpor

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("wizpor", Context.MODE_PRIVATE)

    var words: String
        get() = p.getString("words", DEFAULT_WORDS) ?: ""
        set(v) = p.edit().putString("words", v).apply()

    val wordList: List<String>
        get() = words.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    var removeFillers: Boolean
        get() = p.getBoolean("removeFillers", true)
        set(v) = p.edit().putBoolean("removeFillers", v).apply()

    var autoHide: Boolean
        get() = p.getBoolean("autoHide", true)
        set(v) = p.edit().putBoolean("autoHide", v).apply()

    var bubbleX: Int
        get() = p.getInt("bubbleX", -1)
        set(v) = p.edit().putInt("bubbleX", v).apply()

    var bubbleY: Int
        get() = p.getInt("bubbleY", -1)
        set(v) = p.edit().putInt("bubbleY", v).apply()

    var downloadId: Long
        get() = p.getLong("downloadId", -1L)
        set(v) = p.edit().putLong("downloadId", v).apply()

    companion object {
        const val DEFAULT_WORDS = "# One word or phrase per line. Fixes spelling of names and jargon.\nClaude\nTypeScript\nuseEffect\n"
    }
}
