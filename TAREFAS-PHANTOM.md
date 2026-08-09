# 📋 TAREFAS PHANTOM-CODE — Criação do App

> **Projeto:** Phantom-Code (codinome dev: Dark-Code) — IDE Android + VM Linux
> **Fonte:** `Phantom-Code-Documento-Mestre-3.md` (v4.4 · 05/08/2026)
> **Estratégia recomendada:** 🟣 Fase 0 + Fase 1 primeiro → APK instalável com visual Cyber-Phantom
> **Build local:** ❌ sem SDK Android no Termux/PRoot → **GitHub Actions**
>
> 📋 **TODOS OS PEDIDOS DO USUÁRIO + status real:** ver `docs/REQUESTS-USUARIO.md`
> ✅ **TODAS AS TAREFAS EM ABERTO (lista definitiva):** ver `docs/TASKS-EM-ABERTO.md`
> ⚠️ **LEIA ANTES DE MEXER NA UI:** o usuário reverteu um polish automático de UI — consulte sempre antes

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
- [x] **T5. Primeiro APK instalável rodando** ✅
  - ✅ **APK assinado compilado com SUCESSO** (build `31127986197`, release `apk-20260806-2105`) — assinatura verificada: `CN=Phantom-Code, OU=Asgard, O=VSFLima, C=BR`
  - **Build:** só no GitHub Actions (máquina local é ARM64 — aapt2 x86_64 incompatível; e o Android mata processos por RAM)
  - **Entrega:** APK publicado como GitHub **Release** (contorna cota de artefatos do Actions)

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

> 🔶 **Estado:** motor da VM pronto (T14, T15, T18 ✅) — faltam os **artefatos reais** (binário QEMU arm64 + rootfs Phantom).

- [x] **T14. QEMU arm64 + comando headless (§8.1)** ✅
  - `QemuManager`: `-M virt,accel=tcg -cpu cortex-a72` · virtio-blk · SLIRP · `-nographic` · presets D13 · download do binário + SHA-256 · ciclo de vida
- [x] **T15. Gerenciador de distros (D1)** ✅
  - `DistroManager`: catálogo (Phantom oficial + Ubuntu/Debian/Alpine), download com progresso, checksum, extração tar.gz / imagem .img · UI no Toolbox
- [x] **T16. virtio-9p + virtio-serial** ✅ código pronto — validação real depende dos artefatos
  - **virtio-9p**: `-virtfs local,path=<workspace>,mount_tag=darkcode-ws,security_model=none,id=ws0` → montado no guest pelo `dark-code-init.sh`
  - **virtio-serial**: `-chardev socket,id=term0,path=term.sock,server=on,wait=off` + `-device virtio-serial-device` + `-device virtconsole,chardev=term0`; kernel boota com `console=ttyAMA0 console=hvc0`
  - App conecta no socket via `LocalSocket` (`SocketTermSession`, ponte VT100 → terminal); **fallback** para stdio (`ProcessTermSession`) se o socket falhar; stdout drenado p/ o pipe não travar a VM
- [x] **T17. Widget de terminal VT100 + abas múltiplas (D11)** ✅ emulador real **jackpal `emulatorview` v1.0.70** (JitPack, API clássica `TermSession`/`EmulatorView`, embutido no APK): aba `Linux (QEMU)` = console do guest + abas `Shell N` (mksh local com TERM/PATH) + barra de abas com fechar/nova aba
- [x] **T18. `dark-code-init.sh`** ✅
  - `assets/linux/dark-code-init.sh` (rede SLIRP, user, mount 9p, prompt) · copiado na instalação da distro

## 🟡 FASE 4 — GIT + TOOLBOX + BACKUP

- [x] **T19. Git nativo (JGit)** ✅
  - `GitManager` (JGit 6.10): status (A/M/D/U/C), git init, clone com token, commit, push/pull, log · tela Git real com seleção de projeto + token GitHub
- [x] **T20. Toolbox (visual em cards)** ✅ Integrações & API Keys: catálogo de 30+ serviços (IA/Git/Cloud/Servidor) com preenchimento automático + botão **"Criar token"** que abre o navegador interno na página oficial do serviço; secrets no Android Keystore (AES-256/GCM)
  - [x] **Integrações & API Keys (D8)** — `SecretsManager` (Android Keystore + AES/GCM), cards com valor mascarado `sk-…xxxx`, copiar `$VAR`, toggle "Expor ao Linux", revogar; token do Git migrado p/ Keystore
  - [x] **Scanner de pacotes do guest (IAs/Linguagens/Ferramentas/Sistema)** ✅ `PackageScanner` (protocolo `SCAN:` no `phantom-agent.sh`); auto-registro de IAs no AI Suite (`agentsFromScan`)
