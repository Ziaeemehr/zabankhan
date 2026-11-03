package com.ziaee.zabankhan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.nav_home
import com.ziaee.zabankhan.resources.nav_library
import com.ziaee.zabankhan.resources.nav_review
import com.ziaee.zabankhan.resources.nav_settings
import com.ziaee.zabankhan.ui.theme.ZabankhanTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class Tab(val route: String, val label: StringResource, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", Res.string.nav_home, Icons.Filled.Home),
    Tab("library", Res.string.nav_library, Icons.AutoMirrored.Filled.LibraryBooks),
    Tab("review", Res.string.nav_review, Icons.Filled.Style),
    Tab("settings", Res.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun App() {
    ZabankhanTheme {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = entry?.destination?.route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                tabs.forEach { tab ->
                    composable(tab.route) { Placeholder(stringResource(tab.label)) }
                }
            }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
