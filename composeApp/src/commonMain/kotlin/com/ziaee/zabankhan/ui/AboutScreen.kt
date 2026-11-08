package com.ziaee.zabankhan.ui

import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.backhandler.BackHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.ui.theme.EditorialRole
import com.ziaee.zabankhan.ui.theme.ZabankhanDesign
import com.ziaee.zabankhan.ui.theme.editorialStyle

private const val DEVELOPER_NAME = "Abolfazl Ziaeemehr"
private const val GITHUB_URL = "https://github.com/Ziaeemehr"
private const val LINKEDIN_URL = "https://www.linkedin.com/in/ziaeemehr/"
private const val EMAIL_ADDRESS = "a.ziaeemehr@gmail.com"
private const val PROJECT_URL = "https://github.com/Ziaeemehr/zabankhan"

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AboutScreen(learningLang: LanguageSpec, onBack: () -> Unit, onHelp: () -> Unit) {
    BackHandler(onBack = onBack)
    val uriHandler = LocalUriHandler.current
    val version = remember { com.ziaee.zabankhan.backup.appVersion() }
    val openFailedMessage = stringResource(Res.string.about_open_link_failed)
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openUrl: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }.onFailure { scope.launch { snackbar.showSnackbar(openFailedMessage) } }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.accessibility_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZabankhanDesign.spacing.medium, vertical = ZabankhanDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)
        ) {
            AboutHero(learningLang, version)

            AboutSection(stringResource(Res.string.about_developer_section)) {
                Text(
                    text = DEVELOPER_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = ZabankhanDesign.spacing.small, vertical = ZabankhanDesign.spacing.xSmall)
                )
                AboutLinkRow(Icons.Default.Code, stringResource(Res.string.about_github), GITHUB_URL) { openUrl(GITHUB_URL) }
                AboutLinkRow(Icons.Default.Person, stringResource(Res.string.about_linkedin), LINKEDIN_URL) { openUrl(LINKEDIN_URL) }
                AboutLinkRow(Icons.Default.Email, stringResource(Res.string.about_email), EMAIL_ADDRESS) {
                    openUrl("mailto:$EMAIL_ADDRESS?subject=zabankhan")
                }
            }

            AboutSection(stringResource(Res.string.about_project_section)) {
                AboutLinkRow(Icons.Default.HelpOutline, stringResource(Res.string.help_guides_title), null) { onHelp() }
                AboutLinkRow(Icons.Default.Star, stringResource(Res.string.about_star), stringResource(Res.string.about_star_detail)) { openUrl(PROJECT_URL) }
                AboutLinkRow(Icons.Default.Code, stringResource(Res.string.about_source_code), PROJECT_URL) { openUrl(PROJECT_URL) }
                AboutLinkRow(Icons.Default.BugReport, stringResource(Res.string.about_report_bug), "$PROJECT_URL/issues/new") { openUrl("$PROJECT_URL/issues/new") }
                AboutLinkRow(Icons.Default.Policy, stringResource(Res.string.about_privacy_policy), "PRIVACY.md") { openUrl("$PROJECT_URL/blob/main/PRIVACY.md") }
                AboutLinkRow(Icons.Default.Description, stringResource(Res.string.about_license), "MIT License") { openUrl("$PROJECT_URL/blob/main/LICENSE") }
                CreditText(stringResource(Res.string.edge_privacy))
            }

            AboutSection(stringResource(Res.string.about_credits_section)) {
                AboutLinkRow(Icons.Default.Code, "edge-tts (LGPL-3.0)", stringResource(Res.string.about_edge_tts_credit)) {
                    openUrl("https://github.com/rany2/edge-tts")
                }
                AboutLinkRow(Icons.Default.Description, "Vazirmatn (SIL OFL 1.1)", stringResource(Res.string.about_vazirmatn_credit)) {
                    openUrl("https://github.com/rastikerdar/vazirmatn")
                }
                CreditText(stringResource(Res.string.about_software_credits))
                com.ziaee.zabankhan.datapack.DataPackCredits()
                CreditText(stringResource(Res.string.about_content_credits, languageName(learningLang), learningLang.dictionaryLinks.joinToString { it.name }))
                if (learningLang.code == Languages.FRENCH.code) {
                    CreditText(stringResource(Res.string.about_lexique_credit))
                    CreditText(stringResource(Res.string.about_wiktionary_credit))
                }
            }

            Text(
                text = stringResource(Res.string.about_footer),
                modifier = Modifier.fillMaxWidth().padding(vertical = ZabankhanDesign.spacing.medium),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutHero(learningLang: LanguageSpec, version: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZabankhanDesign.spacing.small),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = ZabankhanDesign.elevations.card,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall),
        ) {
            // Draw the launcher foreground over its brand background; the adaptive-icon XML itself
            // cannot be loaded as a Compose painter.
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF1F4FB5))
            ) {
                Image(
                    painter = painterResource(Res.drawable.about_launcher_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                )
            }
            Text(
                text = stringResource(Res.string.app_name),
                style = editorialStyle(stringResource(Res.string.app_name), EditorialRole.ScreenTitle),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = stringResource(Res.string.about_version, version),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(ZabankhanDesign.spacing.xSmall))
            Text(
                text = stringResource(Res.string.about_description, languageName(learningLang)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = ZabankhanDesign.spacing.medium)) {
        AboutSectionHeader(title = title, modifier = Modifier.padding(bottom = ZabankhanDesign.spacing.xSmall))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZabankhanDesign.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = ZabankhanDesign.spacing.small)
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CreditText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(ZabankhanDesign.spacing.small),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AboutSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = editorialStyle(title, EditorialRole.SectionTitle), modifier = Modifier.weight(1f, fill = false))
    }
}
