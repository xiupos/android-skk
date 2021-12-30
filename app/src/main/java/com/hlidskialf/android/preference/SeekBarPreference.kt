/* The following code was written by Matthew Wiggins
 * and is released under the APACHE 2.0 license
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * converted to Kotlin at 18 Jan, 2018
 */
package com.hlidskialf.android.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.preference.DialogPreference
import android.widget.SeekBar
import android.widget.TextView
import android.widget.LinearLayout


class SeekBarPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs), SeekBar.OnSeekBarChangeListener {
    private lateinit var mSeekBar: SeekBar
    private lateinit var mValueText: TextView

    private val mDialogMessage: String?
    private val mSuffix: String?
    private val mDefault: Int
    private val mMax: Int
    private val mMin: Int
    private val mStep: Int
    private var mValue = 0
// mDefaultだけはPreferenceの値なので，外部に見える値

    init {
        mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage")
        mSuffix = attrs.getAttributeValue(androidns, "text")
        mMin = attrs.getAttributeIntValue(seekbarns, "min", 0)
        mStep = attrs.getAttributeIntValue(seekbarns, "step", 1)
        mMax = (attrs.getAttributeIntValue(androidns, "max", 100) - mMin) / mStep
        mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", mMin)
    }

    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restore, defaultValue)

        mValue = if (restore) {
            (getPersistedInt(mDefault) - mMin) / mStep
        } else {
            (defaultValue as Int - mMin) / mStep
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, mDefault)
    }

    override fun onCreateDialogView(): View {
        val ctx = this.context
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val layout = LinearLayout(ctx)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)

        if (mDialogMessage != null) {
            val tv = TextView(ctx)
            tv.text = mDialogMessage
            layout.addView(tv, params)
        }

        mValueText = TextView(ctx)
        mValueText.gravity = Gravity.CENTER_HORIZONTAL
        mValueText.textSize = 32f
        layout.addView(mValueText, params)

        mSeekBar = SeekBar(ctx)
        mSeekBar.setOnSeekBarChangeListener(this)
        mSeekBar.max = mMax
        layout.addView(mSeekBar, params)

        return layout
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        mValue = (getPersistedInt(mDefault) - mMin) / mStep
        mSeekBar.progress = mValue
        val t = (mMin + mValue * mStep).toString()
        mValueText.text = if (mSuffix == null) t else t + mSuffix
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        if (positiveResult) {
            if (shouldPersist()) {
                val value = mSeekBar.progress
                persistInt(mMin + value * mStep)
            }
        }
    }

    override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
        val t = (mMin + value * mStep).toString()
        mValueText.text = if (mSuffix == null) t else t + mSuffix
        callChangeListener(Integer.valueOf(mMin + value * mStep))
    }

    override fun onStartTrackingTouch(seek: SeekBar) {}
    override fun onStopTrackingTouch(seek: SeekBar) {}

    companion object {
        private const val androidns = "http://schemas.android.com/apk/res/android"
        private const val seekbarns = "http://schemas.android.com/apk/res/link.nombi.androidskklang"
    }
}
