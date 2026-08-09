# 🐧 TAREFAS — DISTRO REAL FUNCIONAL + INSTALAÇÃO POR ARQUIVOS LOCAIS

> **Data:** 09/08/2026
> **Objetivo do usuário:** "Quero uma distro real e **funcional** no meu app" + "opção de **selecionar arquivos já baixados** (ex.: no Termux/Downloads) em vez de baixar".
> **Origem:** revisão de campo da instalação/boot da distro Phantom (SHA-256 ✅, tarball ✅, QEMU roda ✅, boot monta rootfs ✅).

## 📍 Status (09/08 — implementação concluída, aguardando build)

> **Regra:** as tasks só serão **marcadas como concluídas** depois que o build do CI
> passar e a nova distro for validada (boot real com rede + 9p). Decisão do usuário:
> "implementar e só fechar após o build".

| Task | Implementado | Build do CI | Distro publicada | Boot validado |
|------|:---:|:---:|:---:|:---:|
| T-D1 (pc-bios embutido) | ✅ | ⏳ | ⏳ | ⏳ |
| T-D2 (init system) | ✅ | ⏳ | ⏳ | ⏳ |
| T-D3 (rede no rootfs) | ✅ | ⏳ | ⏳ | ⏳ |
| T-D4 (bootstrap 9p do guest) | ✅ | ⏳ | ⏳ | ⏳ |
| T-D5 (`format=raw`) | ✅ | ⏳ | ⏳ | ⏳ |
| T-S1 (arquivos avulsos) | ✅ | ⏳ | — | ⏳ |
| T-S2 (tarball local) | ✅ | ⏳ | — | ⏳ |
| T-S3 (UI "Selecionar arquivos…") | ✅ | ⏳ | — | ⏳ |
| T-S4 (validação de integridade) | ✅ | ⏳ | — | ⏳ |
| T-P1 (rebuild + SHA) | — | ⏳ | ⏳ | — |
| T-P2 (APK + teste device) | — | ⏳ | ⏳ | ⏳ |

### ⚠️ Descoberta crítica durante a implementação — rede SLIRP ausente
O build do QEMU 9.1.0 **não tinha libslirp** → o binário recusava `-netdev user`
(`network backend 'user' is not compiled into this binary`) e **a VM nem subia com o
comando completo do app**. O meson do QEMU resolve slirp só via pkg-config (sem
submódulo/fallback). **Correção adicionada** ao workflow: cross-build do `libslirp`
estático para aarch64 (passo após o glib) + `--enable-slirp` no configure do QEMU.
Sem isso, T-D3 (rede) e o próprio boot seriam impossíveis.

---

## 🔎 Contexto da revisão (achados)

O fluxo de instalação do app (DistroManager → QemuManager) e o artefato da distro
(`build-distros.yml` → Release `distro-phantom`) foram **validados ponta a ponta** em um
QEMU arm64 real. Resultados:

| Item | Status |
|------|--------|
| SHA-256 do `phantom.tar.gz` bate com `PhantomMirror.PHANTOM_SHA256` | ✅ |
| Tarball contém `rootfs.img` + `kernel` + `initrd.img` + `qemu-system-aarch64` no topo, qemu com `+x` | ✅ |
| Kernel arm64 / initrd gzip / rootfs ext2 / qemu aarch64 **estático** válidos | ✅ |
| `qemu-system-aarch64 --version` → 9.1.0 | ✅ |
| Boot: kernel → initramfs → `EXT4-fs (vda): mounted` | ✅ |
| Boot: `run-init: /sbin/init: No such file` → cai em `/bin/sh` cru | ❌ **BUG** |
| `qemu-system-aarch64: failed to find romfile "efi-virtio.rom"` (sem pc-bios) | ❌ **BUG** |

### Bugs encontrados
1. **`efi-virtio.rom` ausente** — o workflow compila o QEMU (`make -j2` + `cp`), mas **não instala o `pc-bios`** (ROMs). O QEMU procura a ROM no **cwd** do processo → no Android (cwd=`/`) **o QEMU nem sobe**.
2. **Rootfs sem init system** — não há `/sbin/init` (container Debian slim não traz sysvinit/systemd) → boot cai em `/bin/sh` sem serviços.
3. **Rootfs sem rede** — não há `ip`, `udhcpc` nem `dhclient` → o `dark-code-init.sh` não sobe a rede do guest.
4. **`dark-code-init.sh` fora do guest** — o app copia o script para o **host** (`filesDir/linux/phantom/`), a imagem é imutável → o guest nunca o executa.
5. Menor: `-drive` sem `format=raw` no `QemuManager` (restringe escrita no bloco 0 → `resize2fs` pode falhar).

---

## 🟥 FASE 1 — DISTRO REAL FUNCIONAL (corrigir boot)

> Toca: `.github/workflows/build-distros.yml` + `QemuManager.kt` + `DistroManager.kt`.

### T-D1. Embutir `pc-bios` no pacote da distro (fix `efi-virtio.rom`)
- **No workflow** (`build-distros.yml`): após `make -j2`, instalar as ROMs do QEMU no pacote:
  ```yaml
  sudo make install DESTDIR=/tmp/inst      # ou copiar pc-bios/
  cp -r /tmp/inst/usr/share/qemu /tmp/pc-bios
  tar czf phantom.tar.gz rootfs.img kernel initrd.img qemu-system-aarch64 pc-bios
  ```
  (garantir que `efi-virtio.rom` + demais ROMs entrem no tarball)
