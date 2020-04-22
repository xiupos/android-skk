package io.github.ha2zakura.androidskk

//private val PAT_QUOTED = "\"(.+?)\"".toRegex()
private val PAT_ESCAPE_NUM = "\\\\([0-9]+)".toRegex()

// 半角から全角 (UNICODE)
fun hankaku2zenkaku(pcode: Int) = if (pcode == 0x20) 0x3000 else pcode - 0x20 + 0xFF00
// スペースだけ、特別

// ひらがなを全角カタカナにする
fun hirakana2katakana(str: String?): String? {
    if (str == null) { return null }

    val str2 = str.map { if (it in 'ぁ'..'ん') it.plus(0x60) else it }.joinToString("")
    val idx = str2.indexOf("ウ゛")
    return if (idx == -1) str2 else str2.replaceRange(idx, idx+2, "ヴ")
}

fun isAlphabet(code: Int) = (code in 0x41..0x5A || code in 0x61..0x7A)

fun isVowel(code: Int) = (code == 0x61 || code == 0x69 || code == 0x75 || code == 0x65 || code == 0x6F)
// a, i, u, e, o

fun removeAnnotation(str: String): String {
    val i = str.lastIndexOf(';') // セミコロンで解説が始まる
    return if (i == -1) str else str.substring(0, i)
}

fun processConcatAndEscape(str: String): String {
    val len = str.length
    if (len < 12) { return str }
    if (str[0] != '('
            || str.substring(1, 9) != "concat \""
            || str.substring(len - 2, len) != "\")"
    ) { return str }
    // (concat "...") の形に決め打ち

//    val str2 = PAT_QUOTED.findAll(str.substring(8 until len-1)).map { it.value }.joinToString("")

    return PAT_ESCAPE_NUM.replace(
            str.substring(9 until len-2), { it.value.substring(1).toInt(8).toChar().toString() }
    )
    // emacs-lispのリテラルは8進数
}

fun createTrimmedBuilder(orig: StringBuilder): StringBuilder {
    val ret = StringBuilder(orig)
    ret.deleteCharAt(ret.length - 1)
    return ret
}

// debug log
fun dlog(msg: String) {
    if (BuildConfig.DEBUG) android.util.Log.d("SKK", msg)
}
