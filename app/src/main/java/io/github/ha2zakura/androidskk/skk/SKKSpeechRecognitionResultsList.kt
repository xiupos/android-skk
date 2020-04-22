package io.github.ha2zakura.androidskk

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView

class SKKSpeechRecognitionResultsList : ListActivity() {
    private lateinit var mResults: List<String>

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        val extras = intent.extras
        mResults = if (extras == null) listOf() else { extras.getStringArrayList(RESULTS_KEY) ?: listOf() }
        listAdapter = ArrayAdapter<String>(this, R.layout.listitem_text_row, mResults)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val retIntent = Intent(ACTION_BROADCAST)
        retIntent.addCategory(CATEGORY_BROADCAST)
        retIntent.putExtra(SKKMushroom.REPLACE_KEY, mResults[position])
        sendBroadcast(retIntent)

        finish()
    }

    companion object {
        const val ACTION_BROADCAST = "io.github.ha2zakura.androidskk.MUSHROOM_RESULT"
        const val CATEGORY_BROADCAST = "io.github.ha2zakura.androidskk.MUSHROOM_VALUE"

        const val RESULTS_KEY = "speech_recognition_results_key"
    }
}
