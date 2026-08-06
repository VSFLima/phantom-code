package com.phantomcode.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.ui.components.PhantomOutlinedButton
import com.phantomcode.app.ui.components.SectionLabel
import com.phantomcode.app.ui.theme.LocalThemeController

@Composable
fun SearchScreen() {
    val palette = LocalThemeController.current.currentPalette()
    var query by remember { mutableStateOf("") }

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
        Spacer(Modifier.height(48.dp))
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Code, contentDescription = null, tint = palette.border, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("Busca global", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Localize e substitua texto em todo o projeto.", color = palette.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            PhantomOutlinedButton(
                text = "Buscar",
                icon = Icons.Filled.Search,
                enabled = query.isNotBlank(),
                onClick = {},
            )
        }
    }
}
