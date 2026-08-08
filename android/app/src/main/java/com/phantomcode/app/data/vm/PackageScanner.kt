package com.phantomcode.app.data.vm

import android.net.LocalSocket
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/** Categorias do scanner do guest (T20). */
enum class PackageCategory(val id: String, val label: String) {
    IA("IA", "IAs"),
    LANG("LANG", "Linguagens / Runtimes"),
    TOOL("TOOL", "Ferramentas"),
    SYS("SYS", "Sistema"),
}

data class GuestPackage(
    val category: PackageCategory,
    val name: String,
    val version: String,
    val running: Boolean,
    val sizeKb: Long?,
)

/**
 * Scanner de pacotes do guest (T20). Fala com o `phantom-agent.sh` do guest
 * pela 2ª porta do virtio-serial (socket local `ctrl.sock`):
 *
 *   SCAN → `PHANTOM-SCAN-BEGIN` + linhas `S:<cat>|<nome>|<versão>|<running>|<sizeKB>` + `END`
 *   RUN:<cmd> → executa no guest (ex.: apt remove) e responde `OK`
 *
 * O protocolo é serial: nunca enviamos um comando novo antes de a resposta
 * anterior terminar (`scanning`/`busy` como trava).
 */
class PackageScanner {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var socket: LocalSocket? = null
    private var reader: BufferedReader? = null
    private var readJob: Job? = null
    private var refreshJob: Job? = null

    private var inScan = false
    private val scanBuffer = StringBuilder()
    private var awaitingResponse = false
    private var scanTimeoutJob: Job? = null

    // Fila de comandos do canal de controle (SERVER/STOPSERVER/SERVERSTATUS).
    // O protocolo é serial: cada comando espera UMA linha de resposta
    // (OK / ERR:… / 0 / 1) antes do próximo.
    private class Op(val cmd: String, val cb: (String) -> Unit)
    private val opQueue = ArrayDeque<Op>()
    private var opBusy = false

    val packages = mutableStateListOf<GuestPackage>()
    var scanning by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var lastError by mutableStateOf<String?>(null)

    /** Conecta ao socket de controle do guest e inicia a escuta + auto-scan. */
    fun attach(s: LocalSocket) {
        disconnect()
        socket = s
        connected = true
        reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
        readJob = scope.launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    onLine(line)
                }
            } catch (_: Exception) {
            }
            onMainDisconnected()
        }
        startRefreshLoop()
        requestScan()
    }

    private fun onMainDisconnected() {
        connected = false
        refreshJob?.cancel()
        refreshJob = null
        scanning = false
        awaitingResponse = false
        inScan = false
        opBusy = false
        opQueue.clear()
    }

    fun disconnect() {
        onMainDisconnected()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        reader = null
    }

    /** Força um novo scan do guest (pull-to-refresh / botão). */
    fun refresh() {
        requestScan()
    }

    fun requestScan() {
        if (!connected || scanning || awaitingResponse) return
        scanning = true
        awaitingResponse = true
        lastError = null
        send("SCAN\n")
        // Se o agente do guest não responder (ainda bootando / ausente), solta a
        // trava depois de um tempo para a UI não ficar "Escaneando…" para sempre.
        scanTimeoutJob = scope.launch {
            delay(8_000)
            if (inScan || awaitingResponse) {
                inScan = false
                scanBuffer.clear()
                awaitingResponse = false
                scanning = false
                lastError = "Agente do guest sem resposta"
            }
        }
    }

    /** Executa um comando no guest (ex.: apt remove -y git). */
    fun run(cmd: String) {
        if (!connected || awaitingResponse) return
        awaitingResponse = true
        send("RUN:$cmd\n")
        // Re-scan curto depois p/ refletir a mudança (ex.: desinstalar).
        scope.launch {
            delay(1500)
            awaitingResponse = false
            requestScan()
        }
    }

    /**
     * Sobe o servidor web do Preview Hub na VM (D24): PHP/Python/Node servindo
     * o projeto do workspace. A porta 80 do guest é exposta no app em
     * http://127.0.0.1:8384 (hostfwd do QEMU). Resposta via callback.
     */
    fun startServer(projectDir: String, lang: String, onResult: (String) -> Unit) {
        sendOp("SERVER:$projectDir|$lang\n", onResult)
    }

    /** Derruba o servidor web do guest. */
    fun stopServer(onDone: () -> Unit = {}) {
        sendOp("STOPSERVER\n") { onDone() }
    }

    /** Verifica se há servidor escutando na porta 80 do guest. */
    fun serverStatus(onResult: (Boolean) -> Unit) {
        sendOp("SERVERSTATUS\n") { line -> onResult(line.trim() == "1") }
    }

    private fun sendOp(cmd: String, cb: (String) -> Unit) {
        if (!connected) {
            cb("ERR")
            return
        }
        opQueue.addLast(Op(cmd, cb))
        if (!opBusy) {
            opBusy = true
            send(cmd)
        }
    }

    private fun send(cmd: String) {
        runCatching {
            socket?.outputStream?.apply {
                write(cmd.toByteArray(Charsets.UTF_8))
                flush()
            }
        }
    }

    private fun onLine(line: String) {
        val trimmed = line.trim()
        // Resposta de um comando do canal de controle (OK / ERR:… / 0 / 1).
        if (opQueue.isNotEmpty() && (trimmed == "OK" || trimmed.startsWith("ERR") || trimmed == "0" || trimmed == "1")) {
            val op = opQueue.removeFirst()
            opBusy = opQueue.isNotEmpty()
            if (opBusy) send(opQueue.first().cmd)
            op.cb(trimmed)
            return
        }
        when {
            inScan -> {
                if (line == "PHANTOM-SCAN-END") {
                    inScan = false
                    awaitingResponse = false
                    scanTimeoutJob?.cancel()
                    parseScan(scanBuffer.toString())
                    scanBuffer.clear()
                    scanning = false
                } else {
                    scanBuffer.append(line).append('\n')
                }
            }
            line == "PHANTOM-SCAN-BEGIN" -> {
                inScan = true
                scanBuffer.clear()
            }
            line == "OK" || line == "ERR" -> {
                awaitingResponse = false
                scanTimeoutJob?.cancel()
            }
        }
    }

    private fun parseScan(raw: String) {
        val list = mutableListOf<GuestPackage>()
        raw.lineSequence().forEach { line ->
            if (!line.startsWith("S:")) return@forEach
            val parts = line.split("|")
            if (parts.size < 4) return@forEach
            val cat = PackageCategory.entries.firstOrNull { it.id == parts[0].removePrefix("S:") } ?: return@forEach
            list += GuestPackage(
                category = cat,
                name = parts[1],
                version = parts[2],
                running = parts[3] == "1",
                sizeKb = parts.getOrNull(4)?.toLongOrNull(),
            )
        }
        packages.clear()
        packages.addAll(list)
    }

    /** Re-scan periódico — pega instalações via apt/pip sem ação do usuário. */
    private fun startRefreshLoop() {
        refreshJob = scope.launch {
            while (isActive && connected) {
                delay(20_000)
                requestScan()
            }
        }
    }
}
