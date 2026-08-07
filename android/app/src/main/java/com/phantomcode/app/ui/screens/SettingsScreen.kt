package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.StorageHelper
import com.phantomcode.app.data.vm.DeviceCapabilities
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.data.vm.QemuPrefs
import com.phantomcode.app.data.vm.QemuPresets
import com.phantomcode.app.ui.components.BorderStylePreview
import com.phantomcode.app.ui.components.ButtonStylePreview
import com.phantomcode.app.ui.components.CornerStylePreview
import com.phantomcode.app.ui.components.FontStylePreview
import com.phantomcode.app.ui.components.LogoPickerSection
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.PhantomPrimaryButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.components.SettingsRow
import com.phantomcode.app.ui.components.StylePickerDialog
import com.phantomcode.app.ui.components.SwatchRow
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.ui.theme.LocalUiStyleController
import com.phantomcode.app.ui.theme.PhantomBorderStyle
import com.phantomcode.app.ui.theme.PhantomButtonStyle
import com.phantomcode.app.ui.theme.PhantomCornerStyle
import com.phantomcode.app.ui.theme.PhantomFontStyle
import com.phantomcode.app.ui.theme.PhantomPreset

private data class CustomField(val key: String, val label: String)

private val CustomFields = listOf(
    CustomField("background", "Fundo"),
    CustomField("surface", "Superfície"),
    CustomField("accent", "Destaque"),
    CustomField("secondary", "Secondary"),
    CustomField("text", "Texto"),
    CustomField("success", "Sucesso"),
)

