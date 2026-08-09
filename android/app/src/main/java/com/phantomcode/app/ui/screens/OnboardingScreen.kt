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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.StorageHelper
import com.phantomcode.app.data.vm.DistroCatalog
import com.phantomcode.app.data.vm.DistroInstallState
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomLogo
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.theme.LocalThemeController

/**
 * Onboarding de 1º uso (T24 · D20 + T30): apresenta o app e os primeiros passos —
 * conceder armazenamento, instalar o motor QEMU + a distro Phantom (vêm no mesmo
 * pacote, com progresso real no terminal), escolher a distro e iniciar o Linux.
 */
@Composable
fun OnboardingScreen(
    storageGranted: Boolean,
    distroInstalled: Boolean,
    qemuReady: Boolean,
    phantomState: DistroInstallState?,
    onRequestStorage: () -> Unit,
    onChooseDistro: () -> Unit,
    onStartLinux: () -> Unit,
    onInstallPhantom: () -> Unit,
    onFinish: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    val downloading = phantomState?.downloading == true
    val phantomInstalled = phantomState?.installed == true

    // T30: no 1º uso, instala AUTOMATICAMENTE o motor QEMU + a distro Phantom
    // (mesmo tarball) e abre o terminal com log + barra de progresso reais.
    LaunchedEffect(Unit) {
        if (!qemuReady && !phantomInstalled && !downloading) {
            onInstallPhantom()
        }
    }

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
            title = "Instale o QEMU + a distro",
            desc = if (downloading) {
                "Baixando e instalando — o motor QEMU e a distro Phantom vêm no mesmo pacote. Acompanhe o progresso ao vivo no terminal."
            } else {
                "Um único pacote traz o motor QEMU e a distro Phantom. Instalação real, com log e barra de progresso ao vivo no terminal."
            },
            done = phantomInstalled,
            actionLabel = "Instalar",
            onAction = onInstallPhantom,
            progress = if (downloading) phantomState?.progress else null,
        )
        phantomState?.error?.let { err ->
            Spacer(Modifier.height(4.dp))
            Text(
                "✗ $err — depois de autenticar, toque em Instalar para tentar de novo.",
                color = palette.error,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Start).padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        OnboardingStep(
            number = 3,
            title = "Escolha sua distro",
            desc = "Toque para ver descrição, consumo e riscos de cada uma (modo terminal apenas). O app instala e configura tudo para você.",
            done = distroInstalled,
            actionLabel = "Escolher",
            onAction = onChooseDistro,
        )
        Spacer(Modifier.height(12.dp))

        OnboardingStep(
            number = 4,
            title = "Inicie o Linux",
            desc = "Sobe a VM QEMU headless e abre o terminal integrado.",
            done = distroInstalled,
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
    progress: Float? = null,
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
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f),
                            color = palette.accentPrimary,
                            trackColor = palette.surfaceAlt,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${(progress * 100).toInt()}%",
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            if (!done && progress == null) {
                PhantomOutlinedButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
