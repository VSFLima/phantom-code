# 📝 TAREFA — IDE / Editor do Phantom-Code (nível SPCK)

> **Especificação de tarefa** · Phantom-Code v1 · 07/08/2026
> **Para:** qualquer IA que for implementar
> **Status:** 🚧 Em execução — P0.2, P1.1–P1.4 e P2.3 implementados (build APK `31230823317`); faltam P1.5 (split terminal), P2.1 (explorer), P2.2 (upload FTP) e P2.4 (watcher no GitScreen/Home)
> **Regra de ouro:** **NÃO rode builds locais** — o ambiente é o app Android no Note 10 Plus; valide por revisão de código + balanceamento e dispare build no GitHub Actions (`gh workflow run "Build APK"`). O usuário compila sempre no GitHub.

---

## 0. Resumo executivo

O editor atual já tem base sólida (CodeMirror 6 no WebView, abas, auto-save, salvar como, renomear, duplicar, buscar/substituir). Faltam **2 bugs** e um conjunto de **funcionalidades estilo SPCK Editor** que o usuário pediu. Esta tarefa corrige os bugs e implementa as funcionalidades por **prioridade**, cada uma com critério de aceite claro.

**Prioridades:**
- **P0 (bugs que o usuário relatou):** Pull do Git não baixa os arquivos; editor não recarrega arquivos após pull/clone/restauração.
- **P1 (uso diário):** Git integrado no editor, atalhos de teclado, ir para linha, preview HTML, terminal integrado.
- **P2 (conveniência):** Explorer lateral completo, download/upload de arquivos, snippets, watcher de arquivos externos.

---

## 1. Estado atual (o que JÁ existe — não duplicar)

| Área | Arquivo | O que faz |
|---|---|---|
| Editor | `android/app/src/main/java/com/phantomcode/app/ui/screens/EditorScreen.kt` | CodeMirror 6 no WebView; abas; auto-save 800ms; salvar como; duplicar; renomear; buscar/substituir; desfazer/refazer; selecionar tudo |
| Git | `android/app/src/main/java/com/phantomcode/app/data/git/GitManager.kt` | JGit: status, clone, commit, push, pull (fetch+merge explícito — **já corrigido**), log, GitHub repos/releases, `createGithubRepo`, `githubLogin`, `syncLocalToGithub` |
| Git UI | `android/app/src/main/java/com/phantomcode/app/ui/screens/GitScreen.kt` | status, commit, push/pull, clone, aba GitHub (repos + releases + baixar) |
| Navegador | `android/app/src/main/java/com/phantomcode/app/ui/screens/BrowserScreen.kt` | WebView com `initialUrl`; rota `browser?url={url}` |
| Terminal | `android/app/src/main/java/com/phantomcode/app/ui/screens/TerminalScreen.kt` + `data/vm/TerminalManager.kt` | Abas de terminal (console QEMU + shells locais) |
| Backup | `data/backup/BackupManager.kt` (ZIP local SAF) + `CloudBackupManager.kt` (WebDAV) | Merge na restauração, nunca apaga |
| Workspace | `data/WorkspaceManager.kt` | `resolve()`, `readText()`, `writeText()`, `rename()`, `projects()` |
| Explorer | HomeScreen (grade de projetos) | listar/criar projeto — **não há árvore de arquivos lateral completa** |

**Ponte JS↔Kotlin existente no editor:** `window.AndroidBridge.save(text)`, `window.AndroidBridge.dirty()`. O JS expõe `window.PhantomEditor.getValue/setValue/undo/redo/selectAll/focus/init`.

---

## 2. P0 — Bugs (FAZER PRIMEIRO)

### P0.1 — Pull do Git não baixa os arquivos ✅ (JÁ CORRIGIDO — validar no device)

**Problema:** `git.pull()` do JGit dependia de upstream configurado; em projetos locais sincronizados falhava silenciosamente ("Pull sem mudanças" ou erro).

**Correção aplicada em `GitManager.kt`:**
- `pull()` agora faz **fetch explícito** (`git.fetch().setRemote(remote).setRemoveDeletedRefs(true)`) + **merge com `refs/remotes/<remote>/<branch>`** (não depende de upstream).
- Retorna mensagens legíveis: "Já atualizado", "Pull OK — branch atualizada", "Pull OK (merge de …)", "Conflitos em N arquivo(s)…", "O remote não tem a branch 'X' — rode Push primeiro".
- `push()` usa **remote + branch explícitos** (`RefSpec("$branch:$branch")`) e **configura o upstream** após o push (`git.branchSetUpstream()`), para pulls futuros funcionarem.
- `syncLocalToGithub()` também configura o upstream após criar o repo.

**Critério de aceite (device):** 1) Clonar um repo → editar um arquivo no GitHub → tocar **Sincronizar** → arquivo aparece atualizado no disco. 2) Projeto local publicado → Push cria o repo e depois Pull funciona sem erros.

### P0.2 — Editor não recarrega arquivos após pull/clone/restauração ✅ (IMPLEMENTADO — build 31230823317)

