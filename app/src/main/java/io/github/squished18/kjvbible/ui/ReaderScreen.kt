package io.github.squished18.kjvbible.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.squished18.kjvbible.data.Bible
import io.github.squished18.kjvbible.data.ThemeMode
import io.github.squished18.kjvbible.data.VerseRef
import io.github.squished18.kjvbible.data.Voice
import io.github.squished18.kjvbible.ui.theme.ScriptureFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: AppViewModel, bible: Bible) {
    val pageCount = bible.chapterIndex.size
    val pagerState = rememberPagerState(initialPage = bible.page(vm.position.book, vm.position.chapter)) { pageCount }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<VerseRef>()) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    // Follow navigation from other screens (book picker, search, bookmarks).
    LaunchedEffect(vm.position) {
        val target = bible.page(vm.position.book, vm.position.chapter)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    // Report swipes back to the view model so the position is remembered.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val (b, c) = bible.chapterIndex[page]
            vm.onChapterShown(b, c)
            selected = emptySet()
        }
    }

    Scaffold(
        topBar = {
            if (selected.isEmpty()) {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { vm.screen = Screen.BOOKS }
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(bible.label(vm.position), fontFamily = ScriptureFont, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose book and chapter")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.screen = Screen.SEARCH }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.screen = Screen.BOOKMARKS }) {
                            Icon(Icons.Filled.Star, contentDescription = "Bookmarks")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Reading settings")
                        }
                    },
                )
            } else {
                SelectionBar(vm, bible, selected, onDone = { selected = emptySet() })
            }
        },
        bottomBar = { AudioBar(vm, bible) },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            beyondViewportPageCount = 1,
        ) { page ->
            val (book, chapter) = bible.chapterIndex[page]
            ChapterPage(
                vm = vm,
                bible = bible,
                book = book,
                chapter = chapter,
                selected = selected,
                onVerseClick = { ref -> selected = if (ref in selected) selected - ref else selected + ref },
                onPrev = if (page > 0) ({ scope.launch { pagerState.animateScrollToPage(page - 1) }; Unit }) else null,
                onNext = if (page < pageCount - 1) ({ scope.launch { pagerState.animateScrollToPage(page + 1) }; Unit }) else null,
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReadingSettings(vm)
        }
    }
}

@Composable
private fun ChapterPage(
    vm: AppViewModel,
    bible: Bible,
    book: Int,
    chapter: Int,
    selected: Set<VerseRef>,
    onVerseClick: (VerseRef) -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val verses = remember(book, chapter) { bible.verses(book, chapter) }
    val listState = rememberLazyListState()
    var highlight by remember { mutableStateOf<Int?>(null) }
    val fontSize = vm.fontSize

    val jump = vm.jumpTarget
    LaunchedEffect(jump) {
        if (jump != null && jump.book == book && jump.chapter == chapter) {
            // Item 0 is the chapter heading, so verse n is item n. Verse 1 keeps the heading in view.
            listState.scrollToItem(if (jump.verse > 1) jump.verse else 0)
            if (jump.verse > 0) highlight = jump.verse
            // Consuming changes the key and cancels this effect, so it must come last.
            vm.consumeJump()
        }
    }
    LaunchedEffect(highlight) {
        if (highlight != null) {
            delay(1800)
            highlight = null
        }
    }

    // Keep the verse being read aloud in view, but only while the reader is following it: if they
    // have scrolled away from the previous verse, leave them where they are.
    val playing = vm.audio.nowPlaying?.takeIf { it.book == book && it.chapter == chapter }?.verse
    var lastPlaying by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(playing) {
        if (playing != null) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val viewEnd = listState.layoutInfo.viewportEndOffset
            fun fullyVisible(item: Int) = visible.any { it.index == item && it.offset >= 0 && it.offset + it.size <= viewEnd }
            val following = lastPlaying == null || visible.any { it.index == lastPlaying }
            if (following && !fullyVisible(playing)) listState.animateScrollToItem(if (playing > 1) playing - 1 else 0)
        }
        lastPlaying = playing
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (chapter == 1) {
                    Text(
                        bible.books[book].name.uppercase(),
                        fontFamily = ScriptureFont,
                        letterSpacing = 3.sp,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    if (bible.books[book].name == "Psalms") "Psalm $chapter" else "Chapter $chapter",
                    fontFamily = ScriptureFont,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        itemsIndexed(verses) { i, text ->
            val ref = VerseRef(book, chapter, i + 1)
            VerseRow(
                number = i + 1,
                text = text,
                fontSize = fontSize,
                isSelected = ref in selected,
                isBookmarked = ref in vm.bookmarks,
                isHighlighted = highlight == i + 1,
                isNowPlaying = playing == i + 1,
                onClick = { onVerseClick(ref) },
            )
        }
        item {
            Row(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onPrev != null) TextButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    Text("Previous")
                } else Spacer(Modifier)
                if (onNext != null) TextButton(onClick = onNext) {
                    Text("Next")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun VerseRow(
    number: Int,
    text: String,
    fontSize: Float,
    isSelected: Boolean,
    isBookmarked: Boolean,
    isHighlighted: Boolean,
    isNowPlaying: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val target = when {
        isSelected -> colors.primaryContainer
        isHighlighted || isNowPlaying -> colors.secondaryContainer
        isBookmarked -> colors.secondaryContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(target, animationSpec = tween(350), label = "verseBg")
    val numberColor = colors.primary
    val annotated = remember(number, text, numberColor, isBookmarked, fontSize) {
        buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = numberColor,
                    fontSize = (fontSize * 0.62f).sp,
                    fontWeight = FontWeight.Bold,
                    baselineShift = BaselineShift(0.35f),
                ),
            ) { append(if (isBookmarked) "★$number " else "$number ") }
            appendWithItalics(text)
        }
    }
    Text(
        annotated,
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .drawBehind {
                // A bar down the left edge marks the verse being read aloud.
                if (isNowPlaying) drawRect(numberColor, size = Size(4.dp.toPx(), size.height))
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        fontFamily = ScriptureFont,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.55f).sp,
        color = if (isSelected) colors.onPrimaryContainer else colors.onBackground,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(vm: AppViewModel, bible: Bible, selected: Set<VerseRef>, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val sorted = selected.sortedBy { it.verse }
    val allMarked = sorted.all { it in vm.bookmarks }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        navigationIcon = {
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Clear selection") }
        },
        title = { Text("${sorted.size} selected", maxLines = 1) },
        actions = {
            IconButton(onClick = { vm.playFrom(sorted.first()); onDone() }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play from verse ${sorted.first().verse}")
            }
            TextButton(onClick = { vm.toggleBookmarks(sorted); onDone() }) {
                Text(if (allMarked) "Unmark" else "Bookmark")
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(formatPassage(bible, sorted)))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                onDone()
            }) { Text("Copy") }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, formatPassage(bible, sorted))
                }
                context.startActivity(Intent.createChooser(send, null))
                onDone()
            }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
        },
    )
}

