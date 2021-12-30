package link.nombi.androidskklang

import android.content.Context
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.ClipboardManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.SparseArray
import android.view.LayoutInflater
import android.widget.PopupWindow
import android.content.Context.CLIPBOARD_SERVICE
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.widget.TextView
import link.nombi.androidskklang.engine.SKKEngine
import kotlinx.android.synthetic.main.popup_flickguide.view.*

class FlickJPKeyboardView : KeyboardView, KeyboardView.OnKeyboardActionListener {
    private lateinit var mService: SKKService

    private var isHiragana = true

    private var mFlickSensitivitySquared = 100
    private var mCurveSensitivityMultiplier = 2.0f
    private var mLastPressedKey = KEYCODE_FLICK_JP_NONE
    private var mFlickState = FLICK_STATE_NONE
    private var mFlickStartX = -1f
    private var mFlickStartY = -1f
    private var mCurrentPopupLabels = POPUP_LABELS_NULL

    private var mUsePopup = true
    private var mFixedPopup = false
    private var mPopup: PopupWindow? = null
    private var mPopupTextView: Array<TextView>? = null
    private val mPopupSize = 100
    private val mPopupOffset = intArrayOf(0, 0)
    private val mFixedPopupPos = intArrayOf(0, 0)

    private val mJPKeyboard: SKKKeyboard
    private val mNumKeyboard: SKKKeyboard
    private val mVoiceKeyboard: SKKKeyboard

    private var mKutoutenLabel = "，．？！"
    private val mKutoutenKey: Keyboard.Key
    private val mQwertyKey: Keyboard.Key

    private var mHighlightedKey: Keyboard.Key? = null
    private val mHighlightedDrawable =
            ShapeDrawable().apply { paint.color = Color.parseColor("#66FF0000") }

    //フリックガイドTextView用
    private val mFlickGuideLabelList = SparseArray<Array<String>>()

