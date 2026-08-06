// Phantom-Code — CodeMirror 6 bundle (tema Cyber-Phantom)
// Build: npm run build (esbuild) → android/app/src/main/assets/editor/editor.js
import { basicSetup, EditorView } from "codemirror"
import { javascript } from "@codemirror/lang-javascript"
import { python } from "@codemirror/lang-python"
import { html } from "@codemirror/lang-html"
import { css } from "@codemirror/lang-css"
import { json } from "@codemirror/lang-json"
import { markdown } from "@codemirror/lang-markdown"

// ── Tema Cyber-Phantom (tokens do Documento Mestre §10.1) ─────────
const phantomTheme = EditorView.theme(
  {
    "&": { color: "#E5E7EB", backgroundColor: "#000000", fontSize: "14px" },
    ".cm-scroller": {
      fontFamily: "'JetBrains Mono', 'Fira Code', Menlo, Consolas, monospace",
      lineHeight: "1.6",
    },
    ".cm-content": { caretColor: "#9F4DFF", padding: "8px 0" },
    "&.cm-focused .cm-selectionBackground, ::selection": { backgroundColor: "#9F4DFF40" },
    ".cm-gutters": {
      backgroundColor: "#000000",
      color: "#4B5563",
      border: "none",
      borderRight: "1px solid #121212",
    },
    ".cm-activeLine": { backgroundColor: "#121212" },
    ".cm-activeLineGutter": { backgroundColor: "#0f0f0f", color: "#9F4DFF" },
    ".cm-cursor": { borderLeftColor: "#9F4DFF", borderLeftWidth: "2px" },
    ".cm-matchingBracket": { backgroundColor: "#9F4DFF33", outline: "1px solid #9F4DFF" },
    ".cm-selectionMatch": { backgroundColor: "#00FFFF22" },
    ".cm-searchMatch": { backgroundColor: "#00FFFF40" },
    ".cm-tooltip": {
      backgroundColor: "#121212",
      border: "1px solid #4B5563",
      color: "#E5E7EB",
    },
  },
  { dark: true },
)

// ── Detecção de linguagem pela extensão ───────────────────────────
function langFor(name) {
  const n = (name || "").toLowerCase()
  if (/\.(js|jsx|ts|tsx|mjs|cjs)$/.test(n)) return javascript()
  if (n.endsWith(".py")) return python()
  if (/\.(html|htm)$/.test(n)) return html()
  if (n.endsWith(".css")) return css()
  if (n.endsWith(".json")) return json()
  if (/\.(md|markdown)$/.test(n)) return markdown()
  return []
}

// ── API global consumida pelo Kotlin (WebView) ────────────────────
let view = null
let saveTimer = null

function scheduleSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    if (view && window.AndroidBridge) window.AndroidBridge.save(view.state.doc.toString())
  }, 800)
}

window.PhantomEditor = {
  /** Inicializa o editor no container. */
  init(container, initialValue, fileName) {
    view = new EditorView({
      parent: container,
      doc: initialValue || "",
      extensions: [
        basicSetup,
        phantomTheme,
        langFor(fileName),
        EditorView.updateListener.of((u) => {
          if (u.docChanged) {
            if (window.AndroidBridge) window.AndroidBridge.dirty()
            scheduleSave()
          }
        }),
      ],
    })
    view.focus()
  },
  /** Substitui todo o conteúdo (usado na primeira carga). */
  setValue(v) {
    if (!view) return
    view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: v || "" } })
  },
  getValue() {
    return view ? view.state.doc.toString() : ""
  },
  focus() {
    if (view) view.focus()
  },
}