**Problema:** quando o Pull/clone/restauração muda arquivos no disco, os arquivos **abertos** no CodeMirror continuam com o texto antigo em memória.

**Correção aplicada no `EditorScreen.kt`:**
- **Watcher leve** a cada 3s: compara `File.lastModified()` do arquivo aberto com o último visto;
- se mudou **e** o editor está salvo → recarrega via `PhantomEditor.setValue` + snackbar "Arquivo atualizado no disco — recarregado";
- se há **edições não salvas** → diálogo "Arquivo mudou no disco — [Recarregar] / [Manter minhas edições]" (o último visto é atualizado ao manter, para não insistir).

**Critério de aceite (device):** mudar um arquivo via Git Pull (ou no editor do GitHub) → o editor aberto mostra o conteúdo novo; se houver edições não salvas, pergunta antes de sobrescrever.

---

## 3. P1 — Uso diário (implementar nesta ordem)

### P1.1 — Git integrado no editor ✅ (IMPLEMENTADO — build 31230823317)
Na barra superior do `EditorScreen`, há um grupo discreto de ações Git do **projeto atual**:
- Indicador de status (limpo / N mudanças) com polling de 5s usando `GitManager.status()` enquanto o editor está aberto;
- Botões: **Commit** (dialog com mensagem, igual ao GitScreen), **Push**, **Pull/Sincronizar** — chamando `GitManager` com a **raiz do projeto** (o path do arquivo aberto → subir até o diretório com `.git`) e mostrando o resultado em snackbar.
- **Critério:** do editor consigo commitar e sincronizar sem sair da tela.

### P1.2 — Atalhos de teclado ✅ (IMPLEMENTADO — build 31230823317)
Adicionados no `editor-actions.js` (sobre o CodeMirror):
- `Ctrl+S` salvar (chama `AndroidBridge.save` + aviso), `Ctrl+F`/`Ctrl+H` buscar/substituir (abre o painel), `Ctrl+G` ir para linha, `Ctrl+Z`/`Ctrl+Y` (do CodeMirror), `Ctrl+A`, `Ctrl+D` duplicar linha, `Alt+↑/↓` mover linha, `Ctrl+/` comentar.
- **Critério:** cada atalho funciona com teclado físico no device.

### P1.3 — Ir para linha ✅ (IMPLEMENTADO — build 31230823317)
No menu "Ações do arquivo", item **"Ir para linha…"** → dialog numérico → `PhantomEditor.gotoLine(n)` (JS: `editor.dispatch({selection:{anchor:lineStart,head:lineStart}})`, foca e rola). Indicador **L:n C:m** ao vivo na barra.
- **Critério:** navega e destaca a linha escolhida.

### P1.4 — Preview HTML no navegador interno ✅ (IMPLEMENTADO — build 31230823317)
No menu de ações: **"Preview no navegador"** → abre o `BrowserScreen` com a URL `file://` do arquivo; o `BrowserScreen` agora habilita `allowFileAccess`/`allowFileAccessFromFileURLs` para CSS/JS relativos da pasta do projeto.
- **Critério:** página HTML do projeto abre com CSS/JS funcionando.

### P1.5 — Terminal integrado (split)
Na `TerminalScreen`, adicionar um modo **split** (editor em cima, terminal embaixo) ou um botão "Abrir terminal aqui" que abre uma aba de terminal local **com cwd = diretório do projeto atual**.
- Usar o `TerminalManager` existente (aba local `addLocalTab(cwd)` — verificar assinatura; se não existir, criar com `ProcessBuilder` apontando para o cwd).
- **Critério:** `ls`, `git status`, `node script.js` rodam a partir da pasta do projeto.

---

## 4. P2 — Conveniência

### P2.1 — Explorer lateral completo (árvore de arquivos)
Criar um painel de árvore (arquivos + pastas do projeto ativo) ao lado do editor, com:
- Expandir/colapsar pastas; toque em arquivo → abre na aba.
- Menu de contexto por item (long-press): **Novo arquivo, Nova pasta, Renomear, Duplicar, Excluir, Mover/copiar para…, Baixar (SAF)**.
- Ícone por tipo de arquivo (usar a paleta existente).
- **Critério:** todas as operações funcionam e o editor reflete a árvore em tempo real (tick de refresh).

### P2.2 — Download / upload de arquivos
- **Download:** selecionar arquivo → `ActivityResultContracts.CreateDocument` grava o conteúdo (SAF — padrão já usado no BackupManager).
- **Upload FTP/SFTP:** usar as chaves do catálogo (categoria SERVER: `FTP_HOST/USER/PASS/PORT`) com `org.apache.commons.net.ftp.FTPClient` (adicionar dependência se não existir) ou NetCipher; começar por **FTP** (mais simples) e depois **SFTP** (JSch). Exibir progresso e resultado em snackbar.
- **Critério:** enviar/baixar um arquivo do servidor FTP configurado nas Integrações.