- **No app** (`QemuManager.kt` `start()`): definir o **cwd** do `ProcessBuilder` como o diretório da distro (onde o qemu + `pc-bios/` ficam):
  ```kotlin
  val pb = ProcessBuilder(cmd).redirectErrorStream(true)
  distros.activeId?.let { pb.directory(distros.dirFor(it)) }
  ```
- **Critério de aceite:** QEMU inicia sem `failed to find romfile`; boot chega ao init.

### T-D2. Init system no rootfs
- **No workflow** (`apt-get install`): adicionar `sysvinit-core` (leve e previsível — sem systemd em VM TCG).
  - Garante `/sbin/init` no rootfs.
- **Critério de aceite:** `run-init` executa o init do rootfs (sem cair em `/bin/sh` cru).

### T-D3. Rede no rootfs
- **No workflow** (`apt-get install`): adicionar `iproute2` **e** `isc-dhcp-client` (fornece `dhclient`; o `dark-code-init.sh` já tenta `udhcpc || dhclient`).
  - `ip` passa a existir → `ip link set eth0 up` funciona.
- **Critério de aceite:** `ip a` mostra `eth0` com IP via SLIRP (10.0.2.x) no guest.

### T-D4. Bootstrap do guest — init roda o `dark-code-init.sh` real
- **Design:** no build, criar um script de init no rootfs (`/etc/init.d/phantom-boot` ou um `/etc/rc.local` via sysvinit) que:
  1. Monta o diretório da distro (9p novo, `mount_tag=darkcode-distro`) em `/mnt/phantom`;
  2. Executa `/mnt/phantom/dark-code-init.sh` (o script REAL vindo do app, sempre atualizado);
  3. Se o 9p da distro falhar, tenta o script embutido no rootfs (fallback).
- **No app** (`QemuManager.start()`): adicionar `-virtfs local,path=<dirDaDistro>,mount_tag=darkcode-distro,security_model=none,id=dist0` (além do workspace).
- **Critério de aceite:** no boot, o guest monta o workspace, cria o usuário, sobe a rede e inicia o `phantom-agent.sh` no canal de controle.

### T-D5. `format=raw` + teste de boot completo
- **No app** (`QemuManager.start()`): `-drive if=none,format=raw,file=...` (remove o warning e libera escrita no bloco 0 → `resize2fs`/`e2fsck` funcionam no 1º boot).
- **Validação:** reproduzir o boot local (QEMU aarch64) até o prompt do usuário; conferir workspace montado e `phantom-agent.sh` respondendo.

---

## 🟨 FASE 2 — INSTALAR POR ARQUIVOS LOCAIS (SAF)

> Toca: `DistroManager.kt` + tela de instalação de distro (Toolbox → Distros).

### T-S1. `installFromFiles()` — selecionar os arquivos avulsos
- Novos parâmetros: usuário escolhe via SAF (multi-seleção) os 4 arquivos: `rootfs.img`, `kernel`, `initrd.img`, `qemu-system-aarch64` (já baixados no aparelho).
- Copiar para `linux/<id>/`, aplicar a validação por tipo de boot (M1) + validação do qemu (tamanho > 1 MB, `+x`), `applyDiskSize`, `writeConfig`, `copyInitScript`.
- **Critério de aceite:** instalar a Phantom **sem nenhum download** selecionando os arquivos → `isInstalled()` = true e VM boota.

### T-S2. `installFromTarball()` — selecionar o pacote `.tar.gz`
- O usuário escolhe um único arquivo `phantom.tar.gz` (ou qualquer tarball) via SAF → reusa o fluxo de extração existente (`extractTarGz` + validação + pós-extração).
- **Critério de aceite:** tarball selecionado localmente instala igual ao download nativo.

### T-S3. UI — botão "Selecionar arquivos…" na instalação da distro
- Na tela de distro (Toolbox → Distros → card da distro), duas opções ao lado de "Instalar":
  - **"Baixar"** (fluxo atual com progresso);
  - **"Selecionar arquivos"** (SAF; aceita `tar.gz` como pacote único ou os 4 arquivos avulsos).
- O log da instalação vai para a aba de terminal (mesmo `LogTermSession` do download).
- **Critério de aceite:** usuário instala a distro só com arquivos que já possui (ex.: baixou no Termux e copiou para o aparelho).

### T-S4. Validação de integridade da seleção local
- Para tarball: se for o pacote oficial, comparar SHA-256 contra `PhantomMirror.PHANTOM_SHA256` (aviso, não bloqueio — pode ser uma versão nova).
- Para arquivos avulsos: conferir `rootfs.img` (ext2 assinatura) via leitura do header, tamanho mínimo de `kernel`/`initrd`, e qemu com tamanho > 1 MB.
- **Critério de aceite:** seleção inválida/incompleta gera erro claro (não falso positivo de instalação).

---

## 🟢 FASE 3 — PUBLICAR + VALIDAR

### T-P1. Rebuild da distro no CI + atualizar SHA-256
- Push das mudanças do `build-distros.yml` → rodar o workflow `Build Distro Artifacts` (manual) → capturar o novo `sha256sum` no log → atualizar `PhantomMirror.PHANTOM_SHA256` (e tamanho, se mudar).
- **Critério de aceite:** `downloadAndInstall` valida o SHA do novo pacote com sucesso.

### T-P2. APK no GitHub Actions + teste no device
- Commit + push no `main` → build.yml gera o APK → usuário instala e confirma: **instalação da distro, boot até o prompt, rede, workspace montado, terminal do app**.

---

<div align="center">

**👻 Phantom-Code** · Distro real funcional · seleção local de arquivos

</div>
