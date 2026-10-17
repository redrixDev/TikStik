// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.ui.nav

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.redrixdev.tikstik.BuildConfig
import com.redrixdev.tikstik.R
import com.redrixdev.tikstik.access.AndroidDeviceState
import com.redrixdev.tikstik.core.ui.NoticeAction
import com.redrixdev.tikstik.core.ui.NoticeHost
import com.redrixdev.tikstik.core.ui.Notices
import com.redrixdev.tikstik.ui.connect.ConnectRoute
import com.redrixdev.tikstik.ui.home.HomeRoute
import com.redrixdev.tikstik.ui.howto.HowToScreen
import com.redrixdev.tikstik.ui.info.InfoPage
import com.redrixdev.tikstik.ui.info.InfoScreen
import com.redrixdev.tikstik.ui.packs.PackDetailRoute
import com.redrixdev.tikstik.ui.packs.PacksRoute
import com.redrixdev.tikstik.ui.packs.WhatsAppHost
import com.redrixdev.tikstik.ui.settings.SettingsRoute
import com.redrixdev.tikstik.ui.update.UpdateHost
import com.redrixdev.tikstik.ui.welcome.WelcomeScreen
import com.redrixdev.tikstik.whatsapp.WhatsAppPacks

/** The app's graph: Home, with the welcome before it the first time and Connect stacked on top of Home. */
@Composable
fun TikStikNavHost(
    startOnWelcome: Boolean,
    legacyStorage: Boolean,
    needsConnect: () -> Boolean,
    onWelcomeDone: () -> Unit,
    howToSeen: () -> Boolean,
    onHowToSeen: () -> Unit,
    notices: Notices,
    whatsApp: WhatsAppPacks,
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Graph(nav, startOnWelcome, legacyStorage, needsConnect, onWelcomeDone, howToSeen, onHowToSeen)
        WhatsAppHost(whatsApp, notices)
        UpdateHost()
        NoticeHost(notices.notices, onAction = { action ->
            when (action) {
                NoticeAction.CONNECT -> nav.navigate(ConnectDestination) { launchSingleTop = true }
                NoticeAction.OPEN_WIRELESS_DEBUGGING -> runCatching { context.startActivity(AndroidDeviceState.wirelessDebuggingIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        })
    }
}

@Composable
private fun Graph(
    nav: NavHostController,
    startOnWelcome: Boolean,
    legacyStorage: Boolean,
    needsConnect: () -> Boolean,
    onWelcomeDone: () -> Unit,
    howToSeen: () -> Boolean,
    onHowToSeen: () -> Unit,
) {
    val context = LocalContext.current
    // The graph is not rebuilt when settings change: always read the current value.
    val seen by rememberUpdatedState(howToSeen)
    NavHost(
        navController = nav,
        startDestination = if (startOnWelcome) WelcomeDestination else HomeDestination,
        enterTransition = { forward() },
        exitTransition = { backward() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
        // Predictive back previews the same slide; the default shrinks the screen to 70 %.
        predictivePopEnterTransition = { popEnter() },
        predictivePopExitTransition = { popExit() },
    ) {
        // Navigate only with the screen settled and in front: a second "back" during the transition left the app black.
        screen<WelcomeDestination> {
            WelcomeScreen(legacyStorage = legacyStorage, onStart = dropUnlessResumed {
                onWelcomeDone()
                // Connect goes on top of Home: when done, or with back, it returns to Home.
                nav.navigate(HomeDestination) { popUpTo(WelcomeDestination) { inclusive = true } }
                if (needsConnect()) nav.navigate(ConnectDestination)
            })
        }
        screen<ConnectDestination> {
            val back = dropUnlessResumed { if (!nav.popBackStack()) nav.navigate(HomeDestination) }
            ConnectRoute(onBack = back, onDone = dropUnlessResumed {
                // Right after connecting, the first time explains how to bring a sticker over; it stays available in Settings.
                if (seen()) back() else nav.navigate(HowToDestination(onboarding = true)) { popUpTo(ConnectDestination) { inclusive = true } }
            })
        }
        screen<HowToDestination> { entry ->
            val done = dropUnlessResumed {
                onHowToSeen()
                if (!nav.popBackStack()) nav.navigate(HomeDestination)
            }
            // The system "back" also marks it as seen: otherwise it showed up again on the next connect.
            BackHandler { done() }
            val video = stringResource(R.string.video_howto_url)
            HowToScreen(
                onBack = done.takeUnless { entry.toRoute<HowToDestination>().onboarding },
                onDone = done,
                onHelp = if (video.isBlank()) null else { { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, video.toUri())) } } },
            )
        }
        screen<HomeDestination> {
            HomeRoute(
                onOpenPacks = dropUnlessResumed { nav.navigate(PacksDestination) },
                onOpenSettings = dropUnlessResumed { nav.navigate(SettingsDestination) },
                onConnect = dropUnlessResumed { nav.navigate(ConnectDestination) },
            )
        }
        screen<PacksDestination> {
            PacksRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenPack = whenResumed { id: String -> nav.navigate(PackDestination(id)) },
            )
        }
        screen<PackDestination> { entry ->
            PackDetailRoute(entry.toRoute<PackDestination>().packId, onClose = dropUnlessResumed { nav.popBackStack() })
        }
        screen<SettingsDestination> {
            SettingsRoute(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onConnectTikTok = dropUnlessResumed { nav.navigate(ConnectDestination) },
                onOpenInfo = whenResumed { page: InfoPage -> nav.navigate(InfoDestination(page)) },
                onOpenHowTo = dropUnlessResumed { nav.navigate(HowToDestination()) },
            )
        }
        screen<InfoDestination> { entry ->
            InfoScreen(entry.toRoute<InfoDestination>().page, BuildConfig.VERSION_NAME, onBack = dropUnlessResumed { nav.popBackStack() }, onOpenUrl = { url ->
                // Without a browser there is nothing to open: the screen stays as it is.
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            })
        }
    }
}

// Material shared axis: the leaving screen shifts a little and fades fast; the entering one fades in slightly later.
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Move = tween<IntOffset>(400, easing = Emphasized)
private val FadeIn = tween<Float>(250, delayMillis = 70, easing = LinearOutSlowInEasing)
private val FadeOut = tween<Float>(110, easing = FastOutLinearInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.forward(): EnterTransition =
    slideInHorizontally(Move) { it / 5 } + fadeIn(FadeIn)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.backward(): ExitTransition =
    slideOutHorizontally(Move) { -it / 10 } + fadeOut(FadeOut)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideInHorizontally(Move) { -it / 10 } + fadeIn(FadeIn)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutHorizontally(Move) { it / 5 } + fadeOut(FadeOut)

private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit) =
    composable<T> { entry -> content(entry) }

// dropUnlessResumed for callbacks that carry a value: ignored while the screen is not settled and in front.
@Composable
private fun <T> whenResumed(block: (T) -> Unit): (T) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { value -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value) }
}
