package com.phantomcode.app.data.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Uma reserva (lock) de arquivo/área. Modos: W (escrita), R (leitura), S (soft/correlato), D (diretório), G (git/global). */
data class AiLock(
    val path: String,
    val mode: Char,
    val owner: String,
    val taskId: String,
    val untilEpochMs: Long,
    val ttlMs: Long,
) {
    val expired: Boolean get() = System.currentTimeMillis() > untilEpochMs
    fun renewed(now: Long = System.currentTimeMillis()): AiLock = copy(untilEpochMs = now + ttlMs)
}

/** Resultado de um pedido de lock. */
data class LockResult(
    val granted: Boolean,
    val reason: String = "",
    val lock: AiLock? = null,
)

/**
 * Conflict Guard (AI Suite · Fase A) — regras de ouro R1/R2/R7/R8 da spec
 * `docs/roteador-ias.md`. Autoridade de reservas fica NO APP: este arquivo é
 * dono do `locks.json` (o daemon do guest nunca escreve direto — pede via canal).
 *
 * - R1: nenhuma IA escreve sem reserva (write lock).
 * - R2: área correlata (soft lock) via correlatedPaths() — mesma pasta + referências.
 * - R7: TTL (15 min) + renovação automática por heartbeat; locks órfãos limpos no boot.
 * - R8: contexto — `canWrite()` nega quando outra IA tem lock conflitante.
 */
class ConflictGuard(private val stateDir: File) {

    private val lockFile = File(stateDir, "locks.json")
    private val locks = LinkedHashMap<String, AiLock>()

    init {
        stateDir.mkdirs()
        load()
        // R7: locks de sessões anteriores (órfãos) sempre partem do zero
        cleanup()
    }

    /** R1 — pedido de reserva. Mesmo dono renova; outro dono com conflito → DENIED. */
    @Synchronized
    fun request(
        path: String,
        mode: Char,
        owner: String,
        taskId: String = "t-manual",
        ttlMs: Long = DEFAULT_TTL_MS,
    ): LockResult {
        cleanup()
        val norm = normalize(path)

        locks[norm]?.let { existing ->
            if (existing.owner == owner && existing.mode == mode) {
                val renewed = existing.renewed()
                locks[norm] = renewed
                save()
                return LockResult(true, "renovado", renewed)
            }
        }

        val conflict = findConflict(norm, mode, owner)
        if (conflict != null) {
            return LockResult(false, "DENIED por ${conflict.owner} (${conflict.mode}) em ${conflict.path}")
        }

        val lock = AiLock(
            path = norm,
            mode = mode,
            owner = owner,
            taskId = taskId,
            untilEpochMs = System.currentTimeMillis() + ttlMs,
            ttlMs = ttlMs,
        )
        locks[norm] = lock
        save()
        return LockResult(true, "ok", lock)
    }

    /** R1/R8 — outra IA pode escrever em `path`? (leitura não conflita com R). */
    @Synchronized
    fun canWrite(path: String, owner: String): Boolean {
        cleanup()
        return findConflict(normalize(path), 'W', owner) == null
    }

    /** Libera locks do dono (todos ou um path específico). Retorna quantos liberou. */
    @Synchronized
    fun release(owner: String, path: String? = null): Int {
        val removed = locks.keys.filter { k ->
            val l = locks[k]!!
            l.owner == owner && (path == null || l.path == normalize(path))
        }
        removed.forEach { locks.remove(it) }
        if (removed.isNotEmpty()) save()
        return removed.size
    }

    /** Libera somente as reservas pertencentes a uma tarefa, sem afetar tarefas irmãs. */
    @Synchronized
    fun releaseTask(owner: String, taskId: String): Int {
        val removed = locks.keys.filter { key ->
            val lock = locks[key]!!
            lock.owner == owner && lock.taskId == taskId
        }
        removed.forEach { locks.remove(it) }
        if (removed.isNotEmpty()) save()
        return removed.size
    }

    /** R7 — heartbeat renova os locks do dono (a IA não precisa lembrar de renovar). */
    @Synchronized
    fun heartbeat(owner: String): Int {
        var renewed = 0
        locks.forEach { (k, l) ->
            if (l.owner == owner) {
                locks[k] = l.renewed()
                renewed++
            }
        }
        if (renewed > 0) save()
        return renewed
    }

