package com.kjvreader.app.ui

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
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
import com.kjvreader.app.data.Bible
import com.kjvreader.app.data.ThemeMode
import com.kjvreader.app.data.VerseRef
import com.kjvreader.app.ui.theme.ScriptureFont
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
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val target = when {
        isSelected -> colors.primaryContainer
        isHighlighted -> colors.secondaryContainer
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
        title = { Text("${sorted.size} selected") },
        actions = {
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
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, formatPassage(bible, sorted))
                }
                context.startActivity(Intent.createChooser(send, null))
                onDone()
            }) { Text("Share") }
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
    }
}

