# 🔍 RELATÓRIO DE AUDITORIA — Phantom-Code

> **Data:** 08/08/2026 · **HEAD:** `b82f12f` (igual a `origin/main`, árvore limpa)
> **Build APK mais recente:** `31264650731` → ✅ **SUCCESS** (APK `apk-20260808-1538`)
> **Builds de distro:** `31207631742` e `31191513295` → ✅ **SUCCESS**
> **Escopo:** varredura de funcionamento (estático, sem device) — 57 arquivos Kotlin analisados
> **Gerado por:** Buffy (assistente)

---

## 📊 RESUMO EXECUTIVO

| Indicador | Estado |
|---|---|
| Compilação (último build do APK) | ✅ **PASSOU** no HEAD atual |
| Sincronização local ↔ GitHub | ✅ Em dia (sem divergência) |
| Extrator de tarball (bug raiz do QEMU) | ✅ Corrigido em `b82f12f` (parse OCTAL + bit +x + validação) |
| Teclado no terminal (abre e digita) | ✅ Corrigido em `443c87e` + `9a50eb6` (verificado contra fonte do jackpal) |
| Git: clone com todos os arquivos | ✅ Corrigido (`67f884d`/`1574da0` + fallback ZIP) |
| Auto-início do Linux na abertura | ✅ Implementado (`ON_RESUME` + `autoStartSuppressed`) |
| Distro instala e QEMU inicia | ⚠️ Código pronto; **falta validação real no device** (T25) |
| Pendências registradas | 14 em aberto (`docs/TASKS-EM-ABERTO.md`) |

**Nota geral:** o código está saudável e compilando. Os problemas encontrados nesta auditoria são, em sua maioria, **riscos latentes** (caminhos que só ativam em cenários ainda não alcançados) e **validações que dependem de device**.

---

## 🧱 1. ESTADO DO REPOSITÓRIO

- `git status`: limpo (sem alterações não commitadas)
- Últimos commits (fixes recentes, todos enviados):
  - `b82f12f` — bug raiz OCTAL no TarExtractor + validação pós-extração + desinstalar distro
  - `d39a9df` — permissão de execução (+x) do qemu + diagnóstico de saída real
  - `443c87e` — digitação morta no terminal (InputConnection + cooked IME)
  - `9a50eb6` — teclado abre ao tocar no terminal (PhantomTerminalView)
- 1 build **failure** (`31264397804`, mesmo SHA) — **causa de infraestrutura** (Maven Central retornou 403 ao baixar `kotlin-reflect`), **não é bug de código**. O re-run passou.

---

## 🧪 2. SAÚDE DE COMPILAÇÃO (verificação estática)

- **Balanceamento de chaves/parênteses:** ✅ OK em 56/57 arquivos. O `EditorScreen.kt` aponta +1 de `{` (linha 129) — **falso positivo da heurística** (o build real passa; é uma string/lambda no código).
- **Imports potencialmente órfãos** (não quebram o build — apenas avisos; candidatos a limpeza):
  - `HomeScreen.kt`: `Intent`, `background`
  - `SearchScreen.kt`: `Box`
  - `EditorScreen.kt`: `Description` (ícone), `mutableStateListOf`
  - `OnboardingScreen.kt`: `DistroCatalog`
  - `QemuManager.kt`: `HttpURLConnection`
  - `SecretKeyWidgets.kt`: `fillMaxSize`, `CircleShape`
  - `CommandPalette.kt`: `FontWeight`
  - `DistroCard.kt`: `Arrangement`, `LocalVm`
  - ⚠️ Os `getValue`/`setValue` apontados em vários arquivos são **falsos positivos** (são usados implicitamente por `by remember { mutableStateOf(...) }`).
- **Manifest:** permissões completas (INTERNET, armazenamento externo + media + `MANAGE_EXTERNAL_STORAGE` API 30+, FGS + notificação) — coerente com o uso (Workspace público + VM em background).
- **Dependências:** sem duplicatas problemáticas; JGit, jackpal `emulatorview`, `commons-net` (FTP), Compose BOM + material3 + icons-extended.

---

## 🗺️ 3. AUDITORIA POR ÁREA

