package com.phantomcode.app.data.vm

/** Preset de recursos da VM (D13 — o limite é escolha do usuário). */
data class QemuPreset(
    val id: String,
    val label: String,
    val cpu: Int,
    val ramMb: Int,
    val custom: Boolean = false,
) {
    /** Tamanho real usado no QEMU (para CUSTOM, o valor do usuário). */
    fun effective(cores: Int, memoryMb: Int) = if (custom) copy(cpu = cores, ramMb = memoryMb) else this
}

object QemuPresets {
    val ECO = QemuPreset("eco", "Econômico", 2, 1024)
    val BALANCED = QemuPreset("balanced", "Equilibrado", 4, 2048)
    val HIGH = QemuPreset("high", "Alto", 6, 4096)
    val MAX = QemuPreset("max", "Máximo", 8, 8192)
    val CUSTOM = QemuPreset("custom", "Custom", 4, 2048, custom = true)

    val ALL = listOf(ECO, BALANCED, HIGH, MAX, CUSTOM)

    fun byId(id: String): QemuPreset = ALL.firstOrNull { it.id == id } ?: BALANCED
}

/**
 * Espelhos oficiais do projeto (D16 — Phantom) e binário QEMU.
 *
 * Artefatos publicados nas Releases oficiais com validação SHA-256.
 * Estrutura esperada do artefato de distro (tarball):
 *   linux/<id>/{ rootfs.img | kernel | initrd.img | rootfs/ } — ver DistroManager.
 */
object PhantomMirror {
    // Binário QEMU arm64 (estático/musl) publicado na Release `qemu-aarch64`
    // pelo workflow `Build QEMU Binary`. É o FALLBACK para distros de terceiros:
    // a distro oficial Phantom já traz o qemu-system-aarch64 DENTRO do pacote
    // (QemuManager.binary() prioriza o da distro) — nada vem embutido no APK.
    const val QEMU_BINARY_URL = "https://github.com/VSFLima/phantom-code/releases/download/qemu-aarch64/qemu-system-aarch64"
    const val QEMU_BINARY_SHA256 = "6bf5d271e3392aad412e60ec8646a15bb7ecca61e2e3ac57bc0c770436a67da1"

    // Artefato real: construído pelo workflow `Build Distro Artifacts`
    // (.github/workflows/build-distros.yml) → Release `distro-phantom`.
    // O app baixa internamente (Toolbox → Distros) com progresso + SHA-256.
    const val PHANTOM_URL = "https://github.com/VSFLima/phantom-code/releases/download/distro-phantom/phantom.tar.gz"
    const val PHANTOM_SHA256 = "ab08d745b5dfa7b3250179f0f5d5cd4c5df44eb62814f2503141caf9b46b92e3"

}
