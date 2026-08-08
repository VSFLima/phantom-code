package com.phantomcode.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/** Ícone + cor de um arquivo, resolvidos pela extensão (explorer com tipos visuais). */
data class FileTypeIcon(
    val icon: ImageVector,
    val tint: Color,
)

/** Mapeia o nome do arquivo para um ícone/tipo visual (pastas usam Folder/FolderOpen). */
fun fileTypeIcon(name: String, fallback: ImageVector = Icons.Filled.Description): FileTypeIcon {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        in CODE_EXTS -> FileTypeIcon(Icons.Filled.Code, Color(0xFF569CD6))
        in HTML_EXTS -> FileTypeIcon(Icons.Filled.Language, Color(0xFFE44D26))
        in CSS_EXTS -> FileTypeIcon(Icons.Filled.Palette, Color(0xFF42A5F5))
        in JSON_EXTS -> FileTypeIcon(Icons.Filled.DataObject, Color(0xFFF9A825))
        in MARKDOWN_EXTS -> FileTypeIcon(Icons.Filled.Article, Color(0xFF26A69A))
        in IMG_EXTS -> FileTypeIcon(Icons.Filled.Image, Color(0xFF66BB6A))
        in AUDIO_EXTS -> FileTypeIcon(Icons.Filled.MusicNote, Color(0xFFAB47BC))
        in VIDEO_EXTS -> FileTypeIcon(Icons.Filled.Movie, Color(0xFFEF5350))
        in PDF_EXTS -> FileTypeIcon(Icons.Filled.PictureAsPdf, Color(0xFFEF5350))
        in ARCHIVE_EXTS -> FileTypeIcon(Icons.Filled.FolderZip, Color(0xFF8D6E63))
        in CONFIG_EXTS -> FileTypeIcon(Icons.Filled.Tune, Color(0xFF78909C))
        in DB_EXTS -> FileTypeIcon(Icons.Filled.Storage, Color(0xFF26C6DA))
        in APK_EXTS -> FileTypeIcon(Icons.Filled.Android, Color(0xFF4CAF50))
        in SECRET_EXTS -> FileTypeIcon(Icons.Filled.Lock, Color(0xFFEF5350))
        in BINARY_EXTS -> FileTypeIcon(Icons.Filled.Memory, Color(0xFF90A4AE))
        else -> FileTypeIcon(fallback, Color(0xFF90A4AE))
    }
}

private val CODE_EXTS = setOf(
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "pyw", "rb", "php",
    "go", "rs", "c", "h", "cpp", "hpp", "cc", "cxx", "cs", "swift", "scala",
    "sh", "bash", "zsh", "ps1", "lua", "r", "dart", "groovy",
)

private val HTML_EXTS = setOf("html", "htm", "xhtml")

private val CSS_EXTS = setOf("css", "scss", "sass", "less")

private val JSON_EXTS = setOf("json", "jsonc")

private val MARKDOWN_EXTS = setOf("md", "markdown", "mdown")

private val IMG_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp", "heic", "avif")

private val AUDIO_EXTS = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "mid", "midi")

private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "flv", "wmv", "mpg", "mpeg")

private val PDF_EXTS = setOf("pdf")

private val ARCHIVE_EXTS = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "rar", "7z", "zst", "jar")

private val CONFIG_EXTS = setOf(
    "yml", "yaml", "toml", "ini", "cfg", "conf", "properties", "env",
    "gradle", "pro", "xml", "editorconfig", "gitignore", "gitattributes",
)

private val DB_EXTS = setOf("db", "sqlite", "sqlite3", "sql", "dbf")

private val APK_EXTS = setOf("apk", "aab")

private val SECRET_EXTS = setOf("pem", "key", "p12", "pfx", "keystore", "crt", "jks")

private val BINARY_EXTS = setOf("bin", "exe", "so", "dll", "class", "a", "o", "deb", "rpm")
