package link.nombi.androidskklang

import android.app.Activity
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.android.synthetic.main.dic_extract.dicExtractButton
import kotlinx.android.synthetic.main.dic_extract.dicExtractMessage
import kotlinx.android.synthetic.main.dic_extract.dicExtractProgressBar

class SKKDicExtractActivity : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dic_extract)

        val task = UnzipTask()
        dicExtractButton.setOnClickListener {
            task.cancel(true)
            dialogDone(false)
        }

        task.execute()
    }

    private fun dialogDone(finished: Boolean) {
        dicExtractProgressBar.visibility = View.GONE

        if (finished) {
            dicExtractMessage.text = getString(R.string.message_finished)
        } else {
            dicExtractMessage.text = getString(R.string.message_cancelled)
        }

        dicExtractButton.text = getString(android.R.string.ok)
        dicExtractButton.setOnClickListener { finish() }
    }

    private inner class UnzipTask : AsyncTask<Void, Int, Boolean>() {
        override fun onPreExecute() {}

        override fun doInBackground(vararg params: Void): Boolean? {
            try {
                val inputs = resources.assets.open(ZIPFILE)
                val zis = ZipInputStream(BufferedInputStream(inputs))
                val ze = zis.nextEntry
                val bos = BufferedOutputStream(FileOutputStream(File(filesDir, ze.name)))
                val extractedSize = java.lang.Long.valueOf(ze.size).toInt()
                val buf = ByteArray(1024)
                var size: Int
                var sizeProcessed = 0

                size = zis.read(buf, 0, buf.size)
                while (size > -1) {
                    if (isCancelled) {
                        bos.close()
                        zis.closeEntry()
                        zis.close()
                        return false
                    }
                    bos.write(buf, 0, size)
                    sizeProcessed += size
                    publishProgress(sizeProcessed * 100 / extractedSize)
                    size = zis.read(buf, 0, buf.size)
                }

                bos.close()
                zis.closeEntry()
                zis.close()
            } catch (e: IOException) {
                Log.e("SKK", "I/O error in extracting dictionary files")
                Log.e("SKK", e.toString())
                return false
            }

            return true
        }

        override fun onProgressUpdate(vararg progress: Int?) {
            if (progress[0] != null) {
                dicExtractProgressBar.progress = progress[0] as Int
            }
        }

        override fun onPostExecute(result: Boolean?) {
            if (result != null) dialogDone(result)
        }
    }

    companion object {
        private const val ZIPFILE = "skk_dict_btree_db.zip"
    }
}
