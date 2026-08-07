# 🧾 REQUESTS DO USUÁRIO — Registro da conversa

> **Projeto:** Phantom-Code (codinome Dark-Code) — IDE Android + VM Linux
> **Data do registro:** 07/08/2026 · **Repositório:** `VSFLima/phantom-code`
> **Estado atual:** HEAD em `c8bc19c` (último commit do Luna) · 2 arquivos pendentes não commitados (`PhantomApp.kt` + `dark-code-init.sh`)
>
> Este documento registra **tudo que o usuário pediu** ao longo da conversa, com o
> status real de cada pedido (✅ feito · 🔶 parcial · ❌ pendente). Serve de guia
> para qualquer IA retomar o trabalho sem perder contexto.

---

## 1️⃣ CORREÇÕES E REQUESTS DE COMPORTAMENTO

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R1 | **Instalar distro fecha o app** (crash ao confirmar instalação) | ✅ | Causa raiz: `LogTermSession` sem `setTermIn`/`setTermOut` → thread do jackpal fazia `mTermIn.read()` com null → NPE. Fix: streams dummy + `initializeEmulator` seguro; `TerminalScreen` não cria emulador sem sessão ativa (commits `a0d7d1d`, `99b6640`, `e3b45fe`) |
| R2 | **QEMU redundante** — "o QEMU já vem na distro, instalar distro oficial e avisar que precisa instalar o binário QEMU é redundante" | ✅ | `start()` verifica a distro instalada PRIMEIRO (`includesQemu` da Phantom pula a etapa do binário); sem download redundante (commit `812195c`, `a6507fb`) |
| R3 | **QEMU nativo no app / instalado através do arquivo da distro** | ✅ | Decisão final do usuário: **não** embutir QEMU no APK — a distro traz o QEMU no pacote. `phantom.tar.gz` (311 MB) tem rootfs + kernel + initrd + **QEMU compilado dentro do próprio build** (workflow autônomo, commit `a6507fb`) |
| R4 | **Distro buildada com QEMU dentro não pode ser perdida** | ✅ | `build-distros.yml` agora **compila o QEMU dentro do workflow** (cross-compile) e publica `distro-phantom` → `phantom.tar.gz` (311 MB). SHAs atualizados (`d6485e6`). Releases recriadas com sucesso |
| R5 | **Downloads de releases privadas com token do GitHub** | ✅ | `GithubAssetClient.kt` (novo): busca o asset via API com token do Keystore + `application/octet-stream` (commit `9db782a`); diálogo orienta autenticação (`99efde1`) |

## 2️⃣ ÁREA DE API KEYS / INTEGRAÇÕES

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R6 | **Mais serviços e integrações na área de chaves** (API Google, FTP, entre outros) | ✅ | Catálogo de **30+ serviços** em 5 categorias (IA · Git · Cloud · Servidor · Manual) — `IntegrationCatalog.kt` (commit `7188711`) |
| R7 | **Link clicável para criar token de cada serviço** (botão que abre a página oficial) | ✅ | Botão **"Criar token/API key de [serviço]"** → abre o **navegador interno** na página oficial (GitHub tokens, Google Console, etc.) — `BrowserScreen` com `initialUrl` + rota `browser?url={url}` (commit `7188711`) |

## 3️⃣ NAVEGADOR INTERNO + DISTROS

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R8 | **Navegador interno** (pré-visualização de código, download/visualização web, integrado ao ecossistema) | ✅ | **T28** — WebView com a cara do app: barra de URL, voltar/avançar/recarregar, progresso, página inicial temática; acesso via Home e Command Palette |
| R9 | **Download das distros com instalação automática dentro do app** | 🔶 | **T29** — Phantom oficial **funcional** (`distro-phantom`, SHA ok, `DistroConfigDialog` com hostname/usuário/preset/HD, instalação ao vivo no terminal). **Falta:** publicar Ubuntu/Debian/Alpine/Kali (ainda `example.com`) + testar no device |

## 4️⃣ UI / DESIGN (estilo do usuário)

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R10 | **Botões com estilo hacker e bonitos ao mesmo tempo; vários tipos de UI; personalização total** (bordas, arredondamento, letras, cores) | ✅ | **T27 — Design System v2**: 7 estilos de botão (Neon/Hacker/Gradient/Glass/Ghost/Pill/Sólido) + dimensões editáveis (cantos/bordas/letras) persistidas, preview ao vivo, animações |
| R11 | **Estilo Phantom mais estilizado; usuário dá personalidade ao app; estilos base prontos + modo estilo livre** | ✅ | Base do Design System v2 pronta; customização via Settings → UI & Botões + temas (8 presets + Custom com color pickers) |
| R12 | **Tema aplicado ao terminal e demais áreas por completo** | 🔶 | Terminal segue a paleta (`setColorScheme`) e tem presets próprios. **Pendente:** um polish mais profundo e *consistente* em todas as telas — **⚠️ IMPORTANTE:** um polish automático de UI foi tentado e o usuário **reverteu** (pediu para voltar ao estado do Luna). Qualquer trabalho de UI futura deve ser **consultado antes** com o usuário |

