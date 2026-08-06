package com.phantomcode.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GitHub
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.QemuStatusPill
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            PhantomLogo(size = 92.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "PHANTOM-CODE",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                letterSpacing = 4.sp,
            )
            Text(
                text = "IDE · TERMINAL LINUX · GIT · IA",
                fontSize = 11.sp,
                color = palette.textSecondary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(16.dp))
            QemuStatusPill(running = false)
            Spacer(Modifier.height(28.dp))
            SectionLabel(text = "Recent Projects", modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text("Nenhum projeto recente", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Crie ou clone um projeto para começar.", color = palette.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            PhantomPrimaryButton(
                text = "Novo Projeto",
                icon = Icons.Filled.Add,
                onClick = { scope.launch { snackbar.showSnackbar("Criação de projeto — disponível na Fase 2") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PhantomOutlinedButton(
                text = "Clonar Repositório",
                icon = Icons.Filled.GitHub,
                onClick = { scope.launch { snackbar.showSnackbar("Git — disponível na Fase 4") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PhantomOutlinedButton(
                text = "Abrir Pasta",
                icon = Icons.Filled.FolderOpen,
                onClick = { scope.launch { snackbar.showSnackbar("SAF — disponível na Fase 2") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Terminal dock no rodapé · expira com ▲",
                color = palette.textSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}
