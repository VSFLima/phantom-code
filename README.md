<div align="center">

# 👻 PHANTOM-CODE

**IDE Android** — Editor de código · **Terminal Linux real** (VM QEMU) · Git/GitHub · IA

> *"O que o editor cria, o terminal e a IA veem na hora."*

```
┌─────────────────────────────────────────────┐
│  👻 PHANTOM-CODE          ● QEMU LINUX: RUNNING │
│  ┌────┬────────────────────────────────────┐ │
│  │ 📁 │  Editor (CodeMirror 6)             │ │
│  │ 🔍 │                                    │ │
│  │ 💎 │  ── Terminal dock ──               │ │
│  │ 🧰 │  root@phantom:~/workspace$         │ │
│  └────┴────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

**Nome real:** Phantom-Code · **Codinome dev:** Dark-Code · **Versão:** 0.1.0
**Autor:** VSFLima / Asgard

</div>

---

## ✨ O que é o Phantom-Code?

Um **IDE Android completo** que une, em um único app:

| 🔧 | O que faz |
|---|---|
| **Editor** | CodeMirror 6 com abas, syntax neon e autosave |
| **Terminal Linux real** | VM **QEMU headless** (glibc completo) — `apt`, `pip`, `npm`, `ollama` funcionam como em qualquer Linux ARM64 |
| **Git/GitHub** | Clone, commit, push, branches e diff direto na UI (JGit) |
| **Workspace único** | Editor e terminal operam na **mesma camada de arquivos** (virtio-9p) — sem sincronização, sem cópia |
| **Toolbox** | Scanner de pacotes do Linux + Integrações & API Keys (Android Keystore) |
| **Temas** | Design **Cyber-Phantom** 100% personalizável (presets + cores custom) |

### 🧠 Por que QEMU (e não "mais um Termux")?

| | Termux | **Phantom-Code** |
|---|---|---|
| libc | Bionic (quebra libs desktop) | **glibc completo** (binários originais) |
| Kernel | Mesmo do Android | **Guest isolado** (systemd funciona) |
| Ferramentas de IA | Precisam de porte | **Rodam nativas** (Ollama, llama.cpp…) |
| Integração IDE | Não existe | **Editor + Terminal + Git na mesma pasta** |

> Detalhes técnicos completos no [Documento Mestre](Phantom-Code-Documento-Mestre-3.md) (v4.4).
> Notas de implementação: [docs/arquitetura-fase-2.md](docs/arquitetura-fase-2.md)

---

## 📱 Funcionalidades por fase

| Fase | Status | O que inclui |
|------|:------:|--------------|
| **0 — Fundação** | ✅ | Projeto Android (Kotlin + Compose), tema Cyber-Phantom, CI no GitHub Actions |
| **1 — Shell da UI** | ✅ | Bottom Nav (5 itens), Home, Activity Bar, Settings com temas, Terminal dock |
| **2 — Editor + Explorer** | ✅ | CodeMirror 6, explorer real do workspace, autosave + sessão |
| **3 — VM + Terminal** | ⬜ | QEMU arm64, Phantom Base, virtio-9p/serial, terminal jackpal |
| **4 — Git + Toolbox** | ⬜ | JGit, Toolbox + API Keys, backup local/cloud |
| **5 — Polish** | ⬜ | Foreground Service, onboarding, testes no device, distribuição |

Plano detalhado: **[TAREFAS-PHANTOM.md](TAREFAS-PHANTOM.md)** (T1–T26).

---

## 🎨 Design System — Cyber-Phantom / Neon Dark IDE

Estilo **OLED preto + neon roxo/cyan**, bordas angulares e glow — a identidade do app também é a identidade deste repositório.

| Token | HEX | Amostra |
|-------|-----|:-------:|
| `bg-primary` (fundo) | `#000000` | <span style="background-color:#000000;border:1px solid #4B5563;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `bg-surface` (painéis) | `#121212` | <span style="background-color:#121212;border:1px solid #4B5563;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `accent-primary` (ações) | `#9F4DFF` | <span style="background-color:#9F4DFF;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `accent-bright` (hover) | `#D34DFF` | <span style="background-color:#D34DFF;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `accent-secondary` (cyan) | `#00FFFF` | <span style="background-color:#00FFFF;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `success` (status) | `#00FF9F` | <span style="background-color:#00FF9F;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `error` | `#FF3366` | <span style="background-color:#FF3366;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `text-primary` | `#E5E7EB` | <span style="background-color:#E5E7EB;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |
| `border` | `#4B5563` | <span style="background-color:#4B5563;display:inline-block;width:24px;height:24px;border-radius:4px"></span> |

