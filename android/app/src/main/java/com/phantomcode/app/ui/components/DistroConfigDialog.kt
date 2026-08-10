package com.phantomcode.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantomcode.app.data.vm.DeviceCapabilities
import com.phantomcode.app.data.vm.DistroConfig
import com.phantomcode.app.data.vm.DistroInfo
import com.phantomcode.app.data.vm.QemuPrefs
import com.phantomcode.app.data.vm.QemuPresets
import com.phantomcode.app.ui.theme.LocalThemeController

/**
 * Diálogo de configuração inicial (antes da instalação automática).
 *
 * O usuário escolhe o que quer (preset de recursos, hostname, usuário e
 * tamanho do HD); o app baixa, instala, configura rede/workspace/prompt e
 * abre o terminal — sem tela chata de configuração no Linux.
 */
@Composable
fun DistroConfigDialog(
    info: DistroInfo,
    initialDiskMb: Int,
    initialPresetId: String = QemuPresets.BALANCED.id,
    initialCores: Int = QemuPresets.BALANCED.cpu,
    initialRamMb: Int = QemuPresets.BALANCED.ramMb,
    githubAuthenticated: Boolean = true,
    onOpenGit: () -> Unit = {},
    onConfirm: (DistroConfig) -> Unit,
    onInstallLocal: (DistroConfig) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var hostname by remember { mutableStateOf("phantom") }
    var user by remember { mutableStateOf("user") }
    var presetId by remember { mutableStateOf(initialPresetId) }
    var diskMb by remember { mutableStateOf(initialDiskMb) }

    val maxCores = DeviceCapabilities.cores(context)
    val maxRam = DeviceCapabilities.maxRamMb(context)
    var cores by remember { mutableStateOf(initialCores) }
    var ramMb by remember { mutableStateOf(initialRamMb) }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(animationSpec = tween(200), initialScale = 0.85f) + fadeIn(tween(200)),
            exit = scaleOut(animationSpec = tween(130), targetScale = 0.9f) + fadeOut(tween(130)),
        ) {
            Column(
                modifier = Modifier
                    .background(palette.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, palette.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Instalar ${info.name}",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Escolha as opções iniciais — o app faz o resto (rede, workspace, prompt).",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(14.dp))

                // ── Preset de recursos ──
                Text("Recursos da VM", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QemuPresets.ALL.filter { !it.custom }.forEach { preset ->
                        val selected = presetId == preset.id
                        Text(
                            text = preset.label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                                .border(1.dp, if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .clickable {
                                    presetId = preset.id
                                    cores = preset.cpu
                                    ramMb = preset.ramMb
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            color = if (selected) palette.accentPrimary else palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }

                // ── Custom ──
                val customSelected = presetId == QemuPresets.CUSTOM.id
                Text(
                    text = "Custom",
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (customSelected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                        .border(1.dp, if (customSelected) palette.accentPrimary else palette.border.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .clickable { presetId = QemuPresets.CUSTOM.id }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    color = if (customSelected) palette.accentPrimary else palette.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (customSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (customSelected) {
                    Spacer(Modifier.height(8.dp))
                    Text("Núcleos (até $maxCores)", color = palette.textSecondary, fontSize = 11.sp)
                    Slider(
                        value = cores.toFloat(),
                        onValueChange = { cores = it.toInt().coerceIn(1, maxCores) },
                        valueRange = 1f..maxCores.toFloat(),
                        steps = (maxCores - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accentPrimary,
                            activeTrackColor = palette.accentPrimary,
                            inactiveTrackColor = palette.surfaceAlt,
                        ),
                    )
                    Text("RAM (MB — até $maxRam)", color = palette.textSecondary, fontSize = 11.sp)
                    Slider(
                        value = ramMb.toFloat(),
                        onValueChange = { ramMb = it.toInt().coerceIn(512, maxRam) },
                        valueRange = 512f..maxRam.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accentPrimary,
                            activeTrackColor = palette.accentPrimary,
                            inactiveTrackColor = palette.surfaceAlt,
                        ),
                    )
                    Text(
                        "Custom: $cores cores · $ramMb MB RAM",
                        color = palette.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Hostname", color = palette.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                ConfigTextField(hostname, { hostname = it }, "phantom")
                Spacer(Modifier.height(10.dp))
                Text("Usuário", color = palette.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                ConfigTextField(user, { user = it }, "user")

                Spacer(Modifier.height(12.dp))
                Text("Tamanho do HD da distro (padrão 3 GB)", color = palette.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (listOf(diskMb) + QemuPrefs(context).diskOptions()).distinct().sorted().forEach { sizeMb ->
                        val selected = diskMb == sizeMb
                        Text(
                            text = "${sizeMb / 1024} GB",
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                                .border(1.dp, if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .clickable { diskMb = sizeMb }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            color = if (selected) palette.accentPrimary else palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (!githubAuthenticated) {
                    Text(
                        "O download automático requer uma conexão com a internet. Também é possível instalar por arquivos locais.",
                        color = palette.warning,
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                     PhantomOutlinedButton(text = "Abrir Git", onClick = onOpenGit)
                    Spacer(Modifier.height(10.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PhantomOutlinedButton(text = "Cancelar", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    PhantomOutlinedButton(
                        text = "Selecionar arquivos…",
                        icon = Icons.Filled.FolderOpen,
                        enabled = hostname.isNotBlank() && user.isNotBlank(),
                        onClick = {
                            onInstallLocal(
                                DistroConfig(
                                    hostname = hostname.trim(),
                                    user = user.trim(),
                                    diskSizeMb = diskMb,
                                    presetId = presetId,
                                    cores = cores,
                                    ramMb = ramMb,
                                ),
                            )
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    PhantomPrimaryButton(
                        text = "Instalar automaticamente",
                        enabled = hostname.isNotBlank() && user.isNotBlank() && githubAuthenticated,
                        onClick = {
                            onConfirm(
                                DistroConfig(
                                    hostname = hostname.trim(),
                                    user = user.trim(),
                                    diskSizeMb = diskMb,
                                    presetId = presetId,
                                    cores = cores,
                                    ramMb = ramMb,
                                ),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "A instalação é acompanhada ao vivo no terminal (aba própria).",
                    color = palette.textSecondary,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\"Selecionar arquivos\" usa arquivos locais (sem internet): o pacote phantom.tar.gz ou os arquivos avulsos (rootfs.img, kernel, initrd.img, qemu-system-aarch64).",
                    color = palette.border,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun ConfigTextField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val palette = LocalThemeController.current.currentPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(palette.surfaceAlt)
            .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(palette.accentSecondary),
            singleLine = true,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = palette.textSecondary, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}
