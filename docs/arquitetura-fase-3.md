# Arquitetura — Fase 3 (Motor da VM QEMU)

> Status: 🔶 motor pronto (T14 ✅ · T15 ✅ · T17 ✅ · T18 ✅ · T29 infra/catálogo ✅) — artefatos pendentes de publicação

## O que foi implementado

| Arquivo | Papel |
|---------|-------|
| `data/vm/QemuConfig.kt` | Presets D13 (Econômico/Equilibrado/Alto/Máximo/Custom) + espelhos oficiais |
| `data/vm/QemuManager.kt` | Comando §8.1, ciclo de vida (start/stop/status), download do binário + SHA-256, presets persistidos, tamanho do HD |
| `data/vm/QemuPrefs.kt` | Detecção do aparelho (`DeviceCapabilities`: núcleos/RAM máx.) + persistência do preset/custom/disco |
| `data/vm/DistroManager.kt` | Catálogo (Phantom Base + Ubuntu/Debian/Alpine/Kali), download, SHA-256, extração, `applyDiskSize` (setLength), `writeConfig` (`dark-code.conf`), instalação com log ao vivo |
| `data/vm/DistroConfig.kt` *(no DistroManager)* | Hostname/usuário/tamanho do disco escolhidos antes da instalação |
| `data/vm/TerminalManager.kt` | Console VT100 (jackpal) com abas QEMU/shell/**log** — aba `LOG` para o app escrever na tela |
| `data/vm/LogTermSession.kt` | Sessão de terminal sem processo: `append()` escreve a saída da instalação em tempo real |
| `data/vm/VmController.kt` | Estado global exposto via `LocalVm` (UI reativa) |
| `ui/components/DistroCard.kt` | Card expansível: descrição, para quem, consumo, risco (leve/moderada/pesada), aviso terminal-only, "Em breve" |
| `ui/components/DistroConfigDialog.kt` | Diálogo pré-instalação: preset + sliders custom, hostname, usuário, tamanho do HD |
| `ui/screens/TerminalScreen.kt` | Tela do terminal: VT100 real com abas |
| `assets/linux/dark-code-init.sh` | Init do guest (T18): rede, user/hostname (lê `dark-code.conf`), resize2fs no 1º boot, mount 9p, prompt |
| Toolbox/Settings/Home | Status QEMU real, iniciar/parar, presets, instalação de distros |

## Fluxo de instalação de distro (acompanhado no terminal)

```
Toolbox → card da distro → "Instalar" → DistroConfigDialog (hostname/usuário/preset/HD)
→ "Instalar automaticamente" → cria aba LOG "Instalando <distro>" → abre o terminal
→ DistroManager.install(info, config, logSession):
     download % (HttpURLConnection) → SHA-256 → extração tar.gz → applyDiskSize (setLength N MB)
     → writeConfig (dark-code.conf) → copyInitScript → "✓ Distro instalada e configurada"
→ 1º boot: dark-code-init.sh lê dark-code.conf, e2fsck + resize2fs /dev/vda (guarda /var/run/phantom-resized)
```

Todas as distros rodam em **modo terminal apenas (headless) — sem área gráfica**.

## Presets D13 (limite = escolha do usuário)

| Preset | Núcleos | RAM | Obs. |
|--------|--------|-----|------|
| Econômico | 2 | 1 GB | |
| Equilibrado (padrão) | 4 | 2 GB | |
| Alto | 6 | 4 GB | |
| Máximo | 8 | 8 GB | |
| Custom | sliders | sliders | até os limites do aparelho (`DeviceCapabilities`) |

Tamanho do HD da distro: padrão **3 GB**, opções 3/4/8/16/32/64 GB, alterável a qualquer momento.

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
2. **Phantom Base** (D16) — rootfs aarch64 headless + kernel/initrd p/ máquina `virt` — publicar em `PHANTOM_BASE_URL` (workflow `build-distros.yml` gera; já com `e2fsprogs` e tamanho ext2 dinâmico)
3. **Ubuntu/Debian/Alpine/Kali** — curadoria publica os builds compatíveis e preenche as URLs (hoje placeholder `example.com` → "Em breve" no catálogo)
4. **Teste real no device** — Note 10 Plus (TCG ~15–20% do nativo; presets ajudam)

## Compatibilidade

- Sem APIs Java 9+ (TarExtractor com leitura manual — minSdk 26)
- Downloads com `HttpURLConnection` (sem dependências novas)
- Estado Compose atualizado por threads de background via snapshot (`mutableStateListOf`/`mutableStateMapOf`)

## Segurança

- `security_model=none` no 9p (adequado ao sandbox Android, §8.2)
- SHA-256 validado antes de extrair rootfs/binário (quando o espelho informa)
- Workspace bloqueado por path-traversal no `WorkspaceManager`
- Download interno com `User-Agent` do app; erro claro quando o artefato não foi publicado ainda
