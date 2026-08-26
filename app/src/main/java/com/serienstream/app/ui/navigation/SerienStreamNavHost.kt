package com.serienstream.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.serienstream.app.ui.components.PremiumBottomBar
import com.serienstream.app.ui.detail.DetailScreen
import com.serienstream.app.ui.home.HomeScreen
import com.serienstream.app.ui.player.PlayerScreen
import com.serienstream.app.ui.search.SearchScreen

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val DETAIL = "detail/{slug}"
    const val PLAYER = "player/{slug}/{season}/{episode}?title={title}"

    fun detail(slug: String) = "detail/$slug"
    fun player(slug: String, season: Int, episode: Int, title: String) =
        "player/$slug/$season/$episode?title=${java.net.URLEncoder.encode(title, "UTF-8")}"
}

@Composable
fun SerienStreamNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.SEARCH)

    androidx.compose.material3.Scaffold(
        bottomBar = {
            if (showBottomBar) {
                PremiumBottomBar(
                    currentRoute = currentRoute ?: Routes.HOME,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            nav.navigate(route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { _ ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onSeriesClick = { slug -> nav.navigate(Routes.detail(slug)) }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onSeriesClick = { slug ->
                        nav.navigate(Routes.detail(slug)) { launchSingleTop = true }
                    }
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) {
                DetailScreen(
                    onBack = { nav.popBackStack() },
                    onPlay = { slug, season, episode, title ->
                        nav.navigate(Routes.player(slug, season, episode, title))
                    }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("season") { type = NavType.StringType },
                    navArgument("episode") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                PlayerScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
