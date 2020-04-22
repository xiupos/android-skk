package io.github.ha2zakura.androidskk

import android.content.Context
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.AttributeSet

class QwertyKeyboardView : KeyboardView, KeyboardView.OnKeyboardActionListener {
    private lateinit var mService: SKKService

    private val mGreekKeyboard = SKKKeyboard(context, R.xml.qwerty_el, 4)
    private val mEsperantoKeyboard = SKKKeyboard(context, R.xml.qwerty_eo, 4)
    private val mLatinKeyboard = SKKKeyboard(context, R.xml.qwerty, 4)
    private val mGermanKeyboard = SKKKeyboard(context, R.xml.qwerty_de, 4)
    private val mRussianKeyboard = SKKKeyboard(context, R.xml.qwerty_ru, 4)
    private val mSymbolsKeyboard = SKKKeyboard(context, R.xml.symbols, 4)
    private val mSymbolsShiftedKeyboard = SKKKeyboard(context, R.xml.symbols_shift, 4)

    private var mFlickSensitivitySquared = 100
    private var mFlickStartX = -1f
    private var mFlickStartY = -1f
    private var mFlicked = false
    private var mLastPressedKey = KEYCODE_QWERTY_NONE
    private var mFlickState = FLICK_STATE_NONE

    private var mKeyboardCount = 2

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    init {
        setKeyboard()
        onKeyboardActionListener = this
    }

    private fun setKeyboard() {
        mSymbolsShiftedKeyboard.isShifted = false
        when (mKeyboardCount) {
            0 -> keyboard = mGreekKeyboard
            1 -> keyboard = mEsperantoKeyboard
            2 -> keyboard = mLatinKeyboard
            3 -> keyboard = mGermanKeyboard
            4 -> keyboard = mRussianKeyboard
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isShifted = false
    }

    fun setService(listener: SKKService) {
        mService = listener
    }

    fun changeKeyHeight(px: Int) {
        mGreekKeyboard.changeKeyHeight(px)
        mEsperantoKeyboard.changeKeyHeight(px)
        mLatinKeyboard.changeKeyHeight(px)
        mGermanKeyboard.changeKeyHeight(px)
        mRussianKeyboard.changeKeyHeight(px)
        mSymbolsKeyboard.changeKeyHeight(px)
        mSymbolsShiftedKeyboard.changeKeyHeight(px)
    }

    fun setFlickSensitivity(sensitivity: Int) {
        mFlickSensitivitySquared = sensitivity * sensitivity
    }

    override fun onLongPress(key: Keyboard.Key): Boolean {
        if (key.codes[0] == KEYCODE_QWERTY_ENTER) {
            mService.keyDownUp(KeyEvent.KEYCODE_SEARCH)
            return true
        }

        return super.onLongPress(key)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mFlickStartX = event.rawX
                mFlickStartY = event.rawY
                mFlicked = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - mFlickStartX
                val dy = event.rawY - mFlickStartY
                if (dx * dx + dy * dy < mFlickSensitivitySquared) { return true }

                processFirstFlick(dx, dy)

                return true
            }
            MotionEvent.ACTION_UP -> {
                mFlicked = mFlickState == FLICK_STATE_UP
                release()
            }

        }

