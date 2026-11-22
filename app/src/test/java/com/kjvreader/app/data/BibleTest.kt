package com.kjvreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class BibleTest {
    companion object {
        lateinit var bible: Bible

        @BeforeClass
        @JvmStatic
        fun load() {
            bible = File("src/main/assets/kjv.tsv").bufferedReader().useLines { Bible.parse(it) }
        }
    }

    @Test
    fun hasAllBooksChaptersAndVerses() {
        assertEquals(66, bible.books.size)
        assertEquals(1189, bible.chapterIndex.size)
        assertEquals(31102, bible.books.sumOf { b -> b.chapters.sumOf { it.size } })
        assertEquals(150, bible.books[18].chapters.size) // Psalms
        assertEquals(176, bible.verses(18, 119).size)    // Psalm 119
    }

    @Test
    fun firstAndLastVerses() {
        assertEquals("In the beginning God created the heaven and the earth.", bible.verseText(VerseRef(0, 1, 1)))
        assertEquals("The grace of our Lord Jesus Christ be with you all. Amen.", bible.plainText(VerseRef(65, 22, 21)))
    }

    @Test
    fun italicMarkersAreStrippedInPlainText() {
        val raw = bible.verseText(VerseRef(61, 2, 23))!!
        assertTrue(raw.contains("[[but]"))
        assertTrue(!bible.plainText(VerseRef(61, 2, 23))!!.contains('['))
    }

    @Test
    fun parsesReferences() {
        assertEquals(VerseRef(42, 3, 16), bible.parseReference("John 3:16"))
        assertEquals(VerseRef(42, 3, 16), bible.parseReference("jn 3.16"))
        assertEquals(VerseRef(45, 13, 0), bible.parseReference("1 cor 13"))
        assertEquals(VerseRef(18, 23, 0), bible.parseReference("ps 23"))
        assertEquals(VerseRef(61, 4, 8), bible.parseReference("1 John 4:8"))
        assertEquals(VerseRef(21, 2, 1), bible.parseReference("song 2:1"))
        assertEquals(VerseRef(49, 4, 13), bible.parseReference("phil 4:13"))
        assertEquals(VerseRef(56, 1, 0), bible.parseReference("philemon 1"))
        assertNull(bible.parseReference("love one another"))
        assertNull(bible.parseReference("John 30"))
        assertNull(bible.parseReference("John 3:99"))
    }

    @Test
    fun searchFindsAllWordsIgnoringCaseAndMarkers() {
        val hits = bible.search("God so loved")
        assertTrue(hits.any { it.ref == VerseRef(42, 3, 16) })
        hits.forEach { h -> listOf("god", "so", "loved").forEach { assertTrue(h.text.lowercase().contains(it)) } }
        // "was upon" spans an italic marker in Genesis 1:2 ("darkness [was] upon").
        assertTrue(bible.search("darkness was upon").any { it.ref == VerseRef(0, 1, 2) })
        assertTrue(bible.search("zzzqqq").isEmpty())
    }

    @Test
    fun pagesRoundTrip() {
        for ((page, bc) in bible.chapterIndex.withIndex()) assertEquals(page, bible.page(bc.first, bc.second))
    }
}
