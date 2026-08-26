package com.novastream.app.ui.navigation

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
import com.novastream.app.ui.components.PremiumBottomBar
import com.novastream.app.ui.detail.DetailScreen
import com.novastream.app.ui.home.HomeScreen
import com.novastream.app.ui.player.PlayerScreen
import com.novastream.app.ui.search.SearchScreen
import com.novastream.app.ui.settings.SettingsScreen
import com.novastream.app.ui.watchlist.WatchlistScreen

object Routes {
    const val HOME = "home"
    const val WATCHLIST = "watchlist"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{slug}"
    const val PLAYER = "player/{slug}/{season}/{episode}?title={title}&seriesTitle={seriesTitle}&coverUrl={coverUrl}"

    fun detail(slug: String) = "detail/$slug"
    fun player(
        slug: String,
        season: Int,
        episode: Int,
        title: String,
        seriesTitle: String = "",
        coverUrl: String? = null
    ): String {
        fun enc(s: String) = try { java.net.URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
        val t = enc(title)
        val st = enc(seriesTitle)
        val cu = coverUrl?.let { enc(it) } ?: ""
        return "player/$slug/$season/$episode?title=$t&seriesTitle=$st&coverUrl=$cu"
    }
}

@Composable
fun NovaStreamNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.WATCHLIST, Routes.SEARCH, Routes.SETTINGS)

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
                    onSeriesClick = { slug -> nav.navigate(Routes.detail(slug)) },
                    onContinueWatchingClick = { slug, season, episode, title ->
                        nav.navigate(Routes.player(slug, season, episode, title))
                    }
                )
            }

            composable(Routes.WATCHLIST) {
                WatchlistScreen(
                    onSeriesClick = { slug ->
                        nav.navigate(Routes.detail(slug)) { launchSingleTop = true }
                    }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onSeriesClick = { slug ->
                        nav.navigate(Routes.detail(slug)) { launchSingleTop = true }
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) {
                DetailScreen(
                    onBack = { nav.popBackStack() },
                    onPlay = { slug, season, episode, title, seriesTitle, coverUrl ->
                        nav.navigate(Routes.player(slug, season, episode, title, seriesTitle, coverUrl))
                    }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("season") { type = NavType.StringType },
                    navArgument("episode") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("seriesTitle") { type = NavType.StringType; defaultValue = "" },
                    navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                val slugArg = it.arguments?.getString("slug") ?: ""
                val seriesTitleArg = it.arguments?.getString("seriesTitle") ?: ""
                val coverUrlArg = it.arguments?.getString("coverUrl")?.takeIf { c -> c.isNotBlank() }
                PlayerScreen(
                    onBack = { nav.popBackStack() },
                    onNextEpisode = { season, episode, title ->
                        nav.navigate(Routes.player(slugArg, season, episode, title, seriesTitleArg, coverUrlArg)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
