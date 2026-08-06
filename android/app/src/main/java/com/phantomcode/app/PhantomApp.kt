package com.phantomcode.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phantomcode.app.ui.components.PhantomScaffold
import com.phantomcode.app.ui.navigation.Routes
import com.phantomcode.app.ui.screens.EditorScreen
import com.phantomcode.app.ui.screens.ExplorerScreen
import com.phantomcode.app.ui.screens.GitScreen
import com.phantomcode.app.ui.screens.HomeScreen
import com.phantomcode.app.ui.screens.SearchScreen
import com.phantomcode.app.ui.screens.SettingsScreen
import com.phantomcode.app.ui.screens.ToolboxScreen
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.PhantomTheme
import com.phantomcode.app.ui.theme.ThemeController

@Composable
fun PhantomRoot() {
    val context = LocalContext.current
    val controller = remember { ThemeController(context.applicationContext) }
    CompositionLocalProvider(LocalThemeController provides controller) {
        PhantomTheme(palette = controller.currentPalette()) {
            PhantomApp()
        }
    }
}

@Composable
fun PhantomApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    PhantomScaffold(
        currentRoute = currentRoute,
        onNavigate = { route -> navController.navigateToTab(route) },
        onHome = {
            navController.navigate(Routes.HOME) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenProject = { project ->
                        navController.navigateToTab(Routes.EXPLORER)
                    },
                    onOpenFile = { path ->
                        navController.navigate(Routes.editorRoute(path))
                    },
                )
            }
            composable(Routes.EXPLORER) {
                ExplorerScreen(
                    onOpenFile = { path ->
                        navController.navigate(Routes.editorRoute(path))
                    },
                )
            }
            composable(Routes.SEARCH) { SearchScreen() }
            composable(Routes.GIT) { GitScreen() }
            composable(Routes.TOOLBOX) { ToolboxScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("path") { type = NavType.StringType }),
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString("path") ?: "")
                EditorScreen(
                    path = path,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Navegação entre abas preservando o estado de cada uma (padrão bottom-nav). */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
