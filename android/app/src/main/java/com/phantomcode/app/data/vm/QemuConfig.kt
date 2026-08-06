package com.phantomcode.app.data.vm

/** Preset de recursos da VM (D13 — o limite é escolha do usuário). */
data class QemuPreset(
    val id: String,
    val label: String,
    val cpu: Int,
    val ramMb: Int,
)

object QemuPresets {
    val ECO = QemuPreset("eco", "Econômico", 2, 1024)
    val BALANCED = QemuPreset("balanced", "Equilibrado", 4, 2048)
    val MAX = QemuPreset("max", "Máximo (todos os cores)", 8, 4096)

    val ALL = listOf(ECO, BALANCED, MAX)
}

/**
 * Espelhos oficiais do projeto (D16 — Phantom Base) e binário QEMU.
 *
 * ⚠️ TODO: publicar os artefatos no GitHub Releases e preencher os URLs/checksums.
 * Enquanto isso, o app baixa e mostra erro claro ("artefato não publicado").
 * Estrutura esperada do artefato de distro (tarball):
 *   linux/<id>/{ rootfs.img | kernel | initrd.img | rootfs/ } — ver DistroManager.
 */
object PhantomMirror {
    // ⚠️ TODO: publicar o binário QEMU arm64 (Release `qemu-aarch64`) e preencher o checksum.
    const val QEMU_BINARY_URL = "https://example.com/phantom-code/qemu-system-aarch64"
    val QEMU_BINARY_SHA256: String? = null

    // Artefato real: construído pelo workflow `Build Distro Artifacts`
    // (.github/workflows/build-distros.yml) → Release `distro-phantom-base`.
    // O app baixa internamente (Toolbox → Distros) com progresso + SHA-256.
    const val PHANTOM_BASE_URL = "https://github.com/VSFLima/phantom-code/releases/download/distro-phantom-base/phantom-base.tar.gz"
    val PHANTOM_BASE_SHA256: String? = null

    // Candidatos upstream (a curadoria publica os builds compatíveis — aarch64 headless):
    const val UBUNTU_URL = "https://example.com/phantom-code/ubuntu-24.04-minimal.tar.gz"
    const val DEBIAN_URL = "https://example.com/phantom-code/debian-bookworm-slim.tar.gz"
    const val ALPINE_URL = "https://example.com/phantom-code/alpine-mini.tar.gz"
}
