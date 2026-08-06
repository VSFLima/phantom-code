package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.StorageHelper
import com.phantomcode.app.data.vm.DistroCatalog
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.theme.LocalThemeController

/**
 * Onboarding de 1º uso (T24 · D20): apresenta o app e os primeiros passos —
 * conceder armazenamento, instalar a Phantom Base e iniciar o Linux.
 */
@Composable
fun OnboardingScreen(
    storageGranted: Boolean,
    distroInstalled: Boolean,
    onRequestStorage: () -> Unit,
    onInstallDistro: () -> Unit,
    onStartLinux: () -> Unit,
    onFinish: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        PhantomLogo(size = 88.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            "BEM-VINDO AO PHANTOM-CODE",
            color = palette.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Text(
            "IDE com terminal Linux embutido, Git e IA no seu celular.",
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(28.dp))

        OnboardingStep(
            number = 1,
            title = "Libere o armazenamento",
            desc = "O app cria a pasta ${StorageHelper.APP_DIR_NAME} no armazenamento interno para seus projetos.",
            done = storageGranted,
            actionLabel = "Permitir",
            onAction = onRequestStorage,
        )
        Spacer(Modifier.height(12.dp))

        OnboardingStep(
            number = 2,
            title = "Instale a Phantom Base",
            desc = "Baixa o sistema Linux oficial (aarch64) — alguns MB, feito uma vez.",
            done = distroInstalled,
            actionLabel = "Instalar",
            onAction = onInstallDistro,
        )
        Spacer(Modifier.height(12.dp))

        OnboardingStep(
            number = 3,
            title = "Inicie o Linux",
            desc = "Sobe a VM QEMU headless e abre o terminal integrado.",
            done = false,
            actionLabel = "Iniciar",
            onAction = onStartLinux,
        )
        Spacer(Modifier.height(28.dp))

        PhantomPrimaryButton(
            text = "Começar",
            icon = Icons.Filled.CheckCircle,
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Você pode fazer tudo isso depois na Toolbox.",
            color = palette.textSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingStep(
    number: Int,
    title: String,
    desc: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    PhantomCard(modifier = Modifier.fillMaxWidth(), glow = done) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(if (done) palette.success else palette.accentPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                } else {
                    Text(number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, color = palette.textSecondary, fontSize = 11.sp, maxLines = 3)
            }
            Spacer(Modifier.width(10.dp))
            if (!done) {
                PhantomOutlinedButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
