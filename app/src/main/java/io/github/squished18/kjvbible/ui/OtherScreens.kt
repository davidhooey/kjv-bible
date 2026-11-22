package io.github.squished18.kjvbible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.squished18.kjvbible.data.Bible
import io.github.squished18.kjvbible.data.Book
import io.github.squished18.kjvbible.data.OLD_TESTAMENT_COUNT
import io.github.squished18.kjvbible.data.VerseRef
import io.github.squished18.kjvbible.ui.theme.ScriptureFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}

// ---------------------------------------------------------------- Books & chapters

@Composable
fun BooksScreen(vm: AppViewModel, bible: Bible) {
    // Three steps: book list -> chapter grid -> verse grid.
    var openBook by remember { mutableStateOf<Book?>(null) }
    var openChapter by remember { mutableStateOf<Int?>(null) }
    val book = openBook
    val chapter = openChapter
    val singleChapter = book != null && book.chapters.size == 1

    fun stepBack() {
        when {
            chapter != null && !singleChapter -> openChapter = null
            book != null -> { openChapter = null; openBook = null }
            else -> vm.screen = Screen.READER
        }
    }
    BackHandler(enabled = book != null) { stepBack() }

    val title = when {
        book == null -> "Books"
        chapter == null -> book.name
        else -> bible.label(VerseRef(book.index, chapter))
    }

    Scaffold(topBar = { BackTopBar(title) { stepBack() } }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                book == null -> BookList(bible, current = vm.position.book, modifier = Modifier) { b ->
                    openBook = b
                    // Books with one chapter (Obadiah, Philemon, 2 & 3 John, Jude) go straight to verses.
                    openChapter = if (b.chapters.size == 1) 1 else null
                }
                chapter == null -> {
                    StepLabel("Choose a chapter")
                    val current = if (vm.position.book == book.index) vm.position.chapter else -1
                    NumberGrid(count = book.chapters.size, current = current) { openChapter = it }
                }
                else -> {
                    StepLabel("Choose a verse")
                    NumberGrid(count = book.chapters[chapter - 1].size, current = -1) { v ->
                        vm.goTo(VerseRef(book.index, chapter, v))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
    )
}

@Composable
private fun NumberGrid(count: Int, current: Int, onPick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(64.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items((1..count).toList()) { n ->
            val isCurrent = n == current
            Surface(
                onClick = { onPick(n) },
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.aspectRatio(1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$n", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun BookList(bible: Bible, current: Int, modifier: Modifier, onPick: (Book) -> Unit) {
    // Headers sit at list index 0 (OT) and OLD_TESTAMENT_COUNT + 1 (NT).
    val initialIndex = if (current < OLD_TESTAMENT_COUNT) current + 1 else current + 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 3).coerceAtLeast(0))
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item { SectionHeader("Old Testament") }
        items(bible.books.take(OLD_TESTAMENT_COUNT)) { BookRow(it, it.index == current, onPick) }
        item { SectionHeader("New Testament") }
        items(bible.books.drop(OLD_TESTAMENT_COUNT)) { BookRow(it, it.index == current, onPick) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun BookRow(book: Book, isCurrent: Boolean, onPick: (Book) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(book) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            book.name,
            fontFamily = ScriptureFont,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (book.chapters.size == 1) "1 chapter" else "${book.chapters.size} chapters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, bible: Bible) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    val query = vm.searchQuery
    val reference = remember(query) { bible.parseReference(query) }
    val words = remember(query) { query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() } }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { vm.screen = Screen.READER }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                TextField(
                    value = query,
                    onValueChange = vm::onSearchQueryChange,
                    placeholder = { Text("Search words or a reference") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        reference?.let { vm.goTo(it) }
                    }),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (reference != null) item {
                    Card(
                        onClick = { vm.goTo(reference) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Go to ${bible.label(reference)}", style = MaterialTheme.typography.titleMedium)
                            bible.plainText(reference)?.let {
                                Text(it, fontFamily = ScriptureFont, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (query.trim().length >= 2 && !vm.searching) item {
                    val n = vm.searchResults.size
                    Text(
                        when (n) {
                            0 -> if (reference == null) "No verses found" else ""
                            1 -> "1 verse"
                            1000 -> "First 1,000 verses"
                            else -> "%,d verses".format(n)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(vm.searchResults, key = { it.ref.key() }) { hit ->
                    VerseListItem(
                        label = bible.label(hit.ref),
                        text = highlightWords(hit.text, words, MaterialTheme.colorScheme.secondaryContainer),
                        onClick = { keyboard?.hide(); vm.goTo(hit.ref) },
                    )
                }
            }
        }
    }
}

private fun highlightWords(text: String, words: List<String>, bg: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    val lower = text.lowercase()
    for (w in words) {
        var i = lower.indexOf(w)
        while (i >= 0) {
            addStyle(SpanStyle(background = bg, fontWeight = FontWeight.SemiBold), i, i + w.length)
            i = lower.indexOf(w, i + w.length)
        }
    }
}

@Composable
private fun VerseListItem(
    label: String,
    text: AnnotatedString,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = if (trailing != null) 4.dp else 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(text, fontFamily = ScriptureFont, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
}

// ---------------------------------------------------------------- Bookmarks

@Composable
fun BookmarksScreen(vm: AppViewModel, bible: Bible) {
    val marks = remember(vm.bookmarks) {
        vm.bookmarks.sortedWith(compareBy({ it.book }, { it.chapter }, { it.verse }))
    }
    Scaffold(topBar = { BackTopBar("Bookmarks") { vm.screen = Screen.READER } }) { padding ->
        if (marks.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No bookmarks yet.\n\nTap a verse while reading, then choose Bookmark.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(marks, key = { it.key() }) { ref ->
                    VerseListItem(
                        label = bible.label(ref),
                        text = AnnotatedString(bible.plainText(ref).orEmpty()),
                        onClick = { vm.goTo(ref) },
                        trailing = {
                            IconButton(onClick = { vm.removeBookmark(ref) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove bookmark")
                            }
                        },
                    )
                }
            }
        }
    }
}
