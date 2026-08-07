package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController
import com.phantomcode.app.data.TextMatch
import com.phantomcode.app.data.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(onOpenFile: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = LocalThemeController.current.currentPalette()
    val workspace = remember { WorkspaceManager(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TextMatch>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank() || searching) return
        searching = true
        scope.launch {
            results = withContext(Dispatchers.IO) { workspace.search(query) }
            searching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        SectionLabel(text = "Search")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(palette.surfaceAlt)
                .border(1.dp, palette.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(palette.accentSecondary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Buscar no projeto…", color = palette.textSecondary, fontSize = 14.sp)
                    }
                    innerTextField()
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        PhantomOutlinedButton(
            text = if (searching) "Buscando…" else "Buscar no workspace",
            icon = Icons.Filled.Search,
            enabled = query.isNotBlank() && !searching,
            onClick = ::search,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (results.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = palette.border, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (query.isBlank()) "Busca global" else "Nenhum resultado",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Arquivos de até 2 MB no workspace", color = palette.textSecondary, fontSize = 12.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(results, key = { "${it.path}:${it.line}" }) { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(match.path) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${match.path}:${match.line}", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(match.preview, color = palette.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
