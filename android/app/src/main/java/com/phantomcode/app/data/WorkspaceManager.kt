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

data class TextMatch(
    val path: String,
    val line: Int,
    val preview: String,
)

/**
 * Gerencia a pasta de projetos do app: pasta pública `/storage/emulated/0/Phantom-Code/workspace`
 * quando há permissão de armazenamento, senão `filesDir/Phantom-Code/workspace` (privado, fallback).
 * D3 — workspace independente da distro; mesma pasta que o guest Linux verá via virtio-9p (Fase 3).
 */
class WorkspaceManager(context: Context) {

    private val appContext = context.applicationContext
    private var lastRoot: File? = null

    /** Raiz resolvida dinamicamente: reavalia quando a permissão muda (fix permissões). */
    val root: File
        get() {
            val current = StorageHelper.workspaceRoot(appContext)
            if (lastRoot == null || lastRoot != current) {
                lastRoot = current
                migrateLegacyWorkspace(current)
            }
            return current
        }

    /** Migração: workspace antigo (filesDir/workspace) e troca de raiz p/ a atual. */
    private fun migrateLegacyWorkspace(newRoot: File) {
        runCatching {
            val legacy = File(appContext.filesDir, "workspace")
            if (legacy.isDirectory && legacy != newRoot) {
                legacy.listFiles()?.forEach { child ->
                    val dest = File(newRoot, child.name)
                    if (!dest.exists()) child.renameTo(dest)
                }
            }
            // Também migra da pasta privada Phantom-Code p/ a pública (quando a permissão é concedida)
            val private = File(appContext.filesDir, StorageHelper.APP_DIR_NAME)
            if (private.isDirectory && private != newRoot) {
                private.listFiles()?.forEach { child ->
                    val dest = File(newRoot, child.name)
                    if (!dest.exists()) child.renameTo(dest)
                }
            }
        }
    }

    /** Caminho legível da pasta raiz (para exibir na UI). */
    val displayPath: String
        get() = if (StorageHelper.hasStorageAccess(appContext)) {
            root.absolutePath
        } else {
            "Interno (sem permissão de armazenamento)"
        }

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

    /** Busca textual limitada para não travar a UI nem ler binários grandes. */
    fun search(query: String, maxResults: Int = 200): List<TextMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val results = mutableListOf<TextMatch>()
        root.walkTopDown()
            .onEnter { it.name != ".git" && results.size < maxResults }
            .filter { it.isFile && it.length() <= MAX_SEARCH_FILE_BYTES }
            .forEach { file ->
                if (results.size >= maxResults) return@forEach
                runCatching {
                    file.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (line.contains(needle, ignoreCase = true)) {
                                results += TextMatch(
                                    path = file.relativeTo(root).invariantSeparatorsPath,
                                    line = index + 1,
                                    preview = line.trim().take(MAX_PREVIEW_LENGTH),
                                )
                            }
                            if (results.size >= maxResults) return@useLines
                        }
                    }
                }
            }
        return results
    }

    companion object {
        private const val MAX_SEARCH_FILE_BYTES = 2L * 1024 * 1024
        private const val MAX_PREVIEW_LENGTH = 180
    }
}

/** Formata tamanho de arquivo (B / KB / MB). */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
