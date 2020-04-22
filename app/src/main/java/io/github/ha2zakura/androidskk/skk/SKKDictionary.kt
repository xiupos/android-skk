package io.github.ha2zakura.androidskk

import android.util.Log
import java.io.IOException
import jdbm.RecordManager
import jdbm.RecordManagerFactory
import jdbm.btree.BTree

class SKKDictionary(mDicFile: String, btreeName: String): SKKDictionaryInterface {
    override val mRecMan: RecordManager
    override val mRecID: Long
    override val mBTree: BTree
    override var isValid: Boolean = true

    init {
        try {
            mRecMan = RecordManagerFactory.createRecordManager(mDicFile)
            mRecID = mRecMan.getNamedObject(btreeName)
            mBTree = BTree.load(mRecMan, mRecID)
        } catch (e: Exception) {
            Log.e("SKK", "Error in opening the dictionary: $e")
            isValid = false
            throw RuntimeException(e)
        }
    }

    fun getCandidates(key: String): List<String>? {
        if (!isValid) { return null }

        val value: String?
        try {
            value = mBTree.find(key) as? String
        } catch (e: IOException) {
            Log.e("SKK", "Error in getCandidates(): $e")
            throw RuntimeException(e)
        }

        if (value == null) return null

        val valArray = value.substring(1).split("/").dropLastWhile { it.isEmpty() }
        // 先頭のスラッシュをとってから分割
        if (valArray.isEmpty()) {
            Log.e("SKK", "Invalid value found: Key=$key value=$value")
            return null
        }

        return valArray
    }
}
