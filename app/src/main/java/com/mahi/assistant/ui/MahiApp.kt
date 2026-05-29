package com.mahi.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mahi.assistant.ui.navigation.MahiNavHost
import com.mahi.assistant.ui.navigation.MahiRoutes
import com.mahi.assistant.ui.theme.MAHITheme
import com.mahi.assistant.ui.theme.*

/**
 * Bottom navigation bar items.
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(MahiRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(MahiRoutes.CHAT, "Chat", Icons.Filled.Chat, Icons.Outlined.Chat),
    BottomNavItem(MahiRoutes.CONTROLS, "Controls", Icons.Filled.ToggleOn, Icons.Outlined.ToggleOn),
    BottomNavItem(MahiRoutes.WEATHER, "Weather", Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
    BottomNavItem(MahiRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * Main composable for the MAHI app.
 *
 * Sets up:
 *  - Scaffold with bottom navigation
 *  - NavHost with all routes
 *  - System UI styling (transparent status/nav bars)
 */
@Composable
fun MahiApp(
    modifier: Modifier = Modifier,
    hasAllPermissions: Boolean = false,
    onRequestPermissions: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine if bottom bar should be shown (hide on detail screens)
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    MAHITheme {
        Scaffold(
            modifier = modifier,
            containerColor = DeepSpaceBlack,
            bottomBar = {
                if (showBottomBar) {
                    MahiBottomBar(
                        navController = navController,
                        currentDestination = currentDestination,
                    )
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            MahiNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * Custom bottom navigation bar with JARVIS neon styling.
 */
@Composable
private fun MahiBottomBar(
    navController: androidx.navigation.NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
) {
    Surface(
        color = DarkPanel.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = TextPrimary,
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentDestination?.hierarchy?.any {
                    it.route == item.route
                } == true

                NavigationBarItem(
                    icon = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = if (isSelected) NeonCyan else TextTertiary,
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = CircleShape,
                                        color = NeonCyan,
                                    ) {}
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = item.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) NeonCyan else TextTertiary,
                        )
                    },
                    selected = isSelected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        unselectedIconColor = TextTertiary,
                        selectedTextColor = NeonCyan,
                        unselectedTextColor = TextTertiary,
                        indicatorColor = NeonCyan.copy(alpha = 0.1f),
                    ),
                )
            }
        }
    }
}
