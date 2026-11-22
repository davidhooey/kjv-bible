package io.github.squished18.kjvbible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** The Kokoro text-to-speech voices the audio was recorded with. [id] names the files online. */
enum class Voice(val id: String, val label: String, val description: String) {
    BELLA("af_bella", "Bella", "American, female"),
    ERIC("am_eric", "Eric", "American, male"),
    DANIEL("bm_daniel", "Daniel", "British, male"),
    ;

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/**
 * Chapter recordings, downloaded one book at a time as `<voice>_<BB>.zip` (BB = 01..66) from
 * [baseUrl]. Each zip holds `CCC.mp3` per chapter and `timings.tsv` with
 * `chapter<TAB>verse<TAB>start_ms` lines. Books are unpacked to `files/audio/<voice>/<BB>/`.
 */
class AudioStore(context: Context, private val baseUrl: String) {
    private val root = File(context.filesDir, "audio")
    private val timingCache = HashMap<Pair<Voice, Int>, Map<Int, IntArray>>()

    private fun bookDir(voice: Voice, book: Int) = File(root, "${voice.id}/%02d".format(book + 1))

    /** timings.tsv is moved into place last, so its presence means the book is complete. */
    fun isDownloaded(voice: Voice, book: Int) = File(bookDir(voice, book), TIMINGS).isFile

    fun chapterFile(voice: Voice, book: Int, chapter: Int) = File(bookDir(voice, book), "%03d.mp3".format(chapter))

    /** Verse start times in ms for each chapter of [book]; index 0 is verse 1. */
    fun timings(voice: Voice, book: Int): Map<Int, IntArray> = synchronized(timingCache) {
        timingCache.getOrPut(voice to book) {
            val rows = File(bookDir(voice, book), TIMINGS).readLines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size == 3) Triple(p[0].toInt(), p[1].toInt(), p[2].toInt()) else null
            }
            rows.groupBy { it.first }.mapValues { (_, vs) ->
                val arr = IntArray(vs.maxOf { it.second })
                vs.forEach { arr[it.second - 1] = it.third }
                arr
            }
        }
    }

    /** Downloads and unpacks one book, reporting progress from 0 to 1. Cancellable. */
    suspend fun download(voice: Voice, book: Int, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val target = bookDir(voice, book)
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.deleteRecursively()
        tmp.mkdirs()
        try {
            val conn = URL("$baseUrl/${voice.id}_%02d.zip".format(book + 1)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("server replied ${conn.responseCode}")
                val total = conn.contentLengthLong
                var read = 0L
                var lastReported = -1
                val counting = object : FilterInputStream(conn.inputStream) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0 && total > 0) {
                            read += n
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct / 100f)
                            }
                        }
                        return n
                    }
                }
                var gotTimings = false
                ZipInputStream(counting.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        coroutineContext.ensureActive()
                        // Only accept the expected flat file names (no paths), so nothing can be
                        // written outside the book folder.
                        if (entry.isDirectory || !ENTRY.matches(entry.name)) continue
                        val name = if (entry.name == TIMINGS) "$TIMINGS.new" else entry.name
                        File(tmp, name).outputStream().use { out -> zip.copyToCancellable(out) }
                        if (entry.name == TIMINGS) gotTimings = true
                    }
                }
                if (!gotTimings) throw IOException("the download had no verse timings")
            } finally {
                conn.disconnect()
            }
            target.deleteRecursively()
            if (!tmp.renameTo(target)) throw IOException("couldn't save the audio")
            File(target, "$TIMINGS.new").renameTo(File(target, TIMINGS))
            synchronized(timingCache) { timingCache.remove(voice to book) }
        } finally {
            tmp.deleteRecursively()
        }
    }

    private suspend fun InputStream.copyToCancellable(out: java.io.OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
    }

    fun downloadedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun deleteAll() {
        root.deleteRecursively()
        synchronized(timingCache) { timingCache.clear() }
    }

    companion object {
        private const val TIMINGS = "timings.tsv"
        private val ENTRY = Regex("""\d{3}\.mp3|timings\.tsv""")
    }
}
