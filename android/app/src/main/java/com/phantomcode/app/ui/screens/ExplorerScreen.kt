package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.launch

@Composable
fun ExplorerScreen() {
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            SectionLabel(text = "Explorer")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "/workspace",
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(40.dp))
            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = palette.border, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Workspace vazio", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Crie um novo projeto ou abra uma pasta do aparelho.",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(20.dp))
                PhantomPrimaryButton(
                    text = "Novo Projeto",
                    icon = Icons.Filled.Add,
                    onClick = { scope.launch { snackbar.showSnackbar("Criação de projeto — disponível na Fase 2") } },
                )
                Spacer(Modifier.height(10.dp))
                PhantomOutlinedButton(
                    text = "Abrir Pasta (SAF)",
                    icon = Icons.Filled.FolderOpen,
                    onClick = { scope.launch { snackbar.showSnackbar("SAF — disponível na Fase 2") } },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.accentPrimary)
                .clickable { scope.launch { snackbar.showSnackbar("Novo arquivo — Fase 2") } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Novo arquivo", tint = Color.White)
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}
