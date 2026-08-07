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
    }

    fun disconnect() {
        onMainDisconnected()
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
        send("SCAN\n")
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

    private fun send(cmd: String) {
        runCatching {
            socket?.outputStream?.apply {
                write(cmd.toByteArray(Charsets.UTF_8))
                flush()
            }
        }
    }

    private fun onLine(line: String) {
        when {
            inScan -> {
                if (line == "PHANTOM-SCAN-END") {
                    inScan = false
                    awaitingResponse = false
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
            line == "OK" || line == "ERR" -> awaitingResponse = false
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
