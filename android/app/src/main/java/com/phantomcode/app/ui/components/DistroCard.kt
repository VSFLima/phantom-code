package com.phantomcode.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.vm.DistroInfo
import com.phantomcode.app.data.vm.DistroInstallState
import com.phantomcode.app.data.vm.DistroRisk
import com.phantomcode.app.data.vm.LocalVm
import com.phantomcode.app.ui.theme.LocalThemeController

/**
 * Card expansível de distro (catálogo D1).
 *
 * Cabeçalho: nome, badge, tamanhos, estado. Expandido: descrição, para quem é
 * recomendada, consumo (disco/RAM), risco de lentidão e aviso de modo terminal.
 */
@Composable
fun DistroCard(
    info: DistroInfo,
    isActive: Boolean,
    state: DistroInstallState,
    onClickInstall: () -> Unit,
    onClickUse: () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    var expanded by remember(info.id) { mutableStateOf(false) }

    PhantomCard(modifier = Modifier.fillMaxWidth(), glow = isActive) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    info.badge?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            color = if (info.available) palette.accentBright else palette.warning,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (info.available) palette.accentPrimary.copy(alpha = 0.18f) else palette.warning.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (state.installed) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Instalada", tint = palette.success, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "~${info.sizeMb} MB download · ~${info.installSizeMb / 1024} GB disco · ${info.packageManager} · ${riskChipLabel(info.risk)}",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                )
                if (state.downloading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.accentPrimary,
                        trackColor = palette.surfaceAlt,
                    )
                }
                state.error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = palette.error, fontSize = 10.sp, maxLines = 2)
                }
            }
            Spacer(Modifier.width(12.dp))
            when {
                state.downloading -> Text("…", color = palette.textSecondary, fontSize = 14.sp)
                !info.available && !state.installed -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Em breve", color = palette.warning, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                state.installed && isActive -> Text("Em uso", color = palette.success, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                state.installed -> PhantomOutlinedButton(text = "Usar", onClick = onClickUse)
                else -> PhantomOutlinedButton(text = "Instalar", icon = Icons.Filled.Download, onClick = onClickInstall)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Recolher" else "Expandir",
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                if (info.headless) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Modo terminal apenas (headless) — sem área gráfica",
                            color = palette.accentSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(info.description, color = palette.textPrimary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                InfoLine("Para quem", info.recommendedFor, palette.textPrimary, palette.textSecondary)
                InfoLine("Consumo", "Disco ~${info.installSizeMb / 1024} GB · RAM mín. ${info.ramMb} MB", palette.textPrimary, palette.textSecondary)
                InfoLine("Risco", riskChipLabel(info.risk), palette.textPrimary, riskColor(info.risk))
                if (info.risk == DistroRisk.HIGH) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = palette.warning, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Imagem grande e pesada: baixe só se o objetivo for o toolkit. Em aparelhos fracos pode ficar lenta.",
                            color = palette.warning,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (!info.available && !state.installed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Artefato ainda não publicado — disponível em breve pela própria Toolbox (download interno).",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, labelColor: Color, valueColor: Color) {
    val palette = LocalThemeController.current.currentPalette()
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = valueColor, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

private fun riskColor(risk: DistroRisk): Color = when (risk) {
    DistroRisk.LOW -> Color(0xFF00FF9F)
    DistroRisk.MEDIUM -> Color(0xFFFFB020)
    DistroRisk.HIGH -> Color(0xFFFF3366)
}

private fun riskChipLabel(risk: DistroRisk): String = when (risk) {
    DistroRisk.LOW -> "Leve"
    DistroRisk.MEDIUM -> "Moderada"
    DistroRisk.HIGH -> "Pesada"
}
