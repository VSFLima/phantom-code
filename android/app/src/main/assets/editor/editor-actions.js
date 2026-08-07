// Small native-friendly commands layered on top of CodeMirror's content DOM.
(() => {
  const send = (key, options = {}) => {
    const target = document.querySelector('.cm-content');
    if (!target) return;
    target.focus();
    target.dispatchEvent(new KeyboardEvent('keydown', {
      key,
      code: key,
      ctrlKey: true,
      bubbles: true,
      cancelable: true,
      ...options,
    }));
  };

  window.PhantomEditor.undo = () => send('z');
  window.PhantomEditor.redo = () => send('y');
  window.PhantomEditor.selectAll = () => send('a');
})();
