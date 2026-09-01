package com.novastream.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novastream.app.R
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.util.LocaleManager
import com.novastream.app.util.findActivity
import kotlinx.coroutines.launch
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.ui.components.PremiumBottomBar
import com.novastream.app.ui.components.PremiumLoading
import com.novastream.app.ui.components.PremiumTopTabBar
import com.novastream.app.ui.detail.DetailScreen
import com.novastream.app.ui.browse.BrowseScreen
import com.novastream.app.ui.continuewatching.ContinueWatchingScreen
import com.novastream.app.ui.home.HomeScreen
import com.novastream.app.ui.onboarding.OnboardingScreen
import com.novastream.app.ui.player.PlayerScreen
import com.novastream.app.ui.provider.ProviderMarketplaceScreen
import com.novastream.app.ui.search.SearchScreen
import com.novastream.app.ui.settings.SettingsScreen
import com.novastream.app.ui.tv.TvUtils
import com.novastream.app.ui.live.LiveTvScreen
import com.novastream.app.ui.watchlist.WatchlistScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val LIVE_TV = "live_tv"
    const val HOME = "home"
    const val WATCHLIST = "watchlist"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val BROWSE = "browse?section={section}&genre={genre}&filter={filter}"
    const val CONTINUE_WATCHING = "continue_watching"
    const val MARKETPLACE = "marketplace"
    const val DETAIL = "detail/{slug}"
    const val PLAYER = "player/{slug}/{season}/{episode}?title={title}&seriesTitle={seriesTitle}&coverUrl={coverUrl}&isMovie={isMovie}"

    fun browse(section: String = "", genre: String? = null, filter: String? = null): String {
        fun enc(s: String) = try { java.net.URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
        return "browse?section=${enc(section)}&genre=${enc(genre.orEmpty())}&filter=${enc(filter.orEmpty())}"
    }

    fun detail(slug: String) = "detail/$slug"
    fun player(
        slug: String,
        season: Int,
        episode: Int,
        title: String,
        seriesTitle: String = "",
        coverUrl: String? = null,
        isMovie: Boolean = false
    ): String {
        fun enc(s: String) = try { java.net.URLEncoder.encode(s, "UTF-8") } catch (_: Exception) { s }
        val t = enc(title)
        val st = enc(seriesTitle)
        val cu = coverUrl?.let { enc(it) } ?: ""
        return "player/$slug/$season/$episode?title=$t&seriesTitle=$st&coverUrl=$cu&isMovie=$isMovie"
    }

    /** Liste aller Haupt-Routes (für Nav-Bar Anzeige). */
    val mainRoutes = listOf(HOME, WATCHLIST, SEARCH, SETTINGS, "browse")

    /** True wenn eine Route eine der Haupt-Routes ist. */
    fun isMainRoute(route: String?): Boolean = route != null && route in mainRoutes

    /** True wenn eine Route ein Player-Route ist. */
    fun isPlayerRoute(route: String?): Boolean = route?.startsWith("player/") == true

    /** True wenn eine Route ein Detail-Route ist. */
    fun isDetailRoute(route: String?): Boolean = route?.startsWith("detail/") == true
}

