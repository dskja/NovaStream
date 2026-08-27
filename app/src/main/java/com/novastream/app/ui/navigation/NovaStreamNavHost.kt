package com.novastream.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novastream.app.ui.components.PremiumBottomBar
import com.novastream.app.ui.components.PremiumTopTabBar
import com.novastream.app.ui.detail.DetailScreen
import com.novastream.app.ui.home.HomeScreen
import com.novastream.app.ui.player.PlayerScreen
import com.novastream.app.ui.search.SearchScreen
import com.novastream.app.ui.settings.SettingsScreen
import com.novastream.app.ui.tv.TvUtils
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

    /** Liste aller Haupt-Routes (für Nav-Bar Anzeige). */
    val mainRoutes = listOf(HOME, WATCHLIST, SEARCH, SETTINGS)

    /** True wenn eine Route eine der Haupt-Routes ist. */
    fun isMainRoute(route: String?): Boolean = route != null && route in mainRoutes

    /** True wenn eine Route ein Player-Route ist. */
    fun isPlayerRoute(route: String?): Boolean = route?.startsWith("player/") == true

    /** True wenn eine Route ein Detail-Route ist. */
    fun isDetailRoute(route: String?): Boolean = route?.startsWith("detail/") == true
}

@Composable
fun NovaStreamNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore("?")
    val context = LocalContext.current

    // TV detection - auf TV Geräten wird eine Top Tab Bar statt Bottom Bar verwendet
    val isTvDevice = remember { TvUtils.isTvDevice(context) }

    val showNavBars = Routes.isMainRoute(currentRoute)

    // Double-back to exit on home screen
    val snackbarHostState = remember { SnackbarHostState() }
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = currentRoute == Routes.HOME) {
        val now = System.nanoTime()
        if (now - backPressedTime < 2_000_000_000L) {
            // Exit app - safe cast
            context.findActivity()?.finish()
        } else {
            backPressedTime = now
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Nochmal zurück drücken zum Beenden",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showNavBars && isTvDevice) {
                // TV: Top Tab Bar statt Bottom Bar (Amazon/Google TV Guidelines)
                PremiumTopTabBar(
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
        },
        bottomBar = {
            if (showNavBars && !isTvDevice) {
                // Phone/Tablet: Bottom Bar
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
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onSeriesClick = { slug -> nav.navigate(Routes.detail(slug)) },
                    onContinueWatchingClick = { slug, season, episode, title, seriesTitle, coverUrl ->
                        nav.navigate(Routes.player(slug, season, episode, title, seriesTitle, coverUrl))
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
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
                enterTransition = {
                    androidx.compose.animation.slideInHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(300),
                        initialOffsetX = { it }
                    ) + androidx.compose.animation.fadeIn()
                },
                exitTransition = {
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                },
                popEnterTransition = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(250))
                },
                popExitTransition = {
                    androidx.compose.animation.slideOutHorizontally(
                        animationSpec = androidx.compose.animation.core.tween(300),
                        targetOffsetX = { it }
                    ) + androidx.compose.animation.fadeOut()
                }
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
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("seriesTitle") { type = NavType.StringType; defaultValue = "" },
                    navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" }
                ),
                enterTransition = {
                    androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(350),
                        initialOffsetY = { it }
                    ) + androidx.compose.animation.fadeIn()
                },
                exitTransition = {
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                },
                popEnterTransition = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(250))
                },
                popExitTransition = {
                    androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.tween(350),
                        targetOffsetY = { it }
                    ) + androidx.compose.animation.fadeOut()
                }
            ) {
                val slugArg = it.arguments?.getString("slug") ?: ""
                val seriesTitleArg = it.arguments?.getString("seriesTitle") ?: ""
                val coverUrlArg = it.arguments?.getString("coverUrl")?.takeIf { c -> c.isNotBlank() }
                val seasonArg = it.arguments?.getInt("season") ?: 1
                val episodeArg = it.arguments?.getInt("episode") ?: 1
                if (slugArg.isBlank() || seasonArg < 1 || episodeArg < 1) {
                    LaunchedEffect(Unit) {
                        snackbarHostState.showSnackbar(
                            message = "Ungültige Wiedergabeparameter",
                            duration = SnackbarDuration.Short
                        )
                        nav.popBackStack()
                    }
                    return@composable
                }
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

/** Findet die Activity aus einem Context (sicherer Cast). */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
