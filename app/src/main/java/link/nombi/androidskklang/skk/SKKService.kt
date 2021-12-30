package link.nombi.androidskklang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.ClipboardManager
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.WindowManager
import android.widget.Toast

import link.nombi.androidskklang.engine.*

class SKKService : InputMethodService() {
    private var mCandidateViewContainer: CandidateViewContainer? = null
    private var mCandidateView: CandidateView? = null
    private var mFlickJPInputView: FlickJPKeyboardView? = null
    private var mQwertyInputView: QwertyKeyboardView? = null
    private var mAbbrevKeyboardView: AbbrevKeyboardView? = null
    private var mScreenHeight = 0

    private val mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    private var mIsRecording = false
    private lateinit var mAudioManager: AudioManager
    private var mStreamVolume = 0

    private lateinit var mEngine: SKKEngine

    // onKeyDown()でEnterキーのイベントを消費したかどうかのフラグ．onKeyUp()で判定するのに使う
    private var isEnterUsed = false

    private var isCandidatesViewShownFlag = false

    private val mShiftKey = SKKStickyShift(this)
    private var mStickyShift = false
    private var mSandS = false
    private var mSpacePressed = false
    private var mSandSUsed = false

    private var mUseSoftKeyboard = false

    private var mMushroomWord: String? = null
    private val hMushroom = Handler()
    private val rMushroom = Runnable {
        val word = mMushroomWord
        word?.let {
            if (word.isNotEmpty()) {
                currentInputConnection.commitText(word, 1)
                mMushroomWord = null
                keyDownUp(KeyEvent.KEYCODE_DPAD_CENTER)
            }
        }
    }
    private val mMushroomReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras
            extras?.let { mMushroomWord = extras.getString(SKKMushroom.REPLACE_KEY) }
            hMushroom.postDelayed(rMushroom, 250)

//                if (mMushroomWord != null) {
//                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
//                    cm.setText(mMushroomWord)
//                }
        }
    }

    private fun openDictionaries(): List<SKKDictionary> {
        val result = mutableListOf<SKKDictionary>()
        val dd = filesDir.absolutePath
        dlog("dict dir: " + dd)

        result.add(SKKDictionary(dd + "/" + getString(R.string.dic_name_main), getString(R.string.btree_name)))
        if (!result[0].isValid) {
            Toast.makeText(this@SKKService, getString(R.string.error_dic), Toast.LENGTH_LONG).show()
            stopSelf()
        }

        val prefVal = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.prefkey_optional_dics), "")
        prefVal?.let {
            if (prefVal.isNotEmpty()) {
                val vals = prefVal.split("/").dropLastWhile { it.isEmpty() }
                var i = 1
                while (i < vals.size) {
                    result.add(SKKDictionary(dd + "/" + vals[i], getString(R.string.btree_name)))
                    val last = result.size - 1
                    if (!result[last].isValid) result.removeAt(last)
                    i += 2
                }
            }
        }

        return result
    }

    private fun openUserDictionary(): SKKUserDictionary {
        val dd = filesDir.absolutePath
        val dic = SKKUserDictionary(
                dd + "/" + getString(R.string.dic_name_user), getString(R.string.btree_name)
        )
        if (!dic.isValid) {
            Toast.makeText(
                    this@SKKService, getString(R.string.error_user_dic),
                    Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }

        return dic
    }

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(MyUncaughtExceptionHandler(applicationContext))

        mEngine = SKKEngine(this@SKKService, openDictionaries(), openUserDictionary())

        val filter = IntentFilter(SKKMushroom.ACTION_BROADCAST)
        filter.addCategory(SKKMushroom.CATEGORY_BROADCAST)
        registerReceiver(mMushroomReceiver, filter)

        mSpeechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onResults(results: Bundle?) {
                results?.let {
                    it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.size == 1) {
                            commitTextSKK(matches[0], 0)
                        } else {
                            val intent = Intent(this@SKKService, SKKSpeechRecognitionResultsList::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putStringArrayListExtra(SKKSpeechRecognitionResultsList.RESULTS_KEY, matches)
                            startActivity(intent)
                        }
                    }
                }
                mFlickJPInputView?.setHighlightedKey(-1)
                mIsRecording = false
                Handler().postDelayed({
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mStreamVolume, 0)
                }, 500)
            }
        })
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        readPrefs()
    }

    private fun readPrefs() {
        val context = applicationContext
        mStickyShift = SKKPrefs.getStickyMeta(context)
        mSandS = SKKPrefs.getSandS(context)
        SKKPrefs.getKutoutenType(context)?.let { mEngine.setZenkakuPunctuationMarks(it) }

        mUseSoftKeyboard = checkUseSoftKeyboard()
        updateInputViewShown()

        if (mFlickJPInputView != null) readPrefsForInputView()
        val container = mCandidateViewContainer
        container?.let {
            val sp = SKKPrefs.getCandidatesSize(context)
            val px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics
            ).toInt()
            container.setSize(px)
        }
    }

    private fun readPrefsForInputView() {
        val flick = mFlickJPInputView
        val qwerty = mQwertyInputView
        val abbrev = mAbbrevKeyboardView
        if (flick == null || qwerty == null || abbrev == null) return

        val context = applicationContext
        val config = resources.configuration
        val keyHeight: Int
        val keyWidth: Int
        when (config.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                keyHeight = SKKPrefs.getKeyHeightPort(context)
                keyWidth = SKKPrefs.getKeyWidthPort(context)
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                keyHeight = SKKPrefs.getKeyHeightLand(context)
                keyWidth = SKKPrefs.getKeyWidthLand(context)
            }
            else -> {
                keyHeight = 30
                keyWidth = 100
            }
        }

        SKKPrefs.getKeyPosition(context)?.let {
            flick.prepareNewKeyboard(
                applicationContext,
                keyWidth, mScreenHeight * keyHeight / (4 * 100), it
            )
        }
        val density = context.resources.displayMetrics.density
        val sensitivity = when (SKKPrefs.getFlickSensitivity(context)) {
            "low"  -> (36 * density + 0.5f).toInt()
            "high" -> (12 * density + 0.5f).toInt()
            else   -> (24 * density + 0.5f).toInt()
        }
        qwerty.setFlickSensitivity(sensitivity)
        qwerty.changeKeyHeight(mScreenHeight * keyHeight / (4 * 100))
        abbrev.changeKeyHeight(mScreenHeight * keyHeight / (4 * 100))
    }

    private fun checkUseSoftKeyboard(): Boolean {
        var result = true
        val use_softkey = SKKPrefs.getUseSoftKey(applicationContext)
        if (use_softkey == "on") {
            dlog("software keyboard forced")
            result = true
        } else if (use_softkey == "off") {
            dlog("software keyboard disabled")
            result = false
        } else {
            val config = resources.configuration
            if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
                result = false
            } else if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
                result = true
            }
        }

        if (result) hideStatusIcon()

        return result
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        mUseSoftKeyboard = checkUseSoftKeyboard()
        updateInputViewShown()
        mScreenHeight = (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.height
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return mUseSoftKeyboard
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        mFlickJPInputView = null
        mQwertyInputView = null
        mAbbrevKeyboardView = null
        super.onConfigurationChanged(newConfig)
    }

    private fun createInputView() {
        val context = applicationContext
        mFlickJPInputView = FlickJPKeyboardView(context, null)
        mFlickJPInputView?.setService(this)
        mQwertyInputView = QwertyKeyboardView(context, null)
        mQwertyInputView?.setService(this)
        mAbbrevKeyboardView = AbbrevKeyboardView(context, null)
        mAbbrevKeyboardView?.setService(this)

        readPrefsForInputView()
    }

    override fun onCreateInputView(): View? {
        createInputView()

        if (mEngine.state === SKKASCIIState) { return mQwertyInputView }
        if (mEngine.state === SKKKatakanaState) { mFlickJPInputView?.setKatakanaMode() }

        return mFlickJPInputView
    }

    /**
     * This is the main point where we do our initialization of the
     * input method to begin operating on an application. At this
     * point we have been bound to the client, and are now receiving
     * all of the detailed information about the target of our edits.
     */
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        if (mStickyShift) mShiftKey.clearState()
        if (mSandS) {
            mSpacePressed = false
            mSandSUsed = false
        }

        mEngine.resetOnStartInput()
        when (attribute.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_CLASS_PHONE -> {
                if (mEngine.state !== SKKASCIIState) mEngine.processKey('l'.toInt())
            }
            InputType.TYPE_CLASS_TEXT -> {
                val variation = attribute.inputType and InputType.TYPE_MASK_VARIATION
                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_URI
                        || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                ) {
                    if (mEngine.state !== SKKASCIIState) mEngine.processKey('l'.toInt())
                }
            }
        }
    }

    /**
     * Called by the framework when your view for showing candidates
     * needs to be generated, like [.onCreateInputView].
     */
    override fun onCreateCandidatesView(): View {
        val container = layoutInflater.inflate(R.layout.candidates, null) as CandidateViewContainer
        container.initViews()
        val view = container.findViewById(R.id.candidates) as CandidateView
        view.setService(this)
        view.setContainer(container)
        mCandidateView = view

        val context = applicationContext
        val sp = SKKPrefs.getCandidatesSize(context)
        val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics
        ).toInt()
        container.setSize(px)

        mCandidateViewContainer = container

        return container
    }

    override fun onStartCandidatesView(info: EditorInfo, restarting: Boolean) {
        isCandidatesViewShownFlag = true
    }

    override fun onFinishCandidatesView(finishingInput: Boolean) {
        isCandidatesViewShownFlag = false
        super.onFinishCandidatesView(finishingInput)
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    override fun onFinishInput() {
        super.onFinishInput()

        setCandidatesViewShown(false)
    }

    override fun onDestroy() {
        mEngine.commitUserDictChanges()
        unregisterReceiver(mMushroomReceiver)

        super.onDestroy()
    }

    // never use fullscreen mode
    override fun onEvaluateFullscreenMode() = false

    override fun onAppPrivateCommand(action: String, data: Bundle?) {
        when (action) {
            ACTION_COMMIT_USERDIC -> {
                dlog("commit user dictionary!")
                mEngine.commitUserDictChanges()
            }
            ACTION_READ_PREFS -> readPrefs()
            ACTION_RELOAD_DICS -> mEngine.reopenDictionaries(openDictionaries())
        }
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        outInsets?.apply { contentTopInsets = visibleTopInsets }
        // CandidatesViewに対して強制的にActivityをリサイズさせるためのhack
    }

    /**
     * Use this to monitor key events being delivered to the
     * application. We get first crack at them, and can either resume
     * them or let them continue to the app.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (mEngine.state === SKKASCIIState) { return super.onKeyUp(keyCode, event) }

        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                if (mStickyShift) {
                    mShiftKey.release()
                    return true
                }
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (mSandS) {
                    mSpacePressed = false
                    if (!mSandSUsed) processKey(' '.toInt())
                    mSandSUsed = false
                    return true
                }
                if (isEnterUsed) {
                    isEnterUsed = false
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (isEnterUsed) {
                    isEnterUsed = false
                    return true
                }
            }
            else -> {}
        }

        return super.onKeyUp(keyCode, event)
    }

    /**
     * Use this to monitor key events being delivered to the
     * application. We get first crack at them, and can either resume
     * them or let them continue to the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val context = applicationContext
        val engineState = mEngine.state
        val encodedKey = SetKeyPreference.encodeKey(event)

        // Process special keys
        if (encodedKey == SKKPrefs.getKanaKey(context)) {
            mEngine.handleKanaKey()
            return true
        }

        if (engineState === SKKASCIIState && !mEngine.isRegistering) {
            return super.onKeyDown(keyCode, event)
        }

        if (encodedKey == SKKPrefs.getCancelKey(context)) {
            if (handleCancel()) { return true }
        }

        if (engineState === SKKAbbrevState && encodedKey == 724) { // 724はCtrl+q
            processKey(-1010)
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (engineState === SKKKanjiState || engineState === SKKAbbrevState) {
                var isShifted = false
                if (mStickyShift) {
                    if (mShiftKey.useState() and KeyEvent.META_SHIFT_ON != 0) {
                        isShifted = true
                    }
                } else if (mSandS) {
                    if (mSpacePressed) {
                        isShifted = true
                        mSandSUsed = true
                    }
                } else {
                    if (event.metaState and KeyEvent.META_SHIFT_ON != 0) {
                        isShifted = true
                    }
                }
                mEngine.chooseAdjacentSuggestion(!isShifted)
                return true
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                if (mStickyShift) {
                    mShiftKey.press()
                    return true
                }
            }
            KeyEvent.KEYCODE_BACK  -> if (mEngine.handleBackKey()) { return true }
            KeyEvent.KEYCODE_DEL   -> if (handleBackspace()) { return true }
            KeyEvent.KEYCODE_ENTER -> if (handleEnter()) { return true }
            KeyEvent.KEYCODE_SPACE -> {
                if (mSandS) {
                    mSpacePressed = true
                } else {
                    processKey(' '.toInt())
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> if (handleDpad(keyCode)) { return true }
            else ->
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to
                // process it and do the appropriate action.
                if (translateKeyDown(event)) { return true }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * This translates incoming hard key events in to edit operations
     * on an InputConnection.
     */
    private fun translateKeyDown(event: KeyEvent): Boolean {
        val c: Int
        if (mStickyShift) {
            c = event.getUnicodeChar(mShiftKey.useState())
        } else {
            if (mSandS && mSpacePressed) {
                c = event.getUnicodeChar(KeyEvent.META_SHIFT_ON)
                mSandSUsed = true
            } else {
                c = event.unicodeChar
            }
        }

        val ic = currentInputConnection
        if (c == 0 || ic == null) { return false }

        processKey(c)

        return true
    }

    fun processKey(pcode: Int) {
        mEngine.processKey(pcode)
    }
    fun handleKanaKey() {
        mEngine.handleKanaKey()
    }
    fun handleCancel(): Boolean {
        return mEngine.handleCancel()
    }
    fun changeLastChar(type: String) {
        mEngine.changeLastChar(type)
    }
    fun commitTextSKK(text: CharSequence, newCursorPosition: Int) {
        mEngine.commitTextSKK(text, newCursorPosition)
    }
    fun pickCandidateViewManually(index: Int) {
        mEngine.pickCandidateViewManually(index)
    }

    fun handleBackspace(): Boolean {
        if (mStickyShift) mShiftKey.useState()
        return mEngine.handleBackspace()
    }

    fun handleEnter(): Boolean {
        if (mStickyShift) mShiftKey.useState()

        if (mEngine.handleEnter()) {
            isEnterUsed = true
            return true
        } else {
            return false
        }
    }

    fun handleDpad(keyCode: Int): Boolean {
        if (mStickyShift) mShiftKey.useState()
        if (mEngine.isRegistering) {
            return true
        } else if (mEngine.state === SKKChooseState) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mEngine.chooseAdjacentCandidate(false)
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mEngine.chooseAdjacentCandidate(true)
            }
            return true
        } else if (mEngine.state.isTransient) { return true }

        return false
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    fun keyDownUp(keyEventCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEventCode))
    }

    fun pressEnter() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo

        when (editorInfo.imeOptions and
                (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            EditorInfo.IME_ACTION_DONE   -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
            EditorInfo.IME_ACTION_GO     -> ic.performEditorAction(EditorInfo.IME_ACTION_GO)
            EditorInfo.IME_ACTION_NEXT   -> ic.performEditorAction(EditorInfo.IME_ACTION_NEXT)
            EditorInfo.IME_ACTION_SEARCH -> ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
            EditorInfo.IME_ACTION_SEND   -> ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
            else -> keyDownUp(KeyEvent.KEYCODE_ENTER)
        }
    }

    fun onStartRegister() {
        val flick = mFlickJPInputView ?: return
        if (mUseSoftKeyboard) flick.setRegisterMode(true)
    }

    fun onFinishRegister() {
        val flick = mFlickJPInputView ?: return
        if (mUseSoftKeyboard) flick.setRegisterMode(false)
    }

    fun sendToMushroom() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val cs = cm.text
        val clip = cs?.toString() ?: ""

        val str = mEngine.prepareToMushroom(clip)

        val mushroom = Intent(this, SKKMushroom::class.java)
        mushroom.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        mushroom.putExtra(SKKMushroom.REPLACE_KEY, str)
        startActivity(mushroom)
    }

    fun recognizeSpeech() {
        if (mIsRecording) {
            mSpeechRecognizer.stopListening()
            mFlickJPInputView?.setHighlightedKey(-1)
            mIsRecording = false
            return
        }
        mIsRecording = true
        mFlickJPInputView?.setHighlightedKey(2) // 「声」キー
        mStreamVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.packageName)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        mSpeechRecognizer.startListening(intent)
    }

    fun setCandidates(list: List<String>?) {
        if (!isCandidatesViewShownFlag) {
            if (mUseSoftKeyboard || SKKPrefs.getUseCandidatesView(this)) {
                setCandidatesViewShown(true)
            }
        }

        if (list != null) mCandidateView?.setContents(list)
    }

    fun requestChooseCandidate(index: Int) {
        mCandidateView?.choose(index)
    }

    fun clearCandidatesView() {
        mCandidateView?.setContents(listOf())
    }

    // カーソル直前に引数と同じ文字列があるなら，それを消してtrue なければfalse
    fun prepareReConversion(candidate: String): Boolean {
        val ic = currentInputConnection
        if (ic != null && candidate == ic.getTextBeforeCursor(candidate.length, 0)) {
            ic.deleteSurroundingText(candidate.length, 0)
            return true
        }

        return false
    }

    fun changeSoftKeyboard(state: SKKState) {
        if (!mUseSoftKeyboard) return
        when (state) {
            SKKASCIIState -> {
                val qwerty = mQwertyInputView ?: return
                setInputView(qwerty)
            }
            SKKHiraganaState -> {
                val flick = mFlickJPInputView ?: return
                flick.setHiraganaMode()
                setInputView(flick)
            }
            SKKKatakanaState -> {
                val flick = mFlickJPInputView ?: return
                flick.setKatakanaMode()
                setInputView(flick)
            }
            SKKAbbrevState -> {
                val abbrev = mAbbrevKeyboardView ?: return
                setInputView(abbrev)
            }
        }
    }

    override fun showStatusIcon(iconRes: Int) {
        if (!mUseSoftKeyboard && iconRes != 0) super.showStatusIcon(iconRes)
    }

    companion object {
        internal const val ACTION_COMMIT_USERDIC = "link.nombi.androidskklang.ACTION_COMMIT_USERDIC"
        internal const val ACTION_READ_PREFS = "link.nombi.androidskklang.ACTION_READ_PREFS"
        internal const val ACTION_RELOAD_DICS = "link.nombi.androidskklang.ACTION_RELOAD_DICS"
    }
}