/** Appends [text], rendering [bracketed] translator-supplied words in italics without the brackets. */
private fun AnnotatedString.Builder.appendWithItalics(text: String) {
    var depth = 0
    var start = 0
    fun flush(end: Int) {
        if (end <= start) return
        val chunk = text.substring(start, end)
        if (depth > 0) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(chunk) } else append(chunk)
    }
    for ((i, ch) in text.withIndex()) {
        if (ch == '[' || ch == ']') {
            flush(i)
            depth = if (ch == '[') depth + 1 else (depth - 1).coerceAtLeast(0)
            start = i + 1
        }
    }
    flush(text.length)
}

/** "1 In the beginning… 2 And the earth…\n— Genesis 1:1-2 (KJV)" */
fun formatPassage(bible: Bible, refs: List<VerseRef>): String {
    if (refs.isEmpty()) return ""
    val first = refs.first()
    val body = if (refs.size == 1) bible.plainText(first).orEmpty()
    else refs.joinToString(" ") { "${it.verse} ${bible.plainText(it).orEmpty()}" }
    val nums = refs.map { it.verse }
    val contiguous = nums.zipWithNext().all { (a, b) -> b == a + 1 }
    val verses = when {
        nums.size == 1 -> "${nums[0]}"
        contiguous -> "${nums.first()}-${nums.last()}"
        else -> nums.joinToString(",")
    }
    return "$body\n— ${bible.books[first.book].name} ${first.chapter}:$verses (KJV)"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReadingSettings(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text("Text size", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = vm.fontSize,
            onValueChange = { vm.updateFontSize(it) },
            valueRange = 14f..32f,
            steps = 8,
        )
        Text(
            "In the beginning God created the heaven and the earth.",
            fontFamily = ScriptureFont,
            fontSize = vm.fontSize.sp,
            lineHeight = (vm.fontSize * 1.5f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to "System",
                ThemeMode.LIGHT to "Light",
                ThemeMode.SEPIA to "Sepia",
                ThemeMode.DARK to "Dark",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = vm.themeMode == mode,
                    onClick = { vm.updateTheme(mode) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Reading voice", style = MaterialTheme.typography.titleMedium)
        Voice.entries.forEach { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = vm.voice == v, role = Role.RadioButton, onClick = { vm.updateVoice(v) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.voice == v, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(v.label, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(v.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The player bar is hidden behind this sheet, so show download progress and errors here too.
        val download = vm.audio.download
        val error = vm.audio.error
        when {
            download != null -> {
                Text(
                    "Downloading ${vm.bible?.books?.getOrNull(download.book)?.name ?: ""} (${download.voice.label})… " +
                        "${(download.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> Text(
                "Audio downloads one book at a time the first time you play it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val bytes = vm.audio.downloadedBytes
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Downloaded audio: " + if (bytes == 0L) "none" else "%.0f MB".format(bytes / 1_000_000.0),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.audio.deleteAll() }, enabled = bytes > 0) { Text("Delete") }
        }
    }
}

/** Player controls under the reader while a book is downloading or playing. */
@Composable
private fun AudioBar(vm: AppViewModel, bible: Bible) {
    val audio = vm.audio
    val download = audio.download
    val playing = audio.nowPlaying
    val error = audio.error
    if (download == null && playing == null && error == null) return
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            if (download != null) LinearProgressIndicator(progress = { download.progress }, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = playing != null && download == null) { playing?.let { vm.goTo(it) } },
                ) {
                    val (title, subtitle) = when {
                        download != null -> "Downloading ${bible.books[download.book].name}…" to
                            "${download.voice.label} · ${(download.progress * 100).toInt()}%"
                        error != null -> "Audio unavailable" to error
                        else -> bible.label(playing!!) to (audio.nowPlayingVoice?.label ?: "")
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall, fontFamily = ScriptureFont, maxLines = 1)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (download == null && error == null) {
                    IconButton(onClick = { audio.togglePause() }) {
                        if (audio.isPlaying) Icon(PauseIcon, contentDescription = "Pause")
                        else Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(onClick = { audio.stop() }) {
                    Icon(Icons.Filled.Close, contentDescription = if (download != null) "Cancel download" else "Stop")
                }
            }
        }
    }
}

/** Material "pause" (not in the core icon set). */
private val PauseIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Pause") {
        materialPath {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
        }
    }
}

