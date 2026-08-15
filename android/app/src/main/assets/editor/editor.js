// Phantom Editor — motor Ace (do SPck) com API PhantomEditor.*
// Compatível com a ponte JS<->Kotlin do EditorScreen.kt (idêntica ao CodeMirror).
// Carrega depois de ace.bundle.js; expõe window.PhantomEditor + helpers.
(function () {
  'use strict';

  var editor = null;       // instância ace.edit
  var currentMode = 'text'; // mode ace/mode/<x>
  var lastTheme = 'phantom';
  var lastLang = '';        // nome do arquivo (para detecção de linguagem)

  // ── Mapa extensão -> modo Ace ─────────────────────────────────────────
  var MODES = {
    js: 'javascript', mjs: 'javascript', cjs: 'javascript', jsx: 'javascript', ts: 'typescript',
    tsx: 'typescript', json: 'json', jsonc: 'json', json5: 'json5',
    html: 'html', htm: 'html', xml: 'xml', svg: 'xml', xhtml: 'html',
    css: 'css', scss: 'scss', sass: 'scss', less: 'less', styl: 'stylus',
    py: 'python', pyw: 'python', rb: 'ruby', php: 'php', java: 'java', kt: 'kotlin',
    kts: 'kotlin', go: 'go', rs: 'rust', c: 'c_cpp', h: 'c_cpp', cpp: 'c_cpp', cc: 'c_cpp',
    hpp: 'c_cpp', cs: 'csharp', swift: 'swift', m: 'objectivec', mm: 'objectivec',
    sh: 'sh', bash: 'sh', zsh: 'sh', bat: 'batchfile', ps1: 'powershell',
    sql: 'sql', md: 'markdown', markdown: 'markdown', tex: 'latex', asciidoc: 'asciidoc',
    yaml: 'yaml', yml: 'yaml', toml: 'toml', ini: 'ini', conf: 'ini', cfg: 'ini',
    lua: 'lua', r: 'r', pl: 'perl', clj: 'clojure', cljs: 'clojure', scala: 'scala',
    hs: 'haskell', elixir: 'elixir', exs: 'elixir', ex: 'elixir', erl: 'erlang',
    dart: 'dart', v: 'v', zig: 'zig', nim: 'nim', groovy: 'groovy', gradle: 'groovy',
    txt: 'text', log: 'text', diff: 'diff', patch: 'diff', dockerfile: 'dockerfile',
    vue: 'vue', svelte: 'svelte', vb: 'vbscript',
    f: 'fortran', f90: 'fortran', graphql: 'graphql', gql: 'graphql',
    proto: 'protobuf', pug: 'pug', jade: 'jade', coffee: 'coffee', elm: 'elm',
    prisma: 'prisma', solidity: 'solidity', sol: 'solidity', terraform: 'hcl',
    tf: 'hcl', tfvars: 'hcl', hcl: 'hcl', handlebars: 'handlebars', hbs: 'handlebars',
    liquid: 'liquid', makefile: 'makefile', mk: 'makefile', jsp: 'jsp',
  };

  function extOf(name) {
    if (!name) return 'text';
    var base = name.split('/').pop() || name;
    var lower = base.toLowerCase();
    if (lower.indexOf('dockerfile') === 0) return 'dockerfile';
    if (lower === 'makefile') return 'makefile';
    var idx = base.lastIndexOf('.');
    return idx < 0 ? 'text' : base.slice(idx + 1).toLowerCase();
  }

  function aceModeFor(name) {
    var ext = extOf(name);
    var m = MODES[ext];
    return m || 'text';
  }

  // Carrega o módulo do Ace (mode/theme/keybinding) sob demanda. O ace.bundle
  // expõe o loader AMD (window.ace + window.define) e os arquivos da pasta ace/
  // se registram nele; ace.require resolve do cache ou dispara o load dinâmico.
  function requireAce(moduleName) {
    try {
      return window.ace.require(moduleName);
    } catch (e) {
      return null;
    }
  }

  // ── init(elementId, value, name) ──────────────────────────────────────
  window.PhantomEditor = {};
  window.PhantomEditor.init = function (element, value, name) {
    var container = typeof element === 'string' ? document.getElementById(element) : element;
    if (!container) return;
    lastLang = name || '';
    currentMode = aceModeFor(name);

    // ace.bundle.js (nome nao casa com o regex ace.js) nao infere basePath.
    // Modos/temas/workers ficam na subpasta ace/.
    try {
      window.ace.config.set('basePath', 'ace/');
      // O moduleUrl geraria "keyboard-<x>.js", mas os arquivos reais sao
      // keybinding-<x>.js (o SPck pre-carrega; aqui mapeamos explicitamente).
      window.ace.config.setModuleUrl('ace/keyboard/vscode', 'ace/keybinding-vscode.js');
      window.ace.config.setModuleUrl('ace/keyboard/emacs', 'ace/keybinding-emacs.js');
      window.ace.config.setModuleUrl('ace/keyboard/sublime', 'ace/keybinding-sublime.js');
    } catch (e) {}

    editor = window.ace.edit(container);
    editor.setOptions({
      fontSize: 14,
      showPrintMargin: false,
      wrap: false,
      useWorker: true,
    });
    editor.session.setMode('ace/mode/' + currentMode);
    editor.session.setValue(value == null ? '' : String(value));
    editor.setTheme('ace/theme/' + lastTheme);
    editor.selection.clearSelection();
    editor.gotoLine(1, 0, false);

    // Sincroniza dirty -> AndroidBridge
    editor.session.on('change', function () {
      if (window.AndroidBridge && window.AndroidBridge.dirty) {
        window.AndroidBridge.dirty();
      }
    });

    // Atalhos físicos do teclado WebView (mesmos do CodeMirror)
    document.addEventListener('keydown', function (e) {
      var bridge = window.AndroidBridge;
      var mod = e.ctrlKey || e.metaKey;
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
      }
    }, true);
  };

  // ── Conteúdo ──────────────────────────────────────────────────────────
  window.PhantomEditor.getValue = function () {
    return editor ? editor.session.getValue() : '';
  };
  window.PhantomEditor.setValue = function (content) {
    if (!editor) return;
    editor.session.setValue(content == null ? '' : String(content));
    editor.gotoLine(1, 0, false);
    editor.selection.clearSelection();
  };
  window.PhantomEditor.getBeforeCursor = function () {
    if (!editor) return '';
    var pos = editor.getCursorPosition();
    var line = editor.session.getLine(pos.row) || '';
    return line.slice(0, pos.column);
  };
  window.PhantomEditor.replaceWordBeforeCursor = function (text) {
    if (!editor) return;
    var pos = editor.getCursorPosition();
    var line = editor.session.getLine(pos.row) || '';
    var before = line.slice(0, pos.column);
    var m = before.match(/[\w.-]*$/);
    var startCol = m ? pos.column - m[0].length : pos.column;
    editor.session.replace(
      new window.ace.Range(pos.row, startCol, pos.row, pos.column),
      String(text)
    );
  };
  window.PhantomEditor.insertText = function (text) {
    if (!editor) return;
    editor.insert(String(text));
  };
  window.PhantomEditor.getCursor = function () {
    if (!editor) return JSON.stringify({ line: 1, col: 1 });
    var pos = editor.getCursorPosition();
    return JSON.stringify({ line: pos.row + 1, col: pos.column + 1 });
  };
  window.PhantomEditor.gotoLine = function (line) {
    if (!editor) return;
    var n = Math.max(1, parseInt(line, 10) || 1);
    editor.gotoLine(n, 0, true);
    editor.focus();
  };
  window.PhantomEditor.focus = function () {
    if (editor) editor.focus();
  };

  // ── Edição ────────────────────────────────────────────────────────────
  window.PhantomEditor.undo = function () { if (editor) editor.undo(); };
  window.PhantomEditor.redo = function () { if (editor) editor.redo(); };
  window.PhantomEditor.selectAll = function () { if (editor) editor.selectAll(); };
  window.PhantomEditor.duplicateLine = function () {
    if (!editor) return;
    var exec = editor.commands && editor.commands.byName ? editor.commands.byName.duplicatelines : null;
    if (exec) { exec.exec(editor); return; }
    var pos = editor.getCursorPosition();
    var line = editor.session.getLine(pos.row) || '';
    editor.session.insert({ row: pos.row + 1, column: 0 }, line + '\n');
  };
  window.PhantomEditor.moveLineUp = function () {
    if (editor) editor.moveLinesUp();
  };
  window.PhantomEditor.moveLineDown = function () {
    if (editor) editor.moveLinesDown();
  };
  window.PhantomEditor.toggleComment = function () {
    if (editor) editor.toggleCommentLines();
  };

  // ── Estrutura (autocomplete / folding) ────────────────────────────────
  window.PhantomEditor.complete = function () {
    if (!editor) return;
    editor.execCommand('startAutocomplete');
  };
  window.PhantomEditor.fold = function () { if (editor) editor.session.toggleFold(); };
  window.PhantomEditor.unfold = function () { if (editor) editor.session.unfold(); };
  window.PhantomEditor.foldAll = function () {
    if (editor) editor.session.foldAll(0, editor.session.getLength(), true);
  };
  window.PhantomEditor.unfoldAll = function () {
    if (!editor) return;
    var folds = editor.session.getAllFolds();
    (folds || []).forEach(function (f) { editor.session.unfold(f, true); });
  };

  // ── Preferências ──────────────────────────────────────────────────────
  // Temas registrados em ace-themes.js (window.PHANTOM_ACE_THEMES).
  window.PhantomEditor.setTheme = function (id) {
    lastTheme = id || 'phantom';
    if (editor) editor.setTheme('ace/theme/' + lastTheme);
  };
  window.PhantomEditor.getTheme = function () { return lastTheme; };

  window.PhantomEditor.setFontSize = function (px) {
    if (!editor) return;
    var n = parseInt(px, 10);
    if (n > 0) editor.setFontSize(n);
  };
  window.PhantomEditor.setWordWrap = function (enabled) {
    if (editor) editor.setOption('wrap', !!enabled);
  };
  window.PhantomEditor.setFontFamily = function (id) {
    if (!editor) return;
    var fam = {
      mono: '"JetBrains Mono", "Fira Code", Consolas, monospace',
      droid: '"Droid Sans Mono", monospace',
      sans: '-apple-system, "Segoe UI", Roboto, sans-serif',
    }[id] || '"JetBrains Mono", "Fira Code", monospace';
    editor.setOption('fontFamily', fam);
  };
  window.PhantomEditor.getFontFamily = function () { return 'mono'; };
  window.PhantomEditor.setCursorStyle = function (id) {
    if (!editor) return;
    var style = ['blink-block', 'block', 'bar', 'underline'].indexOf(id) >= 0 ? id : 'blink-block';
    editor.setOption('cursorStyle', style.replace('blink-', ''));
  };
  window.PhantomEditor.getCursorStyle = function () { return 'blink-block'; };
  window.PhantomEditor.setSelectionColor = function (hex) {
    if (!editor) return;
    if (/^#[0-9a-fA-F]{6}$/.test(hex || '')) {
      var a = hex.slice(1);
      var r = parseInt(a.slice(0, 2), 16), g = parseInt(a.slice(2, 4), 16), b = parseInt(a.slice(4, 6), 16);
      editor.setStyle('phantom-custom-selection');
      document.documentElement.style.setProperty('--phantom-sel', 'rgba(' + r + ',' + g + ',' + b + ',0.35)');
    }
  };

  // ── Novos: keybindings (Vim/Emacs/VS Code) e format (beautify) ───────
  window.PhantomEditor.setKeybindings = function (id) {
    if (!editor) return;
    try {
      if (id === 'vim') {
        var vim = requireAce('ace/keyboard/vim');
        editor.setKeyboardHandler(vim && vim.handler ? vim.handler : null);
      } else if (id === 'emacs') {
        var emacs = requireAce('ace/keyboard/emacs');
        editor.setKeyboardHandler(emacs && emacs.handler ? emacs.handler : null);
      } else if (id === 'vscode') {
        var vsc = requireAce('ace/keyboard/vscode');
        editor.setKeyboardHandler(vsc && vsc.handler ? vsc.handler : null);
      } else {
        editor.setKeyboardHandler(null);
      }
    } catch (e) { /* keybinding não carregado */ }
  };

  // Beautify via editor_tools.bundle.js.
  // Exposto globalmente como window.beautifier = { js(code, opts), css, html }.
  window.PhantomEditor.format = function () {
    if (!editor) return;
    var src = editor.session.getValue();
    if (!src) return;
    var b = window.beautifier || null;
    if (!b) return;

    var line = editor.session.getLine(0) || '';
    var m = line.match(/^(\s+)/);
    var indentChar = m ? m[1].charAt(0) : ' ';
    var opts = {
      indent_size: indentChar === '\t' ? 1 : 4,
      indent_char: indentChar,
      max_preserve_newlines: 20,
      preserve_newlines: true,
      keep_array_indentation: false,
      break_chained_methods: false,
      indent_scripts: 'normal',
      brace_style: 'none,preserve-inline',
      space_before_conditional: true,
      unescape_strings: false,
      jslint_happy: false,
      end_with_newline: false,
      wrap_line_length: '0',
      indent_inner_html: false,
      comma_first: false,
      e4x: true,
      indent_empty_lines: true,
    };

    var out = null;
    try {
      var mode = currentMode;
      if (mode === 'json' || mode === 'json5' || mode === 'javascript' || mode === 'typescript') {
        out = b.js(src, opts);
      } else if (mode === 'html' || mode === 'vue' || mode === 'xml' || mode === 'text') {
        out = b.html(src, opts);
      } else if (mode === 'css' || mode === 'scss' || mode === 'less') {
        out = b.css(src, opts);
      }
    } catch (e) { out = null; }
    if (out != null && out !== src) {
      var pos = editor.getCursorPosition();
      editor.session.setValue(out);
      editor.gotoLine(pos.row + 1, pos.column, false);
    }
  };

  // ── Helper de exposição (para console de debug) ───────────────────────
  window.PhantomEditor.getMode = function () { return currentMode; };
})();
