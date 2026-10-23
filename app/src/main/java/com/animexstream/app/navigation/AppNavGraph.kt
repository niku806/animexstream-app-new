package com.animexstream.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.ui.components.AppBottomBar
import com.animexstream.app.ui.components.AppDrawerContent
import com.animexstream.app.ui.components.PlaceholderScreen
import com.animexstream.app.ui.screens.anime.AnimeTabScreen
import com.animexstream.app.ui.screens.auth.LoginScreen
import com.animexstream.app.ui.screens.auth.OnboardingScreen
import com.animexstream.app.ui.screens.auth.SignupScreen
import com.animexstream.app.ui.screens.auth.SplashScreen
import com.animexstream.app.ui.screens.community.CommunityScreen
import com.animexstream.app.ui.screens.detail.DetailScreen
import com.animexstream.app.ui.screens.home.HomeScreen
import com.animexstream.app.ui.screens.player.PlayerScreen
import com.animexstream.app.ui.screens.profile.ProfileScreen
import com.animexstream.app.ui.screens.search.SearchScreen
import com.animexstream.app.ui.screens.settings.SettingsScreen
import com.animexstream.app.ui.theme.BgDark
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val COMMUNITY = "community"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ANIME_TAB = "anime_tab"
    const val DETAIL = "detail/{animeId}"
    const val PLAYER = "player/{animeId}/{episode}"
    const val MOVIES = "movies"
    const val SPORTS = "sports"
    const val TVSERIES = "tvseries"

    fun detail(animeId: String) = "detail/$animeId"
    fun player(animeId: String, episode: Int) = "player/$animeId/$episode"
}

private val bottomRoutes = setOf(
    Routes.HOME, Routes.SEARCH, Routes.DOWNLOADS, Routes.COMMUNITY, Routes.PROFILE
)

private val drawerRoutes = setOf(
    Routes.HOME, Routes.SEARCH, Routes.COMMUNITY, Routes.PROFILE
)

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController(), onProviderChanged: () -> Unit = {}) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != null && currentRoute in bottomRoutes
    val showDrawer = currentRoute != null && currentRoute in drawerRoutes

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().background(BgDark)) {
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.SPLASH,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.SPLASH) {
                        SplashScreen(onNext = { loggedIn, onboarded ->
                            val target = when {
                                !onboarded -> Routes.ONBOARDING
                                loggedIn -> Routes.HOME
                                else -> Routes.LOGIN
                            }
                            navController.navigate(target) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        })
                    }
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onDone = {
                            val target = if (AuthRepository.isLoggedIn) Routes.HOME else Routes.LOGIN
                            navController.navigate(target) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        })
                    }
                    composable(Routes.LOGIN) {
                        LoginScreen(
                            onLoggedIn = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            },
                            onGoSignup = { navController.navigate(Routes.SIGNUP) }
                        )
                    }
                    composable(Routes.SIGNUP) {
                        SignupScreen(
                            onSignedUp = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SIGNUP) { inclusive = true }
                                }
                            },
                            onGoLogin = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onAnimeClick = { id -> navController.navigate(Routes.detail(id)) },
                            onPlayClick = { id, ep -> navController.navigate(Routes.player(id, ep)) },
                            onSearchClick = { navController.navigate(Routes.SEARCH) },
                            onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onOpenAnimeTab = { navController.navigate(Routes.ANIME_TAB) },
                            onCategoryClick = { route -> navController.navigate(route) { launchSingleTop = true } }
                        )
                    }
                    composable(Routes.ANIME_TAB) {
                        AnimeTabScreen(
                            onAnimeClick = { id -> navController.navigate(Routes.detail(id)) },
                            onBack = { navController.popBackStack() },
                            onSearchClick = { navController.navigate(Routes.SEARCH) }
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(onAnimeClick = { id -> navController.navigate(Routes.detail(id)) })
                    }
                    composable(Routes.DETAIL) { entry ->
                        val id = entry.arguments?.getString("animeId").orEmpty()
                        DetailScreen(
                            animeId = id,
                            onBack = { navController.popBackStack() },
                            onWatchClick = { slug, ep -> navController.navigate(Routes.player(slug, ep)) },
                            onSimilarClick = { s -> navController.navigate(Routes.detail(s)) }
                        )
                    }
                    composable(Routes.PLAYER) { entry ->
                        val id = entry.arguments?.getString("animeId").orEmpty()
                        val ep = entry.arguments?.getString("episode")?.toIntOrNull() ?: 1
                        PlayerScreen(
                            animeId = id,
                            episodeNumber = ep,
                            onBack = { navController.popBackStack() },
                            onNextEpisode = { n -> navController.navigate(Routes.player(id, n)) }
                        )
                    }
                    composable(Routes.COMMUNITY) {
                        CommunityScreen(onProfileClick = { navController.navigate(Routes.PROFILE) })
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            onLogout = {
                                AuthRepository.signOut()
                                navController.navigate(Routes.LOGIN) { popUpTo(0) }
                            },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() }, onProviderChanged = onProviderChanged)
                    }
                    composable(Routes.DOWNLOADS) {
                        com.animexstream.app.ui.screens.downloads.DownloadsScreen(
                            onPlayOffline = { taskId -> navController.navigate("offline_player/${android.net.Uri.encode(taskId)}") }
                        )
                    }
                    composable("offline_player/{taskId}") { entry ->
                        val taskId = android.net.Uri.decode(entry.arguments?.getString("taskId").orEmpty())
                        com.animexstream.app.ui.screens.downloads.OfflinePlayerScreen(
                            taskId = taskId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.MOVIES) { PlaceholderScreen("Movies") }
                    composable(Routes.SPORTS) {
                        com.animexstream.app.ui.screens.livetv.LiveTvScreen(
                            onChannelClick = { ch -> navController.navigate("livetv_player/${android.net.Uri.encode(ch.url)}") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("livetv_player/{url}") { entry ->
                        val url = android.net.Uri.decode(entry.arguments?.getString("url").orEmpty())
                        val channel = com.animexstream.app.data.LiveChannel("Live TV", "", "", url, "")
                        com.animexstream.app.ui.screens.livetv.LiveTvPlayerScreen(channel = channel, onBack = { navController.popBackStack() })
                    }
                    composable(Routes.TVSERIES) { PlaceholderScreen("TV Series") }
                }
            }
            if (showBottomBar) {
                AppBottomBar(currentRoute = currentRoute ?: Routes.HOME) { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

    if (showDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    onNavigate = { route ->
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        ) {
            content()
        }
    } else {
        content()
    }
}
