# 📋 PENDÊNCIAS CONSOLIDADAS — Phantom-Code

> **Atualizado:** 07/08/2026 · **Regra:** nada de mudança de UI/design sem consultar o usuário. Build só no GitHub Actions.
> Este arquivo reúne TODAS as tarefas pendentes de `TAREFAS-PHANTOM.md`, `docs/TAREFA-EDITOR-IDE.md` e `docs/REQUESTS-USUARIO.md` em um só lugar, para nenhuma IA esquecer.

---

## A. EDITOR / IDE (docs/TAREFA-EDITOR-IDE.md)

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| P1.5 | **Terminal integrado ao projeto** | 🔶 feito | `TerminalManager.addShellTab(cwd)` + menu "Terminal aqui (projeto)" no editor. **Falta:** validar no device e (opcional) modo split editor+terminal |
| P2.1 | **Explorer lateral completo** | 🔶 feito | Painel de árvore no editor (expandir/colapsar, abrir, refresh) + menu de contexto por item: renomear, duplicar (arquivo/pasta), excluir. **Falta:** novo arquivo/pasta no menu, mover/copiar, baixar SAF no menu do item |
| P2.2 | **Download/upload de arquivos** | 🔶 feito | **Download:** SAF (`CreateDocument`) + botão Baixar no editor. **Upload:** FTP via commons-net (menu Enviar), `FtpClient.upload(absPath, relPath)`. **Falta:** validar em device; SFTP (JSch) depois |
| P2.3 | **Snippets + autocompletar** | ✅ feito | Snippets com Tab + autocompletar (`complete()`) no bundle CodeMirror |
| P2.4 | **Watcher de arquivos externos** | ✅ feito | EditorScreen recarrega/mostra diálogo quando o arquivo muda no disco (3s); HomeScreen atualiza a grade sozinha (5s); GitScreen atualiza status após pull |
| — | **Code folding** | ✅ feito | Dobrar/desdobrar bloco e tudo (menu + atalhos Ctrl+Shift+[/]) |
| — | **Teclado de programação** | ✅ feito | Barra de símbolos no editor que insere no cursor |
| P3.1 | **Preview Hub por tecnologia** | 🔶 feito | Painel split no editor: HTML/CSS/JS, Markdown, JSON, imagens, texto + **servidor HTTP local** (AJAX) + **servidor na VM** (PHP/Python/Node via hostfwd 8384). Detalhes: `docs/PREVIEW-HUB-IDE-LINUX.md`. **Falta:** validar em device; PDF/CSV/SQL; PHP instalado na Phantom |
| P3.2 | **Temas unificados** | 🔶 feito | Presets do app no editor/terminal/preview. **Falta:** fonte/cursor/seleção/quebra de linha configuráveis |
| P3.3 | **GitHub colaborativo** | ❌ roadmap | Clones/releases/publicação visuais, status de equipe via API |

## B. GIT

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| R13 | Pull do Git | ✅ | fetch+merge explícito |
| R14 | Sincronizar local → GitHub | ✅ | `syncLocalToGithub()` cria repo privado |
| P2.4 | Watcher no GitScreen/Home | ✅ | ver seção A |

## C. BACKUP

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| T21 | Backup local ZIP (SAF) | ✅ | merge nunca apaga |
| T22 | **Backup cloud** | 🔶 código pronto | `CloudBackupManager` (WebDAV via chaves). **Falta:** validar em build real, marcar T22, estender p/ Drive/Dropbox se quiser |

## D. VM / DISTROS

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| T16 | virtio-9p + virtio-serial | 🔶 | código pronto, validação real depende dos artefatos |
| T29 | **Publicar Ubuntu/Debian/Alpine/Kali** | ❌ | catálogo hoje com `example.com`; só a Phantom (`distro-phantom`) está real. Workflow `build-distros.yml` precisa gerar as demais |
| — | **Scanner de pacotes do guest** | ❌ | IAs/Linguagens/Ferramentas/Sistema (sub-item do T20) |

## E. TERMINAL

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| — | Teclado virtual do Android | ✅ | `EmulatorView` jackpal já usa o teclado virtual do usuário (`onCreateInputConnection`) — nada a fazer |
| — | Abas, fonte, cores | ✅ | abas múltiplas + fonte/linhas organizadas + cores seguem a paleta |

## F. TESTES E DISTRIBUIÇÃO

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| T25 | **Testes em device (Galaxy Note 10 Plus)** | ❌ | instalar APK recente, validar instalação da Phantom, terminal, QEMU, git, editor (autocomplete/folding/explorer/teclado prog.) |
| — | Apagar release órfã `apk-20260807-2209` | ❌ | confirmar com o usuário |

## G. AI SUITE (docs/roteador-ias.md)

| # | Tarefa | Status | Detalhe |
|---|--------|--------|---------|
| Fase A | Registro/Router/ConflictGuard | ✅ | `AiSuiteManager.kt`, `ConflictGuard.kt`, `phantom-router.sh` |
| Fase B | **UI de conversa entre IAs** | ❌ | Shared Context Bus visual |
| Fase C | **Integração com runners reais no guest** | ❌ | — |

---

## 📌 PRÓXIMO PASSO RECOMENDADO

1. **P3.1 validação em device** — Preview Hub, servidor local (AJAX), servidor VM (PHP).
2. **Instalar PHP na Phantom** — `apt install php` no guest + documentar no Toolbox.
3. **Executar código (VM)** — JS/PY/SH com saída de volta no app (extensão do SERVER:/RUN:).
4. **P2.2/2.1** — SFTP (JSch), novo arquivo/pasta + mover/copiar no explorer.
