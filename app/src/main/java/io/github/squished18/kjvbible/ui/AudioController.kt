package io.github.squished18.kjvbible.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.squished18.kjvbible.BuildConfig
import io.github.squished18.kjvbible.data.AudioStore
import io.github.squished18.kjvbible.data.Bible
import io.github.squished18.kjvbible.data.VerseRef
import io.github.squished18.kjvbible.data.Voice
import io.github.squished18.kjvbible.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class DownloadState(val voice: Voice, val book: Int, val progress: Float)

/**
 * Audio side of the view model: downloads books on demand, drives the PlaybackService through a
 * MediaController, and tracks which verse is being read from the verse timings.
 */
class AudioController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val bible: () -> Bible?,
) {
    val store = AudioStore(app, BuildConfig.AUDIO_BASE_URL)

    /** The verse being read aloud, or null when nothing is loaded. */
    var nowPlaying by mutableStateOf<VerseRef?>(null)
        private set
    var nowPlayingVoice by mutableStateOf<Voice?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var download by mutableStateOf<DownloadState?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var downloadedBytes by mutableStateOf(0L)
        private set

    /** Called when playback moves to another chapter, so the reader can follow along. */
    var onChapterChange: ((from: VerseRef?, to: VerseRef) -> Unit)? = null

    private val controllerFuture = MediaController.Builder(
        app, SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null
    private var playJob: Job? = null
    private var tickJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (playing) startTicker() else tickJob?.cancel()
            updateNowPlaying()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNowPlaying()

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onPlaylistEnded()
            updateNowPlaying()
        }
    }

    init {
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            // Pick up audio that kept playing while the app was closed.
            isPlaying = c.isPlaying
            if (c.isPlaying) startTicker()
            updateNowPlaying()
        }, ContextCompat.getMainExecutor(app))
        refreshDownloadedBytes()
    }

    private suspend fun awaitController(): MediaController = controller ?: suspendCancellableCoroutine { cont ->
        controllerFuture.addListener(
            { runCatching { controllerFuture.get() }.onSuccess { cont.resume(it) }.onFailure { cont.cancel(it) } },
            ContextCompat.getMainExecutor(app),
        )
    }

    /** Plays from [ref] to the end of its book (then carries on into the next), downloading first if needed. */
    fun playFrom(ref: VerseRef, voice: Voice) {
        playJob?.cancel()
        error = null
        playJob = scope.launch {
            val b = bible() ?: return@launch
            val book = ref.book
            if (!store.isDownloaded(voice, book)) {
                download = DownloadState(voice, book, 0f)
                try {
                    store.download(voice, book) { p -> scope.launch { download = DownloadState(voice, book, p) } }
                } catch (e: CancellationException) {
                    download = null
                    throw e
                } catch (e: Exception) {
                    download = null
                    error = "Couldn't download ${b.books[book].name}: ${e.message ?: e.javaClass.simpleName}"
                    return@launch
                }
                download = null
                refreshDownloadedBytes()
            }
            val timings = withContext(Dispatchers.IO) { store.timings(voice, book) }
            val items = (ref.chapter..b.books[book].chapters.size).map { ch ->
                MediaItem.Builder()
                    .setUri(Uri.fromFile(store.chapterFile(voice, book, ch)))
                    .setMediaId("${voice.id}/$book/$ch")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(b.label(VerseRef(book, ch)))
                            .setArtist("King James Version · ${voice.label}")
                            .setAlbumTitle(b.books[book].name)
                            .build(),
                    )
                    .build()
            }
            // Verse 1 starts at 0 so the chapter announcement is heard too.
            val startMs = if (ref.verse <= 1) 0L else timings[ref.chapter]?.getOrNull(ref.verse - 1)?.toLong() ?: 0L
            val c = awaitController()
            c.setMediaItems(items, 0, startMs)
            c.prepare()
            c.play()
        }
    }

    fun togglePause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun stop() {
        playJob?.cancel()
        download = null
        error = null
        controller?.run {
            stop()
            clearMediaItems()
        }
        nowPlaying = null
        nowPlayingVoice = null
    }

    fun clearError() {
        error = null
    }

    fun deleteAll() {
        stop()
        scope.launch {
            withContext(Dispatchers.IO) { store.deleteAll() }
            refreshDownloadedBytes()
        }
    }

    private fun refreshDownloadedBytes() {
        scope.launch { downloadedBytes = withContext(Dispatchers.IO) { store.downloadedBytes() } }
    }

    private fun onPlaylistEnded() {
        val at = nowPlaying ?: return
        val voice = nowPlayingVoice ?: return
        val b = bible() ?: return
        if (at.book + 1 < b.books.size) playFrom(VerseRef(at.book + 1, 1, 1), voice) else stop()
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                updateNowPlaying()
                delay(250)
            }
        }
    }

    private fun updateNowPlaying() {
        val c = controller ?: return
        val parts = c.currentMediaItem?.mediaId?.split('/')
        val voice = Voice.fromId(parts?.getOrNull(0))
        val book = parts?.getOrNull(1)?.toIntOrNull()
        val chapter = parts?.getOrNull(2)?.toIntOrNull()
        if (voice == null || book == null || chapter == null || c.mediaItemCount == 0) {
            if (download == null && playJob?.isActive != true) {
                nowPlaying = null
                nowPlayingVoice = null
            }
            return
        }
        val starts = if (store.isDownloaded(voice, book)) store.timings(voice, book)[chapter] else null
        val pos = c.currentPosition
        var verse = 1
        if (starts != null) {
            for (i in starts.indices) if (starts[i] <= pos) verse = i + 1 else break
        }
        val ref = VerseRef(book, chapter, verse)
        val previous = nowPlaying
        if (ref != previous) {
            nowPlaying = ref
            if (previous == null || previous.book != book || previous.chapter != chapter) {
                onChapterChange?.invoke(previous, ref)
            }
        }
        nowPlayingVoice = voice
    }

    fun release() {
        tickJob?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}
