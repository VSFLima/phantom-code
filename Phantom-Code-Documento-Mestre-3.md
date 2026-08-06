# Phantom-Code — Documento Mestre

> **Nome real:** Phantom-Code · **Nome de código (dev):** Dark-Code  
> **Versão unificada:** 4.4 · **Data:** 05 de agosto de 2026  
> **Autor:** VSFLima / Asgard  
> **Este arquivo substitui e unifica:** Documento Técnico v2.2 · Design System v1.2 · Telas/Funções/Opções v1.0  
> **Revisão de alinhamento:** 2026-08-05 (§21)

---

## Índice

1. [Visão Geral](#1-visão-geral)
2. [Problema que o app resolve](#2-problema-que-o-app-resolve)
3. [A solução: Linux real dentro do Android](#3-a-solução-linux-real-dentro-do-android)
4. [Decisões de Produto (contrato)](#4-decisões-de-produto-contrato)
5. [Arquitetura](#5-arquitetura)
6. [Estrutura de pastas e workspace compartilhado](#6-estrutura-de-pastas-e-workspace-compartilhado)
7. [Stack técnica](#7-stack-técnica)
8. [VM QEMU em detalhe](#8-vm-qemu-em-detalhe)
9. [Backup e restauração](#9-backup-e-restauração)
10. [Design System e personalização](#10-design-system-e-personalização)
11. [Telas, funções e opções](#11-telas-funções-e-opções)
12. [Segurança, permissões e modos](#12-segurança-privacidade-e-permissões-android)
13. [Performance](#13-performance)
14. [Riscos e mitigação](#14-riscos-e-mitigação)
15. [Roadmap](#15-roadmap)
16. [Testes e validação](#16-testes-e-validação)
17. [Distribuição](#17-distribuição)
18. [Glossário](#18-glossário)
19. [Referências](#19-referências)
20. [Próximos passos](#20-próximos-passos)
21. [Revisão de alinhamento](#21-revisão-de-alinhamento)

---

## 1. Visão Geral

**Phantom-Code** é um IDE Android que une, num único app:

- Editor de código (estilo VS Code / SPCK)
- Gerenciador de projetos e pastas
- Integração Git/GitHub nativa
- **Terminal Linux real** (glibc completo), com a mesma pasta de projetos do editor
- Capacidade de instalar CLIs, linguagens e ferramentas de IA **originais** (apt, pip, npm, ollama, etc.)

**Diferencial central:** editor e terminal operam na **mesma camada de arquivos**. O que um lado cria, o outro vê na hora — sem sincronização, sem cópia, sem delay.

**Decisões de fundação (dono, agosto/2026):**
1. O app **não vem com distro embutida** — o usuário baixa a distro Linux que preferir (com regras do app).
2. Ao trocar/desinstalar distro, o usuário pode **fazer backup dos projetos** (ZIP ou 7z) e restaurar no sistema novo.
3. O workspace de projetos é **independente da rootfs** da distro.

---

## 2. Problema que o app resolve

| Problema atual | Como o Phantom-Code resolve |
|---|---|
| SPCK + Termux + proot = fluxo fragmentado | Um único app, uma única UI |
| Termux (Bionic) quebra libs que esperam glibc | Linux real com glibc completo (VM QEMU) |
| Configurar proot-distro manualmente é fricção | Setup guiado no app (download + validação) |
| IAs/CLIs não enxergam o projeto sendo editado | Workspace compartilhado nativamente (virtio-9p) |
| Sem GitHub fluido | Integração Git nativa na UI |
| Trocar de distro = perder tudo | Workspace isolado da rootfs + backup ZIP/7z |

---

## 3. A solução: Linux real dentro do Android

### 3.1 O desafio

Em Android **sem root**:
- Não há acesso a `/dev/kvm` → virtualização assistida por hardware não está disponível para apps comuns.
- AVF/pKVM (API 34+) existe, mas a permissão `MANAGE_VIRTUAL_MACHINE` é **restrita a apps de sistema** → inviável para app distribuído.
- Termux usa Bionic (libc do Android) → muitas ferramentas desktop (e IAs) quebram ou precisam de portes.

### 3.2 A solução adotada (D5)

**VM real leve com QEMU em modo headless (só terminal), rodando em TCG (emulação por software).**

| Camada | O que faz |
|---|---|
| **Host (Android)** | App Kotlin + Jetpack Compose; guarda `workspace/` no armazenamento do app |
| **QEMU (arm64)** | Emula uma máquina `virt` completa; sem GUI (`-nographic`) |
| **Guest (Linux)** | Distro real (Ubuntu/Debian/Alpine…) com glibc, apt, systemd, pip, npm |
| **Ponte de arquivos** | **virtio-9p**: monta `/DarkCode/workspace` do Android dentro do Linux como `/home/user/workspace` |
| **Ponte de terminal** | virtio-serial → unix socket local → widget de terminal do app |

**Resultado prático:**
- O usuário instala `ollama`, `llama.cpp`, `nodejs`, `python`, `rustc`, etc. **como em qualquer Linux ARM64**.
- O código que ele edita no app **é o mesmo arquivo** que o terminal/IA vê.
- O app controla pastas de projeto, Git, backup e status da VM pela UI — experiência próxima ao VS Code.

### 3.3 Por que QEMU e não só proot?

| Critério | QEMU (VM real) — **oficial** | proot — **fallback opcional** |
|---|---|---|
| Kernel isolado | Sim (kernel guest próprio) | Não (mesmo kernel Android) |
| systemd | Funciona | Não roda |
| Binários originais (glibc) | Sim | Sim (nativos arm64) |
| Velocidade CPU | ~15–20% do nativo (TCG) | Quase nativo |
| Módulos de kernel | Possível no guest | Impossível |
| Isolamento | Mais forte | Mesmo sandbox do app |
| Complexidade | Alta (embutir QEMU ~50–100 MB) | Média |

**Diretriz:** QEMU é o ambiente **oficial**. proot fica documentado só como fallback em aparelhos muito fracos (a UI deixa claro a diferença de performance).

### 3.4 Como a integração “VS Code-like” funciona

```
┌─────────────────────────────────────────────────────────┐
│                    APP ANDROID (UI)                      │
│  Editor │ Explorer │ Git │ Terminal │ Toolbox │ Settings │
└───────────────┬─────────────────────┬───────────────────┘
                │ leitura/escrita     │ console (socket)
                │ direta              │
                ▼                     ▼
     /DarkCode/workspace/    ┌────────────────────┐
     (pasta física Android)  │  VM QEMU headless  │
                ▲            │  Linux (glibc)     │
                │            │  apt/pip/npm/IA    │
                └────────────┤  mount 9p →        │
                  virtio-9p  │  /home/user/workspace
                             └────────────────────┘
```

- **Mesma pasta raiz de projetos** para o app e para o Linux.
- Terminal sempre abre em `workspace/<projeto-ativo>` (igual VS Code).
- Toolbox escaneia o que está instalado no guest e organiza por categoria.
- Git (JGit) opera sobre os mesmos arquivos do workspace.
- Backup compacta o workspace independente da distro.

### 3.5 O que o usuário consegue fazer de verdade

1. Baixar Ubuntu (ou outra distro) pelo app.
2. Abrir o terminal e rodar `apt update && apt install nodejs python3`.
3. Instalar Ollama / llama.cpp / qualquer CLI de IA.
4. Editar o código no editor do app.
5. No terminal: `python main.py` ou `ollama run ...` — os arquivos são os mesmos.
6. Commit/push pelo painel Git da UI.
7. Trocar de distro sem perder projetos (backup ZIP/7z + workspace isolado).

### 3.6 Termux NÃO é baseado em QEMU — esclarecimento importante

**Termux não usa VM.** Ele é um emulador de terminal + gerenciador de pacotes que executa binários **nativos no Android**:

| Aspecto | Termux | Phantom-Code (QEMU) |
|---|---|---|
| Tipo | Terminal + pacotes nativos | IDE + **VM real** |
| libc | **Bionic** (libc do Android) | **glibc** (libc do Linux desktop) |
| Kernel | Mesmo kernel do Android | Kernel **guest** isolado |
| Como roda programas | Compilados com NDK, linkados ao Bionic | Binários Linux originais dentro da VM |
| systemd | Não | Sim (no guest) |
| apt/pip de distro desktop | Não (usa `pkg`/`apt` do Termux, pacotes portados) | Sim (`apt` real da Ubuntu/Debian) |
| Isolamento | Processo do app | Kernel guest separado |
| Performance | Quase nativa | ~15–20% (TCG) ou nativa se houver KVM |

**O que o Termux oferece opcionalmente:**
- **proot / proot-distro** — “distro” sem root via tradução de syscalls (ptrace). Não é VM.
- **Pacotes QEMU** (`qemu-system-aarch64`, etc.) — o *usuário* pode instalar QEMU *dentro* do Termux e rodar uma VM. O Termux em si **não é** baseado nisso.

Por isso ferramentas de IA e CLIs que esperam glibc completo costumam quebrar ou precisar de portes no Termux. O Phantom-Code resolve isso com a VM QEMU (glibc real).

### 3.7 Ecossistema de soluções no Android (mapa de referência)

| Solução | Tipo | Root? | glibc real? | systemd? | Shared folder fácil? | Uso típico |
|---|---|---|---|---|---|---|
| **Termux** | Terminal + pacotes Bionic | Não | Não | Não | Nativo (mesmo FS) | CLI no dia a dia |
| **proot-distro** (no Termux) | Compat layer (ptrace) | Não | Sim (rootfs) | Não | Bind | Distro “leve” sem VM |
| **UserLAnd / AndroNix** | Compat layer (proot) | Não | Sim | Não | Limitado | Distro GUI via VNC |
| **Limbo** | QEMU embutido | Não | Sim | Sim | Possível (9p) | Emular PC completo |
| **vmConsole** | QEMU + Alpine | Não | Sim | Sim | 9p (`host_storage`) | Linux VM headless |
| **Podroid / builds Termux QEMU** | QEMU para Android | Não | Sim | Sim | virtio-9p | Referência de build |
| **AVF / pKVM / crosvm** | VM com hardware | Não* | Sim | Sim | virtiofs | Restrito a system apps |
| **DroidVM** | crosvm/QEMU + hipervisores | **Sim** | Sim | Sim | Sim | Root + SoCs específicos |
| **Phantom-Code** | IDE + **QEMU headless** | Não | **Sim** | **Sim** | **virtio-9p (oficial)** | Editor + terminal + IA + Git |

\*AVF existe em dispositivos recentes, mas a API `MANAGE_VIRTUAL_MACHINE` não está disponível para apps de terceiros de forma livre.

### 3.8 Por que Phantom-Code escolhe QEMU (e não “ser outro Termux”)

1. **glibc completo** → ferramentas e IAs desktop funcionam sem porte.
2. **Kernel isolado** → systemd, módulos e comportamento de Linux real.
3. **Integração de IDE** → editor + terminal + Git + Toolbox na mesma UI, mesma pasta.
4. **Distro escolhida pelo usuário** (D1) → não preso a um rootfs fixo.
5. **Workspace independente da distro** (D3) → trocar Ubuntu por Alpine sem perder projetos.
6. Open-source, sem root, já validado por Limbo, vmConsole, Termux-packages e Podroid.

**Termux continua sendo referência valiosa** para:
- Builds de QEMU arm64 (flags, dependências Bionic).
- Biblioteca de terminal (Termux terminal-emulator / jackpal forks).
- Experiência de UX de shell no mobile.

---

## 4. Decisões de Produto (contrato)

| # | Decisão | Status |
|---|---|---|
| **D1** | Distros: não embute rootfs no APK; **Phantom Base** oficial + Ubuntu/Debian/Alpine/Kali + import arm64 | ✅ |
| **D2** | Backup local ZIP/7z via SAF | ✅ |
| **D3** | `workspace/` independente da distro | ✅ |
| **D4** | Terminal inicia em `workspace/<projeto-ativo>` | ✅ |
| **D5** | Ambiente oficial = **QEMU headless**; proot = fallback opcional | ✅ |
| **D6** | Canal de distribuição (Play vs sideload/F-Droid) | 🔶 Em aberto |
| **D7** | Editor CodeMirror 6 (recomendado; validar no aparelho) | 🔶 Recomendado |
| **D8** | Backup cloud + API keys no app (Keystore → Linux opcional) | ✅ |
| **D9** | Nome: **Phantom-Code** · código **Dark-Code** | ✅ |
| **D10** | Temas e terminal personalizáveis | ✅ |
| **D11** | Múltiplos terminais independentes | ✅ |
| **D12** | Phantom AI Suite (contexto compartilhado entre IAs) | ✅ |
| **D13** | VM pode usar todo o poder do aparelho (opt-in) | ✅ |
| **D14** | Muitas funções ≠ bagunça (nav fixa, painéis, Palette) | ✅ |
| **D15** | Design principal = mockups Gemini + Activity Bar fina | ✅ |
| **D16** | Phantom Base = customizar Linux upstream → rootfs oficial terminal-only | ✅ |
| **D17** | Modos: App-only (SPCK) · App+Terminal · Terminal background | ✅ |
| **D18** | Auto-save + restaurar sessão | ✅ |
| **D19** | Storage: padrão app+SAF; opt-in pasta livre ou amplo | ✅ |
| **D20** | Iniciar Linux na abertura: on/off/perguntar | ✅ |
| **D21** | Ponte browser: interno ou Chrome/sistema para OAuth/CLI | ✅ |

**Contagem:** 19 definidas ✅ · 2 em aberto/recomendado 🔶 (D6, D7).

---


## 5. Arquitetura

### 5.1 Camadas

1. **UI Android** — Kotlin + Jetpack Compose  
2. **Ambiente Linux** — QEMU arm64 headless (TCG)  
3. **Compartilhamento de arquivos** — virtio-9p (VM) / bind (proot)  
4. **Comunicação terminal** — unix socket (virtio-serial) ou PTY  

### 5.2 Componentes principais

- Editor (CodeMirror 6)
- Explorer de projetos
- Painel Git (JGit)
- Terminal dockável
- Gerenciador da VM (start/stop/status/reset)
- Gerenciador de distros (download, validação, troca)
- Backup/restauração (ZIP/7z + SAF)
- Toolbox (scanner de pacotes do guest)

---

## 6. Estrutura de pastas e workspace compartilhado

```
/DarkCode/
├── linux/                 ← rootfs da distro (oculta da UI de projetos)
│   ├── bin/, etc/, usr/…
│   └── dark-code-init.sh  ← boot: rede, user, mount do workspace
├── workspace/             ← ÚNICA pasta visível como “projetos”
│   ├── projeto-1/
│   ├── projeto-2/
│   └── …
├── backups/               ← ZIP/7z gerados pelo app (local)
└── config/                ← temas, preferências
    └── (secrets NÃO ficam aqui em texto plano — Android Keystore)
```

- `linux/` = partição de sistema (não aparece no Explorer).
- `workspace/` montada no guest via 9p em `/home/user/workspace`.
- Terminal inicia já dentro do projeto ativo.
- Trocar distro **não apaga** o workspace.

---

## 7. Stack técnica

| Camada | Tecnologia | Nota |
|---|---|---|
| App | Kotlin + Jetpack Compose | UI nativa |
| Editor | CodeMirror 6 (WebView) | Melhor touch mobile |
| Terminal | jackpal (fork moderno) ou Termux terminal-emulator | PTY/serial nativo |
| Ambiente | **QEMU arm64 headless** | D5; proot = fallback |
| Rootfs | Download (Ubuntu/Debian/Alpine) | D1 |
| Arquivos | virtio-9p | Mesma pasta física |
| Git | JGit (puro Java) | Sem NDK por padrão |
| ZIP | `java.util.zip` | Nativo |
| 7z | SevenZipJBinding-4Android ou Commons Compress | — |
| Destino backup local | SAF (`ACTION_CREATE_DOCUMENT` / `OPEN_DOCUMENT`) | Scoped storage |
| Backup cloud | Drive API · Microsoft Graph · Dropbox · S3 SDK · WebDAV | OAuth / keys no Keystore |
| Secrets / API keys | Android Keystore + injeção env no guest | Toggle “expor ao Linux” |

---

## 8. VM QEMU em detalhe

### 8.1 Linha de comando de referência (headless, sem KVM)

```bash
qemu-system-aarch64 \
  -M virt,accel=tcg \
  -cpu cortex-a72 \
  -smp 4 \
  -m 2G \
  -kernel Image \
  -initrd initrd.img \
  -append "root=/dev/vda rw console=ttyAMA0" \
  -drive if=none,file=rootfs.ext4,id=hd0 \
  -device virtio-blk-device,drive=hd0 \
  -virtfs local,path=/DarkCode/workspace,mount_tag=darkcode-ws,security_model=none,id=ws0 \
  -netdev user,id=net0 \
  -device virtio-net-device,netdev=net0 \
  -chardev socket,id=term0,path=/tmp/darkcode-term.sock,server=on,wait=off \
  -device virtio-serial-device \
  -device virtconsole,chardev=term0 \
  -nographic
```

### 8.2 Pontos-chave

| Item | Detalhe |
|---|---|
| Kernel | Image arm64 para máquina `virt` (drivers virtio + console) |
| Boot | Direto de kernel (`-kernel`) — mais leve que UEFI/GRUB |
| Rootfs | Distros recomendadas §8.5 (usuário baixa, D1) |
| 9p | `security_model=none` (adequado ao sandbox Android) |
| Rede | SLIRP (user-mode NAT) — internet no guest sem root |
| Console | virtio-serial → socket local → terminal do app |
| Snapshot | Usar para boot rápido (evitar boot frio toda vez) |
| Recursos | `-m` configurável; parar VM em background (opção) |

### 8.3 O que funciona de verdade no guest

- `apt` / `pip` / `npm` / compiladores
- systemd
- Ferramentas de IA (Ollama, llama.cpp) — inferência em **CPU do guest** (em TCG a CPU é emulada; ver D13)
- Qualquer binário Linux ARM64 original (glibc)

**O que não acessa por padrão:** GPU/NPU do aparelho, Wi-Fi/BT direto, USB físico (tudo virtualizado).

### 8.3.1 Browser e autenticação CLI (D21)

Muitos CLIs pedem login no **navegador** (`gh auth`, `gcloud`, OAuth de APIs, etc.). No guest **não há browser gráfico**. Solução: o **app Android** abre o URL em nome do Linux.

#### Como funciona

```
CLI no guest  →  xdg-open / $BROWSER / phantom-open-url "https://..."
        │
        ▼
Helper no guest grava URL em arquivo 9p ou socket local
        │
        ▼
App Android lê e abre:
  · Navegador interno (WebView / Custom Tab in-app)
  · ou Chrome / navegador padrão do sistema (Intent)
```

| Peça | Detalhe |
|---|---|
| **No Linux** | Script `phantom-open-url` (ou `BROWSER=phantom-open-url`) + symlink `xdg-open` quando fizer sentido |
| **Canal** | Arquivo em 9p (`workspace/.phantom/open-url`) **ou** virtio-serial / socket já usado pelo terminal |
| **No Android** | Observer → abre UI conforme preferência D21 |
| **OAuth localhost** | `hostfwd` QEMU (ex. `tcp::8765-:8765`) + app abre `http://127.0.0.1:8765/...` no browser do aparelho |

#### Opções do usuário (Settings → Ambiente / Integrações)

| Opção | Comportamento |
|---|---|
| **Navegador interno** | WebView ou Chrome Custom Tabs **dentro** do app (fluxo controlado, volta fácil ao IDE) |
| **Chrome / navegador do sistema** | `ACTION_VIEW` no app padrão (Chrome, Firefox, etc.) |
| **Perguntar sempre** | Dialog: Interno · Sistema · Copiar URL |
| **Só copiar URL** | Para o usuário colar onde quiser (modo privacidade) |

**Padrão recomendado:** **Custom Tabs / navegador interno** (parece app nativo, usa o motor do Chrome se disponível, volta ao Phantom-Code com o botão fechar).

#### Casos cobertos
- `gh auth login`, logins OAuth de CLIs  
- Links que o CLI imprime (`Opening browser...`)  
- Abrir documentação / callback `http://127.0.0.1:porta`  
- Fallback: se o helper falhar, mostrar URL no terminal + botão “Abrir no navegador” na notificação/UI  

#### O que não fazer
- Instalar Chromium/Firefox **completo** dentro da VM só para OAuth (pesado demais no TCG)  
- Depender de GUI no guest  

**Resumo:** o Linux **não precisa** de browser instalado; o **app reconhece o pedido de URL** e usa o navegador do usuário (interno ou Chrome/sistema), conforme a opção escolhida.

### 8.4 Poder do aparelho na VM (D13)

Por padrão a VM pode vir com limites seguros (ex.: 2 GB RAM, 4 cores) para não travar o Android.  
**Se o usuário quiser**, pode liberar **todo o processamento e memória disponíveis**:

| Recurso | Comportamento |
|---|---|
| **CPU (`-smp`)** | Presets: Econômico · Equilibrado · **Máximo (todos os cores)** |
| **RAM (`-m`)** | Presets: 1G · 2G · 4G · **Máximo seguro** (deixa folga mínima para o Android) · **Agressivo** (quase toda a RAM — aviso de risco de low-memory kill) |
| **Prioridade** | Opção de manter a VM em foreground com prioridade alta enquanto o app está aberto |
| **Background** | Continua podendo auto-parar (economia) **ou** “Manter VM ativa com recursos máximos” (bateria) |

**UI (Settings → Ambiente VM):**
- Slider / presets claros: *“Usar todo o poder do aparelho”* (toggle + aviso)
- Indicadores em tempo real: CPU % · RAM usada pela VM · RAM livre do sistema
- Aviso: em TCG (sem KVM) o ganho tem teto pela emulação; mais cores/RAM ajudam, mas não igualam nativo
- Em aparelhos com aceleração futura (se disponível) o mesmo controle se aplica

**Princípio:** o limite é **escolha do usuário**, não uma restrição artificial fixa do app.

### 8.5 Distros: oficial Phantom Base + catálogo amplo (D1 / D16)

**Princípio:** existe uma **distro oficial enxuta** do Phantom-Code (**Phantom Base**), recomendada como padrão. O usuário **sempre pode** instalar Ubuntu, Kali, Debian, Alpine ou importar outra arm64.

### 8.5.1 Phantom Base — distro oficial do app (D16)

#### Intenção de produto (registro oficial)

> **Intenção:** partir de um **Linux base já existente** (upstream), **modificá-lo e curá-lo**, e assim **recriar / publicar a base Linux oficial do Phantom-Code** — a **Phantom Base**.  
> Não se trata de inventar um kernel do zero; trata-se de **customizar um rootfs mínimo headless** para o app (terminal-only, arm64, QEMU no Android), com identidade, scripts de integração e pacotes alinhados ao Phantom-Code.

**Fluxo de criação da distro oficial:**

```
Linux base upstream (Alpine mini  ou  Debian minbase  ou  Ubuntu Base)
        │
        ▼
Remover o desnecessário (GUI, docs pesados, serviços inúteis)
        │
        ▼
Adicionar camada Phantom:
  · dark-code-init.sh (rede, 9p workspace, user, prompt)
  · usuário/skel padrão do app
  · meta-pacotes opcionais (phantom-devtools)
  · branding mínimo / motd Phantom-Code
  · repositórios e defaults testados com a Toolbox
        │
        ▼
Publicar rootfs arm64 + checksum
  (GitHub Releases / mirror do projeto)
        │
        ▼
App: badge “Oficial · Recomendada” = Phantom Base
```

| Item | Definição |
|---|---|
| **Nome** | **Phantom Base** (código: `phantom-base` / alinhado a Dark-Code) |
| **Papel** | Rootfs **oficial e recomendada** — terminal-only, leve, integrada ao app |
| **Método** | **Fork/customização** de Linux base upstream + scripts e pacotes Phantom — **não** kernel from scratch |
| **Upstream candidato** | **Alpine** mini (leveza no TCG) **ou** **Debian minbase** / **Ubuntu Base** (`apt`) — escolha final na PoC de build |
| **Arch** | **aarch64 only** |
| **Modo** | **Somente terminal** (sem DE/GUI) |
| **Tamanho alvo** | Mínimo útil (~20–60 MB base, conforme upstream) |
| **UI no app** | 1ª opção no Gerenciador de Distros · badge **“Oficial · Recomendada”** |
| **Distribuição** | Download no app + checksum; **rootfs não embutida no APK** |

**Camada de modificação Phantom (o que “recria” a base oficial):**
1. Rootfs limpa a partir do upstream escolhido  
2. Pacotes essenciais: shell, `curl`, `ca-certificates`, `git`, Python 3 (e caminho fácil para Node)  
3. Integração app: `dark-code-init.sh`, mount **virtio-9p** do `workspace/`, prompt em `workspace/<projeto>`  
4. Defaults seguros para QEMU headless + SLIRP  
5. Opcional: repositório ou meta-pacote `phantom-devtools`  
6. Versionamento e notas de release no app  

**O que NÃO entra na Phantom Base:**
- Ambiente gráfico / desktop  
- Metapacotes tipo kali-linux-default  
- Toolchains completos pré-instalados (usuário instala sob demanda)  

**Por que existe:**
1. 1º uso previsível (“Instalar Phantom Base”)  
2. Otimizada para VM terminal no Android  
3. Toolbox e docs testados nessa rootfs  
4. Usuário ainda pode instalar Ubuntu, Kali, Debian, Alpine ou importar outra arm64  

**Manutenção:** `phantom-base-YYYY.MM`; update = nova rootfs (workspace preservado, D3); rebuild periódico a partir do upstream para patches de segurança.

#### Catálogo completo (além da oficial)

#### Requisitos técnicos (todas as distros)

| Regra | Detalhe |
|---|---|
| Arquitetura | **Somente aarch64 / arm64** |
| Formato | Tarball rootfs (`.tar.xz` / `.tar.gz`), imagem rootfs, ou instalação via debootstrap |
| Integridade | SHA-256 (ou equivalente) validado antes de extrair |
| Espaço | Verificação de disco livre antes do download |
| Uso | Preferência headless (sem GUI pesada) — combina com terminal-only |

| Prioridade | Distro | Gerenciador | Notas | Ideal para |
|---|---|---|---|---|
| **0 — Oficial** | **Phantom Base** (D16) | apk ou apt (conforme base) | Enxuta, badge “Oficial · Recomendada”, mirror do projeto | **Padrão do app / 1º uso** |
| **1** | **Ubuntu** minimal/Base 22.04 / 24.04 arm64 | `apt` | Máximo de tutoriais e pacotes | Uso geral, quem já conhece Ubuntu |
| **2** | **Debian** bookworm slim/minbase arm64 | `apt` | Estável | Builds, servidores de dev |
| **3** | **Alpine** mini rootfs aarch64 | `apk` | Muito leve (próxima da Phantom Base se a oficial for Alpine-based) | Aparelhos fracos |
| **4** | **Kali Linux** arm64 | `apt` | Ecossistema pentest; imagem **maior** + avisos | Labs / segurança |
| **5** | Outras arm64 + **Importar rootfs** (SAF) | varia | Arch ARM, Fedora aarch64, etc. | Avançado |

**Ubuntu e Kali:** compatibilidade de **primeira classe** no catálogo (não só “toleradas”).  
**Kali — avisos:** mais armazenamento/RAM; uso legítimo de estudo/labs.

#### Importar distro custom
- SAF → validar arm64, formato, espaço, checksum opcional  
- Qualquer rootfs arm64 compatível pode rodar além da lista curada  

#### Recomendação de primeira instalação
1. **Phantom Base** (oficial, enxuta) — onboarding padrão  
2. Ubuntu 24.04 minimal — se o usuário preferir ecossistema Ubuntu  
3. Alpine — se nem Phantom Base estiver disponível e o aparelho for fraco  
4. Kali — só se o objetivo for o toolkit Kali  

#### Evitar no fluxo padrão
- ISO desktop (GNOME/KDE) headless-unfriendly  
- Só x86_64  
- Imagens sem checksum confiável  

#### Pós-install (Phantom Base e demais)
- `dark-code-init.sh`: SLIRP, 9p workspace, user, prompt no projeto ativo  
- Workspace independente (D3); backup antes de trocar (D2)  

**Resumo:** **Phantom Base = oficial e leve**; Ubuntu/Kali/Debian/Alpine/import = liberdade total do usuário.

---


## 9. Backup e restauração

### 9.1 Backup local (D2)

| Inclui (padrão) | Opcional |
|---|---|
| `workspace/` | Chaves SSH / tokens (não recomendado em backup não criptografado) |
| Metadados `.darkcode-project` | Histórico `.git/` |

**Fluxo local:** escolher ZIP ou 7z → SAF (destino livre) → compactar com progresso.  
**Restauração:** SAF → validar estrutura → extrair com merge (nunca apaga silenciosamente).  
**Proteção:** antes de desinstalar distro, o app **sempre pergunta** se quer gerar backup.

### 9.2 Backup em nuvem (D8 — definido)

O usuário escolhe o provedor e autentica **pelo app** (OAuth ou API key). O app sobe o mesmo pacote ZIP/7z para a nuvem.

| Provedor | Método de auth | Notas |
|---|---|---|
| **Google Drive** | OAuth 2.0 (Google Sign-In / Drive API) | Pasta dedicada `Phantom-Code/` no Drive |
| **OneDrive** | OAuth 2.0 (Microsoft Graph) | Pasta `Phantom-Code` |
| **Dropbox** | OAuth 2.0 | App folder |
| **Mega** | API key / login (conforme SDK) | Opcional na v1 |
| **S3-compatível** | Access Key + Secret + endpoint | AWS, MinIO, R2, Backblaze B2, etc. |
| **WebDAV** | URL + usuário/senha | Nextcloud, ownCloud, etc. |

**Fluxo cloud:**
1. Toolbox ou Settings → **Backup em nuvem**
2. Escolher provedor já conectado (ou conectar novo)
3. Formato ZIP ou 7z
4. Upload com barra de progresso + retry
5. Lista de backups remotos (nome, data, tamanho) → restaurar ou excluir

**Regras:**
- Backup cloud é **opt-in** (nunca automático sem consentimento).
- Tokens OAuth e secrets ficam no **Android Keystore** (nunca em texto plano no workspace).
- O app pode agendar backup (manual / ao fechar projeto / periódico) — preferência do usuário.

### 9.3 Gestão de API keys e secrets (D8)

O usuário configura chaves **só pela UI do app** — não precisa digitar no Linux.

**Onde configurar:**
- **Toolbox → Integrações & API Keys** (principal, visual em cards)
- Atalho também em Settings → Conta & Integrações

**Categorias de secrets (exemplos):**

| Categoria | Exemplos | Uso típico |
|---|---|---|
| Nuvem | Google Drive, OneDrive, Dropbox, S3 | Backup / sync |
| Git / Código | GitHub PAT, GitLab token | Clone, push, API |
| IA | OpenAI, Anthropic, Groq, Gemini, Ollama cloud | CLIs e apps no terminal |
| Outros | Hugging Face, custom webhooks | Livre |

**Como o Linux recebe as chaves (sem o usuário escrever no guest):**

1. App guarda tudo no **Android Keystore** (criptografado, amarrado ao aparelho).
2. Ao **iniciar a VM** (ou ao abrir o terminal), o app monta um arquivo **somente leitura** no guest, por exemplo:
   - `/home/user/.phantom/secrets.env` (formato `KEY=value`)
   - ou variáveis de ambiente injetadas no processo do shell (`export OPENAI_API_KEY=...`)
3. O arquivo **não** fica no `workspace/` compartilhado de forma permanente em texto plano — é gerado sob demanda e pode ser tmpfs/9p read-only.
4. O usuário marca, por chave, se ela deve ser **exposta ao Linux** (toggle por item).
5. No Toolbox, botão **“Copiar nome da variável”** (ex.: `$OPENAI_API_KEY`) para usar em scripts.

**Segurança:**
- Nunca logar o valor da key na UI (mostrar só `sk-…xxxx`).
- Confirmação para expor ao Linux.
- Opção “Revogar / apagar” remove do Keystore e do env da próxima sessão.
- Backup local/cloud **não inclui** secrets por padrão (opt-in explícito + aviso).

---



## 10. Design System e personalização

> **Fonte de verdade visual (D15):** os mockups Gemini (Home, Workspace+Explorer+Git, logo ghost/shield) definem o design principal. Qualquer UI gerada por IA ou implementada no app **deve seguir este idioma**.

### 10.0 Design principal — Cyber-Phantom / Neon Dark IDE (para IA e devs)

**Nome do estilo:** Cyber-Phantom · Neon Dark IDE (OLED)  
**Referências obrigatórias:**  
- Home: logo shield gradiente roxo→cyan, Recent Projects, Get Started outlined, Bottom Nav 5 itens, `QEMU LINUX: RUNNING`  
- Workspace: editor central + abas + Terminal dock (`Terminal 1`, `2`, `Run`) + Bottom Nav  
- Laterais: **Activity Bar fina** + gaveta Explorer / painel Git **sob demanda** (não full-screen permanente)  
- Logo app: ghost circuit LED; logo marca: shield geométrico  
- Cores: `#000000` · `#121212` · `#9F4DFF` · `#D34DFF` · `#00FFFF` · `#C0C0C0`

**Regras para a IA seguir ao desenhar telas:**
1. Fundo sempre preto OLED; cards/painéis `#121212`
2. Bottom Nav **fixa:** Explorer · Search · Git · Toolbox · Settings (line-art)
3. **Activity Bar** lateral ~48–56px só com ícones — **não ocupa a tela**
4. Gavetas/painéis abrem por toque no ícone; fecham e devolvem o editor
5. Botões primários = filled roxo angular; secundários = outlined cyan/roxo
6. Status da VM visível (pill verde `RUNNING`)
7. Terminal no rodapé, abas múltiplas, cursor neon
8. Não inventar 6º item na Bottom Nav; features extras = painel/card/Palette
9. Sem Material Design “redondo demais”; preferir cantos levemente angulares
10. Circuit traces só como atmosfera de fundo, nunca por cima do código

### 10.0.1 Activity Bar (menu lateral fino) — D15

```
┌────┬──────────────────────────────┐
│ 📁 │                              │
│ 🔍 │     Editor (área principal)  │
│ 💎 │                              │
│ 🧰 │     ── Terminal dock ──      │
│ ⚙  │                              │
└────┴──────────────────────────────┘
  ↑ trilho ~50px — não cobre o código
```

| Item | Comportamento |
|---|---|
| Largura | ~48–56 dp — só ícones line-art |
| Toque | Abre painel da função (Explorer, Search, Git…) em largura moderada |
| Fechar | Toque fora, swipe ou toque de novo no ícone |
| Phone (retrato) | Trilho opcional; Bottom Nav é a navegação principal |
| Tablet / landscape / DeX | Trilho sempre visível; Bottom Nav pode ocultar |
| Settings | Modos: Trilho+Bottom · Só trilho · Só Bottom · Trilho à direita |

**Isso é o design principal de navegação auxiliar** — igual ao mockup Gemini com Explorer à esquerda e Git à direita sem matar o espaço do editor.

### 10.1 Tema padrão (factory)

**Asgardian Tech / Cyber-Phantom** · Dark Mode OLED · bordas angulares · glow neon.

| Token | HEX padrão | Uso |
|---|---|---|
| `bg-primary` | `#000000` | Fundo editor / app |
| `bg-surface` | `#121212` | Painéis, Top Bar, cards, Activity Bar |
| `accent-primary` | `#9F4DFF` | Ações, ícones ativos, cursor terminal |
| `accent-primary-bright` | `#D34DFF` | Hover / glow |
| `accent-secondary` | `#00FFFF` | Acentos, strings, status sucesso, outlines |
| `border` | `#4B5563` | Bordas, ícones inativos |
| `text-primary` | `#E5E7EB` | Texto principal |
| `text-secondary` | `#888888` | Comentários, labels |
| `error` | `#FF3366` | Erros / alertas |
| `success` | `#00FF9F` | Status RUNNING, commit ok |
| `neutral` | `#C0C0C0` | Apoio de marca (logo kit) |

**Logo:** fantasma neon (launcher) + shield geométrico (Home/status).  
**Layout:** Phantom Dock + Activity Bar fina (D15).  
**Referências de arquivo:** `1785959718563.png` (Home), `1785959708622.png` (Workspace+Git), `1785959736532.png` (Explorer), `1785959659895.png` (logo/paleta).

### 10.2 Personalização completa pelo usuário (D10)

O app é **100% personalizável**. Cada usuário escolhe tema, cores, terminal e detalhes visuais. Preferências ficam em `config/` (e sincronizáveis no futuro via backup cloud).

#### A. Temas prontos (presets)

| Preset | Estilo | Fundo | Destaque |
|---|---|---|---|
| **Phantom** (padrão) | Cyber neon OLED | `#000000` | Roxo + Cyan |
| **Deep Slate** | Menos contraste | `#121212` | Roxo suave |
| **Matrix** | Verde clássico | `#0A0A0A` | `#00FF41` |
| **Dracula** | Popular em IDEs | `#282A36` | `#BD93F9` / `#FF79C6` |
| **Nord** | Frio / minimal | `#2E3440` | `#88C0D0` |
| **Solarized Dark** | Clássico | `#002B36` | `#268BD2` |
| **Light Soft** | Claro (opcional) | `#F8F8F8` | `#6B21A8` |
| **Custom** | Tudo livre | Color pickers | — |

#### B. O que o usuário pode customizar

| Área | Opções |
|---|---|
| **Cores do app** | Fundo, superfícies, accent primary/secondary, texto, erro, sucesso (color picker ou hex) |
| **Glow / neon** | Intensidade do glow (off / baixo / médio / alto) |
| **Bordas** | Angulares (padrão) · levemente arredondadas · sem borda |
| **Editor** | Tema de syntax (independente do tema da UI), fonte, tamanho, ligaduras, word wrap, números de linha |
| **Terminal** | Ver §10.3 (completo) |
| **Layout** | Posição Activity Bar (esq/dir), altura padrão do Terminal dock, animações on/off |
| **Ícones** | Line-art (padrão) · filled (futuro) |

#### C. Onde configurar
- **Settings → Aparência & Temas** (página principal de personalização)
- Atalho rápido: long-press no status da VM ou ícone de paleta na Top Bar (opcional)
- Preview ao vivo ao mudar cor/tema (sem precisar reiniciar o app)

#### D. Persistência
- JSON em `config/theme.json` + preferências Compose/DataStore
- Incluído no backup de **config** (opt-in no backup ZIP/7z/cloud)
- Reset “Restaurar tema Phantom” sempre disponível

### 10.3 Terminal estilizado e personalizável

O terminal não é só “preto e verde”: é um componente visual de primeira classe, alinhado ao design Cyber-Phantom e totalmente configurável.

#### Aparência do Terminal

| Elemento | Padrão Phantom | Customizável |
|---|---|---|
| Fundo | `#000000` ou herdado do tema | Color picker |
| Texto | `#E5E7EB` | Color picker |
| Cursor | Bloco ou barra em `#9F4DFF` + glow | Forma (bloco/barra/underline) · cor · blink on/off |
| Seleção | Roxo translúcido | Cor |
| Prompt | `root@phantom:~/projeto$` | Cor do user/host/path (3 cores) |
| ANSI colors | Paleta neon (16 cores) | Preset (Phantom, Matrix, Dracula, Solarized, Nord) ou 16 slots custom |
| Fonte | JetBrains Mono | Lista de monoespaçadas instaladas + tamanho |
| Padding / densidade | Confortável | Compacto · normal · confortável |
| Scrollbar | Fina, cyan | Cor / auto-hide |

#### Comportamento do Terminal

| Opção | Valores |
|---|---|
| Abas | Nome customizável · cor da aba |
| Bell | Visual (flash) · som · off |
| Opacity do painel | 100% · 95% · 90% (glass sutil) |
| Minimizar ao focar editor | On/Off |
| Sempre visível / só em erro | Preferência de dock |
| Linhas de histórico | 1000 / 5000 / 10000 |

#### Presets de terminal (atalho)

- **Phantom Neon** — cursor roxo, ANSI cyan/magenta  
- **Classic Green** — estilo Matrix  
- **Dracula Term**  
- **Nord Term**  
- **High Contrast** — máximo legibilidade  
- **Custom** — tudo manual  

A escolha do preset de terminal pode ser **independente** do tema da UI (ex.: UI Phantom + terminal Matrix).

#### Implementação (técnica)
- Widget nativo (jackpal / Termux terminal-emulator): cores via API de `TerminalColorScheme` / atributos do emulador
- Se xterm.js (WebView): CSS variables + addons de tema
- Temas salvos como JSON (fácil importar/exportar e incluir no backup de config)

---


## 11. Telas, funções e opções

### 11.1 Navegação (Bottom Nav)
Explorer · Search · Git · Toolbox · Settings

### 11.2 Home / Welcome
- Status `QEMU LINUX: RUNNING/STOPPED`
- Logo + Recent Projects
- Botões: Clone Git Repo · Open Project · New File/Project
- Terminal dock acessível (minimizado)

### 11.3 Workspace (Phantom Dock)
- **Top Bar:** hambúrguer, nome do arquivo, Play/Run, busca no arquivo, status VM
- **Editor:** CodeMirror 6, abas, syntax neon, autocomplete básico
- **Terminal dock:** abas (Terminal 1, 2, Run), prompt em `workspace/<projeto>`, cursor roxo

### 11.4 Project Explorer
- Árvore do workspace
- Long-press: Novo / Renomear / Excluir / Copiar caminho / Abrir no Terminal
- FAB: novo arquivo/pasta/projeto

### 11.5 Busca Global
- Buscar/substituir no projeto, filtros (extensão, case, regex)

### 11.6 Git
- Changes / Staged Changes
- Commit (mensagem + botão Primary)
- Push / Pull
- Diff viewer
- Branch atual + troca de branch
- Login token/OAuth GitHub

### 11.7 Terminal (estilizado + personalizável) — D11

- Shell real (bash/zsh) dentro da VM
- Run do arquivo do editor
- **Múltiplos terminais independentes (D11):**
  - Abas ilimitadas na prática (Terminal 1, 2, 3… + Run)
  - Cada aba = sessão/PTY separada (pode rodar Ollama numa, servidor Node noutra, testes noutra)
  - Nome e **cor da aba** customizáveis (ex.: “IA-1”, “API”, “Build”)
  - Split opcional (2 painéis lado a lado / um em cima do outro) em tablet/landscape
  - Ações: duplicar aba · renomear · fechar · “matar processo desta aba”
- Se VM parada → opção de iniciar antes
- **Aparência:** fundo, texto, cursor, prompt, ANSI, fonte, densidade (D10)
- **Presets:** Phantom Neon · Matrix · Dracula · Nord · High Contrast · Custom
- **Comportamento:** bell, opacity, auto-hide, histórico por aba
- Engrenagem no dock → tema do terminal

### 11.7.1 Phantom AI Suite — roteador de IAs (D12)

Suite **dentro do app** (não só no shell) para várias IAs trabalharem juntas com **contexto compartilhado em tempo real**.

**Objetivo:** o usuário pode usar 2+ IAs (locais no guest ou APIs da Toolbox) sem conflito — cada uma sabe o que a outra está fazendo e pode repassar tarefas.

#### Conceitos

| Peça | Função |
|---|---|
| **AI Router** | Orquestra quem recebe a tarefa (manual ou automático por tipo: código, docs, review, shell) |
| **Shared Context Bus** | Contexto único do projeto: arquivos abertos, diffs Git, últimos comandos do terminal, decisões das IAs |
| **Agents** | Cada IA registrada (Ollama local, OpenAI, Groq, Claude, Gemini, custom…) |
| **Task handoff** | Uma IA pode delegar subtarefa a outra (“você formata, eu reviso”) |
| **Conflict guard** | Trava suave: evita duas IAs editando o mesmo arquivo ao mesmo tempo (fila ou lock por path) |

#### Onde aparece na UI (organizado, não bagunçado)

- **Toolbox → seção IAs** — cards dos engines + botão **“Abrir AI Suite”**
- **Painel lateral / overlay “AI Suite”** (não é mais um item na Bottom Nav — evita poluir a navegação principal)
- Atalho na Command Palette: `AI Suite`, `Ask AI`, `Handoff task`

#### Fluxos principais

1. **Perguntar ao time de IAs** — mensagem + contexto do arquivo/seleção → Router escolhe ou usuário escolhe o agent  
2. **Modo multi-agent** — ex.: Agent A gera código · Agent B revisa · Agent C sugere testes  
3. **Repasse de tarefa** — botão “Enviar para outra IA” com resumo do que já foi feito  
4. **Contexto ao vivo** — o bus atualiza quando o usuário salva arquivo, faz commit ou roda comando no terminal  
5. **Evitar conflito** — se Agent A “reservou” `src/api.ts`, Agent B só lê ou espera  

#### Fontes de contexto (Shared Context Bus)

- Arquivo ativo + seleção  
- Árvore do projeto (resumo)  
- Diff Git atual  
- Últimos N comandos / saída de terminais relevantes  
- Notas de handoff entre agents  
- API keys já configuradas na Toolbox (D8) — cada agent usa a key que o usuário ligou  

#### Regras de produto

- **Opt-in:** Suite desligada por padrão até o usuário abrir/ativar  
- IAs **locais** (Ollama no guest) e **cloud** podem misturar  
- Tudo que for escrito em arquivo passa pelo mesmo workspace (9p) — sem cópia paralela  
- Logs da Suite ficam em `workspace/.phantom/ai-suite/` (opcional, gitignore sugerido)  
- Organização: **um painel**, não cinco menus novos na Bottom Nav (D14)


### 11.8 Toolbox (página completa — visual em cards, estilo Gemini)

A Toolbox é a **central de ferramentas e integrações** do app. Layout em **cards** com glow neon, categorias e status — alinhado aos mockups Cyber-Phantom.

#### Estrutura visual da Toolbox

```
┌─────────────────────────────────────────┐
│  TOOLBOX                    [🔍 Buscar] │
├─────────────────────────────────────────┤
│  ● QEMU LINUX: RUNNING    RAM 1.2/2G    │  ← status do ambiente
├─────────────────────────────────────────┤
│  ☁️  INTEGRAÇÕES & API KEYS             │  ← seção fixa no topo
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │ Google   │ │ OneDrive │ │ + Add    │ │
│  │ Drive ✓  │ │     ✓    │ │ Key/API  │ │
│  └──────────┘ └──────────┘ └──────────┘ │
├─────────────────────────────────────────┤
│  🤖 IAs                                 │
│  [Ollama] [llama.cpp] […]               │
│  📦 Linguagens / Runtimes               │
│  [Node] [Python] [Rust] […]             │
│  🔧 Ferramentas                         │
│  [git] [curl] [htop] […]                │
│  ⚙️ Sistema (protegidos)                │
│  [glibc] [apt] [bash] […]               │
└─────────────────────────────────────────┘
```

#### A. Seção Integrações & API Keys (D8)

Cards por provedor / serviço:

| Card | Conteúdo | Ações |
|---|---|---|
| Google Drive | Status conectado/desconectado, e-mail | Conectar (OAuth) · Desconectar · Fazer backup agora · Ver backups remotos |
| OneDrive | Idem | Idem (Microsoft Graph) |
| Dropbox / S3 / WebDAV | Idem | Configurar endpoint + keys |
| GitHub | Login / PAT mascarado | Conectar · Revogar |
| OpenAI / Anthropic / Groq / Gemini / HF | Key mascarada `sk-…xxxx` | Adicionar · Editar · Expor ao Linux (toggle) · Copiar `$VAR` · Apagar |
| **+ Adicionar** | Modal | Tipo (cloud / IA / custom) → nome da variável → valor → toggle “expor ao Linux” |

**Modal “Adicionar API Key” (design):**
- Fundo Deep Slate, borda cyan/roxo
- Campos: Nome amigável · Nome da variável de ambiente (`OPENAI_API_KEY`) · Valor (oculto) · Toggle “Disponível no terminal Linux”
- Botões: Cancelar (outlined cyan) · Salvar (Primary purple)

**Backup em nuvem a partir da Toolbox:**
- Card do provedor → “Backup agora” → escolhe ZIP/7z → upload
- “Ver backups” → lista remota → Restaurar / Excluir

#### B. Seções de pacotes do Linux (scanner automático)

| Categoria | Origem da detecção | Ações no card |
|---|---|---|
| **IAs** | ollama, llama.cpp, gpt4all, binários conhecidos | Detalhes · Desinstalar · Abrir no Terminal |
| **Linguagens / Runtimes** | node, python, rustc, go, java… | Idem |
| **Ferramentas** | git, curl, vim, htop, make… | Idem |
| **Sistema** | glibc, apt, bash, coreutils, systemd… | Só detalhes (**sem** desinstalar) |
| **Outros** | Resto | Idem |

Cada card mostra: nome, versão, tamanho aproximado, tag verde se “rodando”.

#### C. Comportamento geral da Toolbox
- Busca no topo filtra cards de todas as seções
- Após `apt install` / `pip install` no terminal → scanner atualiza sozinho
- Pull-to-refresh força novo scan
- Visual: cards Deep Slate, ícones line-art, glow no ativo, bordas angulares

### 11.9 Settings (página full-screen) — organizada por seções (D14)

- **Ambiente VM (D13):** start/stop/reset · presets CPU/RAM · toggle “Usar todo o poder do aparelho” · auto-stop em background · baixar/trocar distro · indicadores ao vivo
- **Backup local:** ZIP/7z + SAF; restaurar; aviso antes de desinstalar distro
- **Backup cloud & Integrações:** atalho Toolbox; backup automático
- **Git / Conta / SSH:** token, chaves SSH (Keystore), SSH remoto
- **Editor:** fonte, tamanho, ligaduras, word wrap, tema de syntax, format on save
- **Aparência & Temas (D10):** presets, color pickers, glow, bordas, Activity Bar, tema do Terminal, export/import JSON
- **Teclado & Atalhos:** mapa editável + Command Palette
- **Sobre:** versão, privacidade, changelog, onboarding de novo

### 11.11 Princípio de organização — muitas funções ≠ bagunça (D14)

| Regra | Aplicação |
|---|---|
| **Bottom Nav fixa em 5** | Explorer · Search · Git · Toolbox · Settings — **não** criar ícone novo a cada feature |
| **Features avançadas em painéis** | AI Suite, Ports, Problems = overlay/painel, não nova aba raiz |
| **Command Palette** | Ponto único para achar qualquer ação (teclado ou botão) |
| **Toolbox por categorias** | Integrações · IAs · Linguagens · Ferramentas · Sistema |
| **Settings por seções colapsáveis** | Uma página, âncoras claras — não dezenas de sub-apps |
| **Progressive disclosure** | Avançado escondido atrás de “Avançado” / long-press / Palette |
| **Onboarding enxuto** | Só o essencial na 1ª vez; o resto descobre depois |

### 11.12 Funções recomendadas para completude (priorizadas, D14)

**Já decidido (D1–D20):** VM, distros, Phantom Base, design Gemini, multi-terminal, AI Suite, temas, cloud/keys, modos SPCK/terminal, auto-save, storage opt-in, **iniciar Linux na abertura (D20)**.

**Camada A — produtividade IDE (Fase 2)**  
Command Palette · atalhos · templates · snippets · format on save · Problems · onboarding · exportar projeto  

**Camada B — dev server & remoto (Fase 3)**  
Ports & Services · SSH remoto · watch/live reload  

**Camada C — depois (Fase 4)**  
Docker na Toolbox · LSP · debug · widget · tablet/DeX · plugins leves  

### 11.13 O que ainda indica para completar (pós-D20)

| Prioridade | Item | Por quê |
|---|---|---|
| Alta | Command Palette | Muitas ações, zero bagunça na nav |
| Alta | Onboarding (Phantom Base + D20) | 1º uso claro |
| Alta | PoC VM + 9p + terminal | Valida o produto |
| Média | Ports / abrir no browser | Loop dev server |
| Média | SSH remoto · templates | Uso diário |
| Baixa | LSP, debug, Docker UI | Depois do MVP |

**Não priorizar agora:** rede social in-app, loja de temas online, IDE multi-usuário na nuvem.

### 11.10 Fluxos críticos (resumo)
1. **Clone** → auth → URL → pasta em workspace → abre projeto  
2. **Baixar distro** → escolha → validação (arch/checksum/espaço) → extrai em `linux/`  
3. **Trocar distro** → pergunta backup → remove rootfs → workspace intacto  
4. **Backup local** → ZIP/7z → SAF → progresso  
5. **Backup cloud** → provedor OAuth/key → ZIP/7z → upload  
6. **Restaurar** → local (SAF) ou cloud → validação → merge em workspace  
7. **Adicionar API key** → Toolbox → modal → Keystore → (opcional) expor ao Linux via env  
8. **Run** → inicia VM se necessário → executa no cwd do projeto (com env de secrets se habilitado)  

---

## 12. Segurança, privacidade e permissões Android

### 12.1 Princípios
- Sandbox Android + SELinux enforcing
- **Scoped storage (SAF)** — evitar `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` amplos
- QEMU isola o kernel do guest; proot não isola kernel (mesmo sandbox do app)
- Secrets no **Android Keystore**; exposição ao Linux só com toggle
- Backup não inclui secrets por padrão; cloud opt-in (D8)
- **DCL (Play Store):** download de rootfs em runtime é zona cinzenta → D6 (sideload/F-Droid/import)

### 12.2 Permissões Android — para rodar sem gargalo artificial

Objetivo: a VM, rede, terminal e backups funcionarem **sem o sistema matar o processo** ou bloquear I/O/rede por falta de permissão correta.  
(Gargalo de **emulação TCG** continua existindo; aqui tratamos só de **permissões e ciclo de vida**.)

#### Obrigatórias / fortemente recomendadas

| Permissão / API | Para quê | Nota |
|---|---|---|
| `INTERNET` | `apt`, `pip`, git, OAuth, backup cloud, download de distro | Normal; sem isso o guest e o app ficam offline |
| `ACCESS_NETWORK_STATE` | Detectar rede antes de download/backup | Leve; melhora UX |
| **Foreground Service** (`FOREGROUND_SERVICE` + tipo adequado, ex. `specialUse` / `dataSync` conforme política vigente) | Manter **QEMU/terminal vivos** com app em segundo plano | **Crítico** — sem FGS o Android mata a VM em background |
| Notificação persistente da FGS | Exigência do sistema ao rodar FGS | Texto claro: “Phantom-Code · ambiente Linux ativo” |
| `POST_NOTIFICATIONS` (API 33+) | Aviso de fim de `apt`/backup/clone + FGS | Pedir em runtime |
| Armazenamento **do app** (`context.filesDir` / `noBackupFilesDir`) | `linux/`, `workspace/`, QEMU images | **Sem** permissão perigosa — é o caminho principal |
| **SAF** (`ACTION_CREATE_DOCUMENT` / `OPEN_DOCUMENT` + `takePersistableUriPermission`) | Backup ZIP/7z, import rootfs, export | Substitui storage amplo |

#### Opcionais (pedir só quando o usuário ativar a função)

| Permissão / API | Para quê | Cuidado |
|---|---|---|
| `WAKE_LOCK` | Evitar deep sleep com VM no máximo (D13) | Usar com moderação; preferir FGS |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Usuário opta por não restringir o app | Sensível na Play; explicar o motivo (VM longa) |
| Contas / OAuth (Google, Microsoft) via SDK | Drive / OneDrive | Não precisa de permissão de “ler todos os arquivos” |
| `VIBRATE` | Feedback tátil em ações | Opcional |

#### Storage amplo — **opcional, só se o usuário quiser** (D19)

**Padrão do app:** pasta interna + **SAF** (sem pedir acesso a todos os arquivos).  
**Avançado (opt-in):** o usuário **pode** ativar acesso mais amplo, se quiser usar pastas livres no armazenamento compartilhado.

| Nível | Como | Quando usar |
|---|---|---|
| **0 — Padrão** | Só `filesDir` do app + SAF por arquivo | A maioria dos usuários; melhor para Play |
| **1 — Pasta escolhida (recomendado se quiser “livre”)** | `ACTION_OPEN_DOCUMENT_TREE` (SAF tree) — usuário escolhe **uma pasta** (ex.: `/Documents/Phantom`) | Workspace ou backups numa pasta visível no gerenciador de arquivos, **sem** “todos os arquivos” |
| **2 — Storage amplo (opt-in explícito)** | `MANAGE_EXTERNAL_STORAGE` / “Acesso a todos os arquivos” **ou** legado conforme API | Só se o usuário **ligar** em Settings → Armazenamento avançado |

**Regras de produto (D19):**
1. **Nunca** pedir storage amplo na primeira abertura  
2. Settings → **Armazenamento:** explicar diferença (padrão / pasta SAF / amplo)  
3. Opt-in nível 2: tela de aviso clara (“o app poderá ver outros arquivos do aparelho”) + botão para a tela do sistema  
4. Se a permissão for negada ou revogada → voltar ao modo padrão sem quebrar o app  
5. **Play Store:** nível 2 pode ser rejeitado ou exigir declaração; mitigação = feature atrás de flag, ou mais livre em **sideload/F-Droid**; preferir empurrar o **nível 1 (SAF tree)** como “acesso a pasta livre”  
6. Workspace amplo, se ativado, continua isolado da rootfs da distro (D3)

#### Não usar como dependência obrigatória

| Permissão | Nota |
|---|---|
| `MANAGE_VIRTUAL_MACHINE` (AVF) | Só system apps — **não** depender disso |
| Root / `su` | Fora do escopo do produto |

### 12.3 Ciclo de vida — o que mais evita “gargalo” na prática

| Prática | Efeito |
|---|---|
| **Foreground Service** enquanto a VM está `RUNNING` | Reduz kill por battery/standby |
| Notificação com ação **Parar VM** | Transparência + controle do usuário |
| Auto-stop da VM em background (default) + opção “manter ativa” (D13) | Equilíbrio bateria × poder |
| QEMU/`libqemu` em `jniLibs` (executável pelo linker Bionic) | Evita bloqueio SELinux de `execve` em `app_data_file` solto |
| Workspace e rootfs no armazenamento interno do app | I/O estável, sem depender de permissão de SD |
| Downloads (distro, backup cloud) com retry e Wi-Fi preferencial | Menos falha por rede |

### 12.4 Manifest — esboço de orientação

```xml
<!-- Rede -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- VM / processo longo -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- tipo concreto conforme targetSDK / política Play (ex.: specialUse ou dataSync) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- NÃO declarar MANAGE_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE legado se target moderno -->
```

Serviço: `android:foregroundServiceType` alinhado à declaração; justificativa Play se `specialUse`.

### 12.5 Privacidade (resumo)
- Projetos e secrets no aparelho por padrão  
- Cloud e exposição de keys ao Linux = opt-in  
- Política de privacidade clara: o que a FGS e a rede fazem  

### 12.6 Notificações do app

| Tipo | Quando | Conteúdo / ações |
|---|---|---|
| **FGS — ambiente ativo** | VM/terminal em execução (modo App+Terminal ou Terminal em background) | “Phantom-Code · Linux ativo” · ações: **Abrir app** · **Parar VM** |
| **Tarefa concluída** | Fim de backup, download de distro, clone, `apt` longo (se o app supervisionar) | Sucesso/erro · toque abre o contexto |
| **Auto-save / recuperação** | Após crash ou reinício com sessão recuperada | “Sessão restaurada” (discreto, opt-in) |
| **Lembrete opcional** | Backup cloud agendado falhou (sem rede) | Tentar de novo |

- Respeitar `POST_NOTIFICATIONS` (API 33+)
- Canal separado: `ambiente` (alta importância, FGS) · `tarefas` · `sistema`
- Usuário pode silenciar canais não-FGS nas Settings do Android

### 12.7 App permanece utilizável ao reabrir (estilo Termux) — D18

| Comportamento | Detalhe |
|---|---|
| **Reabrir o app** | Volta para a **última sessão**: projeto, abas do editor, scroll, terminal dock aberto/fechado |
| **Processo** | Se a FGS estiver ativa, a VM **continua** de onde estava; se não, editor abre em modo App-only até o usuário iniciar o ambiente |
| **Estado salvo** | `config/session.json` (+ drafts de arquivos) |
| **Não confundir com “não pode fechar”** | O usuário pode forçar parada; o app não tenta burlar o sistema |

### 12.8 Auto-save e proteção contra desligamento — D18

| Opção | Padrão | Descrição |
|---|---|---|
| **Auto-save ao editar** | On (debounce 1–2 s) | Grava buffer no `workspace/` sem precisar Ctrl+S |
| **Auto-save ao sair / background** | On | Flush de todos os buffers sujos |
| **Recuperar rascunhos** | On | Se o app morrer no meio da digitação, reabre com conteúdo recuperável |
| **Confirmar ao fechar aba com alterações** | On se auto-save off | Segurança extra |
| **Snapshot leve da sessão** | On | Projeto ativo, abas, terminais (IDs), modo de operação |

Em desligamento do aparelho ou kill do sistema: na próxima abertura → restaurar sessão + avisar se a VM precisa ser iniciada de novo.

---

## 12A. Modos de operação do app (D17)

O Phantom-Code **não obriga** a VM ligada o tempo todo. Três modos claros (como SPCK vs Termux combinados):

| Modo | VM / Terminal | O que funciona | Analogia |
|---|---|---|---|
| **A — App-only** | **Desligado** — sem instalar/usar terminal na sessão | Editor, Explorer, Search, Git (JGit), temas, backup local, API keys UI, abrir projetos | **SPCK Editor** |
| **B — App + Terminal** | **Ligado** junto com o app | Tudo do modo A + shell real, apt/pip, Toolbox scanner do guest, AI local no guest, Run no Linux | IDE completo Phantom |
| **C — Terminal em background** | VM **ativa** com FGS mesmo com UI em segundo plano | Continua processos (`npm run dev`, ollama, servers); notificação com Parar VM | Termux com sessão persistente |

### Como o usuário escolhe

- **Settings → Ambiente:** modo padrão ao abrir (A, B ou “perguntar”)
- **Toggle rápido** na Top Bar / status: `Terminal OFF` · `Terminal ON`
- **Primeira vez:** pode começar em **App-only** (editar na hora) e só depois “Instalar Phantom Base + Iniciar terminal”
- **Sem distro instalada:** força modo A até haver rootfs (não quebra o editor)

### Iniciar Linux na abertura do app (D20) — escolha do usuário

| Setting | Valores | Comportamento |
|---|---|---|
| **Iniciar Linux ao abrir o app** | **Desativado** (padrão recomendado na 1ª instalação) | Abre em modo **App-only** (editor na hora, como SPCK); VM só quando o usuário pedir |
| | **Ativado** | Ao abrir o app (e havendo distro instalada), inicia a VM automaticamente → modo **App + Terminal** |
| | **Perguntar sempre** | Dialog na abertura: “Iniciar ambiente Linux?” |

**Regras:**
1. Preferência em **Settings → Ambiente → Iniciar Linux na abertura** (toggle claro)
2. Se **Desativado**: zero espera de boot da VM; ideal para só editar código  
3. Se **Ativado**: pode mostrar splash/status “Iniciando QEMU…”; resume snapshot se existir  
4. Sem distro instalada: ignora o toggle e fica App-only + CTA para instalar Phantom Base  
5. Combina com D13 (poder do aparelho) e FGS se o usuário também mantiver terminal em background  
6. A escolha é **100% do usuário** — o app não força VM ligada

### Regras de produto

1. **Modo A nunca bloqueia** edição, Git local nem backup — zero dependência de QEMU  
2. **Modo B/C** exigem distro (Phantom Base ou outra) + permissões FGS/rede  
3. Trocar A → B = boot da VM (ou resume snapshot); B → A = para VM com confirmação se houver processos  
4. Bottom Nav igual nos três modos; no modo A, Terminal dock mostra CTA “Iniciar ambiente Linux” em vez do shell  
5. Toolbox no modo A: mostra integrações/API keys; pacotes do guest ficam “Indisponível até iniciar terminal”

### Resumo visual

```
[Modo A — SPCK-like]     Editor + Git + arquivos     VM ■ parada
[Modo B — IDE full]      Editor + Terminal + Linux   VM ● RUNNING
[Modo C — Background]    UI fechada/minimizada       VM ● + notificação FGS
```

---



## 13. Performance

| Cenário | QEMU TCG | proot (fallback) |
|---|---|---|
| CPU puro | ~15–20% nativo | ~nativo |
| apt / npm install | Funciona, lento | Mais rápido |
| Scripts Python/Node | Usável | Quase nativo |
| IA (Ollama etc.) | Roda, inferência lenta (só CPU) | Rápido |
| Boot | Lento (snapshot ajuda) | Rápido |
| Bateria/RAM | Alto | Baixo-médio |

Aviso claro na UI sobre performance da VM em aparelhos sem KVM.

---

## 14. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| VM lenta (TCG) | Aviso na UI; proot como fallback opcional |
| Boot lento | Snapshot de estado |
| APK grande (QEMU) | Rootfs **não** embutida (D1); split por ABI |
| Bateria em background | Auto-stop da VM |
| Play Store DCL | Preferir sideload/F-Droid ou import de arquivo (D6) |
| Usuário quebra o ambiente | Pacotes Sistema protegidos + confirmação em desinstalar |
| Backup corrompido | Checksum + validação na restauração |

---

## 15. Roadmap

**Fase 1 — MVP**  
Editor + Explorer + PoC VM (boot headless + 9p + terminal + `apt install`) + workspace compartilhado.

**Fase 2 — Git, distros, produtividade**  
JGit, Toolbox (scanner + cards), Gerenciador de Distros (D1), Backup ZIP/7z local (D2).

**Fase 3 — Robustez + Integrações + Personalização + AI Suite**  
Gerenciador de ambiente, snapshot de boot, medição de performance.  
Toolbox — Integrações & API Keys (D8).  
Backup cloud (Drive/OneDrive…).  
Temas & terminal customizável (D10).  
**Múltiplos terminais + split (D11).**  
**Phantom AI Suite — router + contexto compartilhado (D12).**  
**VM: presets + “todo o poder do aparelho” (D13).**  
Command Palette, Ports & Services, SSH remoto, Problems, format on save (Camada A/B).

**Fase 4 — Refinamento**  
Multi-projeto, Docker na Toolbox, LSP/debug leves, widget, tablet/DeX, mais cloud, plugins leves — sempre dentro da estrutura D14.

**Critério de sucesso Fase 1:** criar arquivo no editor → `ls` no terminal vê o mesmo arquivo → `apt install` funciona.

---

## 16. Testes e validação

- Unitário: editor, JGit, scanner Toolbox, validação de distro  
- Integração: terminal ↔ VM, 9p, backup/restauração  
- Dispositivo real: 1 aparelho KVM-capable + 1 sem KVM  
- PoC obrigatória: boot QEMU + 9p + terminal + apt + medições de performance  

---

## 17. Distribuição

| Canal | Prós | Contras |
|---|---|---|
| Sideload APK | Sem restrição DCL | “Fontes desconhecidas” |
| F-Droid | Ideal para este tipo de app | Público menor |
| Google Play | Alcance máximo | Risco DCL com download de distro |

Builds: `arm64-v8a` principal. Versionamento semântico + changelog no app.

---

## 18. Glossário

| Termo | Significado |
|---|---|
| **QEMU** | Emulador/virtualizador open-source; no Android comum roda em TCG (sem KVM) |
| **TCG** | Tiny Code Generator — emulação por software (~15–20% da velocidade nativa) |
| **virtio-9p / 9pfs** | Protocolo de compartilhamento de pasta host ↔ guest (mesmos arquivos) |
| **SLIRP** | Rede NAT em modo usuário do QEMU (internet no guest sem root) |
| **proot** | Tradução de syscalls via ptrace — “chroot sem root”; **não é VM** |
| **rootfs** | Sistema de arquivos raiz de uma distro Linux (pasta ou imagem) |
| **Bionic** | libc do Android (usada pelo Termux e apps nativos) |
| **glibc** | libc do GNU/Linux desktop (Ubuntu, Debian, etc.) |
| **Termux** | Terminal + pacotes nativos Bionic; **não** é baseado em QEMU (opcionalmente empacota QEMU) |
| **SAF** | Storage Access Framework — seletor de arquivos do Android (scoped storage) |
| **DCL** | Dynamic Code Loading — política do Google Play sobre código baixado em runtime |
| **AVF / pKVM** | Android Virtualization Framework / protected KVM — restrito a apps de sistema |
| **crosvm** | VMM em Rust usado pelo AVF e ChromeOS |
| **MTTCG** | Emulação TCG multithread (usa vários cores do aparelho) |
| **headless** | VM sem interface gráfica — só console/terminal |

---

## 19. Referências

### Virtualização e ambiente Linux no Android
- [Termux execution environment](https://github.com/termux/termux-packages/wiki) — Termux roda nativo em Bionic, **não** em VM
- [Termux Wiki — Differences from Linux](https://wiki.termux.com) — Bionic vs glibc, FHS
- Termux packages: builds `qemu-system-aarch64` com virtio-9p e SLIRP (referência de compilação para Android)
- [vmConsole](https://github.com/markbirss/vmConsole) — Alpine em QEMU no Android, 9p, sem root
- [Limbo](https://github.com/limboemu/limbo) — QEMU embutido em APK (x86/ARM)
- [Podroid](https://github.com/ExTV/Podroid) — scripts de build QEMU para Android com virtfs
- QEMU docs oficiais: máquina `virt` arm64, `-virtfs`/9p, TCG, SLIRP, `-nographic`
- AOSP — Android Virtualization Framework / pKVM / crosvm (API restrita a system apps)
- Wikipedia — Comparison of OS emulation or virtualization apps on Android

### Android e distribuição
- Android Storage Access Framework (SAF) + scoped storage
- Google Play Developer Program Policy — Dynamic Code Loading (DCL)

### Stack do app
- CodeMirror 6, jackpal / Termux terminal-emulator, JGit, SevenZipJBinding-4Android
- Ubuntu Base, Alpine mini rootfs, debootstrap

### Design
- Mockups Gemini agosto/2026 (logo fantasma/shield, Home, Workspace, Git, Kit de marca)

---

## 20. Próximos passos

1. **PoC da VM (D5):** QEMU headless + 9p + terminal + install de ferramenta real; medir performance.
2. **Protótipo Phantom Base (D16):** escolher upstream (Alpine vs Debian minbase) e gerar 1ª rootfs.
3. Fechar **D6** (distribuição) e validar **D7** (CodeMirror 6 no aparelho).
4. Fase 1: editor + explorer + workspace + modos A/B (D17) + D20.
5. Manter este **Documento Mestre** como única fonte de verdade.

---

## 21. Revisão de alinhamento (2026-08-05)

### 21.1 Resultado geral

| Área | Status | Nota |
|---|---|---|
| Núcleo técnico (QEMU, 9p, workspace, terminal-only) | ✅ Alinhado | D5 + D3 + D4 consistentes em todo o doc |
| Distros (oficial + catálogo + import) | ✅ Alinhado | D1 + D16; Ubuntu/Kali explícitos; só arm64 headless |
| Design Gemini + Activity Bar | ✅ Alinhado | D15; regras para IA na §10.0 |
| Modos SPCK / terminal / background | ✅ Alinhado | D17 + D20 + FGS §12 |
| Backup / keys / cloud | ✅ Alinhado | D2 + D8; Keystore; não misturar secrets no workspace |
| Organização UI | ✅ Alinhado | D14; Bottom Nav 5; AI Suite em painel |
| Browser OAuth | ✅ Alinhado | D21; sem browser pesado no guest |
| Permissões / storage | ✅ Alinhado | FGS obrigatória para VM; storage amplo só opt-in D19 |
| Em aberto | 🔶 | **D6** canal loja · **D7** validação final do editor |

### 21.2 Coerências verificadas (sem conflito)

- **D1 ≠ embutir rootfs no APK** e **D16 Phantom Base por download** → compatíveis  
- **D17 App-only** e **D5 QEMU oficial** → VM não é obrigatória o tempo todo  
- **D20 off** = nasce SPCK; **D20 on** = sobe Linux → coerente com D17  
- **D13 poder máximo** + **auto-stop background** → escolha do usuário, não contradição  
- **D12 AI Suite** fora da Bottom Nav → respeita D14  
- **D21 browser no app** + guest headless → respeita D5 terminal-only  
- **Termux ≠ QEMU** documentado → narrativa clara  

### 21.3 Pontos de atenção (não são bloqueios)

| Item | Ação sugerida |
|---|---|
| Ordem interna §11 (11.10 após 11.13) | Cosmético; conteúdo ok |
| D6 em aberto | Decidir antes do release público |
| D7 | Confirmar CodeMirror 6 no device real na Fase 1 |
| Phantom Base upstream | Fechar Alpine vs Debian na PoC (D16) |
| Play + DCL + download de distro | Ligado a D6; sideload/F-Droid mitiga |
| Performance TCG | Expectativa já documentada §13; aviso na UI |

### 21.4 Conclusão da revisão

O Documento Mestre está **alinhado e bem definido** para começar a PoC e a Fase 1.  
As únicas decisões de produto ainda abertas são **D6** (onde distribuir) e a **validação prática de D7** (editor). Todo o restante (D1–D5, D8–D21) forma um contrato coerente: IDE mobile Cyber-Phantom + Linux real opcional + liberdade de distro + organização sem bagunça.

---

*Documento vivo unificado — atualizar aqui qualquer decisão técnica, de design ou de produto.*  
*Versão 4.4 — Revisão de alinhamento §21; tabela D1–D21 ordenada; índice atualizado.*
