package link.nombi.androidskklang

import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class SKKMushroom : ListActivity() {
    private lateinit var mStr: String

    private class AppInfo(
            internal var icon: Drawable,
            internal var appName: String,
            internal var packageName: String,
            internal var className: String
    )

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        val extras = intent.extras
        mStr = if (extras == null) "" else { extras.getString(REPLACE_KEY) ?: "" }
        listAdapter = AppListAdapter(this, loadMushroomAppList())
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val info = l.getItemAtPosition(position) as AppInfo

        val intent = Intent(ACTION_SIMEJI_MUSHROOM)
        intent.addCategory(CATEGORY_SIMEJI_MUSHROOM)
        intent.setClassName(info.packageName, info.className)
        intent.putExtra(REPLACE_KEY, mStr)
        // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, 0)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            val extras = data.extras
            val s = if (extras == null) "" else extras.getString(REPLACE_KEY)

            val retIntent = Intent(ACTION_BROADCAST)
            retIntent.addCategory(CATEGORY_BROADCAST)
            retIntent.putExtra(REPLACE_KEY, s)
            sendBroadcast(retIntent)
        }
        finish()
    }

    private fun loadMushroomAppList(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(ACTION_SIMEJI_MUSHROOM)
        intent.addCategory(CATEGORY_SIMEJI_MUSHROOM)
        val appList = pm.queryIntentActivities(intent, 0)
        if (appList.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_mushroom), Toast.LENGTH_LONG).show()
            finish()
        }

        val result = appList.map {
            val ai = it.activityInfo
            val icon = it.loadIcon(pm)
            icon.setBounds(0, 0, 48, 48)
            AppInfo(icon, it.loadLabel(pm).toString(), ai.packageName, ai.name)
        }

        return result.sortedBy { it.appName }
    }

    private inner class AppListAdapter(
            context: Context,
            items: List<AppInfo>
    ) : ArrayAdapter<AppInfo>(context, 0, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = convertView ?: TextView(this@SKKMushroom).apply {
                textSize = 20f
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
                compoundDrawablePadding = 8
            }

            val item = getItem(position)
            if (item != null) {
                (tv as TextView).apply {
                    setCompoundDrawables(item.icon, null, null, null)
                    text = item.appName
                }
            }

            return tv
        }
    }

    companion object {
        const val ACTION_SIMEJI_MUSHROOM = "com.adamrocker.android.simeji.ACTION_INTERCEPT"
        const val CATEGORY_SIMEJI_MUSHROOM = "com.adamrocker.android.simeji.REPLACE"

        const val ACTION_BROADCAST = "link.nombi.androidskklang.MUSHROOM_RESULT"
        const val CATEGORY_BROADCAST = "link.nombi.androidskklang.MUSHROOM_VALUE"

        const val REPLACE_KEY = "replace_key"
    }
}