- [x] **T21. Backup local ZIP (SAF) + restauração (D2)** ✅
  - `BackupManager`: workspace → ZIP (`java.util.zip`) via SAF com manifest JSON; restauração com **merge que nunca apaga silenciosamente** (entradas inválidas puladas)
  - **Fix permissões**: manifest com `READ/WRITE_EXTERNAL_STORAGE` (≤10) + `MANAGE_EXTERNAL_STORAGE` (11+); `StorageHelper` com pasta pública **`/storage/emulated/0/Phantom-Code/`** + fallback privado; pedido de permissão na Home e Settings; migração automática dos projetos antigos
- [ ] **T22. Backup cloud (D8)**
  - Drive · OneDrive · Dropbox · S3 · WebDAV (OAuth/keys no Keystore)

## 🟡 FASE 5 — POLISH + DISTRIBUIÇÃO

- [x] **T23. Foreground Service p/ VM em background (§12.3)** ✅
  - `VmForegroundService`: notificação persistente "Phantom-Code · ambiente Linux ativo", canal API 26+, `specialUse` (API 34+), START_STICKY
  - Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` + `POST_NOTIFICATIONS` (Android 13+)
  - `QemuManager` inicia/para o FGS junto com a VM (start/watcher/stop)
  - **Ação "Parar sessão" na notificação**: botão encerra a VM direto pela barra de notificações (`ACTION_STOP` → `QemuManager.instance?.stop()`)
- [x] **Auto-início da VM ao abrir o app (estilo Termux)** ✅
  - `ON_RESUME` → se há distro ativa, VM não está rodando e não houve encerramento explícito, `start()` sobe sozinha
  - Encerramento explícito (app ou notificação) marca `autoStartSuppressed` → não volta a subir sozinha até o usuário iniciar
- [x] **T24. Onboarding + Command Palette (D14)** ✅
  - `OnboardingScreen` (D20 + T30): **4 passos** — armazenamento · **instalar QEMU + distro Phantom (auto, com barra de progresso real)** · escolher distro · iniciar Linux; flag `onboardingDone` (só 1ª vez)
  - `CommandPalette` (D14): overlay estilo VS Code com busca, aberta pelo ícone de menu no topo; comandos: Home/Explorer/Terminal/Iniciar-Parar Linux/Git/Toolbox/Settings
- [ ] ~~**T25. Testes em device (Galaxy Note 10 Plus)**~~ ❌ **removida por decisão do usuário (08/08/2026)** — validação em device fica a cargo do usuário quando houver aparelho
- [x] **T27. Design System v2 — UI & Botões (estilo do usuário)** ✅ estilos base prontos (Neon/Hacker/Gradient/Glass/Ghost/Pill/Sólido) + dimensões editáveis (cantos · bordas · letras) persistidas; preview ao vivo; animações (press nos botões, transições de navegação, diálogos com scale+fade); terminal segue a paleta (setColorScheme)
- [x] **T28. Navegador interno (W2)** ✅ WebView com a cara do app: barra de URL, voltar/avançar/recarregar/início, progresso, página inicial temática; acesso via Home e Command Palette
- [x] **T29. Distros com download interno (W3)** ✅ infra + catálogo prontos: workflow `build-distros.yml` gera a Phantom (Debian arm64) → Release **`distro-phantom`** com `phantom.tar.gz` (311 MB: rootfs.img + kernel + initrd.img + **QEMU incluído**) e **`qemu-aarch64`** (binário estático 122 MB, SHA-256 ok); URLs reais no `PhantomMirror`; catálogo expansível (`DistroCard`) + `DistroConfigDialog` (hostname/usuário/preset/HD) + instalação acompanhada ao vivo no terminal (download % → SHA-256 → extração) — resta testar a instalação no device e publicar Ubuntu/Debian/Alpine/Kali (ainda `example.com`)
- [x] **Fix crash terminal** ✅ `EmulatorView(ctx, null, metrics)` crashava com NPE (construtor jackpal chama attachSession(null)); agora `EmulatorView(ctx, null)` + `setDensity` manual — instalar distro e barra de terminais não fecham mais o app
- [x] **Fix redundância QEMU** ✅ `start()` verifica a distro instalada PRIMEIRO (o QEMU vem dentro do pacote Phantom); sem download redundante do binário separado
- [x] **T30. Instalação automática QEMU + distro (fim do falso positivo)** ✅
  - **Causa raiz do falso positivo:** o antigo `isInstalled()` era frouxo — bastava `rootfs.img` **OU** `kernel` **OU** pasta `rootfs/`; uma instalação parcial/antiga (ex.: só `rootfs.img` de um download que morreu) aparecia como "Instalada" e o QEMU nunca subia (sem kernel/qemu) → tela morta.
  - **Fix:** `isInstalled()` estrito por tipo de boot (`KERNEL_INITRD` → `kernel && (rootfs||initrd)`; `ROOTFS_ONLY` → `rootfs`) + exige `qemu-system-aarch64` (> 1 MB) quando `includesQemu`; limpeza de artefatos parciais antes do download e após falha (`rootfs.img/kernel/initrd.img/qemu…` + `rootfs/`); `refreshBinary()` pós-instalação.
  - **Instalação real no terminal:** QEMU + distro vêm no **MESMO tarball** (`phantom.tar.gz`). Novo fluxo `installPhantom()` (PhantomApp) abre o terminal com aba de log + **barra de progresso real** (fase ao vivo no `LogTermSession`: Baixando % → Verificando SHA-256 → Extraindo) e baixa/instala tudo de verdade. Durante o onboarding o NavHost ainda não existe → a navegação é adiada (`pendingTerminal`) e o terminal abre sozinho ao entrar no app.
  - **Auto-instalação no 1º uso:** Onboarding ganhou a etapa "Instale o QEMU + a distro" (T24 atualizado → **4 passos**) e **dispara a instalação automaticamente** ao entrar; a Home ganhou o card **"QEMU + distro Phantom"** com "Instalar" (com progresso) para quem já passou do onboarding.
  - **Terminal 100% celular (sem teclado físico):** teclado virtual garantido (IME no toque + `setUseCookedIME` + `adjustResize`); **pinça** = zoom da fonte (6–32 dp) com quebra de linha reajustando sozinha (`updateSize`); **toque longo = seleção** de texto; barra de ações **Selecionar / Copiar / Colar / Ctrl** (estilo Termux).

---

## 📌 NOTAS IMPORTANTES

---

## 📌 NOTAS IMPORTANTES

| Item | Detalhe |
|------|---------|
| Nome real | Phantom-Code · codinome Dark-Code |
| Design | Cyber-Phantom · Neon Dark IDE (OLED) — mockups Gemini são a fonte de verdade (D15) |
| Cores | `#000000` bg · `#121212` surface · `#9F4DFF` accent · `#00FFFF` secondary · `#00FF9F` success |
| Navegação | Bottom Nav 5 itens fixa + Activity Bar fina — nunca criar 6º item (D14) |
| Ambiente Linux | QEMU headless TCG (oficial) · proot = fallback (D5) |
| Workspace | Independente da rootfs (D3) · montado via virtio-9p · pasta pública `/storage/emulated/0/Phantom-Code/workspace` (com permissão) |
| App ID | `com.phantomcode.app` (provisório — confirmar) |
| Repo GitHub | `VSFLima/phantom-code` (provisório — confirmar) |
| Build | GitHub Actions (sem SDK Android local) |
| Terminal | jackpal `emulatorview` v1.0.70 (VT100 real, abas) via JitPack — cores seguem a paleta do usuário |
| Design System | `DesignSystem.kt` (botões/cantos/bordas/letras) + `UiStyleController` persistido — Settings → UI & Botões |
| Navegador | `BrowserScreen.kt` — WebView interno (W2) |
| Editor | CodeMirror 6 (WebView) |
| AI Suite | **`docs/roteador-ias.md`** — roteador de comunicação entre IAs (spec pronta: registro de agentes, Shared Context Bus, Conflict Guard com locks W/R/S/D/G, delegação com aprovação humana obrigatória, threads de conversação, fases A/B/C de implementação) — **Fase A**: `AiSuiteManager.kt` + `ConflictGuard.kt` + `phantom-router.sh` · **Fase B (✅ 08/08)**: delegação com Aprovar/Ajustar/Recusar, tarefas `tasks/<id>/` (context.json/messages.jsonl/proposal.json), threads append-only com intervenção do dono · **Fase C (✅ 08/08, parte executável)**: "Rodar no guest" por agente/tarefa, `create_task`/`scan`/`delegate`/`approved`/`rejected` no router do guest, aviso de lock no Editor |
| Backup cloud | `CloudBackupManager.kt` — WebDAV (via chaves do catálogo: `WEBDAV_URL`/user/pass), upload/restore do ZIP do workspace |
| Sync GitHub | `GitManager.createGithubRepo()` + `syncToGithub()` — cria repositório automático para projeto local e faz push |
