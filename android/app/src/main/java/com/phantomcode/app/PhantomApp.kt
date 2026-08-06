package com.phantomcode.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phantomcode.app.data.SessionManager
import com.phantomcode.app.data.StorageHelper
import com.phantomcode.app.data.vm.DistroCatalog
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.data.vm.VmController
import com.phantomcode.app.ui.components.CommandPalette
import com.phantomcode.app.ui.components.PaletteCommand
import com.phantomcode.app.ui.components.PhantomScaffold
import com.phantomcode.app.ui.navigation.Routes
import com.phantomcode.app.ui.screens.EditorScreen
import com.phantomcode.app.ui.screens.ExplorerScreen
import com.phantomcode.app.ui.screens.GitScreen
import com.phantomcode.app.ui.screens.HomeScreen
import com.phantomcode.app.ui.screens.OnboardingScreen
import com.phantomcode.app.ui.screens.SearchScreen
import com.phantomcode.app.ui.screens.SettingsScreen
import com.phantomcode.app.ui.screens.TerminalScreen
import com.phantomcode.app.ui.screens.ToolboxScreen
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.PhantomTheme
import com.phantomcode.app.ui.theme.ThemeController
import kotlinx.coroutines.launch

@Composable
fun PhantomRoot() {
    val context = LocalContext.current
    val controller = remember { ThemeController(context.applicationContext) }
    val vm = remember { VmController(context.applicationContext) }
    CompositionLocalProvider(
        LocalThemeController provides controller,
        LocalVm provides vm,
    ) {
        PhantomTheme(palette = controller.currentPalette()) {
            PhantomApp()
        }
    }
}

@Composable
fun PhantomApp() {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    val vm = LocalVm.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME

    var paletteOpen by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(!session.onboardingDone) }
    var storageGranted by remember { mutableStateOf(StorageHelper.hasStorageAccess(context)) }

    val storageSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { storageGranted = StorageHelper.hasStorageAccess(context); showOnboarding = !session.onboardingDone }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> storageGranted = granted || StorageHelper.hasStorageAccess(context) }

    // ── Onboarding de 1º uso (T24 · D20) ────────────────────────
    if (showOnboarding) {
        val distroInstalled = vm.distros.isInstalled(DistroCatalog.ALL.first().id)
        OnboardingScreen(
            storageGranted = storageGranted,
            distroInstalled = distroInstalled,
            onRequestStorage = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageSettingsLauncher.launch(StorageHelper.permissionIntent(context))
                } else {
                    storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            },
            onInstallDistro = { vm.distros.install(DistroCatalog.ALL.first()) },
            onStartLinux = { scope.launch { vm.qemu.start() } },
            onFinish = {
                session.onboardingDone = true
                showOnboarding = false
            },
        )
        return
    }

    val paletteCommands = listOf(
        PaletteCommand("Início", Icons.Filled.Home, "home inicio", { navController.navigateToTab(Routes.HOME) }),
        PaletteCommand("Explorer", Icons.Filled.FolderOpen, "explorer arquivos projetos", { navController.navigateToTab(Routes.EXPLORER) }),
        PaletteCommand("Terminal Linux", Icons.Filled.Terminal, "terminal linux vm qemu", { navController.navigate(Routes.TERMINAL) }),
        PaletteCommand("Iniciar Linux", Icons.Filled.PlayArrow, "iniciar vm qemu start", { scope.launch { vm.qemu.start() } }),
        PaletteCommand("Parar Linux", Icons.Filled.PlayArrow, "parar vm qemu stop", { vm.qemu.stop() }),
        PaletteCommand("Git", Icons.Filled.AccountTree, "git commit push clone", { navController.navigateToTab(Routes.GIT) }),
        PaletteCommand("Toolbox", Icons.Filled.Memory, "toolbox distros backup keys", { navController.navigateToTab(Routes.TOOLBOX) }),
        PaletteCommand("Settings", Icons.Filled.Settings, "settings tema armazenamento", { navController.navigateToTab(Routes.SETTINGS) }),
        PaletteCommand("Fechar", null, "fechar sair", { paletteOpen = false }),
    )

    Box(Modifier.fillMaxSize()) {
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
            onOpenTerminal = { navController.navigate(Routes.TERMINAL) },
            onOpenPalette = { paletteOpen = true },
        ) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenProject = { navController.navigateToTab(Routes.EXPLORER) },
                        onOpenFile = { path -> navController.navigate(Routes.editorRoute(path)) },
                    )
                }
                composable(Routes.EXPLORER) {
                    ExplorerScreen(
                        onOpenFile = { path -> navController.navigate(Routes.editorRoute(path)) },
                    )
                }
                composable(Routes.SEARCH) { SearchScreen() }
                composable(Routes.GIT) { GitScreen() }
                composable(Routes.TOOLBOX) { ToolboxScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.TERMINAL) {
                    TerminalScreen(onBack = { navController.popBackStack() })
                }
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

        if (paletteOpen) {
            CommandPalette(commands = paletteCommands, onDismiss = { paletteOpen = false })
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
