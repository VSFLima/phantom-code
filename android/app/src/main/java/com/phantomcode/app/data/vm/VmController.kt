package com.phantomcode.app.data.vm

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.phantomcode.app.data.WorkspaceManager

/**
 * Estado global da VM (QEMU + distros + terminal), criado uma vez no PhantomRoot
 * e exposto via CompositionLocal para toda a UI reagir ao vivo.
 */
class VmController(context: Context) {
    val workspace = WorkspaceManager(context)
    val distros = DistroManager(context)
    val qemu = QemuManager(context, workspace, distros)
}

val LocalVm = staticCompositionLocalOf<VmController> {
    error("VmController não fornecido — envolva o conteúdo em PhantomRoot()")
}
