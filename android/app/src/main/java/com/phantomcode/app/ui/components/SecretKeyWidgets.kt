package com.phantomcode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.phantomcode.app.data.secrets.IntegrationCatalog
import com.phantomcode.app.data.secrets.IntegrationPreset
import com.phantomcode.app.data.secrets.SecretCategory
import com.phantomcode.app.data.secrets.SecretEntry
import com.phantomcode.app.ui.theme.LocalThemeController

/** Card de chave salva (D8): nome, categoria, valor mascarado, expor ao Linux, ações. */
@Composable
fun SecretKeyCard(
    entry: SecretEntry,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onToggleExpose: (Boolean) -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    PhantomCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(entry.alias, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                entry.category,
                color = palette.accentBright,
                fontSize = 9.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.accentPrimary.copy(alpha = 0.18f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.masked, color = palette.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("\$${entry.envVar}", color = palette.accentSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = entry.exposeToLinux,
                onCheckedChange = onToggleExpose,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = palette.accentPrimary,
                    uncheckedTrackColor = palette.surfaceAlt,
                    checkedThumbColor = Color.White,
                    uncheckedThumbColor = palette.border,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Text("Expor ao Linux", color = palette.textSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copiar nome da variável",
                tint = palette.textSecondary,
                modifier = Modifier.size(32.dp).clickable(onClick = onCopy).padding(8.dp),
            )
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Revogar",
                tint = palette.error,
                modifier = Modifier.size(32.dp).clickable(onClick = onDelete).padding(8.dp),
            )
        }
    }
}

/** Diálogo "Adicionar chave" (D8): escolha o serviço (preenche tudo) ou digite manual. */
@Composable
fun AddSecretKeyDialog(
    onSave: (name: String, value: String, category: SecretCategory, envVar: String, expose: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenService: (String) -> Unit = {},
) {
    val palette = LocalThemeController.current.currentPalette()
    var name by remember { mutableStateOf("") }
    var envVar by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SecretCategory.GIT) }
    var expose by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<IntegrationPreset?>(null) }
    var query by remember { mutableStateOf("") }
    var showServices by remember { mutableStateOf(true) }

    fun pick(preset: IntegrationPreset) {
        selectedPreset = preset
        name = preset.name
        envVar = preset.envVar
        category = preset.category
        showServices = false
    }

    val filtered = IntegrationCatalog.ALL.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) ||
            it.envVar.contains(query, ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(palette.surface, RoundedCornerShape(8.dp))
                .border(1.dp, palette.accentPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(20.dp)
                .heightIn(max = 560.dp),
        ) {
            Text("Nova chave / API Key", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Escolha o serviço ou preencha manualmente.", color = palette.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))

            // ── Seletor de serviços ──
            if (showServices) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.surfaceAlt)
                        .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                        cursorBrush = SolidColor(palette.accentSecondary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Buscar serviço…", color = palette.textSecondary, fontSize = 12.sp)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 220.dp),
                ) {
                    if (filtered.isEmpty()) {
                        Text("Nenhum serviço encontrado — digite manualmente abaixo.", color = palette.textSecondary, fontSize = 11.sp)
                    }
                    filtered.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { pick(preset) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(preset.name, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(preset.category.label, color = palette.textSecondary, fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PhantomOutlinedButton(text = "Preencher manualmente", onClick = { showServices = false })
                Spacer(Modifier.height(6.dp))
            }

            // ── Formulário (após escolher o serviço ou manual) ──
            if (!showServices) {
                // Botão do serviço escolhido: trocar / link para criar o token
                selectedPreset?.let { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accentPrimary.copy(alpha = 0.12f))
                            .border(1.dp, palette.accentPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(preset.name, color = palette.accentPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("trocar", color = palette.accentSecondary, fontSize = 10.sp, modifier = Modifier.clickable { showServices = true }.padding(4.dp))
                    }
                    if (preset.docUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        // Botão clicável: abre no navegador interno a página de criação do token
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.accentSecondary.copy(alpha = 0.10f))
                                .border(1.dp, palette.accentSecondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable { onOpenService(preset.docUrl) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Criar token/API key de ${preset.name}", color = palette.accentSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Field(label = "Nome", placeholder = "ex.: OpenAI", value = name, onChange = { name = it }, hidden = false)
                Spacer(Modifier.height(10.dp))
                Field(label = "Variável de ambiente", placeholder = "OPENAI_API_KEY", value = envVar, onChange = { envVar = it }, hidden = false)
                Spacer(Modifier.height(10.dp))
                Field(
                    label = "Valor",
                    placeholder = selectedPreset?.valueHint ?: "sk-…",
                    value = value,
                    onChange = { value = it },
                    hidden = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecretCategory.values().forEach { c ->
                        val selected = category == c
                        Text(
                            c.label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (selected) palette.accentPrimary.copy(alpha = 0.18f) else palette.surfaceAlt)
                                .border(1.dp, if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                                .clickable { category = c }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            color = if (selected) palette.accentPrimary else palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = expose,
                        onCheckedChange = { expose = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = palette.accentPrimary,
                            uncheckedTrackColor = palette.surfaceAlt,
                            checkedThumbColor = Color.White,
                            uncheckedThumbColor = palette.border,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Disponível no terminal Linux", color = palette.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PhantomOutlinedButton(text = "Cancelar", onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    PhantomPrimaryButton(
                        text = "Salvar",
                        enabled = name.isNotBlank() && value.isNotBlank(),
                        onClick = { onSave(name.trim(), value, category, envVar.trim(), expose) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    hidden: Boolean,
) {
    val palette = LocalThemeController.current.currentPalette()
    Text(label, color = palette.textSecondary, fontSize = 11.sp)
    Spacer(Modifier.height(4.dp))
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
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp, fontFamily = if (hidden) FontFamily.Monospace else FontFamily.Default),
            cursorBrush = SolidColor(palette.accentSecondary),
            singleLine = true,
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = palette.textSecondary, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}
