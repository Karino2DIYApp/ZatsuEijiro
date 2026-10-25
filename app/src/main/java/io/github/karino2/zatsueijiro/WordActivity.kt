package io.github.karino2.zatsueijiro

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jpountz.lz4.LZ4BlockInputStream
import net.jpountz.lz4.LZ4FrameInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class WordActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_word)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val word = intent.getStringExtra("WORD") ?: ""
        val textViewWord = findViewById<TextView>(R.id.textViewWord)
        textViewWord.text = getString(R.string.loading)

        scope.launch {
            val content = withContext(Dispatchers.IO) {
                findWordContent(word)
            }
            textViewWord.text = content ?: getString(R.string.not_found)
        }
    }

    private fun findWordContent(word: String): String? {
        if (word.isEmpty()) return null
        val maxLen = minOf(word.length, 4)
        var targetFileName: String? = null

        for (len in maxLen downTo 1) {
            val prefix = word.substring(0, len)
            val fileName = "split/$prefix.txt.lz4"
            try {
                assets.open(fileName).use {
                    targetFileName = fileName
                    break
                }
            } catch (_: Exception) {
                // File does not exist, try shorter prefix
            }
        }

        if (targetFileName == null) return null

        val matches = mutableListOf<String>()
        try {
            assets.open(targetFileName).use { inputStream ->
                openDecompressedStream(inputStream).use { decompressedIn ->
                    BufferedReader(InputStreamReader(decompressedIn, Charsets.UTF_8)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            if (line.startsWith("■")) {
                                val entryWord = line.substring(1).takeWhile { it != '{' && !it.isWhitespace() }
                                if (entryWord.equals(word, ignoreCase = true)) {
                                    val formattedLine = if (line.startsWith("■")) {
                                        "■" + line.substring(1).replace("■", "\n  ")
                                    } else {
                                        line.replace("■", "\n  ")
                                    }
                                    matches.add(formattedLine)
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (matches.isNotEmpty()) matches.joinToString("\n") else null
    }

    private fun openDecompressedStream(inputStream: InputStream): InputStream {
        val bufferedInput = BufferedInputStream(inputStream)
        bufferedInput.mark(4)
        val header = ByteArray(4)
        val readCount = bufferedInput.read(header)
        bufferedInput.reset()

        return if (readCount == 4 &&
            header[0] == 0x04.toByte() &&
            header[1] == 0x22.toByte() &&
            header[2] == 0x4D.toByte() &&
            header[3] == 0x18.toByte()
        ) {
            LZ4FrameInputStream(bufferedInput)
        } else {
            LZ4BlockInputStream(bufferedInput)
        }
    }
}
