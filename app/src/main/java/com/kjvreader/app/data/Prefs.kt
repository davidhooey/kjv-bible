package com.kjvreader.app.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK, SEPIA }

/** Small wrapper over SharedPreferences for reading position, settings and bookmarks. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kjv_prefs", Context.MODE_PRIVATE)

    var lastPosition: VerseRef
        get() = VerseRef(sp.getInt("last_book", 0), sp.getInt("last_chapter", 1), 0)
        set(v) = sp.edit().putInt("last_book", v.book).putInt("last_chapter", v.chapter).apply()

    var fontSize: Float
        get() = sp.getFloat("font_size", 19f)
        set(v) = sp.edit().putFloat("font_size", v).apply()

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = sp.edit().putString("theme", v.name).apply()

    var bookmarks: Set<VerseRef>
        get() = sp.getStringSet("bookmarks", emptySet())!!.mapNotNull { VerseRef.fromKey(it) }.toSet()
        set(v) = sp.edit().putStringSet("bookmarks", v.map { it.key() }.toSet()).apply()
}
