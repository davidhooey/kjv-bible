package io.github.squished18.kjvbible.data

/** The 66 books of the KJV protocanon, in canonical order. Index 0 = Genesis. */
val BOOK_NAMES = listOf(
    "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
    "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
    "Nehemiah", "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon",
    "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
    "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah",
    "Malachi",
    "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
    "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
    "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
    "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation",
)

const val OLD_TESTAMENT_COUNT = 39

class Book(val index: Int, val name: String, val chapters: List<List<String>>) {
    val isOldTestament get() = index < OLD_TESTAMENT_COUNT
}

/** A position in the text. [verse] is 1-based; 0 means "top of chapter". */
data class VerseRef(val book: Int, val chapter: Int, val verse: Int = 0) {
    /** Stable key used for persisting bookmarks. */
    fun key() = "$book.$chapter.$verse"

    companion object {
        fun fromKey(key: String): VerseRef? {
            val p = key.split('.')
            if (p.size != 3) return null
            return VerseRef(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null)
        }
    }
}

data class SearchHit(val ref: VerseRef, val text: String)

/**
 * The source text marks words supplied by the translators with [brackets]
 * (printed Bibles show them in italics). This removes the markers.
 */
fun plain(text: String): String = text.replace("[", "").replace("]", "")

class Bible(val books: List<Book>) {
    /** Every chapter of the Bible flattened, for paging: (bookIndex, chapterNumber). */
    val chapterIndex: List<Pair<Int, Int>> =
        books.flatMap { b -> (1..b.chapters.size).map { b.index to it } }

    private val pageOf: Map<Pair<Int, Int>, Int> =
        chapterIndex.withIndex().associate { it.value to it.index }

    fun page(book: Int, chapter: Int): Int = pageOf[book to chapter] ?: 0

    fun verses(book: Int, chapter: Int): List<String> =
        books.getOrNull(book)?.chapters?.getOrNull(chapter - 1) ?: emptyList()

    /** Raw verse text, including [italic] markers. */
    fun verseText(ref: VerseRef): String? = verses(ref.book, ref.chapter).getOrNull(ref.verse - 1)

    /** Verse text without markers, for search results, copying and sharing. */
    fun plainText(ref: VerseRef): String? = verseText(ref)?.let(::plain)

    private val searchIndex: List<Pair<VerseRef, String>> by lazy {
        books.flatMap { b ->
            b.chapters.flatMapIndexed { ci, ch ->
                ch.mapIndexed { vi, text -> VerseRef(b.index, ci + 1, vi + 1) to plain(text).lowercase() }
            }
        }
    }

    fun label(ref: VerseRef): String {
        val name = books.getOrNull(ref.book)?.name ?: "?"
        return if (ref.verse > 0) "$name ${ref.chapter}:${ref.verse}" else "$name ${ref.chapter}"
    }

    /**
     * Tries to read [query] as a reference such as "john 3:16", "1 cor 13", "ps 23".
     * Returns null when it does not look like one.
     */
    fun parseReference(query: String): VerseRef? {
        val m = Regex("""^\s*(\d?\s*[a-zA-Z][a-zA-Z .]*?)\s*(\d+)(?:\s*[:.]\s*(\d+))?\s*$""").find(query)
            ?: return null
        val bookPart = m.groupValues[1].replace(" ", "").replace(".", "").lowercase()
        if (bookPart.isEmpty()) return null
        val book = books.firstOrNull { it.name.replace(" ", "").lowercase().startsWith(bookPart) }
            ?: books.firstOrNull { b -> aliases[bookPart] == b.index }
            ?: return null
        val chapter = m.groupValues[2].toInt()
        if (chapter !in 1..book.chapters.size) return null
        val verse = m.groupValues[3].toIntOrNull() ?: 0
        if (verse > book.chapters[chapter - 1].size) return null
        return VerseRef(book.index, chapter, verse)
    }

    /** Case-insensitive search for all words in [query]. Results are in canonical order. */
    fun search(query: String, limit: Int = 1000): List<SearchHit> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for ((ref, lower) in searchIndex) {
            if (words.all { lower.contains(it) }) {
                hits += SearchHit(ref, plainText(ref).orEmpty())
                if (hits.size >= limit) break
            }
        }
        return hits
    }

    private val aliases = mapOf(
        "jdg" to 6, "jg" to 6, "ss" to 21, "song" to 21, "sos" to 21, "canticles" to 21,
        "ps" to 18, "psa" to 18, "psalm" to 18, "pr" to 19, "prov" to 19, "qoh" to 20,
        "ezk" to 25, "jn" to 42, "jhn" to 42, "mk" to 40, "mrk" to 40, "lk" to 41,
        "phm" to 56, "phlm" to 56, "php" to 49, "phil" to 49, "jas" to 58, "rev" to 65,
    )

    companion object {
        /**
         * Parses the bundled TSV: one verse per line, `book<TAB>chapter<TAB>verse<TAB>text`,
         * where book is 1..66. Lines must be in canonical order.
         */
        fun parse(lines: Sequence<String>): Bible {
            val chapters = List(BOOK_NAMES.size) { ArrayList<ArrayList<String>>() }
            for (line in lines) {
                if (line.isBlank()) continue
                val t1 = line.indexOf('\t')
                val t2 = line.indexOf('\t', t1 + 1)
                val t3 = line.indexOf('\t', t2 + 1)
                if (t1 < 0 || t2 < 0 || t3 < 0) continue
                val b = line.substring(0, t1).toInt() - 1
                val c = line.substring(t1 + 1, t2).toInt()
                val bookChapters = chapters[b]
                while (bookChapters.size < c) bookChapters += ArrayList<String>()
                bookChapters[c - 1] += line.substring(t3 + 1)
            }
            return Bible(BOOK_NAMES.mapIndexed { i, n -> Book(i, n, chapters[i]) })
        }
    }
}
