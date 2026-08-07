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
