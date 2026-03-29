package com.example.projectbird.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.projectbird.R

sealed class AppDestination(
    val route: String,
    @StringRes val titleResId: Int,
) {
    data object Home : AppDestination(
        route = "home",
        titleResId = R.string.nav_home,
    )

    data object Analytics : AppDestination(
        route = "analytics",
        titleResId = R.string.nav_analytics,
    )

    data object Map : AppDestination(
        route = "map",
        titleResId = R.string.nav_map,
    )

    data object History : AppDestination(
        route = "history",
        titleResId = R.string.nav_history,
    )

    data object SessionDetail : AppDestination(
        route = "session_detail/{sessionId}",
        titleResId = R.string.nav_session_detail,
    ) {
        const val ARG_SESSION_ID = "sessionId"

        fun createRoute(sessionId: String): String = "session_detail/$sessionId"
    }

    companion object {
        val topLevelDestinations = listOf(
            Home,
            Analytics,
            Map,
            History,
        )
    }
}

object AppIcons {
    val home: ImageVector = Icons.Outlined.Home
    val analytics: ImageVector = Icons.Outlined.Analytics
    val map: ImageVector = Icons.Outlined.Map
    val history: ImageVector = Icons.Outlined.History
}
