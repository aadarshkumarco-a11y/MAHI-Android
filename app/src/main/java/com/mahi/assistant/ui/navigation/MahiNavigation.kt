package com.mahi.assistant.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mahi.assistant.ui.screens.*
import com.mahi.assistant.ui.viewmodel.MahiViewModel

/**
 * Navigation routes for the MAHI app.
 */
object MahiRoutes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val CONTROLS = "controls"
    const val WEATHER = "weather"
    const val NEWS = "news"
    const val ROUTINES = "routines"
    const val SETTINGS = "settings"
}

/**
 * NavHost for the MAHI app — defines all screen routes
 * and their transitions. Uses a SHARED ViewModel instance
 * across all screens so state is preserved during navigation.
 */
@Composable
fun MahiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // Single shared ViewModel for all screens — preserves state across navigation
    val viewModel: MahiViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = MahiRoutes.HOME,
        modifier = modifier,
    ) {
        // ── Home ────────────────────────────────────────────────
        composable(
            MahiRoutes.HOME,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToChat = {
                    navController.navigate(MahiRoutes.CHAT)
                },
                onNavigateToControls = {
                    navController.navigate(MahiRoutes.CONTROLS)
                },
                onNavigateToWeather = {
                    navController.navigate(MahiRoutes.WEATHER)
                },
                onNavigateToNews = {
                    navController.navigate(MahiRoutes.NEWS)
                },
                onNavigateToRoutines = {
                    navController.navigate(MahiRoutes.ROUTINES)
                },
            )
        }

        // ── Chat ────────────────────────────────────────────────
        composable(MahiRoutes.CHAT) {
            ChatScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── Controls ────────────────────────────────────────────
        composable(MahiRoutes.CONTROLS) {
            ControlScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── Weather ─────────────────────────────────────────────
        composable(MahiRoutes.WEATHER) {
            WeatherScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── News ────────────────────────────────────────────────
        composable(MahiRoutes.NEWS) {
            NewsScreen(
                viewModel = viewModel,
                onArticleClick = { url ->
                    // Open URL in browser — handled in screens
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── Routines ────────────────────────────────────────────
        composable(MahiRoutes.ROUTINES) {
            RoutinesScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── Settings ────────────────────────────────────────────
        composable(MahiRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                },
                onRequestNotificationAccess = {
                    // Open notification listener settings
                },
                onRequestAccessibility = {
                    // Open accessibility settings
                },
                onRequestOverlayPermission = {
                    // Request overlay permission
                },
            )
        }
    }
}
