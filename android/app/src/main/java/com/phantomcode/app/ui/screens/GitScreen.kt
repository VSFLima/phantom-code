package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import kotlinx.coroutines.launch

@Composable
fun GitScreen() {
    val palette = LocalThemeController.current.currentPalette()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(text = "Git")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "main",
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.surfaceAlt)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.GitHub, contentDescription = null, tint = palette.border, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nenhum repositório aberto", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Clone um repositório para ver Changes, commits e branches aqui.",
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(20.dp))
                PhantomPrimaryButton(
                    text = "Clonar Repositório",
                    icon = Icons.Filled.GitHub,
                    onClick = { scope.launch { snackbar.showSnackbar("Git (JGit) — disponível na Fase 4") } },
                )
            }
            Spacer(Modifier.height(32.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Row {
                    Text("Changes", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("0", color = palette.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("Staged Changes", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("0", color = palette.textSecondary, fontSize = 12.sp)
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}