### P2.3 — Snippets + autocompletar ✅ (IMPLEMENTADO — build 31230823317)
- Snippets JS no CodeMirror com **Tab** (em `editor-actions.js`): `html5` (doc HTML), `fun` (função Kotlin), `imp` (import), `cl`/`log` (console.log), `fn`, `cls`, `if`, `for`, `def` (Python), `sh` (shebang) — via `PhantomEditor.getBeforeCursor` + `replaceWordBeforeCursor`.
- **Critério:** digitar `html5` + Tab insere o esqueleto HTML.

### P2.4 — Watcher de arquivos externos (reforço do P0.2)
- Além do editor, o **GitScreen** deve mostrar "Projeto atualizado — recarregar" quando o pull terminar, e o **HomeScreen** deve atualizar a grade de projetos automaticamente.

---

## 5. Arquivos afetados (mapa)

| Arquivo | Ação |
|---|---|
| `data/git/GitManager.kt` | ✅ Já corrigido (P0.1). Não reverter. |
| `ui/screens/GitScreen.kt` | Revisar mensagens do Pull; adicionar refresh da grade/editor após sucesso |
| `ui/screens/EditorScreen.kt` | P0.2 (watcher/recarregar), P1.1 (git integrado), P1.3, P1.4 (menu), P2.3 (via JS) |
| `ui/screens/BrowserScreen.kt` | Reutilizar para P1.4 (nada a mudar se rota aceita URL) |
| `ui/screens/TerminalScreen.kt` + `data/vm/TerminalManager.kt` | P1.5 (split + cwd do projeto) |
| `main/assets/editor/index.html` (+ JS do CodeMirror) | P1.2 atalhos, P1.3 gotoLine, P2.3 snippets |
| NOVO `ui/components/FileTree.kt` | P2.1 explorer lateral |
| `data/remote/FtpClient.kt` (novo) | P2.2 upload FTP (verificar se commons-net já está nas deps) |
| `app/build.gradle.kts` | Só adicionar dependência se P2.2 for implementado (commons-net / JSch) |

---

## 6. Regras obrigatórias para a IA executora

1. **Não quebrar o build.** Antes de commitar: verificar **balanceamento de chaves/parênteses** (script python que remove strings/comentários) em todos os arquivos alterados, conferir **imports usados vs declarados** e símbolos referenciados (ex.: `PhantomEditor.gotoLine` deve existir no JS; `addLocalTab` deve existir no TerminalManager — se não existir, criar).
2. **Reaproveitar** `PhantomDialog`, `PhantomCard`, `PhantomPrimaryButton`, `PhantomOutlinedButton`, `SectionLabel`, `LocalThemeController`/paleta — NUNCA criar novos estilos fora do tema.
3. **Não duplicar** funcionalidade do GitScreen/GitManager — chamar os mesmos managers.
4. **Testar no device** (Note 10 Plus) após build no GitHub: terminal → Git → Pull; editar no GitHub → Sincronizar → editor recarrega; preview HTML.
5. **Não rodar builds locais** no Termux (processamento do aparelho). Commit + push + `gh workflow run "Build APK" --repo VSFLima/phantom-code`.
6. Atualizar `TAREFAS-PHANTOM.md` marcando P0.2–P2 concluídas com nota do build.

---

## 7. Critério final de aceite (demo de 5 min)

1. Projeto local no Explorer → **Publicar no GitHub** (cria repo) → edito no GitHub → **Sincronizar** no app → arquivo atualizado, editor recarrega sozinho.
2. Pull com conflito → mensagem clara + arquivos com marcadores de conflito para resolver no editor.
3. HTML do projeto → **Preview** abre no navegador interno com CSS funcionando.
4. **Terminal embaixo do editor** roda `node script.js` do projeto.
5. Tudo com o **tema phantom** (sem componentes fora do padrão visual).

---

## 8. Roadmap futuro — Preview Hub e identidade visual

### P3.1 — Preview Hub por tecnologia
- HTML/CSS/JavaScript com navegador interno e console.
- Markdown com renderização visual.
- JSON, SVG e imagens com visualizador próprio.
- React/Vite e outros projetos web por servidor local no workspace.
- Python, Node, Kotlin e Shell com execução no terminal/QEMU quando o runtime existir.
- Android e outros projetos compiláveis com saída de build, logs e instalação de teste quando o toolchain estiver disponível.
- Detecção automática por arquivos (`package.json`, `build.gradle`, `Cargo.toml`, `requirements.txt`, etc.).

### P3.2 — Temas unificados
- Presets do app aplicados ao editor, terminal, preview e diálogos.
- Presets de terminal independentes: App, Matrix, Amber, Ice e Paper.
- Tamanho de fonte, cursor, seleção, quebra de linha e espaçamento configuráveis.
- Nenhum componente de preview pode criar uma identidade visual separada do Phantom.

### P3.3 — GitHub colaborativo
- Projetos locais, Git genérico e GitHub identificados visualmente.
- Clone, releases, publicação, sincronização, pull, push e conflitos.
- Cada colaborador usa seu próprio token; o app não embute credenciais.
- Futuramente: convites, permissões e status de equipe via API GitHub.
