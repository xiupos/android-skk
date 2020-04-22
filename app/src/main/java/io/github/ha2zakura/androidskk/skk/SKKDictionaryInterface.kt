package io.github.ha2zakura.androidskk

import android.util.Log
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import jdbm.RecordManager
import jdbm.btree.BTree
import jdbm.helper.Tuple
import jdbm.helper.TupleBrowser

@Throws(IOException::class)
private fun appendToEntry(key: String, value: String, btree: BTree) {
    val oldval = btree.find(key)

    if (oldval != null) {
        val valList = value.substring(1).split("/").dropLastWhile { it.isEmpty() }
        val oldvalList = (oldval as String).substring(1).split("/").dropLastWhile { it.isEmpty() }

        val newValue = StringBuilder()
        newValue.append("/")
        valList.union(oldvalList).forEach { newValue.append(it, "/") }
        btree.insert(key, newValue.toString(), true)
    } else {
        btree.insert(key, value, true)
    }
}

@Throws(IOException::class)
internal fun loadFromTextDic(
        file: String,
        recMan: RecordManager,
        btree: BTree,
        overwrite: Boolean
) {
    val decoder = Charset.forName("UTF-8").newDecoder()
    decoder.onMalformedInput(CodingErrorAction.REPORT)
    decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
    val br = BufferedReader(InputStreamReader(FileInputStream(file), decoder))

    var line: String?
    var count = 0

    line = br.readLine()
    while (line != null) {
        if (line.startsWith(";;")) {
            line = br.readLine()
            continue
        }

        val idx = line.indexOf(' ')
        if (idx == -1) {
            line = br.readLine()
            continue
        }
        val key = line.substring(0, idx)
        val value = line.substring(idx + 1, line.length)
        if (overwrite) {
            btree.insert(key, value, true)
        } else {
            appendToEntry(key, value, btree)
        }

        if (++count % 1000 == 0) recMan.commit()
        line = br.readLine()
    }

    recMan.commit()
}

interface SKKDictionaryInterface {
    val mRecMan: RecordManager
    val mRecID: Long
    val mBTree: BTree
    var isValid: Boolean

    fun findKeys(key: String): List<String> {
        val list = mutableListOf<String>()
        val tuple = Tuple()
        val browser: TupleBrowser
        var str: String

        if (!isValid) { return list }

        try {
            browser = mBTree.browse(key) ?: return list

            while (list.size < 5) {
                if (!browser.getNext(tuple)) break
                str = tuple.key as String
                if (!str.startsWith(key)) break
                if (isAlphabet(str[str.length - 1].toInt()) && !isAlphabet(str[0].toInt())) continue
                // 送りありエントリは飛ばす

                list.add(str)
            }
        } catch (e: IOException) {
            Log.e("SKK", "Error in findKeys(): $e")
            throw RuntimeException(e)
        }

        return list
    }

    fun close() {
        try {
            mRecMan.commit()
            mRecMan.close()
        } catch (e: Exception) {
            Log.e("SKK", "Error in close(): $e")
            throw RuntimeException(e)
        }

        isValid = false
    }
}
