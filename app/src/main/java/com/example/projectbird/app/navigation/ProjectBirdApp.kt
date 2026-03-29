package com.example.projectbird.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.projectbird.feature.analytics.AnalyticsScreen
import com.example.projectbird.feature.history.HistoryScreen
import com.example.projectbird.feature.home.HomeRoute
import com.example.projectbird.feature.map.MapScreen
import com.example.projectbird.feature.sessiondetail.SessionDetailScreen

@Composable
fun ProjectBirdApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { destination ->
            destination.route == item.destination.route
        } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { destination ->
                            destination.route == item.destination.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(text = stringResource(id = item.destination.titleResId))
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Home.route) {
                HomeRoute()
            }

            composable(AppDestination.Analytics.route) {
                AnalyticsScreen()
            }

            composable(AppDestination.Map.route) {
                MapScreen()
            }

            composable(AppDestination.History.route) {
                HistoryScreen(
                    onOpenSession = { sessionId ->
                        navController.navigate(AppDestination.SessionDetail.createRoute(sessionId))
                    }
                )
            }

            composable(AppDestination.SessionDetail.route) { entry ->
                val sessionId = entry.arguments
                    ?.getString(AppDestination.SessionDetail.ARG_SESSION_ID)
                    .orEmpty()

                SessionDetailScreen(
                    sessionId = sessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
