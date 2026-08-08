package com.phantomcode.app.ui.components

import android.content.Context
import android.view.MotionEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import jackpal.androidterm.emulatorview.EmulatorView

/**
 * Wrapper do [EmulatorView] do jackpal que GARANTE abertura do teclado virtual.
 *
 * O EmulatorView original não abre o IME de forma confiável quando tocado dentro
 * de um AndroidView do Compose — o foco é pedido, mas o `InputMethodManager`
 * nunca é acionado. Esta subclasse:
 *
 *  1. pede foco + abre o teclado via `showSoftInput` no toque (ACTION_DOWN);
 *  2. expõe `showKeyboard()` para o Compose chamar após anexar sessão/aba;
 *  3. configura o InputConnection como editor de texto (mesma receita do Termux),
 *     garantindo que caracteres do teclado virtual cheguem à sessão VT100.
 */
class PhantomTerminalView(context: Context) : EmulatorView(context, null) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isEnabled = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType =
            EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return BaseInputConnection(this, true)
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