    init {
        val a = mFlickGuideLabelList
        a.append(KEYCODE_FLICK_JP_CHAR_A, arrayOf("あ", "い", "う", "え", "お", "小", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_KA, arrayOf("か", "き", "く", "け", "こ", "", "゛"))
        a.append(KEYCODE_FLICK_JP_CHAR_SA, arrayOf("さ", "し", "す", "せ", "そ", "", "゛"))
        a.append(KEYCODE_FLICK_JP_CHAR_TA, arrayOf("た", "ち", "つ", "て", "と", "", "゛"))
        a.append(KEYCODE_FLICK_JP_CHAR_NA, arrayOf("な", "に", "ぬ", "ね", "の", "", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_HA, arrayOf("は", "ひ", "ふ", "へ", "ほ", "゜", "゛"))
        a.append(KEYCODE_FLICK_JP_CHAR_MA, arrayOf("ま", "み", "む", "め", "も", "", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_YA, arrayOf("や", "（", "ゆ", "）", "よ", "小", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_RA, arrayOf("ら", "り", "る", "れ", "ろ", "", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_WA, arrayOf("わ", "を", "ん", "ー", "〜", "", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_TEN, arrayOf("、", "。", "？", "！", "...", "", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_TEN_SHIFTED, arrayOf("（", "「", "」", "）", "", "", ""))
        a.append(KEYCODE_FLICK_JP_CHAR_TEN_NUM, arrayOf("，", "．", "−", "：", "", "", ""))
        a.append(KEYCODE_FLICK_JP_KOMOJI, arrayOf("小", "゛", "w", "゜", "", "", ""))
        a.append(KEYCODE_FLICK_JP_MOJI, arrayOf("あ", "：", "数", "＞", "号", "", ""))
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        onKeyboardActionListener = this
        isPreviewEnabled = false
        setBackgroundColor(0x00000000)

        mJPKeyboard = SKKKeyboard(context, R.xml.keys_flick_jp, 4)
        mKutoutenKey = checkNotNull(findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_CHAR_TEN)) { "BUG: no kutoten key" }
        mQwertyKey = checkNotNull(findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_TOQWERTY)) { "BUG: no qwerty key" }
        mNumKeyboard = SKKKeyboard(context, R.xml.keys_flick_number, 4)
        mVoiceKeyboard = SKKKeyboard(context, R.xml.keys_flick_voice, 4)
        keyboard = mJPKeyboard
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        onKeyboardActionListener = this
        isPreviewEnabled = false
        setBackgroundColor(0x00000000)

        mJPKeyboard = SKKKeyboard(context, R.xml.keys_flick_jp, 4)
        mKutoutenKey = checkNotNull(findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_CHAR_TEN)) { "BUG: no kutoten key" }
        mQwertyKey = checkNotNull(findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_TOQWERTY)) { "BUG: no qwerty key" }
        mNumKeyboard = SKKKeyboard(context, R.xml.keys_flick_number, 4)
        mVoiceKeyboard = SKKKeyboard(context, R.xml.keys_flick_voice, 4)
        keyboard = mJPKeyboard
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val key = mHighlightedKey
        if (key != null && canvas != null) {
            mHighlightedDrawable.apply {
                setBounds(key.x, key.y, key.x + key.width, key.y + key.height)
                draw(canvas)
            }
        }
    }

    fun setHighlightedKey(index: Int) {
        val keys = keyboard.keys
        mHighlightedKey = if (index in 0 until keys.size) keys[index] else null
        invalidateAllKeys()
    }

    fun setService(listener: SKKService) {
        mService = listener
    }

    internal fun setHiraganaMode() {
        isHiragana = true
        for (key in keyboard.keys) {
            when (key.codes[0]) {
                KEYCODE_FLICK_JP_CHAR_A  -> key.label = "あ"
                KEYCODE_FLICK_JP_CHAR_KA -> key.label = "か"
                KEYCODE_FLICK_JP_CHAR_SA -> key.label = "さ"
                KEYCODE_FLICK_JP_CHAR_TA -> key.label = "た"
                KEYCODE_FLICK_JP_CHAR_NA -> key.label = "な"
                KEYCODE_FLICK_JP_CHAR_HA -> key.label = "は"
                KEYCODE_FLICK_JP_CHAR_MA -> key.label = "ま"
                KEYCODE_FLICK_JP_CHAR_YA -> key.label = "や"
                KEYCODE_FLICK_JP_CHAR_RA -> key.label = "ら"
                KEYCODE_FLICK_JP_CHAR_WA -> key.label = "わ"
                KEYCODE_FLICK_JP_MOJI    -> key.label = "カナ"
            }
        }
        invalidateAllKeys()
    }

    internal fun setKatakanaMode() {
        isHiragana = false
        for (key in keyboard.keys) {
            when (key.codes[0]) {
                KEYCODE_FLICK_JP_CHAR_A  -> key.label = "ア"
                KEYCODE_FLICK_JP_CHAR_KA -> key.label = "カ"
                KEYCODE_FLICK_JP_CHAR_SA -> key.label = "サ"
                KEYCODE_FLICK_JP_CHAR_TA -> key.label = "タ"
                KEYCODE_FLICK_JP_CHAR_NA -> key.label = "ナ"
                KEYCODE_FLICK_JP_CHAR_HA -> key.label = "ハ"
                KEYCODE_FLICK_JP_CHAR_MA -> key.label = "マ"
                KEYCODE_FLICK_JP_CHAR_YA -> key.label = "ヤ"
                KEYCODE_FLICK_JP_CHAR_RA -> key.label = "ラ"
                KEYCODE_FLICK_JP_CHAR_WA -> key.label = "ワ"
                KEYCODE_FLICK_JP_MOJI    -> key.label = "かな"
            }
        }
        invalidateAllKeys()
    }

    private fun findKeyByCode(keyboard: Keyboard, code: Int) =
            keyboard.keys.find { it.codes[0] == code }

    private fun onSetShifted(isShifted: Boolean) {
        if (isShifted) {
            mKutoutenKey.codes[0] = KEYCODE_FLICK_JP_CHAR_TEN_SHIFTED
            mKutoutenKey.label = "「」（）"
            mQwertyKey.label = "abbr"
        } else {
            mKutoutenKey.codes[0] = KEYCODE_FLICK_JP_CHAR_TEN
            mKutoutenKey.label = mKutoutenLabel
            mQwertyKey.label = "ABC"
        }
    }

    internal fun setRegisterMode(isRegistering: Boolean) {
        if (isRegistering) {
            var key = findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_LEFT)
            if (key != null) {
                key.codes[0] = KEYCODE_FLICK_JP_PASTE
                key.label = "貼り付け"
            }
            key = findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_RIGHT)
            if (key != null) {
                key.label = " "
            }
        } else {
            var key = findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_PASTE)
            if (key != null) {
                key.codes[0] = KEYCODE_FLICK_JP_LEFT
                key.label = "◀"
            }
            key = findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_RIGHT)
            if (key != null) {
                key.label = "▶"
            }
        }
        invalidateAllKeys()
    }

    private fun adjustKeysHorizontally(
            keyboard: SKKKeyboard,
            maxKeyWidth: Int,
            ratio: Double,
            position: String
    ) {
        var y = 0
        var colNo = 0
        val newWidth = (maxKeyWidth * ratio).toInt()
        val gap = when (position) {
            "right" -> (maxKeyWidth - newWidth) * 5
            "center" -> ((maxKeyWidth - newWidth) * 2.5).toInt()
            else -> 0
        }
        for (key in keyboard.keys) {
            key.width = newWidth
            if (key.y != y) {
                y = key.y
                colNo = 0
            }
            key.x = gap + key.width * colNo
            colNo++
        }
    }

    // widthはパーセントでheightはpxなので注意
    internal fun prepareNewKeyboard(context: Context, width: Int, height: Int, position: String) {
        val keyWidth = mJPKeyboard.keys[0].width
        adjustKeysHorizontally(mJPKeyboard, keyWidth, width.toDouble() / 100, position)
        mJPKeyboard.changeKeyHeight(height)
        keyboard = mJPKeyboard

        adjustKeysHorizontally(mNumKeyboard, keyWidth, width.toDouble() / 100, position)
        mNumKeyboard.changeKeyHeight(height)

        adjustKeysHorizontally(mVoiceKeyboard, keyWidth, width.toDouble() / 100, position)
        mVoiceKeyboard.changeKeyHeight(height)

        readPrefs(context)
    }

    private fun readPrefs(context: Context) {
        // フリック感度
        val density = context.resources.displayMetrics.density
        val sensitivity = when (SKKPrefs.getFlickSensitivity(context)) {
            "low"  -> (36 * density + 0.5f).toInt()
            "high" -> (12 * density + 0.5f).toInt()
            else   -> (24 * density + 0.5f).toInt()
        }
        mFlickSensitivitySquared = sensitivity * sensitivity
        // カーブフリック感度
        mCurveSensitivityMultiplier = when (SKKPrefs.getCurveSensitivity(context)) {
            "low" -> 0.5f
            "mid" -> 1.0f
            else -> 2.0f
        }
        // 句読点
        when (SKKPrefs.getKutoutenType(context)) {
            "en" -> {
                mKutoutenLabel = "，．？！"
                mFlickGuideLabelList.put(
                        KEYCODE_FLICK_JP_CHAR_TEN, arrayOf("，", "．", "？", "！", "", "", "")
                )
            }
            "jp_en" -> {
                mKutoutenLabel = "，。？！"
                mFlickGuideLabelList.put(
                        KEYCODE_FLICK_JP_CHAR_TEN, arrayOf("，", "。", "？", "！", "", "", "")
                )
            }
            else -> {
                mKutoutenLabel = "、。？！"
                mFlickGuideLabelList.put(
                        KEYCODE_FLICK_JP_CHAR_TEN, arrayOf("、", "。", "？", "！", "", "", "")
                )
            }
        }
        mKutoutenKey.label = mKutoutenLabel
        // キャンセルキー
        if (SKKPrefs.getUseSoftCancelKey(context)) {
            val key = findKeyByCode(mJPKeyboard, KEYCODE_FLICK_JP_KOMOJI)
            if (key != null) {
                key.label = "CXL"
                key.codes[0] = KEYCODE_FLICK_JP_CANCEL
            }
        }
        // ポップアップ
        mUsePopup = SKKPrefs.getUsePopup(context)
        if (mUsePopup) {
            mFixedPopup = SKKPrefs.getFixedPopup(context)
            if (mPopup == null) {
                val popup = createPopupGuide(context)
                mPopup = popup
                mPopupTextView = arrayOf(
                        popup.contentView.labelA,
                        popup.contentView.labelI,
                        popup.contentView.labelU,
                        popup.contentView.labelE,
                        popup.contentView.labelO,
                        popup.contentView.labelLeftA,
                        popup.contentView.labelRightA,
                        popup.contentView.labelLeftI,
                        popup.contentView.labelRightI,
                        popup.contentView.labelLeftU,
                        popup.contentView.labelRightU,
                        popup.contentView.labelLeftE,
                        popup.contentView.labelRightE,
                        popup.contentView.labelLeftO,
                        popup.contentView.labelRightO
                )
            }
        }
    }

    private fun createPopupGuide(context: Context): PopupWindow {
        val view = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                .inflate(R.layout.popup_flickguide, null)

        val scale = getContext().resources.displayMetrics.density
        val size = (mPopupSize * scale + 0.5f).toInt()

        val popup = PopupWindow(view, size, size)
        //~ popup.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        popup.animationStyle = 0

        return popup
    }

    private fun setupPopupTextView() {
        if (!mUsePopup) return

        val labels = checkNotNull(mPopupTextView) { "BUG: popup labels are null!!" }
        labels.forEach {
            it.text = ""
            it.setBackgroundResource(R.drawable.popup_label)
        }
        when (mFlickState) {
            FLICK_STATE_NONE -> {
                labels[0].text = mCurrentPopupLabels[0]
                labels[1].text = mCurrentPopupLabels[1]
                labels[2].text = mCurrentPopupLabels[2]
                labels[3].text = mCurrentPopupLabels[3]
                labels[4].text = mCurrentPopupLabels[4]
                labels[5].text = mCurrentPopupLabels[5]
                labels[6].text = mCurrentPopupLabels[6]
                labels[0].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_NONE_LEFT -> {
                labels[0].text = mCurrentPopupLabels[0]
                labels[5].text = mCurrentPopupLabels[5]
                labels[5].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_NONE_RIGHT -> {
                labels[0].text = mCurrentPopupLabels[0]
                labels[6].text = mCurrentPopupLabels[6]
                labels[6].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_LEFT -> {
                labels[1].text = mCurrentPopupLabels[1]
                labels[7].text = mCurrentPopupLabels[5]
                labels[8].text = mCurrentPopupLabels[6]
                labels[1].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_LEFT_LEFT -> {
                labels[1].text = mCurrentPopupLabels[1]
                labels[7].text = mCurrentPopupLabels[5]
                labels[7].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_LEFT_RIGHT -> {
                labels[1].text = mCurrentPopupLabels[1]
                labels[8].text = mCurrentPopupLabels[6]
                labels[8].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_UP -> {
                labels[2].text  = mCurrentPopupLabels[2]
                labels[9].text  = mCurrentPopupLabels[5]
                labels[10].text = mCurrentPopupLabels[6]
                if (mLastPressedKey == KEYCODE_FLICK_JP_CHAR_TA) {
                    // 例外：小さい「っ」
                    labels[9].text = "小"
                }
                if (mLastPressedKey == KEYCODE_FLICK_JP_CHAR_A && !isHiragana) {
                    // 例外：「ヴ」
                    labels[10].text = "゛"
                }
                labels[2].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_UP_LEFT -> {
                labels[2].text = mCurrentPopupLabels[2]
                labels[9].text = mCurrentPopupLabels[5]
                if (mLastPressedKey == KEYCODE_FLICK_JP_CHAR_TA) {
                    // 例外：小さい「っ」
                    labels[9].text = "小"
                }
                labels[9].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_UP_RIGHT -> {
                labels[2].text  = mCurrentPopupLabels[2]
                labels[10].text = mCurrentPopupLabels[6]
                if (mLastPressedKey == KEYCODE_FLICK_JP_CHAR_A && !isHiragana) {
                    // 例外：「ヴ」
                    labels[10].text = "゛"
                }
                labels[10].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_RIGHT -> {
                labels[3].text  = mCurrentPopupLabels[3]
                labels[11].text = mCurrentPopupLabels[5]
                labels[12].text = mCurrentPopupLabels[6]
                labels[3].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_RIGHT_LEFT -> {
                labels[3].text  = mCurrentPopupLabels[3]
                labels[11].text = mCurrentPopupLabels[5]
                labels[11].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_RIGHT_RIGHT -> {
                labels[3].text  = mCurrentPopupLabels[3]
                labels[12].text = mCurrentPopupLabels[6]
                labels[12].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_DOWN -> {
                labels[4].text  = mCurrentPopupLabels[4]
                labels[13].text = mCurrentPopupLabels[5]
                labels[14].text = mCurrentPopupLabels[6]
                labels[4].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_DOWN_LEFT -> {
                labels[4].text  = mCurrentPopupLabels[4]
                labels[13].text = mCurrentPopupLabels[5]
                labels[13].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
            FLICK_STATE_DOWN_RIGHT -> {
                labels[4].text  = mCurrentPopupLabels[4]
                labels[14].text = mCurrentPopupLabels[6]
                labels[14].setBackgroundResource(R.drawable.popup_label_highlighted)
            }
        }
        for (i in 5..14) {
            if (labels[i].text == "小") {
                labels[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f)
            } else {
                labels[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18f)
            }
        }
    }

    private fun isLeftCurve(flick: Int): Boolean {
        return flick == FLICK_STATE_NONE_LEFT
                || flick == FLICK_STATE_LEFT_LEFT
                || flick == FLICK_STATE_UP_LEFT
                || flick == FLICK_STATE_RIGHT_LEFT
                || flick == FLICK_STATE_DOWN_LEFT
    }

    private fun isRightCurve(flick: Int): Boolean {
        return flick == FLICK_STATE_NONE_RIGHT
                || flick == FLICK_STATE_LEFT_RIGHT
                || flick == FLICK_STATE_UP_RIGHT
                || flick == FLICK_STATE_RIGHT_RIGHT
                || flick == FLICK_STATE_DOWN_RIGHT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mFlickStartX = event.rawX
                mFlickStartY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLeftCurve(mFlickState) || isRightCurve(mFlickState)) { return true }

                val dx = event.rawX - mFlickStartX
                val dy = event.rawY - mFlickStartY
                if (dx * dx + dy * dy < mFlickSensitivitySquared) { return true }

                if (mFlickState == FLICK_STATE_NONE) {
                    // 一回目の終了座標を記憶
                    mFlickStartX = event.rawX
                    mFlickStartY = event.rawY

                    processFirstFlick(dx, dy)
                } else {
                    processCurveFlick(dx, dy)
                }

                if (mUsePopup) setupPopupTextView()
                return true
            }
            MotionEvent.ACTION_UP -> release()
        }

        return super.onTouchEvent(event)
    }

    private fun diamondAngle(x: Float, y: Float): Float {
        return if (y >= 0) {
            if (x >= 0) y / (x + y) else 1 - x / (-x + y)
        } else {
            if (x < 0) 2 - y / (-x - y) else 3 + x / (x - y)
        }
    }

    private fun processFirstFlick(dx: Float, dy: Float) {
        val dAngle = diamondAngle(dx, dy)
        val hasLeftCurve = mCurrentPopupLabels[5].isNotEmpty()
        val hasRightCurve = mCurrentPopupLabels[6].isNotEmpty()

        mFlickState = when (dAngle) {
            in 0.5f..1.5f   -> FLICK_STATE_DOWN
            in 1.5f..2.29f  -> FLICK_STATE_LEFT
            in 2.29f..2.71f -> when {
                    (hasLeftCurve)  -> FLICK_STATE_NONE_LEFT
                    (dAngle < 2.5f) -> FLICK_STATE_LEFT
                    else -> FLICK_STATE_UP
                }
            in 2.71f..3.29f -> FLICK_STATE_UP
            in 3.29f..3.71f -> when {
                    (hasRightCurve) -> FLICK_STATE_NONE_RIGHT
                    (dAngle < 3.5f) -> FLICK_STATE_UP
                    else -> FLICK_STATE_RIGHT
                }
            else -> FLICK_STATE_RIGHT
        }
    }

    private fun processCurveFlick(dx: Float, dy: Float) {
        var hasLeftCurve = mCurrentPopupLabels[5].isNotEmpty()
        var hasRightCurve = mCurrentPopupLabels[6].isNotEmpty()
        //小さい「っ」は特別処理
        if (mLastPressedKey == KEYCODE_FLICK_JP_CHAR_TA && mFlickState == FLICK_STATE_UP) {
            hasLeftCurve = true
        }
        //「ヴ」は特別処理
        if (!isHiragana
                && mLastPressedKey == KEYCODE_FLICK_JP_CHAR_A
                && mFlickState == FLICK_STATE_UP
        ) {
            hasRightCurve = true
        }
        if (!hasLeftCurve && !hasRightCurve) return

        val newstate = when (mFlickState) {
            FLICK_STATE_LEFT -> when (diamondAngle(-dx, -dy)) {
                in 0.45f..2f -> FLICK_STATE_LEFT_RIGHT
                in 2f..3.55f -> FLICK_STATE_LEFT_LEFT
                else -> -1
            }
            FLICK_STATE_UP -> when (diamondAngle(-dy, dx)) {
                in 0.45f..2f -> FLICK_STATE_UP_RIGHT
                in 2f..3.55f -> FLICK_STATE_UP_LEFT
                else -> -1
            }
            FLICK_STATE_RIGHT -> when (diamondAngle(dx, dy)) {
                in 0.45f..2f -> FLICK_STATE_RIGHT_RIGHT
                in 2f..3.55f -> FLICK_STATE_RIGHT_LEFT
                else -> -1
            }
            FLICK_STATE_DOWN -> when (diamondAngle(dy, -dx)) {
                in 0.45f..2f -> FLICK_STATE_DOWN_RIGHT
                in 2f..3.55f -> FLICK_STATE_DOWN_LEFT
                else -> -1
            }
            else -> -1
        }
        if (newstate == -1) {
            //曲がらずにそのままフリックしてるらしい場合
            mFlickStartX += dx
            mFlickStartY += dy
            return
        }

        if (hasLeftCurve && isLeftCurve(newstate) || hasRightCurve && isRightCurve(newstate)) {
            mFlickState = newstate
        }
    }

    private fun processFlickForLetter(keyCode: Int, flick: Int, isShifted: Boolean) {
        var vowel: Int = 'a'.toInt()
        when (flick) {
            FLICK_STATE_LEFT, FLICK_STATE_LEFT_LEFT, FLICK_STATE_LEFT_RIGHT -> vowel = 'i'.toInt()
            FLICK_STATE_UP, FLICK_STATE_UP_LEFT, FLICK_STATE_UP_RIGHT -> vowel = 'u'.toInt()
            FLICK_STATE_RIGHT, FLICK_STATE_RIGHT_LEFT, FLICK_STATE_RIGHT_RIGHT -> vowel = 'e'.toInt()
            FLICK_STATE_DOWN, FLICK_STATE_DOWN_LEFT, FLICK_STATE_DOWN_RIGHT -> vowel = 'o'.toInt()
        }

        val consonant: Int
        when (keyCode) {
            KEYCODE_FLICK_JP_CHAR_A -> {
                if (isLeftCurve(flick)) {
                    mService.processKey('x'.toInt())
                    mService.processKey(vowel)
                } else if (!isHiragana && flick == FLICK_STATE_UP_RIGHT) {
                    mService.processKey('v'.toInt())
                    mService.processKey('u'.toInt())
                } else if (isShifted) {
                    mService.processKey(Character.toUpperCase(vowel))
                } else {
                    mService.processKey(vowel)
                }
                return
            }
            KEYCODE_FLICK_JP_CHAR_KA -> consonant = (if (isRightCurve(flick)) 'g' else 'k').toInt()
            KEYCODE_FLICK_JP_CHAR_SA -> consonant = (if (isRightCurve(flick)) 'z' else 's').toInt()
            KEYCODE_FLICK_JP_CHAR_TA -> consonant = (if (isRightCurve(flick)) 'd' else 't').toInt()
            KEYCODE_FLICK_JP_CHAR_NA -> consonant = 'n'.toInt()
            KEYCODE_FLICK_JP_CHAR_HA -> consonant = when {
                isRightCurve(flick) -> 'b'.toInt()
                isLeftCurve(flick)  -> 'p'.toInt()
                else -> 'h'.toInt()
            }
            KEYCODE_FLICK_JP_CHAR_MA -> consonant = 'm'.toInt()
            KEYCODE_FLICK_JP_CHAR_YA -> {
                when (flick) {
                    FLICK_STATE_LEFT  -> {
                        mService.processKey('('.toInt())
                        return
                    }
                    FLICK_STATE_RIGHT -> {
                        mService.processKey(')'.toInt())
                        return
                    }
                    else -> consonant = 'y'.toInt()
                }
            }
            KEYCODE_FLICK_JP_CHAR_RA -> consonant = 'r'.toInt()
            KEYCODE_FLICK_JP_CHAR_WA -> {
                when (flick) {
                    FLICK_STATE_NONE -> {
                        if (isShifted) {
                            mService.processKey('W'.toInt())
                        } else {
                            mService.processKey('w'.toInt())
                        }
                        mService.processKey('a'.toInt())
                    }
                    FLICK_STATE_LEFT -> {
                        mService.processKey('w'.toInt())
                        mService.processKey('o'.toInt())
                    }
                    FLICK_STATE_UP -> {
                        if (isShifted) {
                            mService.processKey('N'.toInt())
                        } else {
                            mService.processKey('n'.toInt())
                        }
                        mService.processKey('n'.toInt())
                    }
                    FLICK_STATE_RIGHT -> mService.processKey('-'.toInt())
                    FLICK_STATE_DOWN  -> mService.processKey('~'.toInt())
                }
                return
            }
            KEYCODE_FLICK_JP_CHAR_TEN -> {
                when (flick) {
                    FLICK_STATE_NONE  -> mService.processKey(','.toInt())
                    FLICK_STATE_LEFT  -> mService.processKey('.'.toInt())
                    FLICK_STATE_UP    -> mService.processKey('?'.toInt())
                    FLICK_STATE_RIGHT -> mService.processKey('!'.toInt())
                    FLICK_STATE_DOWN  -> mService.commitTextSKK("...", 0)
                }
                return
            }
            KEYCODE_FLICK_JP_CHAR_TEN_SHIFTED -> {
                when (flick) {
                    FLICK_STATE_NONE  -> mService.processKey('('.toInt())
                    FLICK_STATE_LEFT  -> mService.processKey('['.toInt())
                    FLICK_STATE_UP    -> mService.processKey(']'.toInt())
                    FLICK_STATE_RIGHT -> mService.processKey(')'.toInt())
                }
                return
            }
            KEYCODE_FLICK_JP_CHAR_TEN_NUM -> {
                when (flick) {
                    FLICK_STATE_NONE  -> mService.commitTextSKK(",", 0)
                    FLICK_STATE_LEFT  -> mService.commitTextSKK(".", 0)
                    FLICK_STATE_UP    -> mService.commitTextSKK("-", 0)
                    FLICK_STATE_RIGHT -> mService.commitTextSKK(":", 0)
                }
                return
            }
            else -> return
        }

        if (isShifted) {
            mService.processKey(Character.toUpperCase(consonant))
        } else {
            mService.processKey(consonant)
        }
        mService.processKey(vowel)

        if (isLeftCurve(flick)) {
            if (consonant == 't'.toInt() && vowel == 'u'.toInt()
                    || consonant == 'y'.toInt() && (vowel == 'a'.toInt() || vowel == 'u'.toInt() || vowel == 'o'.toInt())) {
                mService.changeLastChar(SKKEngine.LAST_CONVERTION_SMALL)
            }
        }
    }

    override fun onLongPress(key: Keyboard.Key): Boolean {
        if (key.codes[0] == KEYCODE_FLICK_JP_ENTER) {
            mService.keyDownUp(KeyEvent.KEYCODE_SEARCH)
            return true
        }

        return super.onLongPress(key)
    }

    override fun onPress(primaryCode: Int) {
        if (mFlickState == FLICK_STATE_NONE) {
            mLastPressedKey = primaryCode
        }

        if (mUsePopup) {
            val labels = mFlickGuideLabelList.get(primaryCode)
            if (labels == null) {
                mCurrentPopupLabels = POPUP_LABELS_NULL
                return
            }

            for (i in 0..6) {
                if (isHiragana) {
                    mCurrentPopupLabels[i] = labels[i]
                } else {
                    mCurrentPopupLabels[i] = checkNotNull(hirakana2katakana(labels[i])) { "BUG: invalid popup label!!"}
                }
            }
            setupPopupTextView()

            if (mFixedPopupPos[0] == 0) calculatePopupPos()

            val popup = checkNotNull(mPopup) { "BUG: popup is null!!" }
            if (mFixedPopup) {
                popup.showAtLocation(
                        this, android.view.Gravity.NO_GRAVITY,
                        mFixedPopupPos[0], mFixedPopupPos[1]
                )
            } else {
                popup.showAtLocation(
                        this, android.view.Gravity.NO_GRAVITY,
                        mFlickStartX.toInt() + mPopupOffset[0],
                        mFlickStartY.toInt() + mPopupOffset[1]
                )
            }
        }
    }

    private fun calculatePopupPos() {
        val scale = context.resources.displayMetrics.density
        val size = (mPopupSize * scale + 0.5f).toInt()

        val offsetInWindow = IntArray(2)
        getLocationInWindow(offsetInWindow)
        val windowLocation = IntArray(2)
        getLocationOnScreen(windowLocation)
        mPopupOffset[0] = -size / 2 - 200
        mPopupOffset[1] = -windowLocation[1] + offsetInWindow[1] - size / 2 - 200
        mFixedPopupPos[0] = windowLocation[0] + this.width / 2 + mPopupOffset[0]
        mFixedPopupPos[1] = windowLocation[1] - size / 2 + mPopupOffset[1]
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray) {
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                isShifted = !isShifted
                onSetShifted(isShifted)
            }
            Keyboard.KEYCODE_DELETE -> if (!mService.handleBackspace()) {
                mService.keyDownUp(KeyEvent.KEYCODE_DEL)
            }
            33, 40, 41, 44, 46, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 63, 91, 93 ->
                // ! ( ) , . 0〜9 ? [ ]
                mService.processKey(primaryCode)
            KEYCODE_FLICK_JP_PASTE -> {
                val cm = mService.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val cs = cm.text
                val clip = cs?.toString() ?: ""
                mService.commitTextSKK(clip, 1)
            }
        }
    }

    private fun release() {
        when (mLastPressedKey) {
            KEYCODE_FLICK_JP_LEFT  -> when (mFlickState) {
                FLICK_STATE_RIGHT -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
                FLICK_STATE_UP     -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_UP)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_UP)
                }
                FLICK_STATE_DOWN   -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_DOWN)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN)
                }
                else               -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_LEFT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT)
                }
            }
            KEYCODE_FLICK_JP_RIGHT -> when (mFlickState) {
                FLICK_STATE_LEFT -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_LEFT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT)
                }
                FLICK_STATE_UP     -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_UP)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_UP)
                }
                FLICK_STATE_DOWN   -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_DOWN)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN)
                }
                else               -> if (!mService.handleDpad(KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    mService.keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
            }
            KEYCODE_FLICK_JP_SPACE  -> when (mFlickState) {
                FLICK_STATE_LEFT -> {
                    isShifted = !isShifted
                    onSetShifted(isShifted)
                }
                else -> mService.processKey(' '.toInt())
            }
            KEYCODE_FLICK_JP_ENTER  -> if (!mService.handleEnter()) mService.pressEnter()
            KEYCODE_FLICK_JP_KOMOJI -> when (mFlickState) {
                FLICK_STATE_NONE  -> mService.changeLastChar(SKKEngine.LAST_CONVERTION_SMALL)
                FLICK_STATE_UP    -> mService.commitTextSKK("w", 1)
                FLICK_STATE_LEFT  -> mService.changeLastChar(SKKEngine.LAST_CONVERTION_DAKUTEN)
                FLICK_STATE_RIGHT -> mService.changeLastChar(SKKEngine.LAST_CONVERTION_HANDAKUTEN)
            }
            KEYCODE_FLICK_JP_CANCEL -> mService.handleCancel()
            KEYCODE_FLICK_JP_MOJI   -> when (mFlickState) {
                FLICK_STATE_NONE -> mService.processKey('q'.toInt())
                FLICK_STATE_UP   -> if (keyboard !== mNumKeyboard) { keyboard = mNumKeyboard }
                FLICK_STATE_RIGHT-> mService.processKey('>'.toInt())
                FLICK_STATE_LEFT  -> mService.processKey(':'.toInt())
                FLICK_STATE_DOWN -> if (keyboard !== mVoiceKeyboard) { keyboard = mVoiceKeyboard }
            }
            KEYCODE_FLICK_JP_TOKANA -> if (keyboard !== mJPKeyboard) { keyboard = mJPKeyboard }
            KEYCODE_FLICK_JP_TOQWERTY -> if (isShifted) {
                mService.processKey('/'.toInt())
            } else {
                mService.processKey('l'.toInt())
            }
            KEYCODE_FLICK_JP_CHAR_A, KEYCODE_FLICK_JP_CHAR_KA, KEYCODE_FLICK_JP_CHAR_SA,
                    KEYCODE_FLICK_JP_CHAR_TA, KEYCODE_FLICK_JP_CHAR_NA, KEYCODE_FLICK_JP_CHAR_HA,
                    KEYCODE_FLICK_JP_CHAR_MA, KEYCODE_FLICK_JP_CHAR_YA, KEYCODE_FLICK_JP_CHAR_RA,
                    KEYCODE_FLICK_JP_CHAR_WA, KEYCODE_FLICK_JP_CHAR_TEN,
                    KEYCODE_FLICK_JP_CHAR_TEN_SHIFTED, KEYCODE_FLICK_JP_CHAR_TEN_NUM
                    -> processFlickForLetter(mLastPressedKey, mFlickState, isShifted)
        }

        if (mLastPressedKey != Keyboard.KEYCODE_SHIFT &&
                !(mLastPressedKey == KEYCODE_FLICK_JP_SPACE &&
                mFlickState == FLICK_STATE_LEFT)) {
            isShifted = false
            onSetShifted(false)
        }

        mLastPressedKey = KEYCODE_FLICK_JP_NONE
        mFlickState = FLICK_STATE_NONE
        mFlickStartX = -1f
        mFlickStartY = -1f
        if (mUsePopup) {
            val popup = checkNotNull(mPopup) { "BUG: popup is null!!" }
            if (popup.isShowing) popup.dismiss()
        }
    }

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence) {}

    override fun swipeRight() {}

    override fun swipeLeft() {}

    override fun swipeDown() {}

    override fun swipeUp() {}

    companion object {
        private const val KEYCODE_FLICK_JP_CHAR_A = -201
        private const val KEYCODE_FLICK_JP_CHAR_KA = -202
        private const val KEYCODE_FLICK_JP_CHAR_SA = -203
        private const val KEYCODE_FLICK_JP_CHAR_TA = -204
        private const val KEYCODE_FLICK_JP_CHAR_NA = -205
        private const val KEYCODE_FLICK_JP_CHAR_HA = -206
        private const val KEYCODE_FLICK_JP_CHAR_MA = -207
        private const val KEYCODE_FLICK_JP_CHAR_YA = -208
        private const val KEYCODE_FLICK_JP_CHAR_RA = -209
        private const val KEYCODE_FLICK_JP_CHAR_WA = -210
        private const val KEYCODE_FLICK_JP_CHAR_TEN = -211
        private const val KEYCODE_FLICK_JP_CHAR_TEN_SHIFTED = -212
        private const val KEYCODE_FLICK_JP_CHAR_TEN_NUM = -213
        private const val KEYCODE_FLICK_JP_NONE = -1000
        private const val KEYCODE_FLICK_JP_LEFT = -1001
        private const val KEYCODE_FLICK_JP_RIGHT = -1002
        private const val KEYCODE_FLICK_JP_TOQWERTY = -1003
        private const val KEYCODE_FLICK_JP_SPACE = -1004
        private const val KEYCODE_FLICK_JP_MOJI = -1005
        private const val KEYCODE_FLICK_JP_KOMOJI = -1006
        private const val KEYCODE_FLICK_JP_ENTER = -1007
        private const val KEYCODE_FLICK_JP_CANCEL = -1009
        private const val KEYCODE_FLICK_JP_TOKANA = -1010
        private const val KEYCODE_FLICK_JP_PASTE = -1011
        private const val FLICK_STATE_NONE = 0
        private const val FLICK_STATE_LEFT = 1
        private const val FLICK_STATE_UP = 2
        private const val FLICK_STATE_RIGHT = 3
        private const val FLICK_STATE_DOWN = 4
        private const val FLICK_STATE_NONE_LEFT = 5
        private const val FLICK_STATE_NONE_RIGHT = 6
        private const val FLICK_STATE_LEFT_LEFT = 7
        private const val FLICK_STATE_LEFT_RIGHT = 8
        private const val FLICK_STATE_UP_LEFT = 9
        private const val FLICK_STATE_UP_RIGHT = 10
        private const val FLICK_STATE_RIGHT_LEFT = 11
        private const val FLICK_STATE_RIGHT_RIGHT = 12
        private const val FLICK_STATE_DOWN_LEFT = 13
        private const val FLICK_STATE_DOWN_RIGHT = 14
        private val POPUP_LABELS_NULL = arrayOf("", "", "", "", "", "", "")
    }
}
