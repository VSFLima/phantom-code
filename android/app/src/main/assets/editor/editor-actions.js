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
