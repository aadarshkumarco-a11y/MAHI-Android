package com.mahi.assistant.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mahi.assistant.ui.screens.*

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
 * and their transitions.
 */
@Composable
fun MahiNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MahiRoutes.HOME,
        modifier = modifier,
    ) {
        // ── Home ────────────────────────────────────────────────
        composable(
            MahiRoutes.HOME,
            enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
            exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) },
        ) {
            HomeScreen(
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
                onVoiceInput = {
                    // Triggered from ViewModel in production
                },
                onTextSubmit = { text ->
                    // Navigate to chat with text
                    navController.navigate(MahiRoutes.CHAT)
                },
            )
        }

        // ── Chat ────────────────────────────────────────────────
        composable(MahiRoutes.CHAT) {
            ChatScreen(
                onBack = {
                    navController.popBackStack()
                },
                onVoiceInput = {
                    // Triggered from ViewModel in production
                },
                onSendMessage = { message ->
                    // Handled by ViewModel in production
                },
            )
        }

        // ── Controls ────────────────────────────────────────────
        composable(MahiRoutes.CONTROLS) {
            ControlScreen(
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── Weather ─────────────────────────────────────────────
        composable(MahiRoutes.WEATHER) {
            WeatherScreen(
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        // ── News ────────────────────────────────────────────────
        composable(MahiRoutes.NEWS) {
            NewsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onArticleClick = { url ->
                    // Open URL in browser via Intent
                },
            )
        }

        // ── Routines ────────────────────────────────────────────
        composable(MahiRoutes.ROUTINES) {
            RoutinesScreen(
                onBack = {
                    navController.popBackStack()
                },
                onActivateRoutine = { routineId ->
                    // Handled by ViewModel in production
                },
                onCreateCustom = {
                    // Navigate to custom routine creation
                },
            )
        }

        // ── Settings ────────────────────────────────────────────
        composable(MahiRoutes.SETTINGS) {
            SettingsScreen(
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
