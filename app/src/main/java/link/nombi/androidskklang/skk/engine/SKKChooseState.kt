package link.nombi.androidskklang.engine

import link.nombi.androidskklang.isAlphabet

// 変換候補選択中(▼モード)
object SKKChooseState : SKKState {
    override val isTransient = true
    override val icon = 0

    override fun handleKanaKey(context: SKKEngine) {
        context.pickCurrentCandidate()
        if (context.toggleKanaKey) {
            context.changeState(SKKASCIIState)
        } else {
            context.changeState(SKKHiraganaState)
        }
    }

    override fun processKey(context: SKKEngine, pcode: Int) {
        when (pcode) {
            ' '.toInt() -> context.chooseAdjacentCandidate(true)
            'x'.toInt() -> context.chooseAdjacentCandidate(false)
            '>'.toInt() -> {
                // 接尾辞入力
                context.pickCurrentCandidate()
                context.changeState(SKKKanjiState)
                val kanjiKey = context.mKanjiKey
                kanjiKey.append('>')
                context.setComposingTextSKK(kanjiKey, 1)
            }
            'l'.toInt() -> {
                // 暗黙の確定
                context.pickCurrentCandidate()
                context.changeState(SKKASCIIState)
            }
            ':'.toInt() -> context.changeState(SKKNarrowingState)
            else -> {
                // 暗黙の確定
                context.pickCurrentCandidate()
                SKKHiraganaState.processKey(context, pcode)
            }
        }
    }

    override fun afterBackspace(context: SKKEngine) {
        val kanjiKey = context.mKanjiKey
        if (kanjiKey.isEmpty()) {
            context.changeState(SKKHiraganaState)
        } else {
            if (isAlphabet(kanjiKey[0].toInt())) { // Abbrevモード
                context.changeState(SKKAbbrevState)
            } else { // 漢字変換中
                context.mOkurigana = null
                context.changeState(SKKKanjiState)
                if (isAlphabet(kanjiKey[kanjiKey.length - 1].toInt())) {
                    kanjiKey.deleteCharAt(kanjiKey.length - 1) // 送りがなのアルファベットを削除
                }
            }
            context.setComposingTextSKK(kanjiKey, 1)
            context.updateSuggestions(kanjiKey.toString())
        }
    }

    override fun handleCancel(context: SKKEngine): Boolean {
        afterBackspace(context)
        return true
    }
}