## 5️⃣ GIT

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R13 | **Pull do Git não puxa os arquivos do projeto corretamente** (funções que já existem no SPCK) | ✅ | `GitManager.pull()` agora usa **fetch + merge explícitos** (não depende de upstream); push com upstream configurado via `StoredConfig` (commits `a0d7d1d`, `68ec79b`) |
| R14 | **Sincronizar projetos locais com GitHub criando repositório para cada projeto local** | ✅ | `syncLocalToGithub()` no `GitManager` (cria repo privado + push) — botão "Publicar no GitHub" no `GitScreen` (commit `a0d7d1d`) |
| R15 | **Definir task detalhada do editor (funções estilo SPCK) para outra IA executar** | ✅ | `docs/TAREFA-EDITOR-IDE.md` — especificação detalhada das funções do editor (tabs, busca, preview, atalhos, etc.) |

## 6️⃣ BACKUP

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R16 | **Backup cloud** (T22 — Drive/WebDAV/Dropbox com as chaves do catálogo) | 🔶 | `CloudBackupManager.kt` criado (WebDAV via chaves do catálogo) + seção no Toolbox. **Falta:** validar em build real e marcar T22 no TAREFAS; estender para Drive/Dropbox se desejado |

## 7️⃣ IA / ECOSSISTEMA

| # | Pedido do usuário | Status | O que foi feito / falta |
|---|---|---|---|
| R17 | **Roteador de comunicação entre IAs** (todas as IAs instaladas no app dentro do Linux com contexto compartilhado, delegar funções, regras anti-conflito, aprovação humana obrigatória) | 🔶 | Spec completa em `docs/roteador-ias.md` (Agent Registry · Router · Shared Context Bus · Conflict Guard com locks W/R/S/D/G · Human Approval Gate · protocolo `ROUTER:<json>`). **Fase A implementada** (`a0d7d1d`): `ConflictGuard.kt`, `AiSuiteManager.kt`, `phantom-router.sh` (daemon do guest) + seção AI Suite no Toolbox. **Faltam:** Fases B (UI de conversa entre IAs) e C (integração com os runners reais) |

## 8️⃣ PERGUNTAS RESPONDIDAS (contexto)

| # | Pergunta | Resposta |
|---|---|---|
| R18 | **Qual distro é a base da Phantom e o que ela modifica?** | Base: **Debian (arm64)**. A Phantom adiciona: kernel + initrd + QEMU estático no pacote, `dark-code-init.sh` (rede SLIRP, usuário, mount 9p, prompt), workspace via virtio-9p, canal de controle via virtio-serial (`phantom-agent.sh`) |
| R19 | **Como funcionará o roteador / quais tecnologias?** | App Android (Kotlin/Compose) + guest Linux via QEMU. Locks de escrita por arquivo com TTL, soft-lock de área correlata, task scoping, aprovação humana obrigatória, protocolo JSON pelo virtio-serial (canal `phantom.ctrl`) |

---

## ⏳ PENDÊNCIAS GERAIS (resumo executivo)

### Código — a decidir
- [ ] **2 arquivos não commitados do Luna** (`PhantomApp.kt` auto-abrir terminal + `dark-code-init.sh` sanitização/su) — avaliar com o usuário se commitar ou ajustar (o `autoStartSuppressed` foi removido da checagem no `PhantomApp.kt`, mas o comentário e o `QemuManager.stop()` ainda o usam)
- [ ] **Apagar release órfã** `apk-20260807-2209` (build do polish que foi revertido) — confirmar com o usuário
- [ ] **Scanner de pacotes do guest** (IAs/Linguagens/Ferramentas/Sistema) — desmarcado no TAREFAS

### Tarefas oficiais pendentes
- [ ] **T22 — Backup cloud (D8)**: código WebDAV pronto, validar + marcar
- [ ] **T25 — Testes em device (Galaxy Note 10 Plus)**: instalar APK recente (`apk-20260807-2043`), validar instalação da Phantom, terminal, QEMU, git
- [ ] **T29 — publicar Ubuntu/Debian/Alpine/Kali** no catálogo de distros (hoje `example.com`)

### AI Suite (Fase A feita)
- [ ] **Fase B** — UI de conversa entre IAs (Shared Context Bus visual)
- [ ] **Fase C** — integração com agent runners reais no guest

---

## 🚨 LIÇÕES APRENDIDAS (importante para qualquer IA)

1. **NÃO fazer mudanças grandes de UI por conta própria** — o usuário reverteu um polish completo de UI feito sem consulta (removia o menu inferior de terminais / alterava a tela inicial). **Sempre perguntar antes** de mexer no visual.
2. **Build é só no GitHub Actions** (máquina local sem SDK Android; compilar localmente derruba o app/termux). Commits → push → `gh workflow run 'Build APK'`.
3. **SHA-256 importa**: depois de rebuildar a distro, atualizar `QEMU_BINARY_SHA256` e `PHANTOM_SHA256` no `QemuConfig.kt` — senão a validação falha na instalação.
4. **Releases do GitHub podem ser apagadas por cota** — o fluxo atual (workflow autônomo que publica via Release) já foi desenhado para minimizar isso.
