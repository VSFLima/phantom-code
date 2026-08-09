package com.phantomcode.app.ui.components

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.inputmethod.InputMethodManager
import com.phantomcode.app.data.vm.TerminalPrefs
import jackpal.androidterm.emulatorview.EmulatorView

/**
 * Wrapper do [EmulatorView] do jackpal que garante uso total no celular:
 *
 *  1. **Teclado virtual** — pede foco e abre o IME no toque (`showKeyboard`),
 *     com `setUseCookedIME(true)` para o GBoard entregar os caracteres
 *     (digitar funciona sem teclado físico);
 *  2. **Pinça (zoom)** — dois dedos aumentam/diminuem o tamanho da fonte; o
 *     jackpal reajusta colunas/linhas sozinho (`updateSize`), então a quebra
 *     de linha acompanha o tamanho escolhido pelo usuário;
 *  3. **Toque longo = seleção** — ativa o modo de seleção de texto do jackpal
 *     (segurar + arrastar); Copiar/Colar ficam na barra de ações do
 *     TerminalScreen.
 *
 * NÃO sobrescreve `onCreateInputConnection` — o EmulatorView do jackpal já
 * roteia cada caractere digitado para `TermSession.write()`.
 */
class PhantomTerminalView(context: Context) : EmulatorView(context, null) {

    private var lastTextSizeDp = TerminalPrefs.DEFAULT_FONT_SIZE_SP
    private var onFontSizeChanged: ((Int) -> Unit)? = null
    private var onSelectionChanged: ((Boolean) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var baseDp = 0f

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                baseDp = lastTextSizeDp.toFloat()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val size = (baseDp * detector.scaleFactor)
                    .toInt()
                    .coerceIn(TerminalPrefs.MIN_FONT_SIZE_SP, TerminalPrefs.MAX_FONT_SIZE_SP)
                if (size != lastTextSizeDp) {
                    lastTextSizeDp = size
                    runCatching { setTextSize(size) }
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                onFontSizeChanged?.invoke(lastTextSizeDp)
            }
        },
    )

    private val longPressDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Ativa o modo de seleção do jackpal (segurar + arrastar para
                // selecionar; Copiar na barra do TerminalScreen).
                if (!getSelectingText()) toggleSelectingText()
            }
        },
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isEnabled = true
        // IME "cooked": inputType = TYPE_CLASS_TEXT — sem isso o default do
        // jackpal é TYPE_NULL e o teclado (ex.: GBoard) abre mas não entrega
        // os caracteres via commitText. O app Term de referência faz o mesmo.
        setUseCookedIME(true)
    }

    fun setOnFontSizeChanged(listener: (Int) -> Unit) {
        onFontSizeChanged = listener
    }

    fun setOnSelectionChanged(listener: (Boolean) -> Unit) {
        onSelectionChanged = listener
    }

    /** Aplica o tamanho salvo nas preferências (dp) e sincroniza a base do zoom. */
    fun applyFontSize(sizeDp: Int) {
        lastTextSizeDp = sizeDp
        runCatching { setTextSize(sizeDp) }
    }

    override fun toggleSelectingText() {
        super.toggleSelectingText()
        onSelectionChanged?.invoke(getSelectingText())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Gesto de pinça (2 dedos): só o ScaleGestureDetector — evita o scroll
        // interno interpretar o movimento como "rolagem" durante o zoom.
        if (event.pointerCount >= 2) {
            scaleDetector.onTouchEvent(event)
            return true
        }
        if (event.action == MotionEvent.ACTION_DOWN && !getSelectingText()) {
            requestFocus()
            showKeyboard()
        }
        longPressDetector.onTouchEvent(event)
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
