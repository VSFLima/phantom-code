package com.phantomcode.app

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.phantomcode.app.ui.components.PhantomScaffold
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.navigation.Routes
import com.phantomcode.app.ui.screens.EditorScreen
import com.phantomcode.app.ui.screens.ExplorerScreen
import com.phantomcode.app.ui.screens.GitScreen
import com.phantomcode.app.ui.screens.BrowserScreen
import com.phantomcode.app.ui.screens.HomeScreen
import com.phantomcode.app.ui.screens.OnboardingScreen
import com.phantomcode.app.ui.screens.SearchScreen
import com.phantomcode.app.ui.screens.SettingsScreen
import com.phantomcode.app.ui.screens.TerminalScreen
import com.phantomcode.app.ui.screens.ToolboxScreen
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.LocalUiStyleController
import com.phantomcode.app.ui.theme.LocalTerminalStyleController
import com.phantomcode.app.ui.theme.PhantomTheme
import com.phantomcode.app.ui.theme.ThemeController
import com.phantomcode.app.ui.theme.TerminalStyleController
import com.phantomcode.app.ui.theme.UiStyleController
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun PhantomRoot() {
    val context = LocalContext.current
    val controller = remember { ThemeController(context.applicationContext) }
    val uiStyle = remember { UiStyleController(context.applicationContext) }
    val terminalStyle = remember { TerminalStyleController(context.applicationContext) }
    val vm = remember { VmController(context.applicationContext) }
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(850)
        showSplash = false
    }
    CompositionLocalProvider(
        LocalThemeController provides controller,
        LocalUiStyleController provides uiStyle,
        LocalTerminalStyleController provides terminalStyle,
        LocalVm provides vm,
    ) {
        PhantomTheme(palette = controller.currentPalette()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                PhantomApp()
                AnimatedVisibility(
                    visible = showSplash,
                    enter = fadeIn(tween(180)) + scaleIn(tween(420), initialScale = 0.82f),
                    exit = fadeOut(tween(320)),
                ) {
                    PhantomLaunchSplash()
                }
            }
        }
    }
}

@Composable
private fun PhantomLaunchSplash() {
    val palette = LocalThemeController.current.currentPalette()
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        PhantomLogo(size = 104.dp)
        Spacer(Modifier.height(18.dp))
        Text("PHANTOM-CODE", color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(6.dp))
        Text("IDE · LINUX · GIT", color = palette.accentSecondary, fontSize = 10.sp, letterSpacing = 1.5.sp)
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

    var editorTabs by remember { mutableStateOf<List<String>>(emptyList()) }
    var autoOpenedTerminal by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(!session.onboardingDone) }
    var storageGranted by remember { mutableStateOf(StorageHelper.hasStorageAccess(context)) }

    val storageSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { storageGranted = StorageHelper.hasStorageAccess(context); showOnboarding = !session.onboardingDone }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> storageGranted = granted || StorageHelper.hasStorageAccess(context) }

    fun openEditor(path: String) {
        val cleanPath = path.trim().trimStart('/')
        if (cleanPath.isBlank()) return
        editorTabs = (editorTabs.filterNot { it == cleanPath } + cleanPath).takeLast(8)
        navController.navigate(Routes.editorRoute(cleanPath))
    }

    fun closeEditor(path: String) {
        editorTabs = editorTabs.filterNot { it == path }
        navController.popBackStack()
    }

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
            onChooseDistro = { navController.navigateToTab(Routes.TOOLBOX) },
            onStartLinux = { scope.launch { vm.qemu.start() } },
            onFinish = {
                session.onboardingDone = true
                showOnboarding = false
            },
        )
        return
    }

    // ── Auto-início da VM (como o Termux) ───────────────────────
    // Ao abrir o app, a distro ativa sobe sozinha em background e o Terminal
    // fica disponível. A notificação do Foreground Service permite encerrar a
    // sessão sem transformar o app em um shell permanente.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val active = vm.distros.activeId
                if (active != null && !vm.qemu.running) {
                    scope.launch { vm.qemu.start() }
                    if (!autoOpenedTerminal) {
                        autoOpenedTerminal = true
                        navController.navigate(Routes.TERMINAL) {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        PhantomScaffold(
            currentRoute = currentRoute,
            qemuRunning = vm.qemu.running,
            onNavigate = { route -> navController.navigateToTab(route) },
            onHome = {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onToggleLinux = { scope.launch { if (vm.qemu.running) vm.qemu.stop() else vm.qemu.start() } },
        ) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(
                    Routes.HOME,
                    enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 6 } },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { it / 6 } },
                ) {
                    HomeScreen(
                        onOpenProject = { navController.navigateToTab(Routes.EXPLORER) },
                        onOpenFile = ::openEditor,
                    )
                }
                composable(Routes.EXPLORER) {
                    ExplorerScreen(
                        onOpenFile = ::openEditor,
                    )
                }
                 composable(Routes.SEARCH) {
                     SearchScreen(onOpenFile = ::openEditor)
                 }
                composable(Routes.GIT) { GitScreen() }
                composable(Routes.TOOLBOX) {
                    ToolboxScreen(
                        onOpenTerminal = { navController.navigate(Routes.TERMINAL) },
                        onOpenBrowser = { url -> navController.navigate(Routes.browserRoute(url)) },
                        onOpenGit = { navController.navigateToTab(Routes.GIT) },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen() }
                composable(Routes.TERMINAL) {
                    TerminalScreen(
                        onBack = { navController.popBackStack() },
                        onOpenToolbox = { navController.navigateToTab(Routes.TOOLBOX) },
                    )
                }
                composable(
                    route = Routes.BROWSER_URL,
                    arguments = listOf(navArgument("url") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    val url = Uri.decode(entry.arguments?.getString("url") ?: "").ifBlank { null }
                    BrowserScreen(onBack = { navController.popBackStack() }, initialUrl = url)
                }
                composable(
                    route = Routes.EDITOR,
                    arguments = listOf(navArgument("path") { type = NavType.StringType }),
                ) { entry ->
                    val path = Uri.decode(entry.arguments?.getString("path") ?: "")
                    EditorScreen(
                        path = path,
                        openTabs = editorTabs.ifEmpty { listOf(path) },
                        onSelectTab = ::openEditor,
                        onClose = { closeEditor(path) },
                        onCloseTab = { tab ->
                            editorTabs = editorTabs.filterNot { it == tab }
                            if (tab == path) navController.popBackStack()
                        },
                        onOpenFile = ::openEditor,
                    )
                }
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
