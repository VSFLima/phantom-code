package com.phantomcode.app.data.ai

import android.content.Context
import com.phantomcode.app.data.WorkspaceManager
import com.phantomcode.app.data.vm.GuestPackage
import com.phantomcode.app.data.vm.PackageCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Perfil de uma IA registrada (local ou cloud). */
data class AiAgent(
    val id: String,
    val name: String,
    val type: String, // "local" | "cloud"
    val invoke: String, // ex.: "ollama run codellama" / "claude -p"
    val status: String, // "online" | "offline"
    val skills: Map<String, Int>, // code/review/docs/shell/test/architecture
    val keyRef: String?, // ex.: "$ANTHROPIC_API_KEY" — NUNCA o valor (SecretsManager é a fonte)
)

/** Tarefa do bus (Fase B) — ciclo de vida em `docs/roteador-ias.md` §8. */
data class AiTask(
    val id: String,
    val title: String,
    val status: String, // proposed | pending_approval | approved | in_progress | done | rejected | cancelled
    val owner: String, // agente dono ou "dono" (o usuário)
    val scopeFiles: List<String>, // arquivos que pode escrever (R3)
    val scopeRead: List<String>, // arquivos de leitura
    val createdAtEpoch: Long,
    val due: String,
)

/** Mensagem de uma thread de tarefa — append-only (R5). */
data class AiThreadMessage(
    val ts: Long,
    val from: String,
    val type: String, // system | msg | done | delegation | approval
    val text: String,
    val refs: List<String> = emptyList(),
)

/** Proposta de delegação entre IAs — passa pelo Human Approval Gate (R4). */
data class AiProposal(
    val id: String, // = id da tarefa
    val from: String,
    val to: String,
    val subtask: String,
    val scopeWrite: List<String>,
    val scopeRead: List<String>,
    val reason: String,
    val due: String,
    val status: String, // pending_approval | approved | rejected
)

/**
 * AI Suite Manager — Fase A do Phantom AI Suite (`docs/roteador-ias.md`).
 *
 * - Agent Registry: agentes manuais persistidos em `agents.json` + auto-registro
 *   a partir do scan do guest (pacotes de IA detectados pelo phantom-agent.sh).
 * - Conflict Guard: autoridade de reservas de arquivos (locks W/R/S/D/G).
 * - Kill switch: `pauseAll()` libera locks e congela a suite (o dono decide sempre).
 * - O daemon `phantom-router.sh` (guest) é o par deste manager no Linux.
 */
class AiSuiteManager(context: Context) {

    private val workspace = WorkspaceManager(context)
    private val stateDir = File(workspace.root, ".phantom/ai-suite")
    private val agentsFile = File(stateDir, "agents.json")

    /** Guard de reservas — autoridade de locks no app. */
    val guard = ConflictGuard(stateDir)

    @Volatile
    var paused: Boolean = false
        private set

    init {
        stateDir.mkdirs()
    }

    // ── Agent Registry ────────────────────────────────────────────────────

    fun listManualAgents(): List<AiAgent> {
        if (!agentsFile.exists()) return emptyList()
        val agents = mutableListOf<AiAgent>()
        runCatching {
            val arr = JSONArray(agentsFile.readText())
            for (i in 0 until arr.length()) {
                agents.add(agentFromJson(arr.getJSONObject(i)))
            }
        }
        return agents
    }

