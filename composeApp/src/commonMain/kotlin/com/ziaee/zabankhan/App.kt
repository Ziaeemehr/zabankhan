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
import com.ziaee.zabankhan.ui.reader.readerRoute
import com.ziaee.zabankhan.watch.*
import com.ziaee.zabankhan.ui.theme.*
import org.jetbrains.compose.resources.stringResource

@Composable fun App(container: AppContainer, request: AppOpenRequest? = null, onRequestHandled: () -> Unit = {}) {
    key(container) { AppContent(container, request, onRequestHandled) }
}

data class AppOpenRequest(val text: String? = null, val review: Boolean = false, val id: Long = 0)

@Composable private fun AppContent(container: AppContainer, request: AppOpenRequest?, onRequestHandled: () -> Unit) {
    val vm = viewModel(key = "app-${container.hashCode()}") { AppViewModel(container) }
    val settings by vm.settings.collectAsState()
    CompositionLocalProvider(LocalAppContainer provides container) {
        ZabankhanTheme(ThemeMode.entries.firstOrNull { it.name == settings?.themeMode } ?: ThemeMode.SYSTEM) {
            val prefs = settings
            if (prefs == null) { CircularProgressIndicator(); return@ZabankhanTheme }
            if (prefs.learningLang == null) { Onboarding(vm); return@ZabankhanTheme }
            SideEffect { com.ziaee.zabankhan.speech.SpeechEngine.configure(prefs) }
            val tts = remember { com.ziaee.zabankhan.speech.createTts() }
            val speaking by tts.isSpeaking.collectAsState()
            val spokenLang by com.ziaee.zabankhan.speech.SpeechActivity.language.collectAsState()
            spokenLang?.let { lang -> com.ziaee.zabankhan.statistics.TrackActivity(container, "tts", lang, com.ziaee.zabankhan.statistics.ActivityKind.LISTENING, speaking) }
            val nav = rememberNavController()
            val reminderOpen by com.ziaee.zabankhan.reminder.ReviewReminderNavigation.pending.collectAsState()
            LaunchedEffect(reminderOpen) {
                if (reminderOpen) {
                    nav.navigate("review") { launchSingleTop = true }
                    com.ziaee.zabankhan.reminder.ReviewReminderNavigation.consume()
                }
            }
            var sharedText by remember { mutableStateOf("") }
            LaunchedEffect(request) {
                if (request != null) {
                    when {
                        request.review -> nav.navigate("review") { launchSingleTop = true }
                        request.text != null -> { sharedText = request.text; nav.navigate("add") { launchSingleTop = true } }
                    }
                    onRequestHandled()
                }
            }
            com.ziaee.zabankhan.reminder.ReviewReminderLifecycle(container, prefs)
            LaunchedEffect(prefs.learningLang, prefs.meaningLang) {
                val language = com.ziaee.zabankhan.lang.Languages.byCode(requireNotNull(prefs.learningLang)) ?: return@LaunchedEffect
                try {
                    com.ziaee.zabankhan.content.DatasetSeeder.seedIfNeeded(
                        com.ziaee.zabankhan.content.DatasetRepository(container.database, language, prefs.meaningLang),
                        container.database, container.cache)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* The catalog remains available for a retry from Words. */ }
            }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val labels = listOf(Res.string.nav_home, Res.string.nav_library, Res.string.nav_words, Res.string.nav_review, Res.string.nav_settings)
            val routes = listOf("home", "library", "words", "review", "settings")
            val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.LibraryBooks, Icons.Default.Translate, Icons.Default.Style, Icons.Default.Settings)
            val snackbar = remember { SnackbarHostState() }
            val errorMessage = stringResource(Res.string.error_save)
            LaunchedEffect(vm.failed) { if (vm.failed) { snackbar.showSnackbar(errorMessage); vm.clearError() } }
            val unavailable by com.ziaee.zabankhan.speech.SpeechEngine.onlineUnavailable.collectAsState()
            val fallbackMessage = stringResource(Res.string.edge_fallback)
            LaunchedEffect(unavailable) { if (unavailable) snackbar.showSnackbar(fallbackMessage) }
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                if (route in routes) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    routes.forEachIndexed { i, target -> NavigationBarItem(selected = route == target,
                        onClick = { nav.navigate(target) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icons[i], stringResource(labels[i])) }, label = { EditorialNavigationLabel(stringResource(labels[i])) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        )) }
                }
            }) { padding ->
                NavHost(nav, "home", Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable("home") { HomeScreen(vm, prefs, { id -> vm.action { val doc = container.texts.getById(id); nav.navigate(if (doc?.pdfPath != null) "pdf/$id" else "reader/$id") } }, { nav.navigate("add") }, { nav.navigate("review") }, { nav.navigate("videos") }, { nav.navigate("statistics") }, { nav.navigate("discover") }, { nav.navigate("grammar") }, { nav.navigate("exams") }, { nav.navigate("settings") }, { nav.navigate("words") { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, { nav.navigate("news") }) { video, path, subs -> nav.navigate(watchRoute(video, path, subs)) } }
                    composable("library") { LibraryScreen(vm, prefs, { id -> vm.action { val doc = container.texts.getById(id); nav.navigate(if (doc?.pdfPath != null) "pdf/$id" else "reader/$id") } }, { nav.navigate("add") }, { nav.navigate("videos") }, { nav.navigate("discover") }, openAt = { id, query ->
                        nav.navigate(readerRoute(id, query))
                    }) }
                    composable("videos") { VideoPickerScreen(vm, prefs, { nav.popBackStack() }) { video, path, subs -> nav.navigate(watchRoute(video, path, subs)) } }
                    composable("watch?videoId={videoId}&path={path}&subs={subs}", arguments = listOf("videoId", "path", "subs").map { name -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }) { entry ->
                        WatchScreen(vm, prefs, entry.arguments?.read { getString("videoId") }.orEmpty(), entry.arguments?.read { getString("path") }.orEmpty(), entry.arguments?.read { getString("subs") }.orEmpty()) { nav.popBackStack() }
                    }
                    composable("news") { NewsScreen(vm, prefs, { nav.popBackStack() }) { id -> nav.navigate("reader/$id") } }
                    composable("statistics") { com.ziaee.zabankhan.statistics.StatisticsScreen(vm, prefs) { nav.popBackStack() } }
                    composable("discover") { com.ziaee.zabankhan.discover.DiscoverScreen(vm, prefs, { nav.popBackStack() }, { nav.navigate("reader/$it") }) }
                    composable("grammar") { com.ziaee.zabankhan.grammar.GrammarScreen(prefs) { nav.popBackStack() } }
                    composable("exams") { com.ziaee.zabankhan.datapack.TcfPracticeScreen(prefs.learningLang.orEmpty()) { nav.popBackStack() } }
                    composable("words") { WordsScreen(vm, prefs, review = { nav.navigate("review") },
                        onOpenDataset = { nav.navigate("datasets") }, reviewScope = { nav.navigate("review-scope/$it") }) }
                    composable("review-scope/{listId}", arguments = listOf(navArgument("listId") { type = NavType.LongType })) {
                        ReviewScreen(container, prefs, { nav.popBackStack() }, { nav.navigate("settings") },
                            listScope = requireNotNull(it.arguments?.read { getLong("listId") }))
                    }
                    composable("datasets") { DatasetScreen(vm, prefs) { nav.popBackStack() } }
                    composable("review") { ReviewScreen(container, prefs, { nav.popBackStack() }, { nav.navigate("settings") }) }
                    composable("settings") { SettingsScreen(vm, prefs, { nav.navigate("statistics") }, { nav.navigate("grammar") }, { nav.navigate("exams") }) }
                    composable("add") { AddTextScreen(vm, prefs, { sharedText = ""; nav.popBackStack() }, { id -> sharedText = ""; nav.navigate("reader/$id") { popUpTo("add") { inclusive = true } } }, initialText = sharedText) }
                    composable("pdf/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ReaderScreen(vm, prefs, requireNotNull(it.arguments?.read { getLong("id") }), onOpenVocab = { nav.navigate("words") }) { nav.popBackStack() }
                    }
                    composable("reader/{id}?find={find}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("find") { type = NavType.StringType; defaultValue = "" })) {
                        ReaderScreen(vm, prefs, requireNotNull(it.arguments?.read { getLong("id") }), onOpenVocab = { nav.navigate("words") }, initialQuery = it.arguments?.read { getString("find") }.orEmpty()) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}
