# Arquitetura — Fase 3 (Motor da VM QEMU)

> Status: 🔶 motor pronto (T14 ✅ · T15 ✅ · T16/T17 v1 · T18 ✅) — artefatos pendentes de publicação

## O que foi implementado

| Arquivo | Papel |
|---------|-------|
| `data/vm/QemuConfig.kt` | Presets D13 (Econômico/Equilibrado/Máximo) + espelhos oficiais |
| `data/vm/QemuManager.kt` | Comando §8.1, ciclo de vida (start/stop/status), download do binário + SHA-256 |
| `data/vm/DistroManager.kt` | Catálogo (Phantom Base oficial + Ubuntu/Debian/Alpine), download, checksum, extração tar.gz |
| `data/vm/TerminalManager.kt` | Console ligado às streams do processo (v1 — sem VT100/jackpal) |
| `data/vm/VmController.kt` | Estado global exposto via `LocalVm` (UI reativa) |
| `ui/screens/TerminalScreen.kt` | Tela do terminal: saída em tempo real + envio de comandos |
| `assets/linux/dark-code-init.sh` | Init do guest (T18): rede, user, mount 9p, prompt |
| Toolbox/Settings/Home | Status QEMU real, iniciar/parar, presets, instalação de distros |

## Comando QEMU gerado (Documento Mestre §8.1)

```
qemu-system-aarch64 -M virt,accel=tcg -cpu cortex-a72 -smp N -m XM
  [-kernel Image -initrd initrd.img -append "root=/dev/vda rw console=ttyAMA0"]
  [-drive file=rootfs.img,id=hd0 -device virtio-blk-device,drive=hd0]
  -virtfs local,path=<workspace>,mount_tag=darkcode-ws,security_model=none
  -netdev user,id=net0 -device virtio-net-device,netdev=net0
  -nographic   → stdio = console serial do guest (→ terminal do app)
```

## ⚠️ Pendências (artefatos reais)

1. **Binário QEMU arm64 para Android** — publicar no GitHub Releases e preencher `PhantomMirror.QEMU_BINARY_URL` (+ SHA-256)
2. **Phantom Base** (D16) — rootfs aarch64 headless + kernel/initrd p/ máquina `virt` — publicar em `PHANTOM_BASE_URL`
3. **Teste real no device** — Note 10 Plus (TCG ~15–20% do nativo; D13 presets ajudam)
4. **T17 final** — trocar o console de linhas pelo emulador VT100 (jackpal `emulatorview`)

## Compatibilidade

- Sem APIs Java 9+ (TarExtractor com leitura manual — minSdk 26)
- Downloads com `HttpURLConnection` (sem dependências novas)
- Estado Compose atualizado por threads de background via snapshot (`mutableStateListOf`/`mutableStateMapOf`)

## Segurança

- `security_model=none` no 9p (adequado ao sandbox Android, §8.2)
- SHA-256 validado antes de extrair rootfs/binário (quando o espelho informa)
- Workspace bloqueado por path-traversal no `WorkspaceManager`
