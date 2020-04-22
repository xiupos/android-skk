package io.github.ha2zakura.androidskk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import jdbm.RecordManager
import jdbm.RecordManagerFactory
import jdbm.btree.BTree
import jdbm.helper.StringComparator
import jdbm.helper.Tuple
import jp.deadend.noname.dialog.ConfirmationDialogFragment
import jp.deadend.noname.dialog.TextInputDialogFragment
import kotlinx.android.synthetic.main.dic_manager.dicManagerButton
import kotlinx.android.synthetic.main.dic_manager.dicManagerList

class SKKDicManager : AppCompatActivity() {
    private val mDics = mutableListOf<Tuple>()

    private var mAddingDic: String = "" // workaround
    private var isModified = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dic_manager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val externalStorageDir = getExternalFilesDir(null)

        if (externalStorageDir != null) {
            dicManagerButton.setOnClickListener {
                val intent = Intent(this@SKKDicManager, FileChooser::class.java)
                intent.putExtra(FileChooser.KEY_MODE, FileChooser.MODE_OPEN)
                intent.putExtra(FileChooser.KEY_DIRNAME, externalStorageDir.path)
                startActivityForResult(intent, REQUEST_TEXTDIC)
            }
        }

        mDics.add(Tuple(getString(R.string.label_dicmanager_ldic), ""))
        val optDics = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(getString(R.string.prefkey_optional_dics), "")
        if (optDics != null) {
            if (optDics.isNotEmpty()) {
                optDics.split("/").dropLastWhile { it.isEmpty() }.chunked(2).forEach {
                    mDics.add(Tuple(it[0], it[1]))
                }
            }
        }

        dicManagerList.adapter = TupleAdapter(this, mDics)
        dicManagerList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (position == 0) return@OnItemClickListener
            val dialog = ConfirmationDialogFragment.newInstance(getString(R.string.message_confirm_remove_dic))
            dialog.setListener(
                    object : ConfirmationDialogFragment.Listener {
                        override fun onPositiveClick() {
                            val dicName = mDics[position].value as String
                            deleteFile(dicName + ".db")
                            deleteFile(dicName + ".lg")
                            mDics.removeAt(position)
                            (dicManagerList.adapter as TupleAdapter).notifyDataSetChanged()
                            isModified = true
                        }
                        override fun onNegativeClick() {}
                    })
            dialog.show(supportFragmentManager, "dialog")
        }
    }

    override fun onPause() {
        if (isModified) {
            val dics = StringBuilder()
            for (i in 1 until mDics.size) {
                dics.append(mDics[i].key, "/", mDics[i].value, "/")
            }

            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(getString(R.string.prefkey_optional_dics), dics.toString())
                    .apply()


            val inputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.sendAppPrivateCommand(null, SKKService.ACTION_RELOAD_DICS, null)
        }

        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == REQUEST_TEXTDIC && resultCode == Activity.RESULT_OK) {
            val str = intent?.getStringExtra(FileChooser.KEY_FILEPATH) ?: return
            mAddingDic = str
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (mAddingDic.isNotEmpty()) {
            addDic(mAddingDic) // use dialog after fragments resumed
            mAddingDic = ""
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun addDic(filePath: String) {
        val dicFileBaseName = loadDic(filePath)
        if (dicFileBaseName != null) {
            val dialog = TextInputDialogFragment.newInstance(getString(R.string.label_dicmanager_input_name))
            dialog.setSingleLine(true)
            dialog.isCancelable = false
            dialog.setListener(
                    object : TextInputDialogFragment.Listener {
                        override fun onPositiveClick(result: String) {
                            val dicName = if (result.isEmpty()) {
                                getString(R.string.label_dicmanager_optionaldic)
                            } else {
                                result.replace("/", "")
                            }
                            var name = dicName
                            var suffix = 1
                            while (containsName(name)) {
                                suffix++
                                name = "$dicName($suffix)"
                            }
                            mDics.add(Tuple(name, dicFileBaseName))
                            (dicManagerList.adapter as TupleAdapter).notifyDataSetChanged()
                            isModified = true
                        }

                        override fun onNegativeClick() {
                            deleteFile(dicFileBaseName + ".db")
                            deleteFile(dicFileBaseName + ".lg")
                        }
                    })
            dialog.show(supportFragmentManager, "dialog")
        }
    }

    private fun loadDic(filePath: String): String? {
        val file = File(filePath)
        val name = file.name
        val dicFileBaseName = if (name.startsWith("SKK-JISYO.")) {
            "skk_dict_" + name.substring(10)
        } else {
            "skk_dict_" + name.replace(".", "_")
        }

        val filesDir = filesDir
        filesDir.listFiles().forEach {
            if (it.name == dicFileBaseName + ".db") { return null }
        }

        var recMan: RecordManager? = null
        try {
            recMan = RecordManagerFactory.createRecordManager(
                    filesDir.absolutePath + "/" + dicFileBaseName
            )
            val btree = BTree.createInstance(recMan, StringComparator())
            recMan.setNamedObject(getString(R.string.btree_name), btree.recid)
            recMan.commit()
            loadFromTextDic(filePath, recMan, btree, true)
        } catch (e: IOException) {
            if (e is CharacterCodingException) {
                Toast.makeText(
                        this@SKKDicManager, getString(R.string.error_text_dic_coding),
                        Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                        this@SKKDicManager, getString(R.string.error_file_load, filePath),
                        Toast.LENGTH_LONG
                ).show()
            }
            Log.e("SKK", "SKKDicManager#loadDic() Error: " + e.toString())
            if (recMan != null) {
                try {
                    recMan.close()
                } catch (ee: IOException) {
                    Log.e("SKK", "SKKDicManager#loadDic() can't close(): " + ee.toString())
                }

            }
            deleteFile(dicFileBaseName + ".db")
            deleteFile(dicFileBaseName + ".lg")
            return null
        }

        try {
            recMan.close()
        } catch (ee: IOException) {
            Log.e("SKK", "SKKDicManager#loadDic() can't close(): " + ee.toString())
            return null
        }

        return dicFileBaseName
    }

    private fun containsName(s: String) = mDics.any { s == it.key }

    private class TupleAdapter constructor(
            context: Context,
            items: List<Tuple>
    ) : ArrayAdapter<Tuple>(context, 0, items) {
        private val mLayoutInflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                    ?: mLayoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
            getItem(position)?.let {
                (view as TextView).text = it.key as String
            }

            return view
        }
    }

    companion object {
        private const val REQUEST_TEXTDIC = 0
    }
}
