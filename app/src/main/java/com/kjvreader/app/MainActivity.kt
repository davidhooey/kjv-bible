package com.kjvreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kjvreader.app.ui.AppViewModel
import com.kjvreader.app.ui.BookmarksScreen
import com.kjvreader.app.ui.BooksScreen
import com.kjvreader.app.ui.ReaderScreen
import com.kjvreader.app.ui.Screen
import com.kjvreader.app.ui.SearchScreen
import com.kjvreader.app.ui.theme.KjvTheme
import com.kjvreader.app.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KjvTheme(vm.themeMode) {
                val dark = isDarkTheme(vm.themeMode)
                val bg = MaterialTheme.colorScheme.background.toArgb()
                LaunchedEffect(dark, bg) {
                    val style = if (dark) SystemBarStyle.dark(bg) else SystemBarStyle.light(bg, bg)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val bible = vm.bible
    if (bible == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val err = vm.loadError
            if (err != null) Text("Could not load the Bible text: $err", Modifier.padding(24.dp))
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text("Opening the King James Bible…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    BackHandler(enabled = vm.screen != Screen.READER) { vm.screen = Screen.READER }

    when (vm.screen) {
        Screen.READER -> ReaderScreen(vm, bible)
        Screen.BOOKS -> BooksScreen(vm, bible)
        Screen.SEARCH -> SearchScreen(vm, bible)
        Screen.BOOKMARKS -> BookmarksScreen(vm, bible)
    }
}
