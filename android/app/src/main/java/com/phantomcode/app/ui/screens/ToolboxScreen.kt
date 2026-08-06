package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.launch

@Composable
fun ToolboxScreen() {
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            SectionLabel(text = "Toolbox")
            Spacer(Modifier.height(12.dp))

            // Status do ambiente
            PhantomCard(glow = false, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(palette.border, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("QEMU LINUX", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("STOPPED", color = palette.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Text("RAM — / 2G", color = palette.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))
                    PhantomOutlinedButton(
                        text = "Iniciar",
                        onClick = { scope.launch { snackbar.showSnackbar("VM QEMU — disponível na Fase 3") } },
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Integrações & API Keys (D8)
            SectionLabel(text = "Integrações & API Keys")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IntegrationCard(name = "Google Drive", connected = true)
                IntegrationCard(name = "OneDrive", connected = false)
                Box(
                    modifier = Modifier
                        .size(width = 84.dp, height = 84.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.surfaceAlt),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar", tint = palette.textSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "IAs")
            Spacer(Modifier.height(8.dp))
            ToolChipRow(listOf("Ollama", "llama.cpp", "gpt4all"))

            Spacer(Modifier.height(16.dp))
            SectionLabel(text = "Linguagens / Runtimes")
            Spacer(Modifier.height(8.dp))
            ToolChipRow(listOf("Node", "Python", "Rust", "Go"))

            Spacer(Modifier.height(16.dp))
            SectionLabel(text = "Ferramentas")
            Spacer(Modifier.height(8.dp))
            ToolChipRow(listOf("git", "curl", "vim", "htop"))

            Spacer(Modifier.height(16.dp))
            SectionLabel(text = "Sistema (protegidos)")
            Spacer(Modifier.height(8.dp))
            ToolChipRow(listOf("glibc", "apt", "bash", "systemd"))

            Spacer(Modifier.height(16.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phantom AI Suite (D12)", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Roteador de IAs com contexto compartilhado — em breve.", color = palette.textSecondary, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun IntegrationCard(name: String, connected: Boolean) {
    val palette = LocalThemeController.current.currentPalette()
    Column(
        modifier = Modifier
            .size(width = 84.dp, height = 84.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surface)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Cloud,
            contentDescription = null,
            tint = if (connected) palette.success else palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(name, color = palette.textPrimary, fontSize = 9.sp, maxLines = 1)
        Text(
            if (connected) "Conectado" else "Offline",
            color = if (connected) palette.success else palette.textSecondary,
            fontSize = 8.sp,
        )
    }
}

@Composable
private fun ToolChipRow(list: List<String>) {
    val palette = LocalThemeController.current.currentPalette()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { name ->
            Text(
                text = name,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}
