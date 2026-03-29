    package com.example.projectbird.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val destination: AppDestination,
    val icon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(
        destination = AppDestination.Home,
        icon = AppIcons.home,
    ),
    BottomNavItem(
        destination = AppDestination.Analytics,
        icon = AppIcons.analytics,
    ),
    BottomNavItem(
        destination = AppDestination.Map,
        icon = AppIcons.map,
    ),
    BottomNavItem(
        destination = AppDestination.History,
        icon = AppIcons.history,
    ),
)
