package io.github.karino2.zatsueijiro

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
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

class MainActivity : AppCompatActivity() {

    private lateinit var editTextQuery: EditText
    private lateinit var listViewCandidates: ListView

    private val searchScope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editTextQuery = findViewById(R.id.editTextQuery)
        listViewCandidates = findViewById(R.id.listViewCandidates)

        editTextQuery.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString()?.trim() ?: ""
            searchJob?.cancel()
            if (query.isEmpty()) {
                listViewCandidates.adapter = null
                return@doOnTextChanged
            }

            searchJob = searchScope.launch {
                val matches = withContext(Dispatchers.IO) {
                    searchCandidates(query)
                }
                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, matches)
                listViewCandidates.adapter = adapter
            }
        }

        listViewCandidates.setOnItemClickListener { _, _, position, _ ->
            val selectedWord = listViewCandidates.getItemAtPosition(position) as? String
            if (selectedWord != null) {
                val intent = Intent(this, WordActivity::class.java).apply {
                    putExtra("WORD", selectedWord)
                }
                startActivity(intent)
            }
        }
    }

    private fun searchCandidates(query: String): List<String> {
        val maxLen = minOf(query.length, 4)
        var targetFileName: String? = null

        for (len in maxLen downTo 1) {
            val prefix = query.substring(0, len)
            val fileName = "split_index/$prefix.txt.lz4"
            try {
                assets.open(fileName).use {
                    targetFileName = fileName
                    break
                }
            } catch (_: Exception) {
                // File does not exist, try shorter prefix
            }
        }

        val matches = mutableListOf<String>()
        if (targetFileName != null) {
            try {
                assets.open(targetFileName).use { inputStream ->
                    openDecompressedStream(inputStream).use { decompressedIn ->
                        BufferedReader(InputStreamReader(decompressedIn, Charsets.UTF_8)).use { reader ->
                            var line = reader.readLine()
                            while (line != null && matches.size < 500) {
                                if (line.startsWith(query, ignoreCase = true)) {
                                    matches.add(line)
                                }
                                line = reader.readLine()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return matches
    }

    private fun openDecompressedStream(inputStream: InputStream): InputStream {
        val bufferedInput = BufferedInputStream(inputStream)
        bufferedInput.mark(4)
        val header = ByteArray(4)
        val readCount = bufferedInput.read(header)
        bufferedInput.reset()

        // LZ4 Frame magic number: 0x184D2204 -> bytes: 0x04, 0x22, 0x4D, 0x18
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
