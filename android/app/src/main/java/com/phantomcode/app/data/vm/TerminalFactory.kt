package com.phantomcode.app.data.vm

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * O `TermSession` do jackpal cria `new Handler()` no construtor — exige um
 * `Looper` na thread que o constrói. Construir em thread de IO (como o
 * `withContext(Dispatchers.IO)` do QemuManager) lança
 * "Can't create handler inside thread ... that has not called Looper.prepare()"
 * e a VM sobe sem console (o runCatching engolia o erro e o terminal nunca
 * anexava). Este helper executa o bloco na MAIN thread de forma síncrona,
 * seguro de qualquer thread chamadora.
 */
object TerminalFactory {

    /** Executa [block] na main thread e devolve o resultado. */
    fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return block()
        }
        val latch = CountDownLatch(1)
        val ref = AtomicReference<T>()
        Handler(Looper.getMainLooper()).post {
            try {
                ref.set(block())
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        return ref.get()
    }
}