**7 presets de tema:** Phantom · Deep Slate · Matrix · Dracula · Nord · Solarized Dark · Light Soft · **Custom** (color pickers).

**Navegação (regras D14/D15):**
- Bottom Nav **fixa com 5 itens**: Explorer · Search · Git · Toolbox · Settings *(nunca um 6º)*
- **Activity Bar** lateral fina (~50dp) só com ícones — painéis abrem sob demanda
- Terminal dock no rodapé com abas múltiplas (Terminal 1/2/Run)

---

## 📁 Estrutura do repositório

```
Phantom-Code/
├── android/                    ← 📱 Projeto Android (Kotlin + Jetpack Compose)
│   ├── app/                    ← Módulo principal
│   │   └── src/main/java/com/phantomcode/app/
│   │       ├── ui/theme/       ← Paletas Cyber-Phantom + ThemeController (D10)
│   │       ├── ui/components/  ← Scaffold, ActivityBar, BottomNav, TerminalDock, widgets
│   │       ├── ui/screens/     ← Home, Explorer, Search, Git, Toolbox, Settings
│   │       └── ui/navigation/  ← Rotas + BottomNavItems (5 itens)
│   ├── gradle/                 ← Wrapper (8.11.1) + version catalog
│   └── (builds pelo CI — sem SDK local)
├── .github/workflows/build.yml ← CI: gera o APK assinado
├── docs/                       ← Documentação de desenvolvimento
├── scripts/                    ← generate-keystore.sh e utilitários
├── GIT.md                      ← 📖 Guia completo de Git/CI deste projeto
├── TAREFAS-PHANTOM.md          ← Plano de tarefas (T1–T26)
├── Phantom-Code-Documento-Mestre-3.md ← Documento Mestre (arquitetura, D1–D21)
├── config/ · linux/ · workspace/ · backups/  ← Runtime (gitignored)
└── README.md                   ← Esta página
```

---

## 🚀 Build — GitHub Actions

Não há SDK Android no ambiente local (Termux/PRoot) → **todo build roda no GitHub Actions**.

| Passo | Comando |
|-------|---------|
| 1. Push | `git push origin main` |
| 2. CI | Workflow `build.yml` → `./gradlew :app:assembleRelease` |
| 3. APK | Artifact **`phantom-code-apk`** (release + debug) |

### 🔑 Assinatura (release)

```bash
bash scripts/generate-keystore.sh    # gera android/release.keystore + imprime base64
```

Adicione no repositório → **Settings → Secrets and variables → Actions**:

| Secret | Valor |
|--------|-------|
| `PHANTOM_KEYSTORE_BASE64` | `base64 -w 0 release.keystore` |
| `PHANTOM_STORE_PASSWORD` | senha do keystore |
| `PHANTOM_KEY_ALIAS` | alias (padrão: `phantom`) |
| `PHANTOM_KEY_PASSWORD` | senha da chave |

> Sem os secrets o CI gera um APK release **não assinado** (ou use o debug).

---

## 🔐 Git & Workflow

Guia completo (comandos, convenções de commit, branches, CI, troubleshooting): **[GIT.md](GIT.md)**

Resumo:

```bash
git init
git add -A
git commit -m "feat(fase-1): shell da UI Cyber-Phantom"
git remote add origin git@github.com:VSFLima/phantom-code.git
git push -u origin main
```

---

## 🗺️ Roadmap (TAREFAS-PHANTOM.md)

- ✅ **T1–T5** — Fase 0: Fundação (estrutura, Gradle, tema, CI, APK)
- ✅ **T6–T10** — Fase 1: Shell da UI (navegação completa, temas)
- ✅ **T11–T13** — Fase 2: Explorer real + Editor CodeMirror 6 + auto-save/sessão
- ⬜ **T14–T18** — Fase 3: VM QEMU + Terminal (coração do app)
- ⬜ **T19–T22** — Fase 4: Git (JGit) + Toolbox + Backup
- ⬜ **T23–T26** — Fase 5: Polish + distribuição

---

<div align="center">

**👻 Phantom-Code** — *Projeto proprietário · uso autorizado apenas*

**VSFLima / Asgard** · 🔮 Ideia → Código → APK

</div>
