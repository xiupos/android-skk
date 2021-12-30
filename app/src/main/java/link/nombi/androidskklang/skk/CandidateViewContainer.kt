/*
 * Copyright (C) 2008-2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package link.nombi.androidskklang

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.candidates.view.candidates
import kotlinx.android.synthetic.main.candidates.view.c_left
import kotlinx.android.synthetic.main.candidates.view.c_right

class CandidateViewContainer(screen: Context, attrs: AttributeSet) : LinearLayout(screen, attrs) {
    private var mFontSize = -1
    private var mButtonWidth = screen.resources.getDimensionPixelSize(R.dimen.candidates_scrollbutton_width)

    fun initViews() {
        c_left.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) candidates.scrollPrev()
            false
        }
        c_right.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) candidates.scrollNext()
            false
        }
    }

    fun setScrollButtonsEnabled(left: Boolean, right: Boolean) {
        c_left.isEnabled = left
        c_right.isEnabled = right
    }

    fun setSize(px: Int) {
        if (px == mFontSize) return

        candidates.setTextSize(px)
        candidates.layoutParams = LinearLayout.LayoutParams(0, px + px / 3, 1f)
        c_left.layoutParams = LinearLayout.LayoutParams(mButtonWidth, px + px / 3)
        c_right.layoutParams = LinearLayout.LayoutParams(mButtonWidth, px + px / 3)
        requestLayout()

        mFontSize = px
    }
}
