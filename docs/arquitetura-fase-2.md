# Arquitetura — Fase 2 (Explorer real + Editor CodeMirror 6)

> Status: ✅ implementado (T11–T13) · Build manual no CI

## Decisões técnicas

| Item | Decisão |
|------|---------|
| **Workspace** | `filesDir/workspace` (D3 — independente da distro; mesma pasta do guest via 9p na Fase 3) |
| **Gerenciador** | `WorkspaceManager` — lista/cria/renomeia/exclui com bloqueio de path traversal |
| **Editor** | CodeMirror 6 em WebView (`assets/editor/`) — bundle gerado por esbuild (`android/editor/`) |
| **Ponte JS↔Kotlin** | `@JavascriptInterface` → `window.AndroidBridge.save/dirty`; ProGuard mantém os métodos |
| **Auto-save (D18)** | Debounce 800ms no JS + salvar ao fechar/voltar (BackHandler) |
| **Sessão** | `SessionManager` (SharedPreferences): último arquivo, projeto ativo, recentes |
| **SAF** | Importar pasta do aparelho (`OpenDocumentTree` + `androidx.documentfile`) |
| **Linguagens** | JS/TS, Python, HTML, CSS, JSON, Markdown (detecção por extensão) |

## Arquivos

```
data/WorkspaceManager.kt   → operações reais de arquivo (D3)
data/SessionManager.kt     → sessão (D18)
ui/screens/ExplorerScreen.kt → árvore real, FAB, long-press, diálogos
ui/screens/EditorScreen.kt → WebView + CodeMirror + ponte + auto-save
ui/components/PhantomDialog.kt → diálogos estilizados (texto, confirmação, ações)
ui/screens/HomeScreen.kt   → Recent Projects reais + Continuar sessão + import SAF
editor/                    → fonte npm (src/editor.js) → bundle em assets/editor/
```

## Tema do editor

Tema `EditorView.theme` espelhando o Cyber-Phantom:
`#000000` fundo · `#E5E7EB` texto · cursor `#9F4DFF` · linha ativa `#121212` · gutters `#4B5563`.

## Fluxos

1. **Abrir arquivo** → Explorer (tap) ou Home (Continuar) → rota `editor/{path}` (URL-encoded)
2. **Editar** → JS marca `dirty()` e salva via `save()` a cada 800ms sem digitar
3. **Fechar/voltar** → `getValue()` → grava → `popBackStack`
4. **Novo projeto** → Home (dialog) ou Explorer (FAB) → cria pasta no workspace
5. **Importar pasta** → SAF → cópia recursiva para `workspace/<nome>`

## Próxima fase (T14–T18 — VM QEMU + Terminal)

- Binários QEMU arm64 (jniLibs) + comando headless (§8.1)
- Gerenciador de distros (Phantom Base + checksum)
- virtio-9p (workspace) + virtio-serial (console → terminal jackpal)
