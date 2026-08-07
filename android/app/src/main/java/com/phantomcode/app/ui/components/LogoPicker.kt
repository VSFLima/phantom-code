package com.phantomcode.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantomcode.app.data.LogoController
import com.phantomcode.app.data.StorageHelper
import com.phantomcode.app.ui.theme.LocalThemeController

/**
 * Seletor de logo do app (aba Aparência & Temas).
 *
 * Mostra o escudo padrão + as imagens encontradas na pasta `linux/` da memória
 * interna do celular. Tocar em uma opção troca a logo em todo o app na hora.
 */
@Composable
fun LogoPickerSection() {
    val palette = LocalThemeController.current.currentPalette()
    val context = LocalContext.current
    val logos = remember { LogoController(context) }
    val options = remember(logos.selected) { logos.available() }

    Spacer(Modifier.height(12.dp))
    Text("Logo do app", color = palette.textSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoOption(
            selected = logos.selected == null,
            onClick = { logos.setSelected(null) },
        ) {
            PhantomLogo(size = 44.dp)
        }
        options.forEach { (name, file) ->
            val bitmap = remember(name) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }
            if (bitmap != null) {
                LogoOption(
                    selected = logos.selected == name,
                    onClick = { logos.setSelected(name) },
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = name,
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
    if (options.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Column {
            Text(
                "Nenhuma imagem encontrada. Coloque suas logos na pasta:",
                color = palette.textSecondary,
                fontSize = 10.sp,
            )
            Text(
                "/${StorageHelper.APP_DIR_NAME}/linux  ou  /linux",
                color = palette.accentPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Text(
                "Formatos: PNG · JPG · WEBP",
                color = palette.textSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun LogoOption(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalThemeController.current.currentPalette()
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.accentPrimary.copy(alpha = 0.15f) else palette.surfaceAlt)
            .border(
                width = 2.dp,
                color = if (selected) palette.accentPrimary else palette.border.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
