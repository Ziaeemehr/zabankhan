package com.ziaee.zabankhan.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.ziaee.zabankhan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    titleStyle: TextStyle = editorialStyle(title, EditorialRole.SectionTitle),
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = titleModifier
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            // Opaque and matched to the page background rather than truly
            // transparent -- pull-to-refresh's idle indicator sits just
            // above its own bounds by design and needs an opaque bar over
            // it, or it bleeds through (see PullToRefreshContainer).
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = TopAppBarDefaults.windowInsets
    )
}