| Área | Arquivo(s) | Verificação | Status |
|---|---|---|---|
| Terminal (teclado) | `PhantomTerminalView.kt`, `TerminalScreen.kt` | `setUseCookedIME(true)`, sem override do InputConnection, `showSoftInput` no toque | ✅ OK (receita do Termux, conferida com fonte oficial) |
| Sessões de terminal | `TerminalManager.kt`, `ProcessTermSession.kt`, `SocketTermSession.kt`, `LogTermSession.kt` | Abas, anexação de processo/socket, sessão de log da instalação | ✅ OK |
| QEMU start | `QemuManager.kt` | Ordem: refreshBinary → rootfs/kernel → socket console (5s) → ctrl socket → watcher com buffer rotativo; diagnóstico de saída | ✅ OK |
| Extrator tarball | `DistroManager.kt` (`TarExtractor`) | Octal (`toLongOrNull(8)`), bit +x (0x40), anti path-traversal (`canonicalFile` + `check`) | ✅ OK |
| Instalação de distro | `DistroManager.kt` | Validação pós-extração (rootfs/kernel/qemu), erro claro, `refreshBinary()` no fim, **desinstalar** com confirmação | ✅ OK |
| Git clone | `GitManager.kt`, `GitScreen.kt` | JGit (clone completo) → fallback **zipball** descartando a pasta-raiz; `materializedCount` detecta checkout vazio | ✅ OK |
| Auto-início Linux | `PhantomApp.kt` | `ON_RESUME` + `activeId` + `autoStartSuppressed`; splash 850ms; onboarding bloqueia até terminar | ✅ OK |
| TerminalDock (rodapé) | `PhantomScaffold.kt` | Barra de terminais restaurada (`a0103b1`), mostra abas QEMU/shell, abre a tela do terminal | ✅ OK |
| Toolbox | `ToolboxScreen.kt` | Card QEMU com `lastError` visível, GuestPackages, diálogo de desinstalação, config (CPU/RAM/disco) | ✅ OK |
| Editor (WebView + bundle) | `EditorScreen.kt`, `assets/editor/*` | Bundle completo (`editor.js`, `editor-actions.js`, `editor-themes.js`, `index.html`) versionado | ✅ OK |
| Preview Hub | `PreviewPane.kt`, `LocalServer.kt` | Painel split + servidor HTTP local; **falta** PDF/CSV/SQL e PHP no guest | 🔶 Parcial (pendência P3.1) |
| FTP | `FtpClient.kt` (commons-net) | Upload/download com credenciais do catálogo | 🔶 Feito, sem SFTP (pendência P2.2+) |
| Backup | `CloudBackupManager.kt` | WebDAV pronto; **sem validação real** (pendência T22) | 🔶 Código pronto |
| AI Suite | `docs/roteador-ias.md`, `phantom-router.sh` | Especificação definida; Fase B (UI) e Fase C (runners) pendentes | 🔶 Em aberto |
| Segredos/API keys | `data/secrets/` | Catálogo de chaves + links de criação de token | ✅ OK (validar interação) |

---

## 🐞 4. BUGS / ERROS ENCONTRADOS (para correção futura)

### 🟠 MÉDIA

**M1 — Distro de terceiros com SÓ `rootfs.img` não inicia**
- Onde: `QemuManager.kt` → `start()`, linha `if (rootfs == null && kernel == null)`
- O que: o guard só reclama quando **ambos** faltam. Se uma distro futura (ex.: T29 Ubuntu/Alpine) vier apenas com `rootfs.img` (sem `-kernel/-append/-initrd`), o QEMU inicia **sem kernel** → tela preta/morte silenciosa.
- Hoje não afeta (só a Phantom é real e ela traz kernel+initrd). **Vira bug quando T29 for implementada.**
- Recomendação: ao preparar T29, exigir `kernel`+`initrd` no `DistroCatalog` ou passar kernel genérico; adicionar validação por tipo de distro.

**M2 — Projeto clonado via ZIP não vira repositório Git**
- Onde: `GitManager.cloneViaZip()` / `GitScreen.cloneRemote()` (fallback)
- O que: o fallback traz os arquivos mas **sem `.git`** — depois, `commit/push/pull` daquele projeto não funcionam (e o `GitScreen` mostra "não é repositório").
- Recomendação: após o ZIP, rodar `git init` + `git add/commit` inicial + apontar `origin` (documentado como aceitável hoje, mas deixar explícito no GitScreen).

