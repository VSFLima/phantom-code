package com.phantomcode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.navigation.BottomNavItems
import com.phantomcode.app.ui.navigation.Routes
import com.phantomcode.app.ui.theme.LocalThemeController

/**
 * Layout principal (D15): TopBar + Activity Bar fina + conteúdo + Terminal dock + Bottom Nav.
 */
@Composable
fun PhantomScaffold(
    currentRoute: String,
    qemuRunning: Boolean,
    onNavigate: (String) -> Unit,
    onHome: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenPalette: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        PhantomTopBar(running = qemuRunning, onHome = onHome, onOpenPalette = onOpenPalette)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ActivityBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                onHome = onHome,
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
        TerminalDock(onOpen = onOpenTerminal)
    }
}

@Composable
private fun PhantomTopBar(running: Boolean, onHome: () -> Unit, onOpenPalette: () -> Unit) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = palette.border.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhantomLogo(size = 26.dp, onClick = onHome)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "PHANTOM-CODE",
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        QemuStatusPill(running = running)
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = "Command Palette (⌘⇧P)",
            tint = palette.textSecondary,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onOpenPalette)
                .padding(6.dp),
        )
    }
}

/** Pill de status da VM (verde quando RUNNING, neutro quando STOPPED). */
@Composable
fun QemuStatusPill(running: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalThemeController.current.currentPalette()
    val dot = if (running) palette.success else palette.border
    val text = if (running) "QEMU LINUX: RUNNING" else "QEMU LINUX: STOPPED"
    val color = if (running) palette.success else palette.textSecondary
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = palette.surfaceAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(dot, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
    }
}

/** Activity Bar fina (~50dp), só ícones — abre painéis sob demanda (D15). */
@Composable
fun ActivityBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onHome: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Column(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .background(palette.surface)
            .drawBehind {
                drawLine(
                    color = palette.border.copy(alpha = 0.4f),
                    start = Offset(size.width - 1.dp.toPx(), 0f),
                    end = Offset(size.width - 1.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivityIcon(
            icon = Icons.Filled.Home,
            label = "Home",
            selected = currentRoute == Routes.HOME,
            onClick = onHome,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(palette.border.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(8.dp))
        BottomNavItems.forEach { item ->
            ActivityIcon(
                icon = item.icon,
                label = item.label,
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ActivityIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.accentPrimary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.accentPrimary else palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight(0.6f)
                    .background(palette.accentPrimary)
            )
        }
    }
}

/** Terminal dock no rodapé — toque abre o terminal (D4/D11). */
@Composable
fun TerminalDock(onOpen: () -> Unit) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .drawBehind {
                drawLine(
                    color = palette.border.copy(alpha = 0.5f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .clickable(onClick = onOpen)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Terminal,
            contentDescription = null,
            tint = palette.accentSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        TermTab("Terminal", active = true)
        Spacer(Modifier.weight(1f))
        Text(
            text = "Linux · workspace",
            color = palette.textSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Abrir terminal",
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TermTab(name: String, active: Boolean) {
    val palette = LocalThemeController.current.currentPalette()
    Text(
        text = name,
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontSize = 10.sp,
        color = if (active) palette.accentPrimary else palette.textSecondary,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
    )
}
