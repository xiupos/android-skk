package io.github.ha2zakura.androidskk

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.View.OnKeyListener
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.filechooser.*
import java.io.File
import java.util.Locale

class FileChooser : AppCompatActivity() {
    private lateinit var mCurrentDir: File
    private lateinit var mMode: String
    private lateinit var mSearchToast: Toast
    private val mSearchString = StringBuilder()
    private var mFontSize = DEFAULT_FONTSIZE

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.filechooser)

        val extras = intent.extras
        extras ?: finish()

        val m = extras?.getString(KEY_MODE)
        m ?: finish()
        when (m) {
            MODE_SAVE -> {
                title = getString(R.string.label_filechooser_save_as)
                mMode = MODE_SAVE
            }
            MODE_OPEN -> {
                title = getString(R.string.label_filechooser_open)
                mMode = MODE_OPEN
            }
            MODE_DIR -> {
                title = getString(R.string.label_filechooser_opendir)
                editTextFileName.visibility = View.GONE
                mMode = MODE_DIR
            }
            else -> finish()
        }

        if (extras != null) {
            mFontSize = extras.getInt(KEY_FONTSIZE, DEFAULT_FONTSIZE)
        }

        val dirName = extras?.getString(KEY_DIRNAME)
        val defaultDir = Environment.getExternalStorageDirectory().path
        if (dirName == null) {
            mCurrentDir = File(defaultDir)
        } else {
            mCurrentDir = File(dirName)
            if (!(mCurrentDir.isDirectory && mCurrentDir.canRead())) {
                mCurrentDir = File(defaultDir)
            }
        }
        fillList()

        buttonOK.setOnClickListener {
            val intent = Intent()
            val currentDirStr = mCurrentDir.absolutePath + File.separator
            if (mMode == MODE_DIR) {
                intent.putExtra(KEY_FILENAME, "")
                intent.putExtra(KEY_DIRNAME, currentDirStr)
                intent.putExtra(KEY_FILEPATH, currentDirStr)
                setResult(Activity.RESULT_OK, intent)
            } else {
                val fileNameStr = editTextFileName.text.toString()
                if (fileNameStr.isEmpty()) {
                    setResult(Activity.RESULT_CANCELED, intent)
                } else {
                    intent.putExtra(KEY_FILENAME, fileNameStr)
                    intent.putExtra(KEY_DIRNAME, currentDirStr)
                    intent.putExtra(KEY_FILEPATH, currentDirStr + fileNameStr)
                    setResult(Activity.RESULT_OK, intent)
                }
            }
            finish()
        }

        buttonCancel.setOnClickListener {
            val intent = Intent()
            setResult(Activity.RESULT_CANCELED, intent)
            finish()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = listView.adapter.getItem(position) as String

            if (item.startsWith(getString(R.string.label_filechooser_parent_dir))) {
                checkAndChangeDir(mCurrentDir.parentFile)
            } else if (item.substring(item.length - 1) == File.separator) {
                checkAndChangeDir(File(mCurrentDir, item))
            } else {
                if (mMode == MODE_OPEN) {
                    editTextFileName.setText(item)
                    buttonOK.requestFocus()
                } else if (mMode == MODE_SAVE) {
                    editTextFileName.setText(item)
                    editTextFileName.requestFocus()
                }
            }
        }

        // quick search
        mSearchToast = Toast.makeText(this, "", Toast.LENGTH_SHORT)
        mSearchToast.setGravity(Gravity.BOTTOM or Gravity.END, 10, 10)
        listView.setOnKeyListener(OnKeyListener { v, _, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val label = event.displayLabel
                if (label.toInt() in 65..90 || label.toInt() in 97..122) {
                    mSearchString.append(label.toLowerCase())
                    val str = mSearchString.toString()
                    mSearchToast.setText(str)
                    mSearchToast.show()

                    val lv = v as ListView
                    val startIndex = if (lv.selectedItem == null) 0 else lv.selectedItemPosition
                    for (i in startIndex until lv.count) {
                        if ((lv.getItemAtPosition(i) as String)
                                        .toLowerCase(Locale.US).startsWith(str)
                        ) {
                            lv.setSelection(i)
                            return@OnKeyListener true
                        }
                    }
                    if (startIndex > 0) { // restart from the top
                        for (i in 0 until startIndex - 1) {
                            if ((lv.getItemAtPosition(i) as String)
                                            .toLowerCase(Locale.US).startsWith(str)
                            ) {
                                lv.setSelection(i)
                                return@OnKeyListener true
                            }
                        }
                    }
                    return@OnKeyListener true
                } else if (event.keyCode == KeyEvent.KEYCODE_DEL) {
                    mSearchString.deleteCharAt(mSearchString.length - 1)
                    mSearchToast.setText(String(mSearchString))
                    mSearchToast.show()
                    return@OnKeyListener true
                } else {
                    mSearchString.setLength(0)
                }
            }

            false
        })
    }

    private fun checkAndChangeDir(newDir: File) {
        if (newDir.isDirectory && newDir.canRead()) {
            mCurrentDir = newDir
            fillList()
            editTextFileName.setText("")
        } else {
            Toast.makeText(
                    this, getString(R.string.error_access_failed, newDir.absolutePath),
                    Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun fillList() {
        val filesArray = mCurrentDir.listFiles()
        if (filesArray == null) {
            Toast.makeText(
                    this, getString(R.string.error_access_failed, mCurrentDir.absolutePath),
                    Toast.LENGTH_SHORT
            ).show()
            return
        }

        textViewDirName.text = mCurrentDir.absolutePath
        textViewDirName.textSize = (mFontSize + 2).toFloat()

        val dirs = mutableListOf<String>()
        val files = mutableListOf<String>()
        for (file in filesArray) {
            if (file.isDirectory) {
                dirs.add(file.name + File.separator)
            } else {
                files.add(file.name)
            }
        }
        dirs.sort()
        files.sort()
        if (mCurrentDir.absolutePath != File.separator) {
            dirs.add(0, getString(R.string.label_filechooser_parent_dir) + File.separator)
        }
        val items = dirs.plus(files)

        val fileList: ArrayAdapter<String>
        if (mMode == MODE_DIR) {
            fileList = object : ArrayAdapter<String>(this, R.layout.filechooser_row, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.textSize = mFontSize.toFloat()

                    val text = view.text.toString()
                    if (text.substring(text.length - 1) != File.separator) {
                        // not a directory
                        view.setTextColor(Color.parseColor("#9E9E9E"))
                        view.setTypeface(null, Typeface.NORMAL)
                    } else {
                        view.setTextColor(Color.BLACK)
                        view.setTypeface(null, Typeface.BOLD)
                    }
                    return view
                }
            }
        } else {
            fileList = object : ArrayAdapter<String>(this, R.layout.filechooser_row, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.textSize = mFontSize.toFloat()
                    return view
                }
            }
        }
        listView.adapter = fileList
    }

    companion object {
        const val KEY_DIRNAME = "FileChooserDirName"
        const val KEY_FILENAME = "FileChooserFileName"
        const val KEY_FILEPATH = "FileChooserFilePath"
        const val KEY_FONTSIZE = "FileChooserFontSize"
        const val KEY_MODE = "FileChooserMode"
        const val MODE_OPEN = "ModeOPEN"
        const val MODE_SAVE = "ModeSAVE"
        const val MODE_DIR = "ModeDIR"
        private const val DEFAULT_FONTSIZE = 24
    }
}
