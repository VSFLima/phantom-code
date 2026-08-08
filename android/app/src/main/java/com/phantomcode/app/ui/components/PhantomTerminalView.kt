package com.phantomcode.app.ui.components

import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import jackpal.androidterm.emulatorview.EmulatorView

/**
 * Wrapper do [EmulatorView] do jackpal que GARANTE abertura do teclado virtual
 * SEM interferir no roteamento de teclas.
 *
 * O EmulatorView original não abre o IME de forma confiável quando tocado dentro
 * de um AndroidView do Compose — o foco é pedido, mas o `InputMethodManager`
 * nunca é acionado. Esta subclasse:
 *
 *  1. pede foco + abre o teclado via `showSoftInput` no toque (ACTION_DOWN);
 *  2. expõe `showKeyboard()` para o Compose chamar após anexar sessão/aba;
 *  3. **NÃO sobrescreve `onCreateInputConnection`** — o EmulatorView do jackpal já
 *     devolve um InputConnection que roteia cada caractere digitado para
 *     `TermSession.write()` (stdin do processo). Sobrescrever isso com um
 *     `BaseInputConnection` genérico faz o teclado abrir mas NADA ser digitado.
 */
class PhantomTerminalView(context: Context) : EmulatorView(context, null) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isEnabled = true
        // IME "cooked": inputType = TYPE_CLASS_TEXT — sem isso o default do
        // jackpal é TYPE_NULL e o teclado (ex.: GBoard) abre mas não entrega
        // os caracteres via commitText. O app Term de referência faz o mesmo.
        setUseCookedIME(true)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            requestFocus()
            showKeyboard()
        }
        return super.onTouchEvent(event)
    }

    /** Abre o teclado virtual (postado na main thread; seguro chamar de qualquer lugar). */
    fun showKeyboard() {
        post {
            requestFocus()
            runCatching {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
}