    /** R7 — remove locks expirados (IA travada/crashada perde as reservas). */
    @Synchronized
    fun cleanup(): Int {
        val expired = locks.filterValues { it.expired }
        expired.keys.forEach { locks.remove(it) }
        if (expired.isNotEmpty()) save()
        return expired.size
    }

    /** Kill switch — libera tudo (o dono é quem decide, sempre). */
    @Synchronized
    fun pauseAll() {
        locks.clear()
        save()
    }

    @Synchronized
    fun snapshot(): List<AiLock> = locks.values.sortedBy { it.path }

    /** R2 — área correlata: mesma pasta + arquivos importados/referenciados. */
    fun correlatedPaths(workspaceRoot: File, path: String): Set<String> {
        val result = LinkedHashSet<String>()
        val file = File(workspaceRoot, path)
        file.parentFile?.listFiles()?.forEach { sibling ->
            if (sibling.isFile) result.add(relative(workspaceRoot, sibling))
        }
        if (file.isFile) {
            val text = runCatching { file.readText().take(64 * 1024) }.getOrNull() ?: return result
            Regex("""(?:import\s+[.\w]+|require\(['"][^'"]+|#include\s*[<"][^>"]+)""")
                .findAll(text)
                .forEach { m ->
                    val ref = m.value
                        .substringAfter("import ").substringAfter("require('").substringAfter("require(\"")
                        .substringAfter("#include ").trim().trim('"', '\'', '>', '<').trim()
                    if (ref.isNotEmpty()) {
                        val candidates = listOf(
                            File(file.parentFile, ref),
                            File(file.parentFile, ref.removePrefix("./")),
                        )
                        candidates.firstOrNull { it.exists() }?.let { result.add(relative(workspaceRoot, it)) }
                    }
                }
        }
        return result
    }

    private fun findConflict(norm: String, mode: Char, owner: String): AiLock? {
        for (l in locks.values) {
            if (l.owner == owner) continue
            if (l.expired) continue
            if (conflicts(l, norm, mode)) return l
        }
        return null
    }

    private fun conflicts(existing: AiLock, requestedPath: String, requestedMode: Char): Boolean {
        // G (git/commit) é exclusivo global
        if (existing.mode == 'G' || requestedMode == 'G') return true
        // D (diretório): bloqueia W/R/S dentro da pasta (leitura de fora é livre)
        if (existing.mode == 'D') return requestedPath.startsWith(existing.path + "/") && requestedMode != 'R'
        if (requestedMode == 'D') return existing.path.startsWith(requestedPath + "/")
        // Paths diferentes não conflitam na Fase A (R2 é aplicado por quem reserva o correlato)
        if (existing.path != requestedPath) return false
        // Mesmo path: W bloqueia tudo; R/S bloqueiam W; R permite R/S
        return when (existing.mode) {
            'W' -> true
            'R', 'S' -> requestedMode == 'W'
            else -> true
        }
    }

    private fun normalize(path: String): String =
        path.trim().replace('\\', '/').removePrefix("./").let { p -> p.trimEnd('/').ifBlank { "." } }

    private fun relative(root: File, f: File): String =
        f.relativeTo(root).path.replace(File.separatorChar, '/')

    private fun load() {
        if (!lockFile.exists()) return
        runCatching {
            val arr = JSONArray(lockFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val lock = AiLock(
                    path = o.getString("path"),
                    mode = o.getString("mode").first(),
                    owner = o.getString("owner"),
                    taskId = o.optString("task_id", "t-manual"),
                    untilEpochMs = o.getLong("until"),
                    ttlMs = o.optLong("ttl", DEFAULT_TTL_MS),
                )
                locks[lock.path] = lock
            }
        }
    }

    private fun save() {
        runCatching {
            stateDir.mkdirs()
            val arr = JSONArray()
            locks.values.forEach { l ->
                arr.put(
                    JSONObject()
                        .put("path", l.path)
                        .put("mode", l.mode.toString())
                        .put("owner", l.owner)
                        .put("task_id", l.taskId)
                        .put("until", l.untilEpochMs)
                        .put("ttl", l.ttlMs),
                )
            }
            lockFile.writeText(arr.toString(2))
        }
    }

    companion object {
        /** R7 — TTL padrão: 15 min. */
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
    }
}
