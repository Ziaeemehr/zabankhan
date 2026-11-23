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
import androidx.navigation.NavHostController
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
import com.ziaee.zabankhan.content.ImportRepository
import com.ziaee.zabankhan.files.PickedFile
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable fun App(container: AppContainer, request: AppOpenRequest? = null, onRequestHandled: () -> Unit = {}) {
    key(container) { AppContent(container, request, onRequestHandled) }
}

data class AppOpenRequest(
    val text: String? = null,
    val review: Boolean = false,
    val id: Long = 0,
    val youtubeVideoId: String? = null,
    val file: PickedFile? = null,
    val importFailed: Boolean = false
)

private fun NavHostController.navigateToTab(route: String) {
    when (route) {
        "home" -> if (currentDestination?.route != "home" && !popBackStack("home", inclusive = false)) {
            navigate("home") { launchSingleTop = true }
        }
        "review" -> navigate("review") {
            // A review tab tap always starts at the overview, even if a card session was saved.
            popUpTo("home") { saveState = false }
            launchSingleTop = true
        }
        else -> {
            val cameFromReview = route == "settings" && currentDestination?.route == "review"
            navigate(route) {
                popUpTo("home") { saveState = !cameFromReview }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}

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
                        request.youtubeVideoId != null -> nav.navigate(watchRoute(videoId = request.youtubeVideoId)) { launchSingleTop = true }
                        request.importFailed -> vm.action { throw IllegalArgumentException("Could not import file") }
                        request.file != null -> vm.action {
                            val language = requireNotNull(Languages.byCode(requireNotNull(prefs.learningLang)))
                            val copied = request.file
                            val id = withContext(Dispatchers.IO) {
                                val body = FileSystem.SYSTEM.read(copied.path.toPath()) { readUtf8() }
                                ImportRepository(container.database, language, prefs.meaningLang).text(body, copied.displayName)
                            }
                            nav.navigate(readerRoute(id)) { launchSingleTop = true }
                        }
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
                        com.ziaee.zabankhan.content.DatasetRepository(container.database, language, prefs.meaningLang, container.http),
                        container.database, container.cache)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* The catalog remains available for a retry from Words. */ }
            }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            var reviewingCards by remember { mutableStateOf(false) }
            var reviewEntryIds by remember { mutableStateOf<Set<Long>?>(null) }
            val labels = listOf(Res.string.nav_home, Res.string.nav_library, Res.string.nav_words, Res.string.nav_review, Res.string.nav_settings)
            val routes = listOf("home", "library", "words", "review", "settings")
            val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.LibraryBooks, Icons.Default.Translate, Icons.Default.Style, Icons.Default.Settings)
            val snackbar = remember { SnackbarHostState() }
            val errorMessage = stringResource(Res.string.error_save)
            LaunchedEffect(vm.failed) { if (vm.failed) { snackbar.showSnackbar(errorMessage); vm.clearError() } }
            val unavailable by com.ziaee.zabankhan.speech.SpeechEngine.onlineUnavailable.collectAsState()
            val fallbackMessage = stringResource(Res.string.edge_fallback)
            LaunchedEffect(unavailable) { if (unavailable) snackbar.showSnackbar(fallbackMessage) }
            val celebrationTemplate = stringResource(Res.string.progress_celebration)
            LaunchedEffect(vm, celebrationTemplate) {
                vm.progress.collect { summary ->
                    if (summary != null) {
                        try {
                            val change = container.settings.claimProgress(summary)
                            if (change.hasNews) {
                                snackbar.showSnackbar(celebrationTemplate.replace("%1\$s", summary.level.toString())
                                    .replace("%2\$s", change.newBadgeCount.toString()), duration = SnackbarDuration.Short)
                            }
                        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                        catch (_: Exception) { vm.action { throw IllegalStateException("Could not save progress celebration") } }
                    }
                }
            }
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                if (route in routes && !(route == "review" && reviewingCards)) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            routes.forEachIndexed { index, target ->
                                NavigationBarItem(
                                    selected = route == target,
                                    onClick = { nav.navigateToTab(target) },
                                    icon = { Icon(icons[index], null) },
                                    label = { EditorialNavigationLabel(stringResource(labels[index])) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }
            }) { padding ->
                NavHost(nav, "home", Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable("home") { HomeScreen(vm, prefs, { id -> vm.action { val doc = container.texts.getById(id); nav.navigate(if (doc?.pdfPath != null) "pdf/$id" else "reader/$id") } }, { nav.navigate("add") }, { nav.navigate("review") }, { nav.navigate("videos") }, { nav.navigate("statistics") }, { nav.navigate("discover") }, { nav.navigate("grammar") }, { nav.navigate("exams") }, { nav.navigate("settings") }, { nav.navigate("words") { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, { nav.navigate("news") }, datasets = { nav.navigate("datasets") }, achievements = { nav.navigate("achievements") }, watchVideo = { video, path, subs -> nav.navigate(watchRoute(video, path, subs)) }, shadow = { nav.navigate("shadow") }) }
                    composable("library") { LibraryScreen(vm, prefs, { id -> vm.action { val doc = container.texts.getById(id); nav.navigate(if (doc?.pdfPath != null) "pdf/$id" else "reader/$id") } }, { nav.navigate("add") }, { nav.navigate("videos") }, { nav.navigate("discover") }, openAt = { id, query ->
                        nav.navigate(readerRoute(id, query))
                    }, openDatasets = { nav.navigate("datasets") }) }
                    composable("videos") { VideoPickerScreen(vm, prefs, { nav.popBackStack() }) { video, path, subs -> nav.navigate(watchRoute(video, path, subs)) } }
                    composable("shadow") { ShadowEntryScreen(vm, prefs, { nav.popBackStack() }, { nav.navigate("shadow-videos") }) { video -> nav.navigate(watchRoute(video.videoId, video.path.orEmpty(), video.subs.orEmpty(), true)) } }
                    composable("shadow-videos") { VideoPickerScreen(vm, prefs, { nav.popBackStack() }, shadow = true) { video, path, subs -> nav.navigate(watchRoute(video, path, subs, true)) } }
                    composable("watch?videoId={videoId}&path={path}&subs={subs}&shadow={shadow}", arguments = listOf("videoId", "path", "subs").map { name -> navArgument(name) { type = NavType.StringType; defaultValue = "" } } + navArgument("shadow") { type = NavType.BoolType; defaultValue = false }) { entry ->
                        WatchScreen(vm, prefs, entry.arguments?.read { getString("videoId") }.orEmpty(), entry.arguments?.read { getString("path") }.orEmpty(), entry.arguments?.read { getString("subs") }.orEmpty(), entry.arguments?.read { getBoolean("shadow") } ?: false) { nav.popBackStack() }
                    }
                    composable("news") { NewsScreen(vm, prefs, { nav.popBackStack() }) { id -> nav.navigate("reader/$id") } }
                    composable("achievements") { com.ziaee.zabankhan.progress.AchievementsScreen(vm, prefs) { nav.popBackStack() } }
                    composable("statistics") { com.ziaee.zabankhan.statistics.StatisticsScreen(vm, prefs, { nav.popBackStack() }, { nav.navigate("study-log") }) }
                    composable("study-log") { com.ziaee.zabankhan.statistics.StudyLogScreen(prefs.learningLang.orEmpty(), { nav.popBackStack() }, { nav.navigate("study-log/entry") }, { id -> nav.navigate("study-log/entry/$id") }, { nav.navigate("study-log/sources") }) }
                    composable("study-log/entry") { com.ziaee.zabankhan.statistics.StudyLogEntryScreen(prefs.learningLang.orEmpty(), null) { nav.popBackStack() } }
                    composable("study-log/entry/{id}") { entry -> com.ziaee.zabankhan.statistics.StudyLogEntryScreen(prefs.learningLang.orEmpty(), entry.arguments?.read { getString("id")?.toLongOrNull() }) { nav.popBackStack() } }
                    composable("study-log/sources") { com.ziaee.zabankhan.statistics.StudySourcesScreen(prefs.learningLang.orEmpty()) { nav.popBackStack() } }
                    composable("discover") { com.ziaee.zabankhan.discover.DiscoverScreen(vm, prefs, { nav.popBackStack() }, { nav.navigate("reader/$it") }) }
                    composable("grammar") { com.ziaee.zabankhan.grammar.GrammarScreen(prefs) { nav.popBackStack() } }
                    composable("exams") { com.ziaee.zabankhan.datapack.TcfPracticeScreen(prefs.learningLang.orEmpty()) { nav.popBackStack() } }
                    composable("words") { WordsScreen(vm, prefs, review = { nav.navigate("review") },
                        onOpenDataset = { nav.navigate("datasets") }, reviewScope = { listId, ids ->
                            reviewEntryIds = ids
                            nav.navigate("review-scope/$listId")
                        }) }
                    composable("review-scope/{listId}", arguments = listOf(navArgument("listId") { type = NavType.LongType })) {
                        SideEffect { reviewingCards = false }
                        ReviewScreen(container, prefs,
                            onBack = { reviewEntryIds = null; nav.popBackStack() },
                            onOpenSettings = { nav.navigateToTab("settings") },
                            listScope = requireNotNull(it.arguments?.read { getLong("listId") }),
                            reviewEntryIds = reviewEntryIds,
                            onReviewingCardsChanged = { reviewingCards = it })
                    }
                    composable("datasets") { DatasetScreen(vm, prefs) { nav.popBackStack() } }
                    composable("review") { ReviewScreen(container, prefs, { nav.popBackStack() }, { nav.navigateToTab("settings") }, onOpenDatasets = { nav.navigate("datasets") }, onReviewingCardsChanged = { reviewingCards = it }) }
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
