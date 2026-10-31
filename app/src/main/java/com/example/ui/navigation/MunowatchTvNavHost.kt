package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppContainer
import com.example.presentation.auth.AuthScreen
import com.example.presentation.auth.AuthViewModel
import com.example.presentation.categories.CategoriesScreen
import com.example.presentation.categories.CategoriesViewModel
import com.example.presentation.details.DetailsScreen
import com.example.presentation.details.DetailsViewModel
import com.example.presentation.home.HomeScreen
import com.example.presentation.home.HomeViewModel
import com.example.presentation.movies.MoviesScreen
import com.example.presentation.movies.MoviesViewModel
import com.example.presentation.player.PlayerScreen
import com.example.presentation.player.PlayerViewModel
import com.example.presentation.search.SearchScreen
import com.example.presentation.search.SearchViewModel
import com.example.presentation.series.SeriesScreen
import com.example.presentation.series.SeriesViewModel
import com.example.presentation.seriesdetails.SeriesDetailsScreen
import com.example.presentation.seriesdetails.SeriesDetailsViewModel
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.SettingsViewModel
import com.example.presentation.splash.SplashScreen
import com.example.presentation.splash.SplashViewModel

@Composable
fun MunowatchTvNavHost(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    // Hoisted SearchViewModel to retain search query, results, and scroll state across navigation
    val searchViewModel = remember {
        SearchViewModel(
            searchRepository = appContainer.searchRepository,
            movieRepository = appContainer.movieRepository,
            seriesRepository = appContainer.seriesRepository,
            searchHistoryDao = appContainer.database.searchHistoryDao()
        )
    }

    // Hoisted MoviesViewModel to retain movies catalog state, loaded items, filters, and scroll position across navigation
    val moviesViewModel = remember {
        MoviesViewModel(
            movieRepository = appContainer.movieRepository,
            searchRepository = appContainer.searchRepository,
            munowatchRepository = appContainer.munowatchRepository
        )
    }

    // Hoisted SeriesViewModel to retain series catalog state and scroll position across navigation
    val seriesViewModel = remember {
        SeriesViewModel(
            seriesRepository = appContainer.seriesRepository
        )
    }

    // Hoisted CategoriesViewModel to retain categories catalog state, active VJ/genre filters, loaded items, and scroll position across navigation
    val categoriesViewModel = remember {
        CategoriesViewModel(
            munowatchRepository = appContainer.munowatchRepository,
            movieRepository = appContainer.movieRepository,
            searchRepository = appContainer.searchRepository
        )
    }

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        // Splash Screen
        composable("splash") {
            val splashViewModel = remember { SplashViewModel(appContainer.authRepository) }
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // Auth Screen
        composable("auth") {
            val authViewModel = remember { AuthViewModel(appContainer.authRepository) }
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
                onSkipToGuest = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        // Home Screen
        composable("home") {
            val homeViewModel = remember {
                HomeViewModel(
                    appContainer.movieRepository,
                    appContainer.seriesRepository,
                    appContainer.playbackRepository,
                    appContainer.munowatchRepository,
                    appContainer.appUpdateRepository
                )
            }
            HomeScreen(
                viewModel = homeViewModel,
                onNavigate = { route ->
                    if (route != "home") navController.navigate(route)
                },
                onPlayMedia = { contentId ->
                    val encoded = safeEncodeForNav(contentId)
                    navController.navigate("player/$encoded")
                },
                onOpenDetails = { contentId ->
                    val encoded = safeEncodeForNav(contentId)
                    if (contentId.startsWith("series_") || com.example.data.repository.MediaCache.get(contentId)?.isSeries == true) {
                        navController.navigate("series_details/$encoded")
                    } else {
                        navController.navigate("details/$encoded")
                    }
                },
                onOpenSeriesDetails = { seriesId ->
                    val encoded = safeEncodeForNav(seriesId)
                    navController.navigate("series_details/$encoded")
                }
            )
        }

        // Movies Catalog Screen
        composable("movies") {
            MoviesScreen(
                viewModel = moviesViewModel,
                onNavigate = { route ->
                    if (route != "movies") navController.navigate(route)
                },
                onOpenDetails = { contentId ->
                    val encoded = safeEncodeForNav(contentId)
                    if (contentId.startsWith("series_") || com.example.data.repository.MediaCache.get(contentId)?.isSeries == true) {
                        navController.navigate("series_details/$encoded")
                    } else {
                        navController.navigate("details/$encoded")
                    }
                }
            )
        }

        // Series Screen
        composable("series") {
            SeriesScreen(
                viewModel = seriesViewModel,
                onNavigate = { route ->
                    if (route != "series") navController.navigate(route)
                },
                onOpenSeriesDetails = { seriesId ->
                    val encoded = safeEncodeForNav(seriesId)
                    navController.navigate("series_details/$encoded")
                }
            )
        }

        // Categories (Veejays & Genres) Screen
        composable(
            route = "categories?vj_id={vj_id}&genre_id={genre_id}",
            arguments = listOf(
                navArgument("vj_id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("genre_id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val vjId = backStackEntry.arguments?.getString("vj_id")?.toIntOrNull()
            val genreId = backStackEntry.arguments?.getString("genre_id")?.toIntOrNull()

            LaunchedEffect(vjId, genreId) {
                if (vjId != null || genreId != null) {
                    categoriesViewModel.loadCategories(initialVjId = vjId, initialGenreId = genreId)
                }
            }
            CategoriesScreen(
                viewModel = categoriesViewModel,
                onNavigate = { route ->
                    if (!route.startsWith("categories")) navController.navigate(route)
                },
                onOpenDetails = { contentId ->
                    val encoded = safeEncodeForNav(contentId)
                    if (contentId.startsWith("series_") || com.example.data.repository.MediaCache.get(contentId)?.isSeries == true) {
                        navController.navigate("series_details/$encoded")
                    } else {
                        navController.navigate("details/$encoded")
                    }
                }
            )
        }

        // Search Screen
        composable("search") {
            SearchScreen(
                viewModel = searchViewModel,
                onNavigate = { route ->
                    if (route != "search") navController.navigate(route)
                },
                onOpenDetails = { contentId ->
                    val encoded = safeEncodeForNav(contentId)
                    if (contentId.startsWith("series_") || com.example.data.repository.MediaCache.get(contentId)?.isSeries == true) {
                        navController.navigate("series_details/$encoded")
                    } else {
                        navController.navigate("details/$encoded")
                    }
                },
                onPlayEpisode = { episodeId ->
                    val encoded = safeEncodeForNav(episodeId)
                    navController.navigate("player/$encoded")
                }
            )
        }

        // Movie Details Screen
        composable(
            route = "details/{contentId}",
            arguments = listOf(navArgument("contentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("contentId") ?: ""
            val contentId = safeDecodeFromNav(rawId)
            val detailsViewModel = remember(contentId) {
                DetailsViewModel(
                    contentId = contentId,
                    movieRepository = appContainer.movieRepository,
                    munowatchRepository = appContainer.munowatchRepository,
                    searchRepository = appContainer.searchRepository
                )
            }
            DetailsScreen(
                viewModel = detailsViewModel,
                onBack = { navController.popBackStack() },
                onPlay = { id ->
                    val encoded = safeEncodeForNav(id)
                    navController.navigate("player/$encoded")
                },
                onOpenMovie = { recommendedId ->
                    val encoded = safeEncodeForNav(recommendedId)
                    navController.navigate("details/$encoded")
                }
            )
        }

        // Series Details Screen
        composable(
            route = "series_details/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawSeriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            val seriesId = safeDecodeFromNav(rawSeriesId)
            val seriesDetailsViewModel = remember(seriesId) {
                SeriesDetailsViewModel(
                    seriesId,
                    appContainer.seriesRepository,
                    appContainer.munowatchRepository
                )
            }
            SeriesDetailsScreen(
                viewModel = seriesDetailsViewModel,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { episodeId ->
                    val encoded = safeEncodeForNav(episodeId)
                    navController.navigate("player/$encoded")
                }
            )
        }

        // AndroidX Media3 Video Player Screen
        composable(
            route = "player/{contentId}?server={server}",
            arguments = listOf(
                navArgument("contentId") { type = NavType.StringType },
                navArgument("server") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val rawContentId = backStackEntry.arguments?.getString("contentId") ?: ""
            val contentId = safeDecodeFromNav(rawContentId)
            val server = backStackEntry.arguments?.getString("server")
            val playerViewModel = remember(contentId, server) {
                PlayerViewModel(contentId, server, appContainer.playbackRepository)
            }
            PlayerScreen(
                viewModel = playerViewModel,
                networkClient = appContainer.networkClient,
                onBack = { navController.popBackStack() },
                onNavigateToAuth = { navController.navigate("auth") }
            )
        }

        // Settings & Diagnostics Screen
        composable("settings") {
            val settingsViewModel = remember {
                SettingsViewModel(
                    appContainer.authRepository,
                    appContainer.munowatchRepository,
                    appContainer.playbackRepository,
                    appContainer.sessionManager,
                    appContainer.appUpdateRepository
                )
            }
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigate = { route ->
                    if (route != "settings") navController.navigate(route)
                },
                onNavigateToAuth = {
                    navController.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun safeEncodeForNav(value: String): String {
    if (value.isBlank()) return "empty"
    return if (value.contains('/') || value.contains('?') || value.contains('&') || value.contains('=') || value.contains('%')) {
        "b64_" + android.util.Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    } else {
        value
    }
}

private fun safeDecodeFromNav(param: String): String {
    return if (param.startsWith("b64_")) {
        try {
            val bytes = android.util.Base64.decode(
                param.removePrefix("b64_"),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            param
        }
    } else {
        try {
            java.net.URLDecoder.decode(param, "UTF-8")
        } catch (_: Exception) {
            param
        }
    }
}