        return super.onTouchEvent(event)
    }

    private fun release() {
        when (mLastPressedKey) {
            KEYCODE_QWERTY_LEFT -> when (mFlickState) {
                FLICK_STATE_RIGHT -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
                FLICK_STATE_UP    -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_UP)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_UP)
                }
                FLICK_STATE_DOWN  -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_DOWN)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN)
                }
                else              -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_LEFT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT)
                }
            }
            KEYCODE_QWERTY_RIGHT -> when (mFlickState) {
                FLICK_STATE_LEFT -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_LEFT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT)
                }
                FLICK_STATE_UP   -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_UP)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_UP)
                }
                FLICK_STATE_DOWN -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_DOWN)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN)
                }
                else             -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
            }
            KEYCODE_QWERTY_SPACE -> when (mFlickState) {
                FLICK_STATE_RIGHT -> {
                    when (mKeyboardCount) {
//                        0 -> mKeyboardCount = 4
                        1,2,3,4 -> mKeyboardCount--
                    }
                    setKeyboard()
                }
                FLICK_STATE_LEFT -> {
                    when (mKeyboardCount) {
                        0,1,2,3 -> mKeyboardCount++
//                        4 -> mKeyboardCount = 0
                    }
                    setKeyboard()
                }
                else              -> {
                    mService.commitTextSKK(" ", 1)
                }
            }
        }
        mLastPressedKey = KEYCODE_QWERTY_NONE
        mFlickState = FLICK_STATE_NONE
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (!mService.handleBackspace()) mService.keyDownUp(KeyEvent.KEYCODE_DEL)
            }
            Keyboard.KEYCODE_SHIFT -> {
                isShifted = !isShifted
                if (keyboard === mSymbolsKeyboard) {
                    mSymbolsKeyboard.isShifted = true
                    keyboard = mSymbolsShiftedKeyboard
                    mSymbolsShiftedKeyboard.isShifted = true
                } else if (keyboard === mSymbolsShiftedKeyboard) {
                    mSymbolsShiftedKeyboard.isShifted = false
                    keyboard = mSymbolsKeyboard
                    mSymbolsKeyboard.isShifted = false
                }
            }
            KEYCODE_QWERTY_ENTER -> if (!mService.handleEnter()) mService.pressEnter()
            KEYCODE_QWERTY_TOJP    -> mService.handleKanaKey()
            KEYCODE_QWERTY_TOSYM   -> {
                mSymbolsShiftedKeyboard.isShifted = false
                keyboard = mSymbolsKeyboard
            }
            KEYCODE_QWERTY_TOLATIN -> setKeyboard()
            KEYCODE_QWERTY_LEFT, KEYCODE_QWERTY_RIGHT -> return
            KEYCODE_QWERTY_SPACE -> return
            else -> {
                val code = if ((keyboard === mGreekKeyboard ||
                            keyboard === mEsperantoKeyboard ||
                            keyboard === mLatinKeyboard ||
                            keyboard === mGermanKeyboard ||
                            keyboard === mRussianKeyboard) &&
                            (isShifted xor mFlicked)) {
                    Character.toUpperCase(primaryCode)
                } else {
                    primaryCode
                }
                mService.commitTextSKK(code.toChar().toString(), 1)
            }
        }
    }

    private fun processFirstFlick(dx: Float, dy: Float) {
        val dAngle = diamondAngle(dx, dy)

        mFlickState = when (dAngle) {
            in 0.5f..1.5f   -> FLICK_STATE_DOWN
            in 1.5f..2.29f  -> FLICK_STATE_LEFT
            in 2.29f..2.71f -> when {
                (dAngle < 2.5f) -> FLICK_STATE_LEFT
                else -> FLICK_STATE_UP
            }
            in 2.71f..3.29f -> FLICK_STATE_UP
            in 3.29f..3.71f -> when {
                (dAngle < 3.5f) -> FLICK_STATE_UP
                else -> FLICK_STATE_RIGHT
            }
            else -> FLICK_STATE_RIGHT
        }
    }
    private fun diamondAngle(x: Float, y: Float): Float {
        return if (y >= 0) {
            if (x >= 0) y / (x + y) else 1 - x / (-x + y)
        } else {
            if (x < 0) 2 - y / (-x - y) else 3 + x / (x - y)
        }
    }

    override fun onPress(primaryCode: Int) {
        if (mFlickState == FLICK_STATE_NONE) {
            mLastPressedKey = primaryCode
        }
    }

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence) {}

    override fun swipeRight() {}

    override fun swipeLeft() {}

    override fun swipeDown() {}

    override fun swipeUp() {}

    companion object {
        private const val KEYCODE_QWERTY_NONE    = -1000
        private const val KEYCODE_QWERTY_TOJP    = -1008
        private const val KEYCODE_QWERTY_TOSYM   = -1009
        private const val KEYCODE_QWERTY_TOLATIN = -1010
        private const val KEYCODE_QWERTY_ENTER   = -1011
        private const val KEYCODE_QWERTY_LEFT    = -1001
        private const val KEYCODE_QWERTY_RIGHT   = -1002
        private const val KEYCODE_QWERTY_SPACE   = 32
        private const val FLICK_STATE_NONE = 0
        private const val FLICK_STATE_LEFT = 1
        private const val FLICK_STATE_UP = 2
        private const val FLICK_STATE_RIGHT = 3
        private const val FLICK_STATE_DOWN = 4
    }

}