### 🟡 BAIXA / COSMÉTICA

**B1 — Imports órfãos (9 arquivos, ver §2)** — não quebram build; limpar quando houver um commit de housekeeping.

**B2 — Mensagens do `ensureBinary` ainda citam "download do QEMU" em alguns fluxos** — o QEMU agora vem na distro; conferir que nenhum texto antigo confunda o usuário (a mensagem principal já foi corrigida em `d39a9df`).

**B3 — Acúmulo de releases** — há 8 releases de APK publicadas (07/08 a 08/08) + a órfã `apk-20260807-2209` (build de polish revertido, aguardando sua aprovação para excluir). Sugestão: manter só as 3 últimas.

### 🔵 INFORMATIVO / RISCO LATENTE (não é bug hoje)

- **I1 — `-virtfs` (9p) depende do build estático do QEMU** ter suporte a 9p. Validar no device (T16). Se faltar, trocar por `-hda` + montagem via script de init.
- **I2 — Boot TCG é lento** (1–3 min) no Note 10 Plus; o FGS mantém a VM viva em background. Comportamento esperado, mas o usuário pode achar que travou — considerar mensagem "iniciando...".
- **I3 — `autoStartSuppressed`**: não há UI para o usuário desativar o auto-início; se parar o QEMU pelo pill e reabrir o app, ele sobe de novo. Confirmar se é o comportamento desejado.
- **I4 — WebView do BrowserScreen**: conferir se `setAllowFileAccess(false)`/`setJavaScriptEnabled(true)` estão explícitos (boas práticas).
- **I5 — `applyDiskSize`** (redimensionar disco na instalação): verificar se usa sparse/fallocate; em disco cheio pode falhar de forma silenciosa.

---

## ✅ 5. O QUE FOI CONFIRMADO FUNCIONANDO (por análise)

1. **Teclado no terminal** — cadeia completa verificada ponta a ponta contra a fonte oficial do jackpal v1.0.70 (toque → foco → IME `TYPE_CLASS_TEXT` → `TermSession.write()` → stdin do processo).
2. **Instalação da distro** — extração agora correta (octal + +x + validação) com log em tempo real e erro claro.
3. **QEMU start** — ordem de dependências correta; diagnóstico real de saída (`QEMU saiu (código X): ...`) quando morre rápido.
4. **Git clone** — duas estratégias (JGit completo + fallback ZIP) com proteção anti path-traversal.
5. **Auto-início do Linux** — disparado em `ON_RESUME` sem navegação intrusiva; pill do topo + TerminalDock no rodapé.
6. **Segurança do extrator** — `canonicalFile` + `check()` bloqueiam entries fora do destino (zip-slip/tar-slip).

---

## 📋 6. PENDÊNCIAS JÁ REGISTRADAS (não criar duplicatas)

Consultar `docs/TASKS-EM-ABERTO.md` (documento único): **14 em aberto** — bloqueadas (T25 device, R12 polish com aprovação, release órfã, T22 WebDAV, T16 virtio) e executáveis (T29 distros, Fase B/C AI Suite, P3.1 Preview, P3.2 cursor/seleção, P2.2+ SFTP, P3.3 GitHub colaborativo, P1.5 split, executar código na VM, explorer lateral do editor).

---

## 🎯 7. PRÓXIMOS PASSOS RECOMENDADOS

1. **Device (desbloqueia tudo):** instalar `apk-20260808-1538` → validar T25 (instalar Phantom, terminal digita, QEMU inicia + console, Git traz todos os arquivos, editor, Preview Hub). Bugs achados viram novas tasks.
2. **Código:** T29 (publicar distros — obrigatório resolver **M1** antes) e Fase B (UI de conversa entre IAs).
3. **Código:** P3.1 completar (PDF/CSV/SQL + PHP no guest) e "Executar código na VM".
4. **Housekeeping:** limpar imports órfãos (B1), excluir release órfã (aguarda OK), manter 3 releases.
5. **UI:** qualquer polish **somente com aprovação prévia** (regra R12).

---

*Relatório gerado por auditoria estática em 08/08/2026. Não substitui a validação em device (T25).*