@Composable
fun NovaStreamNavHost(deepLinkSlug: String? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore("?")
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(appSettings) {
        appSettings.onboardingComplete.collect { onboardingComplete = it }
    }
    val activeProviderId by ProviderManager.activeProviderIdFlow(context)
        .collectAsStateWithLifecycle(initialValue = ActiveProvider.id)
    val watchRepo = remember { WatchRepository.get(context) }
    val watchlistItems by watchRepo.watchlist().collectAsStateWithLifecycle(initialValue = emptyList())
    val watchlistCount = remember(watchlistItems, activeProviderId) {
        watchlistItems.count {
            it.providerId.isBlank() || it.providerId == activeProviderId || it.providerId == "unknown"
        }
    }
    val uiLocale by appSettings.uiLocale.collectAsStateWithLifecycle(initialValue = LocaleManager.SYSTEM_LOCALE)

    if (onboardingComplete == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PremiumLoading(label = context.getString(R.string.loading))
        }
        return
    }

    val onboardingDone = onboardingComplete == true

    LocaleManager.ProvideLayoutDirection(uiLocale) {

    LaunchedEffect(deepLinkSlug, onboardingDone) {
        if (onboardingDone && !deepLinkSlug.isNullOrBlank()) {
            nav.navigate(Routes.detail(deepLinkSlug)) {
                launchSingleTop = true
            }
        }
    }

    // TV detection - auf TV Geräten wird eine Top Tab Bar statt Bottom Bar verwendet
    val isTvDevice = remember { TvUtils.isTvDevice(context) }

    val showNavBars = Routes.isMainRoute(currentRoute) && currentRoute != Routes.ONBOARDING

    // Double-back to exit on home screen
    val snackbarHostState = remember { SnackbarHostState() }
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = currentRoute == Routes.HOME) {
        val now = System.currentTimeMillis()
        if (now - backPressedTime < 2000L) {
            // Exit app - safe cast
            context.findActivity()?.finish()
        } else {
            backPressedTime = now
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.press_back_again),
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
                    watchlistCount = watchlistCount,
                    onNavigate = { route ->
                        val target = if (route == "browse") Routes.browse() else route
                        if (currentRoute != route) {
                            nav.navigate(target) {
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
            startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        nav.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onSeriesClick = { slug -> nav.navigate(Routes.detail(slug)) },
                    onContinueWatchingClick = { slug, season, episode, title, seriesTitle, coverUrl, isMovie ->
                        nav.navigate(Routes.player(slug, season, episode, title, seriesTitle, coverUrl, isMovie))
                    },
                    onBrowseSection = { section, genre ->
                        nav.navigate(Routes.browse(section = section, genre = genre))
                    },
                    onSeeAllWatchlist = {
                        nav.navigate(Routes.WATCHLIST) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSeeAllContinueWatching = {
                        nav.navigate(Routes.CONTINUE_WATCHING)
                    },
                    onLiveTvClick = {
                        nav.navigate(Routes.LIVE_TV) { launchSingleTop = true }
                    }
                )
            }

            composable(
                route = Routes.BROWSE,
                arguments = listOf(
                    navArgument("section") { type = NavType.StringType; defaultValue = "" },
                    navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                    navArgument("filter") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                BrowseScreen(
                    onSeriesClick = { slug -> nav.navigate(Routes.detail(slug)) }
                )
            }

            composable(Routes.CONTINUE_WATCHING) {
                ContinueWatchingScreen(
                    onBack = { nav.popBackStack() },
                    onPlay = { slug, season, episode, title, seriesTitle, coverUrl, isMovie ->
                        nav.navigate(Routes.player(slug, season, episode, title, seriesTitle, coverUrl, isMovie))
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
                SettingsScreen(
                    onOpenMarketplace = {
                        nav.navigate(Routes.MARKETPLACE) { launchSingleTop = true }
                    }
                )
            }

            composable(Routes.MARKETPLACE) {
                ProviderMarketplaceScreen(onBack = { nav.popBackStack() })
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
                    onPlay = { slug, season, episode, title, seriesTitle, coverUrl, isMovie ->
                        nav.navigate(Routes.player(slug, season, episode, title, seriesTitle, coverUrl, isMovie))
                    },
                    onRelatedClick = { relatedSlug ->
                        nav.navigate(Routes.detail(relatedSlug)) { launchSingleTop = true }
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
                    navArgument("coverUrl") { type = NavType.StringType; defaultValue = "" },
                    navArgument("isMovie") { type = NavType.BoolType; defaultValue = false }
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
                            message = context.getString(R.string.invalid_playback_params),
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
                    },
                    onPreviousEpisode = { season, episode, title ->
                        nav.navigate(Routes.player(slugArg, season, episode, title, seriesTitleArg, coverUrlArg)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.LIVE_TV) {
                LiveTvScreen(
                    onBack = { nav.popBackStack() },
                    onPlayChannel = { channel ->
                        nav.navigate(
                            Routes.player(
                                slug = "live_${channel.id.take(32)}",
                                season = 1,
                                episode = 1,
                                title = channel.name,
                                seriesTitle = channel.group ?: "Live TV",
                                coverUrl = channel.logoUrl,
                                isMovie = true
                            )
                        )
                    }
                )
            }
        }
    }
    }
}