@Composable
fun SettingsScreen() {
    val controller = LocalThemeController.current
    val palette = controller.currentPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SectionLabel(text = "Settings")
        Spacer(Modifier.height(12.dp))

        // ── Aparência & Temas (D10) ─────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Palette, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel(text = "Aparência & Temas")
        }
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tema ativo", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(palette.name, color = palette.accentPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhantomPreset.values().forEach { preset ->
                    val selected = controller.preset == preset
                    Text(
                        text = preset.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                            .border(
                                1.dp,
                                if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f),
                                RoundedCornerShape(3.dp),
                            )
                            .clickable { controller.selectPreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (selected) palette.accentPrimary else palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            LogoPickerSection()
        }

        if (controller.preset == PhantomPreset.CUSTOM) {
            Spacer(Modifier.height(10.dp))
            PhantomCard(modifier = Modifier.fillMaxWidth()) {
                Text("Cores custom (preview ao vivo)", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                CustomFields.forEach { field ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(field.label, color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(currentCustomColor(field.key))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SwatchRow(
                        selected = currentCustomColor(field.key),
                        onPick = { controller.setCustomColor(field.key, it) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── UI & Botões (Design System v2 — estilo do usuário) ─
        val uiController = LocalUiStyleController.current
        val uiPrefs = uiController.ui
        var uiPicker by remember { mutableStateOf<String?>(null) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel(text = "UI & Botões")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Personalize o visual do seu jeito — preview ao vivo em todo o app e no terminal.",
            color = palette.textSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                label = "Estilo dos botões",
                value = uiPrefs.buttonStyle.label,
                onClick = { uiPicker = "button" },
            )
            SettingsRow(
                label = "Cantos (raio)",
                value = uiPrefs.cornerStyle.label,
                onClick = { uiPicker = "corner" },
            )
            SettingsRow(
                label = "Bordas / linhas",
                value = uiPrefs.borderStyle.label,
                onClick = { uiPicker = "border" },
            )
            SettingsRow(
                label = "Letras",
                value = uiPrefs.fontStyle.label,
                onClick = { uiPicker = "font" },
            )
            Spacer(Modifier.height(10.dp))
            Text("Prévia", color = palette.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhantomPrimaryButton(text = "Ação principal", onClick = {})
                Spacer(Modifier.width(10.dp))
                PhantomOutlinedButton(text = "Secundária", onClick = {})
            }
        }

        when (uiPicker) {
            "button" -> StylePickerDialog(
                title = "Estilo dos botões",
                options = PhantomButtonStyle.values().toList(),
                selected = uiPrefs.buttonStyle,
                render = { ButtonStylePreview(it) },
                onPick = {
                    uiController.update { p -> p.copy(buttonStyle = it) }
                    uiPicker = null
                },
                onDismiss = { uiPicker = null },
            )
            "corner" -> StylePickerDialog(
                title = "Cantos (raio)",
                options = PhantomCornerStyle.values().toList(),
                selected = uiPrefs.cornerStyle,
                render = { CornerStylePreview(it) },
                onPick = {
                    uiController.update { p -> p.copy(cornerStyle = it) }
                    uiPicker = null
                },
                onDismiss = { uiPicker = null },
            )
            "border" -> StylePickerDialog(
                title = "Bordas / linhas",
                options = PhantomBorderStyle.values().toList(),
                selected = uiPrefs.borderStyle,
                render = { BorderStylePreview(it) },
                onPick = {
                    uiController.update { p -> p.copy(borderStyle = it) }
                    uiPicker = null
                },
                onDismiss = { uiPicker = null },
            )
            "font" -> StylePickerDialog(
                title = "Letras",
                options = PhantomFontStyle.values().toList(),
                selected = uiPrefs.fontStyle,
                render = { FontStylePreview(it) },
                onPick = {
                    uiController.update { p -> p.copy(fontStyle = it) }
                    uiPicker = null
                },
                onDismiss = { uiPicker = null },
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Ambiente VM (D13) ───────────────────────────────────
        val vm = LocalVm.current
        val qemu = vm.qemu
        val qemuContext = LocalContext.current
        var localCores by remember { mutableStateOf(qemu.preset.cpu) }
        var localRam by remember { mutableStateOf(qemu.preset.ramMb) }
        var localDiskMb by remember { mutableStateOf(qemu.diskSizeMb()) }
        val maxCores = DeviceCapabilities.cores(qemuContext)
        val maxRam = DeviceCapabilities.maxRamMb(qemuContext)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel(text = "Ambiente VM")
        }
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (qemu.running) palette.success else palette.border, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "QEMU LINUX: ${qemu.statusText}",
                    color = if (qemu.running) palette.success else palette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Use Toolbox para iniciar ou parar o Linux.",
                color = palette.textSecondary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("Preset de recursos (D13)", color = palette.textSecondary, fontSize = 12.sp)
            Text(
                "Seu aparelho: ${qemu.deviceSummary()}",
                color = palette.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QemuPresets.ALL.forEach { preset ->
                    val selected = qemu.preset.id == preset.id
                    Text(
                        text = preset.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                            .border(
                                1.dp,
                                if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f),
                                RoundedCornerShape(3.dp),
                            )
                            .clickable {
                                qemu.setPreset(preset)
                                localCores = qemu.preset.cpu
                                localRam = qemu.preset.ramMb
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (selected) palette.accentPrimary else palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${qemu.preset.cpu} cores · ${qemu.preset.ramMb} MB RAM",
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )

            if (qemu.preset.custom) {
                Spacer(Modifier.height(10.dp))
                Text("Núcleos (até $maxCores)", color = palette.textSecondary, fontSize = 11.sp)
                Slider(
                    value = localCores.toFloat(),
                    onValueChange = {
                        localCores = it.toInt().coerceIn(1, maxCores)
                    },
                    onValueChangeFinished = {
                        qemu.setPreset(QemuPresets.CUSTOM, customCores = localCores, customRamMb = localRam)
                    },
                    valueRange = 1f..maxCores.toFloat(),
                    steps = maxCores - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accentPrimary,
                        activeTrackColor = palette.accentPrimary,
                        inactiveTrackColor = palette.surfaceAlt,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text("RAM (MB — até $maxRam)", color = palette.textSecondary, fontSize = 11.sp)
                Slider(
                    value = localRam.toFloat(),
                    onValueChange = {
                        localRam = it.toInt().coerceIn(512, maxRam)
                    },
                    onValueChangeFinished = {
                        qemu.setPreset(QemuPresets.CUSTOM, customCores = localCores, customRamMb = localRam)
                    },
                    valueRange = 512f..maxRam.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accentPrimary,
                        activeTrackColor = palette.accentPrimary,
                        inactiveTrackColor = palette.surfaceAlt,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Custom: ${qemu.preset.cpu} cores · ${qemu.preset.ramMb} MB RAM",
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Tamanho do HD da distro (padrão 3 GB)", color = palette.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf(qemu.diskSizeMb()) + QemuPrefs(qemuContext).diskOptions()).distinct().sorted().forEach { sizeMb ->
                    val selected = localDiskMb == sizeMb
                    Text(
                        text = if (sizeMb >= 1024) "${sizeMb / 1024} GB" else "${sizeMb} MB",
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                            .border(
                                1.dp,
                                if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f),
                                RoundedCornerShape(3.dp),
                            )
                            .clickable {
                                localDiskMb = sizeMb
                                qemu.setDiskSizeMb(sizeMb)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (selected) palette.accentPrimary else palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Espaço do disco Linux no aparelho. Aplicado no 1º boot da VM (resize2fs).",
                color = palette.textSecondary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Distros (Phantom oficial): Toolbox → baixar/trocar",
                color = palette.textSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(20.dp))

        // ── Armazenamento (pasta pública do app) ────────────────
        val storageContext = LocalContext.current
        var storageGranted by remember { mutableStateOf(StorageHelper.hasStorageAccess(storageContext)) }
        val storageSettingsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { storageGranted = StorageHelper.hasStorageAccess(storageContext) }
        val storagePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> storageGranted = granted || StorageHelper.hasStorageAccess(storageContext) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storage, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel(text = "Armazenamento")
        }
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                label = "Pasta do app",
                value = if (storageGranted) "/${StorageHelper.APP_DIR_NAME}/workspace" else "Interno (privado)",
            )
            SettingsRow(
                label = "Acesso",
                value = if (storageGranted) "Concedido" else "Não concedido",
                onClick = {
                    if (!storageGranted) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            storageSettingsLauncher.launch(Intent(StorageHelper.permissionIntent(storageContext)))
                        } else {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                },
            )
        }
        if (!storageGranted) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Toque em \"Acesso\" para liberar a pasta ${StorageHelper.APP_DIR_NAME} no armazenamento interno.",
                color = palette.textSecondary,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Editor ──────────────────────────────────────────────
        SectionLabel(text = "Editor")
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(label = "Fonte", value = "JetBrains Mono")
            SettingsRow(label = "Tamanho", value = "14")
            SettingsRow(label = "Word wrap", value = "On")
            SettingsRow(label = "Tema de syntax", value = "Cyber-Phantom")
        }

        Spacer(Modifier.height(20.dp))

        // ── Sobre ───────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel(text = "Sobre")
        }
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(label = "Versão", value = "0.1.0")
            SettingsRow(label = "Codinome", value = "Dark-Code")
            SettingsRow(label = "Ambiente", value = "QEMU headless (Fase 3)")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun currentCustomColor(key: String): Color {
    val controller = LocalThemeController.current
    return when (key) {
        "background" -> controller.custom.background
        "surface" -> controller.custom.surface
        "accent" -> controller.custom.accentPrimary
        "secondary" -> controller.custom.accentSecondary
        "text" -> controller.custom.textPrimary
        "success" -> controller.custom.success
        else -> Color.Transparent
    }
}
