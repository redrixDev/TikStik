// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.info

import androidx.annotation.Keep
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.core.common.Links
import com.redrixdev.tikstik.core.designsystem.ScreenPreview
import com.redrixdev.tikstik.core.designsystem.brand.BrandMark
import com.redrixdev.tikstik.core.designsystem.component.QuietButton
import com.redrixdev.tikstik.core.designsystem.component.TopBar
import com.redrixdev.tikstik.core.designsystem.icon.TikStikIcons
import com.redrixdev.tikstik.core.designsystem.theme.BrandGreen
import com.redrixdev.tikstik.core.designsystem.theme.Surface1
import com.redrixdev.tikstik.core.designsystem.theme.TextMid
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.safeDrawingStable
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A route argument: R8 must not obfuscate its serializer.
@Keep
enum class InfoPage { PRIVACY, LICENSES, ABOUT }

@Composable
fun InfoScreen(page: InfoPage, versionName: String, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val title = stringResource(
        when (page) {
            InfoPage.PRIVACY -> R.string.settings_privacy
            InfoPage.LICENSES -> R.string.settings_licenses
            InfoPage.ABOUT -> R.string.settings_about
        },
    )
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawingStable)) {
        TopBar(title, onBack)
        if (page == InfoPage.LICENSES) {
            Licenses(onOpenUrl, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp))
            return@Column
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (page == InfoPage.PRIVACY) Privacy() else About(versionName, onOpenUrl)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Everything inside the app, from the list AboutLibraries generates at build time; TikStik itself goes first. */
@Composable
private fun Licenses(onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val libraries by produceState(emptyList<Library>()) {
        value = withContext(Dispatchers.IO) {
            val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
            Libs.Builder().withJson(json).build().libraries.sortedBy { if (it.uniqueId == SELF) 0 else 1 }
        }
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(libraries, key = { it.uniqueId }) { LibraryRow(it, onOpenUrl) }
    }
}

private const val SELF = "com.redrixdev:tikstik"

@Composable
private fun Privacy() {
    listOf(
        R.string.privacy_1_title to R.string.privacy_1_body,
        R.string.privacy_2_title to R.string.privacy_2_body,
        R.string.privacy_3_title to R.string.privacy_3_body,
        R.string.privacy_4_title to R.string.privacy_4_body,
        R.string.privacy_5_title to R.string.privacy_5_body,
    ).forEach { (title, body) ->
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Surface1).padding(18.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}

/** A library: its name and licenses; tapping unfolds the license text, with the link to its project below. */
@Composable
private fun LibraryRow(library: Library, onOpenUrl: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // A library can carry the same license twice (the POM's and the project's own entry): the one with the text wins.
    val licenses = library.licenses.sortedByDescending { it.licenseContent != null }.distinctBy { it.name }.filter { it.licenseContent != null || it.url != null }
    val text = licenses.mapNotNull { it.licenseContent?.takeIf(String::isNotBlank) }.joinToString("\n\n")
    // Without a project page (Google Play services), the license page is the link.
    val website = library.website?.takeIf { it.isNotBlank() } ?: licenses.firstNotNullOfOrNull { it.url?.takeIf(String::isNotBlank) }
    val state = if (text.isEmpty()) null else stringResource(if (open) R.string.cd_expanded else R.string.cd_collapsed)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Surface1)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (text.isNotEmpty()) open = !open else website?.let(onOpenUrl) }
                // When it unfolds its text, TalkBack says whether it is open.
                .semantics { state?.let { stateDescription = it } }
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(library.name, style = MaterialTheme.typography.titleMedium)
                Text(licenses.joinToString(stringResource(R.string.separator)) { it.name }, style = MaterialTheme.typography.bodySmall, color = TextMid)
            }
            Icon(TikStikIcons.OpenInNew, null, tint = TextMid, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = TextMid)
                if (website != null) QuietButton(stringResource(R.string.licenses_open_source), { onOpenUrl(website) }, color = BrandGreen)
            }
        }
    }
}

@Composable
private fun About(versionName: String, onOpenUrl: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(Modifier.size(160.dp), alive = true, contentDescription = stringResource(R.string.app_name))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyMedium, color = TextMid)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge, color = TextMid, textAlign = TextAlign.Center)
        if (Links.WEBSITE.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            QuietButton(stringResource(R.string.about_website), { onOpenUrl(Links.WEBSITE) }, color = BrandGreen)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodySmall, color = TextMid, textAlign = TextAlign.Center)
    }
}

@ScreenPreview
@Composable
private fun AboutPreview() {
    TikStikTheme { InfoScreen(InfoPage.ABOUT, "1.0", {}, {}) }
}
