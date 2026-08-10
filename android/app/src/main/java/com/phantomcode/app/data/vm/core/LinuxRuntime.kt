package com.phantomcode.app.data.vm.core

import kotlinx.coroutines.flow.StateFlow

/** Contrato do runtime. A UI conhece apenas este contrato, nunca Process/QEMU. */
interface LinuxRuntime {
    val state: StateFlow<LinuxRuntimeState>

    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun sendInput(input: String): Result<Unit>
    fun observeEvents(listener: (LinuxRuntimeEvent) -> Unit): AutoCloseable
}
