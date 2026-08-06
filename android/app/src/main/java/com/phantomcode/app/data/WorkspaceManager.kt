package com.phantomcode.app.data

import android.content.Context
import java.io.File

/** Entrada do workspace (arquivo ou pasta). */
data class FileEntry(
    val name: String,
    val relPath: String,
    val isDir: Boolean,
    val sizeBytes: Long,
)

/**
 * Gerencia a pasta de projetos do app: `filesDir/workspace` (D3 — workspace
 * independente da distro). Mesma pasta que o guest Linux verá via virtio-9p (Fase 3).
 */
class WorkspaceManager(context: Context) {

    val root: File = File(context.filesDir, "workspace").apply { mkdirs() }

    /** Resolve um caminho relativo, bloqueando saída do workspace (path traversal). */
    fun resolve(relPath: String): File {
        val f = File(root, relPath)
        val rootPath = root.canonicalPath
        val fPath = f.canonicalPath
        if (fPath != rootPath && !fPath.startsWith(rootPath + File.separator)) {
            throw IllegalArgumentException("Caminho fora do workspace: $relPath")
        }
        return f
    }

    /** Lista o conteúdo de uma pasta (pastas primeiro, ordenadas). */
    fun list(relPath: String = ""): List<FileEntry> {
        val dir = resolve(relPath)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.mapNotNull { f ->
                val name = f.name
                if (name == ".gitkeep") return@mapNotNull null
                FileEntry(
                    name = name,
                    relPath = if (relPath.isEmpty()) name else "$relPath/$name",
                    isDir = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                )
            }
            ?.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
            ?: emptyList()
    }

    /** Projetos = pastas de primeiro nível do workspace. */
    fun projects(): List<String> =
        root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun createDir(relPath: String): Boolean = resolve(relPath).mkdirs()

    fun createFile(relPath: String, content: String = ""): Boolean {
        val f = resolve(relPath)
        f.parentFile?.mkdirs()
        val ok = f.createNewFile()
        if (ok && content.isNotEmpty()) f.writeText(content)
        return ok
    }

    fun rename(oldRelPath: String, newName: String): Boolean {
        val f = resolve(oldRelPath)
        if (newName.isBlank()) return false
        return f.renameTo(File(f.parentFile, newName.trim()))
    }

    fun delete(relPath: String): Boolean = resolve(relPath).deleteRecursively()

    fun readText(relPath: String): String =
        runCatching { resolve(relPath).readText() }.getOrDefault("")

    fun writeText(relPath: String, content: String) {
        resolve(relPath).writeText(content)
    }
}

/** Formata tamanho de arquivo (B / KB / MB). */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
