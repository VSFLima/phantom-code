// Comandos nativos do editor sobre o DOM do CodeMirror (P1.2, P1.3, P2.3).
(() => {
  const send = (key, code, options = {}) => {
    const target = document.querySelector('.cm-content');
    if (!target) return;
    target.focus();
    target.dispatchEvent(new KeyboardEvent('keydown', {
      key,
      code,
      ctrlKey: true,
      bubbles: true,
      cancelable: true,
      ...options,
    }));
  };

  // Comandos que o CodeMirror já conhece via defaultKeymap (eventos sintéticos):
  window.PhantomEditor.undo = () => send('z', 'KeyZ');
  window.PhantomEditor.redo = () => send('y', 'KeyY');
  window.PhantomEditor.selectAll = () => send('a', 'KeyA');
  window.PhantomEditor.duplicateLine = () => send('d', 'KeyD');
  window.PhantomEditor.moveLineUp = () => send('ArrowUp', 'ArrowUp', { altKey: true, ctrlKey: false });
  window.PhantomEditor.moveLineDown = () => send('ArrowDown', 'ArrowDown', { altKey: true, ctrlKey: false });
  window.PhantomEditor.toggleComment = () => send('/', 'Slash');

  // ── Fonte do editor (P3.2) ──
  // Aplica a família de fonte no DOM do CodeMirror sem depender do bundle
  // minificado: seta a fonte no contêiner; o editor-actions roda por último.
  const FONT_FAMILIES = {
    mono: '"JetBrains Mono", "Fira Code", monospace',
    droid: '"Droid Sans Mono", monospace',
    sans: '-apple-system, "Segoe UI", Roboto, sans-serif',
  };
  window.PhantomEditor.setFontFamily = (id) => {
    const css = FONT_FAMILIES[id] || FONT_FAMILIES.mono;
    const apply = () => {
      const cm = document.querySelector('.cm-editor');
      if (cm) cm.style.fontFamily = css;
      const content = document.querySelector('.cm-content');
      if (content) content.style.fontFamily = css;
    };
    apply();
    // O CodeMirror re-renderiza linhas; reaplica após um tick e na próxima
    // mutação do DOM (defensivo para linhas criadas depois).
    setTimeout(apply, 50);
    setTimeout(apply, 300);
    window.PhantomEditor.getFontFamily = () => id;
  };

  // ── Cursor e seleção (P3.2) ──
  // Overrides via CSS com !important injetado por último (o bundle re-renderiza
  // a cada reconfigure, mas estas regras prevalecem por especificidade).
  const phantomStyles = document.createElement('style');
  phantomStyles.textContent = `
    .cm-editor.phantom-caret-block .cm-cursor,
    .cm-editor.phantom-caret-underline .cm-cursor {
      border-left: none !important;
      margin-left: 0 !important;
      width: 0.75em !important;
      background: var(--phantom-caret, #9F4DFF) !important;
    }
    .cm-editor.phantom-caret-underline .cm-cursor {
      transform: translateY(calc(100% - 2px));
      height: 2px !important;
    }
    .cm-editor .cm-cursorLayer { animation: none !important; }
    @keyframes phantom-cm-blink { 0% {}, 50% { opacity: 0 }, 100% {} }
    .cm-editor.phantom-caret-blink-block.cm-focused .cm-cursorLayer {
      animation: steps(1) phantom-cm-blink 1.2s infinite !important;
    }
    .cm-editor.phantom-selection-custom .cm-selectionBackground,
    .cm-editor.phantom-selection-custom ::selection {
      background-color: var(--phantom-selection, #9F4DFF40) !important;
    }
  `;
  document.head.appendChild(phantomStyles);

  const CARET_STYLES = ['blink-block', 'block', 'bar', 'underline'];
  const readCaretColor = () => {
    const content = document.querySelector('.cm-content');
    if (!content) return '#9F4DFF';
    const color = getComputedStyle(content).caretColor;
    return color && color !== 'rgb(0, 0, 0)' && color !== 'currentcolor' ? color : '#9F4DFF';
  };
  window.PhantomEditor.setCursorStyle = (id) => {
    const style = CARET_STYLES.includes(id) ? id : 'blink-block';
    const root = document.querySelector('.cm-editor');
    if (!root) return;
    root.classList.remove(...CARET_STYLES.map((c) => 'phantom-caret-' + c));
    root.classList.add('phantom-caret-' + style);
    root.style.setProperty('--phantom-caret', readCaretColor());
    window.PhantomEditor.getCursorStyle = () => style;
  };
  window.PhantomEditor.setSelectionColor = (hex) => {
    const root = document.querySelector('.cm-editor');
    if (!root) return;
    const color = (hex || '').trim();
    if (/^#[0-9a-fA-F]{6}$/.test(color) || /^#[0-9a-fA-F]{8}$/.test(color)) {
      root.style.setProperty('--phantom-selection', color);
      root.classList.add('phantom-selection-custom');
    } else {
      root.classList.remove('phantom-selection-custom');
    }
    window.PhantomEditor.getSelectionColor = () => color || null;
  };

  // ── Autocomplete e code folding (P3, estilo SPCK) ──
  // Ctrl+Espaço abre o autocomplete; Ctrl+Shift+[ / ] dobra/desdobra o bloco;
  // Ctrl+Alt+[ / ] dobra/desdobra tudo (keymaps do CodeMirror).
  window.PhantomEditor.complete = () => send(' ', 'Space');
  window.PhantomEditor.fold = () => send('[', 'BracketLeft', { shiftKey: true });
  window.PhantomEditor.unfold = () => send(']', 'BracketRight', { shiftKey: true });
  window.PhantomEditor.foldAll = () => send('[', 'BracketLeft', { altKey: true });
  window.PhantomEditor.unfoldAll = () => send(']', 'BracketRight', { altKey: true });

  // ── Snippets estilo SPCK: digitar a palavra-chave + Tab insere o bloco ──
  const SNIPPETS = {
    html5: '<!DOCTYPE html>\n<html lang="pt-BR">\n<head>\n  <meta charset="utf-8" />\n  <meta name="viewport" content="width=device-width, initial-scale=1" />\n  <title>Novo documento</title>\n</head>\n<body>\n\n</body>\n</html>',
    fun: 'fun main() {\n    \n}',
    imp: 'import ',
    cl: 'console.log();',
    log: 'console.log();',
    fn: 'function () {\n    \n}',
    cls: 'class  {\n    constructor() {\n        \n    }\n}',
    if: 'if () {\n    \n}',
    for: 'for (let i = 0; i < ; i++) {\n    \n}',
    def: 'def ():\n    pass',
    sh: '#!/bin/bash\nset -e\n',
  };

  const expandSnippet = (e) => {
    const before = window.PhantomEditor.getBeforeCursor();
    if (!before) return false;
    const word = (before.match(/[\w.-]+$/) || [''])[0];
    const body = SNIPPETS[word.toLowerCase()];
    if (!body) return false;
    e.preventDefault();
    window.PhantomEditor.replaceWordBeforeCursor(body);
    return true;
  };

  // Atalhos de teclado físico no WebView (P1.2):
  //   Ctrl+S salvar · Ctrl+F/H buscar · Ctrl+G ir para linha
  document.addEventListener('keydown', (e) => {
    const bridge = window.AndroidBridge;
    const mod = e.ctrlKey || e.metaKey;
    if (mod && !e.shiftKey && e.key === 's') {
      e.preventDefault();
      if (bridge) {
        bridge.save(window.PhantomEditor.getValue());
        bridge.saved();
      }
    } else if (mod && !e.shiftKey && (e.key === 'f' || e.key === 'h')) {
      e.preventDefault();
      if (bridge) bridge.openSearch();
    } else if (mod && !e.shiftKey && e.key === 'g') {
      e.preventDefault();
      if (bridge) bridge.openGoto();
    } else if (e.key === 'Tab') {
      expandSnippet(e);
    }
  }, true);
})();
