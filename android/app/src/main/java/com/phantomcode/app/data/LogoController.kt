package com.phantomcode.app.data

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Logos do app definidas pelo usuário (D?).
 *
 * O app vem com logos embutidas (assets/linux/logos — a padrão é o escudo).
 * O usuário pode colocar novas imagens (.png/.jpg/.webp) na pasta `linux/` da
 * memória interna do celular (`/storage/emulated/0/linux`) e escolher qual
 * usar como logo na aba de temas. A escolha é persistida e aplicada em todo o
 * app (Home, Onboarding, cabeçalho) — sem imagem selecionada, usa o escudo padrão.
 */
class LogoController(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("phantom_logo", Context.MODE_PRIVATE)

    /** Estado Compose compartilhado entre as instâncias — logo muda na hora em todo o app. */
    var selected: String? by selectedState
        private set

    init {
        if (selectedState.value == null) {
            selectedState.value = prefs.getString(KEY, null)
        }
    }

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun setSelected(name: String?) {
        selected = name
        if (name == null) {
            prefs.edit().remove(KEY).apply()
        } else {
            prefs.edit().putString(KEY, name).apply()
        }
    }

    /** Pastas onde o app procura as imagens de logo na memória interna. */
    fun logoDirs(): List<File> = buildList {
        val publicRoot = Environment.getExternalStorageDirectory()
        add(File(publicRoot, "linux"))
        add(File(publicRoot, "${StorageHelper.APP_DIR_NAME}/linux"))
        add(File(appContext.filesDir, "linux"))
        add(cachedBundledLogos())
    }

    /** Copia as logos embutidas (assets/linux/logos) para cache no filesDir. */
    private fun cachedBundledLogos(): File {
        val dir = File(appContext.filesDir, "linux-logos").apply { mkdirs() }
        runCatching {
            (appContext.assets.list("linux/logos") ?: emptyArray())
                .filter { File(it).extension.lowercase() in imageExtensions }
                .forEach { name ->
                    val out = File(dir, name)
                    if (!out.exists() || out.length() == 0L) {
                        appContext.assets.open("linux/logos/$name").use { input ->
                            out.outputStream().use { o -> input.copyTo(o) }
                        }
                    }
                }
        }
        return dir
    }

    /** Imagens encontradas (nome + arquivo), sem duplicar por nome. */
    fun available(): List<Pair<String, File>> {
        val seen = LinkedHashSet<String>()
        return logoDirs().flatMap { dir ->
            (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension.lowercase() in imageExtensions }
                .sortedBy { it.name.lowercase() }
                .filter { seen.add(it.name) }
                .map { it.name to it }
        }
    }

    /** Arquivo da logo selecionada, ou null se removida/não encontrada. */
    fun selectedFile(): File? {
        val name = selected ?: return null
        return available().firstOrNull { it.first == name }?.second
    }

    companion object {
        private const val KEY = "selected_logo"
        private val selectedState = mutableStateOf<String?>(null)
    }
}
