package com.ziaee.zabankhan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.read
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import com.ziaee.zabankhan.app.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.ui.reader.ReaderScreen
import com.ziaee.zabankhan.watch.*
import com.ziaee.zabankhan.ui.theme.*
import org.jetbrains.compose.resources.stringResource

@Composable fun App(container: AppContainer) {
    val vm = viewModel { AppViewModel(container) }
    val settings by vm.settings.collectAsState()
    CompositionLocalProvider(LocalAppContainer provides container) {
        ZabankhanTheme(ThemeMode.entries.firstOrNull { it.name == settings?.themeMode } ?: ThemeMode.SYSTEM) {
            val prefs = settings
            if (prefs == null) { CircularProgressIndicator(); return@ZabankhanTheme }
            if (prefs.learningLang == null) { Onboarding(vm); return@ZabankhanTheme }
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val labels = listOf(Res.string.nav_home, Res.string.nav_library, Res.string.nav_words, Res.string.nav_review, Res.string.nav_settings)
            val routes = listOf("home", "library", "words", "review", "settings")
            val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.LibraryBooks, Icons.Default.Translate, Icons.Default.Style, Icons.Default.Settings)
            val snackbar = remember { SnackbarHostState() }
            val errorMessage = stringResource(Res.string.error_save)
            LaunchedEffect(vm.failed) { if (vm.failed) { snackbar.showSnackbar(errorMessage); vm.clearError() } }
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                if (route in routes) NavigationBar {
                    routes.forEachIndexed { i, target -> NavigationBarItem(selected = route == target,
                        onClick = { nav.navigate(target) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icons[i], null) }, label = { Text(stringResource(labels[i]), maxLines = 1) }) }
                }
            }) { padding ->
                NavHost(nav, "home", Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable("home") { HomeScreen(vm, prefs, { id -> vm.action { val doc = container.texts.getById(id); nav.navigate(if (doc?.pdfPath != null) "pdf/$id" else "reader/$id") } }, { nav.navigate("add") }, { nav.navigate("review") }, { nav.navigate("videos") }) }
                    composable("library") { LibraryScreen(vm, prefs, { id -> vm.action { val doc = container.texts.getById(id); nav.navigate(if (doc?.pdfPath != null) "pdf/$id" else "reader/$id") } }, { nav.navigate("add") }, { nav.navigate("videos") }) }
                    composable("videos") { VideoPickerScreen(vm, prefs, { nav.popBackStack() }) { video, path, subs -> nav.navigate(watchRoute(video, path, subs)) } }
                    composable("watch?videoId={videoId}&path={path}&subs={subs}", arguments = listOf("videoId", "path", "subs").map { name -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }) { entry ->
                        WatchScreen(vm, prefs, entry.arguments?.read { getString("videoId") }.orEmpty(), entry.arguments?.read { getString("path") }.orEmpty(), entry.arguments?.read { getString("subs") }.orEmpty()) { nav.popBackStack() }
                    }
                    composable("words") { WordsScreen(vm, prefs) }
                    composable("review") { ReviewScreen(container, prefs) }
                    composable("settings") { SettingsScreen(vm, prefs) }
                    composable("add") { AddTextScreen(vm, prefs, { nav.popBackStack() }) { id -> nav.navigate("reader/$id") { popUpTo("add") { inclusive = true } } } }
                    composable("pdf/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ReaderScreen(vm, prefs, requireNotNull(it.arguments?.read { getLong("id") })) { nav.popBackStack() }
                    }
                    composable("reader/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ReaderScreen(vm, prefs, requireNotNull(it.arguments?.read { getLong("id") })) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}
