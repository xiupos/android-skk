package io.github.ha2zakura.androidskk

import android.util.Log
import java.io.IOException
import jdbm.btree.BTree
import jdbm.helper.StringComparator
import jdbm.RecordManager
import jdbm.RecordManagerFactory

class SKKUserDictionary(mDicFile: String, btreeName: String): SKKDictionaryInterface {
    override val mRecMan: RecordManager
    override val mRecID: Long
    override val mBTree: BTree
    override var isValid = true
    private var mOldKey: String = ""
    private var mOldValue: String = ""

    class Entry(val candidates: MutableList<String>, val okuri_blocks: MutableList<List<String>>)

    init {
        try {
            mRecMan = RecordManagerFactory.createRecordManager(mDicFile)
            mRecID = mRecMan.getNamedObject(btreeName)
            if (mRecID == 0L) {
                mBTree = BTree.createInstance(mRecMan, StringComparator())
                mRecMan.setNamedObject(btreeName, mBTree.recid)
                mRecMan.commit()
                dlog("New user dictionary created")
            } else {
                mBTree = BTree.load(mRecMan, mRecID)
            }
        } catch (e: Exception) {
            Log.e("SKK", "Error in opening the dictionary: $e")
            isValid = false
            throw RuntimeException(e)
        }
    }

    fun getEntry(key: String): Entry? {
        if (!isValid) { return null }
        val cd = mutableListOf<String>()
        val okr = mutableListOf<List<String>>()

        val value: String?
        try {
            value = mBTree.find(key) as? String
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        if (value == null) { return null }

        val valArray = value.substring(1).split("/").dropLastWhile { it.isEmpty() }
        // 先頭のスラッシュをとってから分割
        if (valArray.isEmpty()) {
            Log.e("SKK", "Invalid value found: Key=$key value=$value")
            return null
        }

        // 送りがなブロック以外の部分を追加
        valArray.takeWhile { !it.startsWith("[") }.forEach { cd.add(it) }

        if (value.contains("[") && value.contains("]")) {
            // 送りがなブロック
            val regex = "\\[.*?\\]".toRegex()
            regex.findAll(value).forEach {
                okr.add(it.value.drop(1).dropLast(1).split('/').dropLastWhile { it.isEmpty() })
            }
        }

        return Entry(cd, okr)
    }

    fun addEntry(key: String, value: String, okuri: String?) {
        if (!isValid) return

        mOldKey = key
        val newVal = StringBuilder()
        val entry = getEntry(key)

        if (entry == null) {
            newVal.append("/", value, "/")
            if (okuri != null) newVal.append("[", okuri, "/", value, "/]/")
            mOldValue = ""
        } else {
            val cands = entry.candidates
            cands.remove(value)
            cands.add(0, value)

            val okrs = mutableListOf<List<String>>()
            if (okuri != null) {
                entry.okuri_blocks.forEach { lst ->
                    if (lst[0] == okuri) {
                        if (!lst.contains(value)) {
                            okrs.add(lst + value)
                        } else {
                            okrs.add(lst)
                        }
                    } else {
                        okrs.add(listOf(okuri, value))
                    }
                }
            }

            for (str in cands) { newVal.append("/", str) }
            for (lst in okrs) {
                newVal.append("/[")
                for (str in lst) { newVal.append(str, "/") }
                newVal.append("]")
            }
            newVal.append("/")

            try {
                mOldValue = mBTree.find(key) as String
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        try {
            mBTree.insert(key, newVal.toString(), true)
            mRecMan.commit()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun rollBack() {
        if (!isValid) return
        if (mOldKey.isEmpty()) return

        try {
            if (mOldValue.isEmpty()) {
                mBTree.remove(mOldKey)
            } else {
                mBTree.insert(mOldKey, mOldValue, true)
            }
            mRecMan.commit()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        mOldValue = ""
        mOldKey = ""
    }

    fun commitChanges() {
        if (!isValid) return
        try {
            mRecMan.commit()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
