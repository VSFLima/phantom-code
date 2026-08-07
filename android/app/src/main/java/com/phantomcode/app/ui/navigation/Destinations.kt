package com.phantomcode.app.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val EXPLORER = "explorer"
    const val SEARCH = "search"
    const val GIT = "git"
    const val TOOLBOX = "toolbox"
    const val SETTINGS = "settings"
    const val TERMINAL = "terminal"
    const val BROWSER = "browser"
    const val BROWSER_URL = "browser?url={url}"

    const val EDITOR = "editor/{path}"

    /** Monta a rota do editor com o caminho do arquivo (URL-encoded). */
    fun editorRoute(relPath: String): String = "editor/${Uri.encode(relPath)}"

    /** Monta a rota do navegador com uma URL inicial (URL-encoded, opcional).
     *  Sempre inclui o parâmetro para casar com a rota registrada `browser?url={url}`. */
    fun browserRoute(url: String? = null): String = "browser?url=${Uri.encode(url ?: "")}"
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Bottom Nav fixa com 5 itens (D14) — nunca adicionar um 6º. */
val BottomNavItems = listOf(
    NavItem(Routes.EXPLORER, "Explorer", Icons.Filled.Folder),
    NavItem(Routes.SEARCH, "Search", Icons.Filled.Search),
    NavItem(Routes.GIT, "Git", Icons.Filled.AccountTree),
    NavItem(Routes.TOOLBOX, "Toolbox", Icons.Filled.Memory),
    NavItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)
