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
    Column(modifier) {
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
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                // An opaque bar covers the pull-to-refresh indicator above its resting position.
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
            windowInsets = TopAppBarDefaults.windowInsets
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
