package com.phantomcode.app.data.vm.core

/** Estado único observado por todas as telas que controlam o Linux. */
sealed interface LinuxRuntimeState {
    data object NoDistro : LinuxRuntimeState
    data class Ready(val distroId: String) : LinuxRuntimeState
    data class Starting(val distroId: String) : LinuxRuntimeState
    data class Running(val distroId: String) : LinuxRuntimeState
    data class Stopping(val distroId: String) : LinuxRuntimeState
    data class Error(val distroId: String?, val message: String, val recoverable: Boolean = true) : LinuxRuntimeState
}

/** Eventos de saída do runtime, separados da representação visual do terminal. */
sealed interface LinuxRuntimeEvent {
    data class Output(val text: String) : LinuxRuntimeEvent
    data class Exit(val code: Int, val message: String? = null) : LinuxRuntimeEvent
    data class Failure(val message: String, val cause: Throwable? = null) : LinuxRuntimeEvent
}