    fun registerAgent(
        name: String,
        invoke: String,
        type: String,
        skills: Map<String, Int> = defaultSkills(),
    ): AiAgent {
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "agent-${System.currentTimeMillis()}" }
        val agent = AiAgent(
            id = id,
            name = name,
            type = if (type == "cloud") "cloud" else "local",
            invoke = invoke.trim(),
            status = "online",
            skills = skills,
            keyRef = null,
        )
        val all = listManualAgents().toMutableList()
        all.removeAll { it.id == id }
        all.add(agent)
        saveAgents(all)
        return agent
    }

    fun removeAgent(id: String) {
        val all = listManualAgents().toMutableList()
        all.removeAll { it.id == id }
        saveAgents(all)
    }

    /** Auto-registro: IAs detectadas no guest (scan do phantom-agent.sh). */
    fun agentsFromScan(guestPackages: List<GuestPackage>): List<AiAgent> =
        guestPackages
            .filter { it.category == PackageCategory.IA }
            .map { pkg ->
                AiAgent(
                    id = "scan-" + pkg.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
                    name = pkg.name,
                    type = "local",
                    invoke = pkg.name,
                    status = if (pkg.running) "online" else "offline",
                    skills = defaultSkills(),
                    keyRef = null,
                )
            }

    fun allAgents(guestPackages: List<GuestPackage>): List<AiAgent> {
        val manual = listManualAgents().toMutableList()
        val manualIds = manual.map { it.id }.toSet()
        manual.addAll(agentsFromScan(guestPackages).filter { it.id !in manualIds })
        return manual
    }

    // ── Kill switch ───────────────────────────────────────────────────────

    fun pauseAll() {
        paused = true
        guard.pauseAll()
    }

    fun resume() {
        paused = false
    }

    // ── Fase B — tarefas, threads e delegação com aprovação humana ────────

    fun tasksDir(): File = File(stateDir, "tasks")

    fun taskFile(taskId: String): File = File(tasksDir(), taskId)

    private fun contextFile(taskId: String): File = File(taskFile(taskId), "context.json")

    private fun messagesFile(taskId: String): File = File(taskFile(taskId), "messages.jsonl")

    private fun proposalFile(taskId: String): File = File(taskFile(taskId), "proposal.json")

    fun listTasks(): List<AiTask> {
        val dir = tasksDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { taskFromJson(it.name, File(it, "context.json")) }
            ?.sortedByDescending { it.createdAtEpoch }
            ?: emptyList()
    }

    fun taskMessages(taskId: String): List<AiThreadMessage> {
        val f = messagesFile(taskId)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                runCatching {
                    val o = JSONObject(line)
                    AiThreadMessage(
                        ts = o.optLong("ts"),
                        from = o.optString("from", "?"),
                        type = o.optString("type", "msg"),
                        text = o.optString("text", ""),
                        refs = buildList {
                            o.optJSONArray("refs")?.let { arr ->
                                for (i in 0 until arr.length()) add(arr.optString(i))
                            }
                        },
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** Propostas de delegação aguardando aprovação do dono (R4). */
    fun pendingProposals(): List<AiProposal> = listProposals().filter { it.status == "pending_approval" }

    fun listProposals(): List<AiProposal> {
        val dir = tasksDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, "proposal.json").exists() }
            ?.mapNotNull { proposalFromJson(it.name, File(it, "proposal.json")) }
            ?.sortedByDescending { it.id }
            ?: emptyList()
    }

    /**
     * Cria uma tarefa no bus. [owner] recebe locks de escrita do escopo (R1)
     * se não houver conflito — sem isso ela só pode ler.
     */
    fun createTask(
        title: String,
        owner: String,
        scopeFiles: List<String>,
        scopeRead: List<String> = emptyList(),
        due: String = "",
        status: String = "in_progress",
    ): AiTask {
        val id = newTaskId()
        val task = AiTask(
            id = id,
            title = title,
            status = status,
            owner = owner,
            scopeFiles = scopeFiles,
            scopeRead = scopeRead,
            createdAtEpoch = System.currentTimeMillis(),
            due = due,
        )
        val granted = buildList {
            scopeFiles.forEach { add(guard.request(it, 'W', owner, id)) }
            scopeRead.forEach { add(guard.request(it, 'R', owner, id)) }
        }
        val ready = granted.all { it.granted }
        if (!ready) guard.releaseTask(owner, id)
        val saved = task.copy(status = if (ready) status else "blocked")
        saveTask(saved)
        appendMessage(id, "router", "system", "Tarefa $id ${if (ready) "criada" else "bloqueada por conflito de escopo"} · dono: $owner")
        return saved
    }

    /**
     * Proposta de delegação (R4) — uma IA pede que outra execute uma subtarefa.
     * Fica em `pending_approval` até o dono Aprovar/Ajustar/Recusar.
     */
    fun proposeDelegation(
        from: String,
        to: String,
        subtask: String,
        scopeWrite: List<String>,
        scopeRead: List<String> = emptyList(),
        reason: String = "",
        due: String = "",
    ): AiProposal {
        val id = newTaskId()
        val proposal = AiProposal(
            id = id,
            from = from,
            to = to,
            subtask = subtask,
            scopeWrite = scopeWrite,
            scopeRead = scopeRead,
            reason = reason,
            due = due,
            status = "pending_approval",
        )
        saveProposal(proposal)
        saveTask(
            AiTask(
                id = id,
                title = subtask,
                status = "pending_approval",
                owner = to,
                scopeFiles = scopeWrite,
                scopeRead = scopeRead,
                createdAtEpoch = System.currentTimeMillis(),
                due = due,
            ),
        )
        appendMessage(id, "router", "system", "Delegação de $from → $to aguarda o dono")
        appendMessage(id, from, "msg", subtask)
        return proposal
    }

    /** R4 — dono aprova: concede locks de escrita ao destino e inicia a tarefa. */
    fun approveProposal(id: String): Boolean {
        val p = listProposals().firstOrNull { it.id == id } ?: return false
        val granted = buildList {
            p.scopeWrite.forEach { add(guard.request(it, 'W', p.to, id)) }
            p.scopeRead.forEach { add(guard.request(it, 'R', p.to, id)) }
        }
        if (granted.any { !it.granted }) {
            guard.releaseTask(p.to, id)
            appendMessage(id, "router", "approval", "Delegação não aprovada: conflito de escopo")
            return false
        }
        saveProposal(p.copy(status = "approved"))
        saveTask(taskFromJson(id, contextFile(id))?.copy(status = "approved", owner = p.to) ?: return false)
        appendMessage(id, "dono", "approval", "✅ Delegação aprovada — $p.to pode executar")
        return true
    }

    /** R4 — dono recusa (ou timeout expirou). */
    fun rejectProposal(id: String): Boolean {
        val p = listProposals().firstOrNull { it.id == id } ?: return false
        saveProposal(p.copy(status = "rejected"))
        saveTask(taskFromJson(id, contextFile(id))?.copy(status = "rejected") ?: return false)
        appendMessage(id, "dono", "approval", "❌ Delegação recusada")
        return true
    }

    /** R4 — dono ajusta o escopo; a proposta volta para `pending_approval` (timeout reinicia). */
    fun adjustProposal(id: String, scopeWrite: List<String>, scopeRead: List<String>): Boolean {
        val p = listProposals().firstOrNull { it.id == id } ?: return false
        saveProposal(p.copy(scopeWrite = scopeWrite, scopeRead = scopeRead, status = "pending_approval"))
        saveTask(taskFromJson(id, contextFile(id))?.copy(scopeFiles = scopeWrite, scopeRead = scopeRead, status = "pending_approval") ?: return false)
        appendMessage(id, "dono", "approval", "✏️ Escopo ajustado pelo dono: escreve ${scopeWrite.joinToString(", ") { it }.ifBlank { "—" }} · lê ${scopeRead.joinToString(", ") { it }.ifBlank { "—" }}")
        return true
    }

    /** R5 — o dono intervém na thread (prioridade máxima; pausa a tarefa). */
    fun sendOwnerMessage(taskId: String, text: String) {
        if (text.isBlank()) return
        appendMessage(taskId, "dono", "msg", text.trim())
    }

    fun finishTask(taskId: String, summary: String = "") {
        saveTask(taskFromJson(taskId, contextFile(taskId))?.copy(status = "done") ?: return)
        appendMessage(taskId, "router", "done", summary.ifBlank { "Tarefa $taskId concluída" })
        taskFromJson(taskId, contextFile(taskId))?.owner?.let { guard.releaseTask(it, taskId) }
    }

    // ── Fase B — persistência ──────────────────────────────────────────────

    private var idCounter = 0L

    private fun newTaskId(): String {
        idCounter = (idCounter + 1) % 1000000
        return "t-" + (System.nanoTime() % 1000000 + idCounter) % 1000000
    }

    private fun saveTask(task: AiTask) {
        runCatching {
            val f = contextFile(task.id)
            f.parentFile?.mkdirs()
            f.writeText(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("status", task.status)
                    .put("owner", task.owner)
                    .put("scope_files", JSONArray(task.scopeFiles))
                    .put("scope_read", JSONArray(task.scopeRead))
                    .put("created_at", task.createdAtEpoch)
                    .put("due", task.due)
                    .toString(2),
            )
        }
    }

    private fun taskFromJson(id: String, f: File): AiTask? {
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            AiTask(
                id = id,
                title = o.optString("title", id),
                status = o.optString("status", "proposed"),
                owner = o.optString("owner", "?"),
                scopeFiles = jsonToStringList(o.optJSONArray("scope_files")),
                scopeRead = jsonToStringList(o.optJSONArray("scope_read")),
                createdAtEpoch = o.optLong("created_at", 0),
                due = o.optString("due", ""),
            )
        }.getOrNull()
    }

    private fun saveProposal(p: AiProposal) {
        runCatching {
            val f = proposalFile(p.id)
            f.parentFile?.mkdirs()
            f.writeText(
                JSONObject()
                    .put("id", p.id)
                    .put("from", p.from)
                    .put("to", p.to)
                    .put("subtask", p.subtask)
                    .put("scope_write", JSONArray(p.scopeWrite))
                    .put("scope_read", JSONArray(p.scopeRead))
                    .put("reason", p.reason)
                    .put("due", p.due)
                    .put("status", p.status)
                    .toString(2),
            )
        }
    }

    private fun proposalFromJson(id: String, f: File): AiProposal? {
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            AiProposal(
                id = id,
                from = o.optString("from", "?"),
                to = o.optString("to", "?"),
                subtask = o.optString("subtask", ""),
                scopeWrite = jsonToStringList(o.optJSONArray("scope_write")),
                scopeRead = jsonToStringList(o.optJSONArray("scope_read")),
                reason = o.optString("reason", ""),
                due = o.optString("due", ""),
                status = o.optString("status", "pending_approval"),
            )
        }.getOrNull()
    }

    private fun jsonToStringList(arr: JSONArray?): List<String> =
        buildList { arr?.let { for (i in 0 until it.length()) add(it.optString(i)) } }

    /** R5 — append-only: a linha é adicionada ao final, nunca sobrescreve. */
    private fun appendMessage(taskId: String, from: String, type: String, text: String, refs: List<String> = emptyList()) {
        runCatching {
            val f = messagesFile(taskId)
            f.parentFile?.mkdirs()
            val line = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("from", from)
                .put("type", type)
                .put("text", text)
                .put("refs", JSONArray(refs))
                .toString()
            f.appendText(line + "\n")
        }
    }

    // ── Persistência ──────────────────────────────────────────────────────

    private fun saveAgents(agents: List<AiAgent>) {
        runCatching {
            stateDir.mkdirs()
            val arr = JSONArray()
            agents.forEach { a ->
                arr.put(
                    JSONObject()
                        .put("id", a.id)
                        .put("name", a.name)
                        .put("type", a.type)
                        .put("invoke", a.invoke)
                        .put("status", a.status)
                        .put("skills", JSONObject(a.skills)),
                )
            }
            agentsFile.writeText(arr.toString(2))
        }
    }

    private fun agentFromJson(o: JSONObject): AiAgent {
        val skills = mutableMapOf<String, Int>()
        o.optJSONObject("skills")?.let { sk ->
            sk.keys().forEach { skills[it] = sk.optInt(it, 0) }
        }
        return AiAgent(
            id = o.optString("id", "agent"),
            name = o.optString("name", "IA"),
            type = o.optString("type", "local"),
            invoke = o.optString("invoke", ""),
            status = o.optString("status", "offline"),
            skills = skills,
            keyRef = null,
        )
    }

    companion object {
        fun defaultSkills(): Map<String, Int> = mapOf(
            "code" to 3,
            "review" to 3,
            "docs" to 3,
            "shell" to 2,
            "test" to 3,
        )
    }
}
