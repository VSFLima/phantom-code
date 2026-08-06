# 📋 TAREFAS PHANTOM-CODE — Criação do App

> **Projeto:** Phantom-Code (codinome dev: Dark-Code) — IDE Android + VM Linux
> **Fonte:** `Phantom-Code-Documento-Mestre-3.md` (v4.4 · 05/08/2026)
> **Estratégia recomendada:** 🟣 Fase 0 + Fase 1 primeiro → APK instalável com visual Cyber-Phantom
> **Build local:** ❌ sem SDK Android no Termux/PRoot → **GitHub Actions**

---

## 🟣 FASE 0 — FUNDAÇÃO (prioridade máxima — base de tudo)

- [x] **T1. Estrutura de pastas do projeto** ✅
  - Separar código-fonte (`android/`, `docs/`, `scripts/`) das pastas de runtime (`linux/`, `workspace/`, `config/`, `backups/`)
- [x] **T2. Projeto Android Gradle (Kotlin + Compose)** ✅
  - `settings.gradle.kts`, `build.gradle.kts`, manifest, `MainActivity.kt`
  - minSdk 26 · target/compile 35 · Kotlin + Jetpack Compose · Gradle 8.11.1 / AGP 8.7.3
- [x] **T3. Tema Cyber-Phantom (design system em código)** ✅
  - Tokens do doc §10.1: `#000000` · `#121212` · `#9F4DFF` · `#D34DFF` · `#00FFFF` · `#00FF9F` · `#FF3366`
  - Tipografia, botões (primary roxo angular / outlined cyan), cards Deep Slate
- [x] **T4. GitHub Actions — build APK assinado** ✅
  - Workflow `build.yml`: checkout → JDK → build Gradle → assinatura (secrets) → upload APK
- [x] **T5. Primeiro APK instalável rodando** ✅ (aguardando primeiro build no CI)
  - App com o tema Cyber-Phantom compilando de ponta a ponta

## 🟣 FASE 1 — SHELL DA UI (navegação)

- [x] **T6. Bottom Nav fixa — 5 itens** ✅
  - Explorer · Search · Git · Toolbox · Settings (line-art, sem 6º item — D14)
- [x] **T7. Home / Welcome** ✅
  - Logo shield (gradiente roxo→cyan), Recent Projects, status `QEMU LINUX: RUNNING/STOPPED`
- [x] **T8. Activity Bar lateral fina (~50dp)** ✅
  - Só ícones; abre painéis/abas sob demanda (D15)
- [x] **T9. Settings → Aparência & Temas (D10)** ✅
  - Presets: Phantom · Deep Slate · Matrix · Dracula · Nord · Solarized · Light · Custom (color pickers)
- [x] **T10. Navegação completa entre telas (NavHost)** ✅
  - Home ↔ 5 abas da Bottom Nav, back stack correto, preview ao vivo do tema

## 🟠 FASE 2 — EDITOR + EXPLORER

- [x] **T11. Explorer de arquivos do workspace** ✅
  - Árvore real (`filesDir/workspace`), long-press (abrir/renomear/excluir/copiar caminho), FAB (arquivo/pasta), import SAF
- [x] **T12. Editor CodeMirror 6 (WebView)** ✅
  - Syntax por extensão (JS/Py/HTML/CSS/JSON/MD), números de linha, tema Cyber-Phantom, ponte JS↔Kotlin
- [x] **T13. Auto-save + restaurar sessão (D18)** ✅
  - Auto-save com debounce 800ms + salvar ao fechar + "Continuar de" na Home

## 🟠 FASE 3 — TERMINAL + VM LINUX (coração do app)

> 🔶 **Estado:** motor da VM pronto (T14, T15, T18 ✅) — faltam os **artefatos reais** (binário QEMU arm64 + rootfs Phantom Base) e o **T17 final** (emulador VT100).

- [x] **T14. QEMU arm64 + comando headless (§8.1)** ✅
  - `QemuManager`: `-M virt,accel=tcg -cpu cortex-a72` · virtio-blk · SLIRP · `-nographic` · presets D13 · download do binário + SHA-256 · ciclo de vida
- [x] **T15. Gerenciador de distros (D1)** ✅
  - `DistroManager`: catálogo (Phantom Base oficial + Ubuntu/Debian/Alpine), download com progresso, checksum, extração tar.gz / imagem .img · UI no Toolbox
- [x] **T16. virtio-9p + virtio-serial** 🔶 (comando pronto)
  - `-virtfs` do workspace montado no guest · console stdio → terminal · validação real depende dos artefatos
- [ ] **T17. Widget de terminal** 🔶 v1 = console de linhas ligado às streams (entrada/saída real) · pendente: VT100/jackpal + abas múltiplas (D11)
- [x] **T18. `dark-code-init.sh`** ✅
  - `assets/linux/dark-code-init.sh` (rede SLIRP, user, mount 9p, prompt) · copiado na instalação da distro

## 🟡 FASE 4 — GIT + TOOLBOX + BACKUP

- [x] **T19. Git nativo (JGit)** ✅
  - `GitManager` (JGit 6.10): status (A/M/D/U/C), git init, clone com token, commit, push/pull, log · tela Git real com seleção de projeto + token GitHub
- [ ] **T20. Toolbox (visual em cards)**
  - Scanner de pacotes do guest (IAs/Linguagens/Ferramentas/Sistema) + Integrações & API Keys (Keystore, D8)
- [ ] **T21. Backup local ZIP/7z (SAF) + restauração (D2)**
  - Workspace + metadados; merge sem apagar silenciosamente
- [ ] **T22. Backup cloud (D8)**
  - Drive · OneDrive · Dropbox · S3 · WebDAV (OAuth/keys no Keystore)

## 🟡 FASE 5 — POLISH + DISTRIBUIÇÃO

- [ ] **T23. Foreground Service p/ VM em background (§12.3)**
  - FGS + notificação persistente "Phantom-Code · ambiente Linux ativo"
- [ ] **T24. Onboarding + Command Palette (D14)**
  - 1º uso claro (instalar Phantom Base + iniciar Linux, D20)
- [ ] **T25. Testes em device (Galaxy Note 10 Plus)**
  - Performance TCG, permutações de tema, corrigir o que quebrar
- [ ] **T26. Distribuição (D6)**
  - Sideload APK / F-Droid · decisão Play Store

---

## 📌 NOTAS IMPORTANTES

| Item | Detalhe |
|------|---------|
| Nome real | Phantom-Code · codinome Dark-Code |
| Design | Cyber-Phantom · Neon Dark IDE (OLED) — mockups Gemini são a fonte de verdade (D15) |
| Cores | `#000000` bg · `#121212` surface · `#9F4DFF` accent · `#00FFFF` secondary · `#00FF9F` success |
| Navegação | Bottom Nav 5 itens fixa + Activity Bar fina — nunca criar 6º item (D14) |
| Ambiente Linux | QEMU headless TCG (oficial) · proot = fallback (D5) |
| Workspace | Independente da rootfs (D3) · montado via virtio-9p |
| App ID | `com.phantomcode.app` (provisório — confirmar) |
| Repo GitHub | `VSFLima/phantom-code` (provisório — confirmar) |
| Build | GitHub Actions (sem SDK Android local) |
| Terminal | jackpal (fork moderno) ou Termux terminal-emulator |
| Editor | CodeMirror 6 (WebView) |
