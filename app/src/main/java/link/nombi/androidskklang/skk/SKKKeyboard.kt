package link.nombi.androidskklang

import android.content.Context
import android.inputmethodservice.Keyboard

internal class SKKKeyboard(
        context: Context,
        xmlLayoutResId: Int,
        private val mNumRow: Int
) : Keyboard(context, xmlLayoutResId) {
    override fun getHeight() = keyHeight * mNumRow

    fun changeKeyHeight(px: Int) {
        var y = 0
        var rowNo = 0
        for (key in keys) {
            key.height = px
            if (key.y != y) {
                y = key.y
                rowNo++
            }
            key.y = px * rowNo
        }
        keyHeight = px
        getNearestKeys(0, 0)
        //somehow adding this fixed a weird bug where bottom row keys could not be pressed if keyboard height is too tall.. from the Keyboard source code seems like calling this will recalculate some values used in keypress detection calculation
    }
}
