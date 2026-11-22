package com.kjvreader.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kjvreader.app.data.Bible
import com.kjvreader.app.data.Prefs
import com.kjvreader.app.data.SearchHit
import com.kjvreader.app.data.ThemeMode
import com.kjvreader.app.data.VerseRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { READER, BOOKS, SEARCH, BOOKMARKS }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var bible by mutableStateOf<Bible?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    var screen by mutableStateOf(Screen.READER)

    /** Chapter currently shown in the reader. */
    var position by mutableStateOf(prefs.lastPosition)
        private set

    /** A verse the reader should scroll to and briefly highlight, then clear. */
    var jumpTarget by mutableStateOf<VerseRef?>(null)
        private set

    var fontSize by mutableStateOf(prefs.fontSize)
        private set
    var themeMode by mutableStateOf(prefs.themeMode)
        private set
    var bookmarks by mutableStateOf(prefs.bookmarks)
        private set

    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                bible = withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open("kjv.tsv").bufferedReader().useLines { Bible.parse(it) }
                }
            } catch (e: Exception) {
                loadError = e.message ?: e.toString()
            }
        }
    }

    /** Called by the pager when the user swipes to another chapter. */
    fun onChapterShown(book: Int, chapter: Int) {
        if (position.book == book && position.chapter == chapter) return
        position = VerseRef(book, chapter)
        prefs.lastPosition = position
    }

    fun goTo(ref: VerseRef) {
        position = VerseRef(ref.book, ref.chapter)
        prefs.lastPosition = position
        jumpTarget = ref
        screen = Screen.READER
    }

    fun consumeJump() {
        jumpTarget = null
    }

    fun updateFontSize(size: Float) {
        fontSize = size
        prefs.fontSize = size
    }

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.themeMode = mode
    }

    fun toggleBookmarks(refs: Collection<VerseRef>) {
        val allMarked = refs.all { it in bookmarks }
        bookmarks = if (allMarked) bookmarks - refs.toSet() else bookmarks + refs
        prefs.bookmarks = bookmarks
    }

    fun removeBookmark(ref: VerseRef) {
        bookmarks = bookmarks - ref
        prefs.bookmarks = bookmarks
    }

    fun onSearchQueryChange(q: String) {
        searchQuery = q
        searchJob?.cancel()
        val b = bible ?: return
        if (q.trim().length < 2) {
            searchResults = emptyList()
            searching = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // debounce typing
            searching = true
            searchResults = withContext(Dispatchers.Default) { b.search(q) }
            searching = false
        }
    }
}
