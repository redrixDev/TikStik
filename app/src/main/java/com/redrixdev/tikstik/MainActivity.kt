// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.redrixdev.tikstik.access.AccessController
import com.redrixdev.tikstik.backup.BackupAccount
import com.redrixdev.tikstik.core.data.settings.SettingsRepository
import com.redrixdev.tikstik.core.designsystem.brand.BrandSplash
import com.redrixdev.tikstik.core.designsystem.theme.Ink
import com.redrixdev.tikstik.core.designsystem.theme.TikStikTheme
import com.redrixdev.tikstik.core.designsystem.theme.highFrameRateWhile
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.ui.nav.TikStikNavHost
import com.redrixdev.tikstik.whatsapp.WhatsAppPacks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var access: AccessController

    @Inject lateinit var notices: Notices

    @Inject lateinit var whatsApp: WhatsAppPacks

    // Lives with the app to schedule the Drive backup shortly after each change, and merges it on opening.
    @Inject lateinit var backup: BackupAccount

    private val systemSplashIcon = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest starts with Theme.TikStik.Starting (the brand splash).
        setTheme(R.style.Theme_TikStik)
        super.onCreate(savedInstanceState)
        val playSplash = savedInstanceState == null
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { view ->
                if (view.iconView != null) {
                    // BrandSplash draws exactly this frame: removed with no fade.
                    view.remove()
                } else {
                    // No icon (not started from the launcher): BrandSplash brings the sticker in and a short fade hides the switch.
                    systemSplashIcon.value = false
                    view.animate().alpha(0f).setDuration(160).withEndAction { view.remove() }.start()
                }
            }
        }
        // Fixed dark theme: light icons in the system bars.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            var showSplash by rememberSaveable { mutableStateOf(playSplash) }
            var contentHidden by remember { mutableStateOf(playSplash) }
            TikStikTheme {
                // One vote for the whole window: when only animations voted, scrolls and sheets stayed at 60 Hz.
                Box(Modifier.fillMaxSize().background(Ink).highFrameRateWhile(true)) {
                    // Covered by the splash: composed but neither drawn nor present for TalkBack.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (contentHidden) 0f else 1f }
                            .then(if (contentHidden) Modifier.clearAndSetSemantics { } else Modifier),
                    ) {
                        // Where to start is not decided until the settings are read (milliseconds, under the splash).
                        val saved by settings.settings.collectAsStateWithLifecycle(initialValue = null)
                        saved?.let { loaded ->
                            // Decided once: if it changed when the welcome finishes, NavHost would rebuild the back stack.
                            val startOnWelcome = rememberSaveable { !loaded.onboardingDone }
                            TikStikNavHost(
                                startOnWelcome = startOnWelcome,
                                legacyStorage = access.legacyStorage,
                                needsConnect = { !access.status.value.ready },
                                onWelcomeDone = { lifecycleScope.launch { settings.setOnboardingDone() } },
                                howToSeen = { loaded.howToSeen },
                                onHowToSeen = { lifecycleScope.launch { settings.setHowToSeen() } },
                                notices = notices,
                                whatsApp = whatsApp,
                            )
                        }
                    }
                    if (showSplash) {
                        BrandSplash(
                            systemIconShown = systemSplashIcon.value,
                            onReveal = { contentHidden = false },
                            onFinished = { showSplash = false },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        backup.restoreIfDue()
    }
}
