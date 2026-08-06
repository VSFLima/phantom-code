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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomCard
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.components.SettingsRow
import com.phantomcode.app.ui.components.SwatchRow
import com.phantomcode.app.ui.theme.LocalThemeController
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

        // ── Ambiente VM (D13) ───────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel(text = "Ambiente VM")
        }
        Spacer(Modifier.height(8.dp))
        PhantomCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(label = "Iniciar Linux na abertura (D20)", value = "Off")
            SettingsRow(label = "Presets CPU / RAM", value = "2G · 4 cores")
            SettingsRow(label = "Usar todo o poder do aparelho (D13)", value = "Off")
            SettingsRow(label = "Baixar / trocar distro", value = "—")
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
