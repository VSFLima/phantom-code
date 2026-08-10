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
 * Artefatos publicados nas Releases com validação SHA-256. O repo de código é
 * privado; os downloads públicos apontam para o MIRROR `VSFLima/phantom-releases`.
 * Estrutura esperada do artefato de distro (tarball):
 *   linux/<id>/{ rootfs.img | kernel | initrd.img | rootfs/ } — ver DistroManager.
 */
object PhantomMirror {
    // Binário QEMU arm64 (estático/musl) publicado na Release `qemu-aarch64`
    // pelo workflow `Build QEMU Binary`. É o FALLBACK para distros de terceiros:
    // a distro oficial Phantom já traz o qemu-system-aarch64 DENTRO do pacote
    // (QemuManager.binary() prioriza o da distro) — nada vem embutido no APK.
    const val QEMU_BINARY_URL = "https://github.com/VSFLima/phantom-code/releases/download/qemu-aarch64/qemu-system-aarch64"
    const val QEMU_BINARY_SHA256 = "2a3f85d622d250c24a9028484ad65fb700faecf8e253c3ab63947f59a7859c49"

    // Artefato real: construído pelo workflow `Build Distro Artifacts`
    // (.github/workflows/build-distros.yml) e publicado no MIRROR público
    // `VSFLima/phantom-releases` (o repo de código VSFLima/phantom-code é
    // privado — download público só funciona no mirror). O app baixa
    // internamente (Toolbox → Distros) com progresso + SHA-256.
    const val PHANTOM_URL = "https://github.com/VSFLima/phantom-releases/releases/download/distro-phantom/phantom.tar.gz"
    // SHA-256 do tarball da Release `distro-phantom` (copiado do log do workflow:
    // "sha256sum phantom.tar.gz" no passo "Package artifact"). Atualize após cada
    // rebuild — qualquer rebuild gera um tarball novo com hash diferente.
    const val PHANTOM_SHA256 = "79d591f67913a33edd22abfa0ac0ff9bf37c053b427d425f155767fb60304d74"

    // Distros extras (T29): publicadas pelo workflow `Build Extra Distros`.
    // SHA-256 preenchido APÓS o build real (o app valida o download contra ele;
    // vazio = sem checagem até o valor real ser copiado do log do workflow).
    const val UBUNTU_URL = "https://github.com/VSFLima/phantom-code/releases/download/distro-ubuntu/ubuntu.tar.gz"
    const val UBUNTU_SHA256 = ""
    const val DEBIAN_URL = "https://github.com/VSFLima/phantom-code/releases/download/distro-debian/debian.tar.gz"
    const val DEBIAN_SHA256 = ""
    const val KALI_URL = "https://github.com/VSFLima/phantom-code/releases/download/distro-kali/kali.tar.gz"
    const val KALI_SHA256 = ""
    const val ALPINE_URL = "https://github.com/VSFLima/phantom-code/releases/download/distro-alpine/alpine.tar.gz"
    const val ALPINE_SHA256 = ""

}
