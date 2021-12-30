package link.nombi.androidskklang

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnKeyListener
import android.content.res.TypedArray
import android.os.Bundle
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

class SetKeyPreference : DialogPreference, OnKeyListener {
    private var mValue: Int = 0
    private lateinit var mTextView: TextView

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    internal fun getValue(defaultValue: Int) = getPersistedInt(defaultValue)

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        mValue = if (restorePersistedValue) {
            this.getPersistedInt(DEFAULT_VALUE)
        } else {
            defaultValue as Int
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, DEFAULT_VALUE)
    }

    override fun onCreateDialogView(): View {
        mTextView = TextView(context)
        mTextView.gravity = Gravity.CENTER
        mTextView.textSize = 25F
        return mTextView
    }

    override fun showDialog(state: Bundle?) {
        super.showDialog(state)
        dialog.setOnKeyListener(this)
        dialog.takeKeyEvents(true)
        mValue = this.getPersistedInt(DEFAULT_VALUE)
        mTextView.text = getKeyName(mValue)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        if (positiveResult) {
            if (callChangeListener(mValue)) {
                persistInt(mValue)
            }
        }
    }

    override fun onKey(dialog: DialogInterface, keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER -> return false
            KeyEvent.KEYCODE_HOME -> return true
            else -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    mValue = encodeKey(event)
                    mTextView.text = getKeyName(mValue)
                }
                return true
            }
        }
    }

    companion object {
        private const val SHIFT_PRESSED = 1
        private const val ALT_PRESSED   = 2
        private const val CTRL_PRESSED  = 4
        private const val META_PRESSED  = 8

        private const val DEFAULT_VALUE = KeyEvent.KEYCODE_UNKNOWN shl 4

        internal fun encodeKey(event: KeyEvent): Int {
            val keycode = event.keyCode
            var meta = 0
            if (event.metaState and KeyEvent.META_SHIFT_MASK != 0) {
                meta = meta or SHIFT_PRESSED
            }
            if (event.metaState and KeyEvent.META_ALT_MASK != 0) {
                meta = meta or ALT_PRESSED
            }
            if (event.metaState and KeyEvent.META_CTRL_MASK != 0) {
                meta = meta or CTRL_PRESSED
            }
            if (event.metaState and KeyEvent.META_META_MASK != 0) {
                meta = meta or META_PRESSED
            }

            return keycode shl 4 or meta
        }

        private fun getKeyName(key: Int): String {
            val result = StringBuilder()
            if (key and META_PRESSED != 0)  result.append("META+")
            if (key and CTRL_PRESSED != 0)  result.append("CTRL+")
            if (key and ALT_PRESSED != 0)   result.append("ALT+")
            if (key and SHIFT_PRESSED != 0) result.append("SHIFT+")

            val keyCode = key ushr 4
            when (keyCode) {
                KeyEvent.KEYCODE_A -> result.append("A")
                KeyEvent.KEYCODE_B -> result.append("B")
                KeyEvent.KEYCODE_C -> result.append("C")
                KeyEvent.KEYCODE_D -> result.append("D")
                KeyEvent.KEYCODE_E -> result.append("E")
                KeyEvent.KEYCODE_F -> result.append("F")
                KeyEvent.KEYCODE_G -> result.append("G")
                KeyEvent.KEYCODE_H -> result.append("H")
                KeyEvent.KEYCODE_I -> result.append("I")
                KeyEvent.KEYCODE_J -> result.append("J")
                KeyEvent.KEYCODE_K -> result.append("K")
                KeyEvent.KEYCODE_L -> result.append("L")
                KeyEvent.KEYCODE_M -> result.append("M")
                KeyEvent.KEYCODE_N -> result.append("N")
                KeyEvent.KEYCODE_O -> result.append("O")
                KeyEvent.KEYCODE_P -> result.append("P")
                KeyEvent.KEYCODE_Q -> result.append("Q")
                KeyEvent.KEYCODE_R -> result.append("R")
                KeyEvent.KEYCODE_S -> result.append("S")
                KeyEvent.KEYCODE_T -> result.append("T")
                KeyEvent.KEYCODE_U -> result.append("U")
                KeyEvent.KEYCODE_V -> result.append("V")
                KeyEvent.KEYCODE_W -> result.append("W")
                KeyEvent.KEYCODE_X -> result.append("X")
                KeyEvent.KEYCODE_Y -> result.append("Y")
                KeyEvent.KEYCODE_Z -> result.append("Z")

                KeyEvent.KEYCODE_0 -> result.append("0")
                KeyEvent.KEYCODE_1 -> result.append("1")
                KeyEvent.KEYCODE_2 -> result.append("2")
                KeyEvent.KEYCODE_3 -> result.append("3")
                KeyEvent.KEYCODE_4 -> result.append("4")
                KeyEvent.KEYCODE_5 -> result.append("5")
                KeyEvent.KEYCODE_6 -> result.append("6")
                KeyEvent.KEYCODE_7 -> result.append("7")
                KeyEvent.KEYCODE_8 -> result.append("8")
                KeyEvent.KEYCODE_9 -> result.append("9")

                KeyEvent.KEYCODE_NUM -> result.append("NUM")
                KeyEvent.KEYCODE_SYM -> result.append("SYM")
                KeyEvent.KEYCODE_SPACE -> result.append("SPACE")
                KeyEvent.KEYCODE_DEL -> result.append("DEL")
                KeyEvent.KEYCODE_ENTER -> result.append("ENTER")
                KeyEvent.KEYCODE_TAB -> result.append("TAB")
                KeyEvent.KEYCODE_AT -> result.append("@")
                KeyEvent.KEYCODE_PERIOD -> result.append(".")
                KeyEvent.KEYCODE_COMMA -> result.append(",")
                KeyEvent.KEYCODE_APOSTROPHE -> result.append("'")
                KeyEvent.KEYCODE_EQUALS -> result.append("=")
                KeyEvent.KEYCODE_GRAVE -> result.append("`")
                KeyEvent.KEYCODE_MINUS -> result.append("-")
                KeyEvent.KEYCODE_PLUS -> result.append("+")
                KeyEvent.KEYCODE_SEMICOLON -> result.append(";")
                KeyEvent.KEYCODE_SLASH -> result.append("/")
                KeyEvent.KEYCODE_STAR -> result.append("*")

                KeyEvent.KEYCODE_DPAD_CENTER -> result.append("DPAD CENTER")
                KeyEvent.KEYCODE_DPAD_DOWN -> result.append("DPAD DOWN")
                KeyEvent.KEYCODE_DPAD_LEFT -> result.append("DPAD LEFT")
                KeyEvent.KEYCODE_DPAD_RIGHT -> result.append("DPAD RIGHT")
                KeyEvent.KEYCODE_DPAD_UP -> result.append("DPAD UP")
                KeyEvent.KEYCODE_MENU -> result.append("MENU")
                KeyEvent.KEYCODE_BACK -> result.append("BACK")
                KeyEvent.KEYCODE_CALL -> result.append("CALL")
                KeyEvent.KEYCODE_ENDCALL -> result.append("ENDCALL")
                KeyEvent.KEYCODE_CAMERA -> result.append("CAMERA")
                KeyEvent.KEYCODE_FOCUS -> result.append("FOCUS")
                KeyEvent.KEYCODE_SEARCH -> result.append("SEARCH")
                KeyEvent.KEYCODE_VOLUME_UP -> result.append("Volume UP")
                KeyEvent.KEYCODE_VOLUME_DOWN -> result.append("Volume DOWN")

                else -> result.append("Unknown")
            }

            return result.toString()
        }
    }
}
