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

class WordActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val eijiro by lazy { Eijiro(assets) }

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
        return eijiro.lookupEntry(word) { reader ->
            val matches = mutableListOf<String>()
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("■")) {
                    val entryWord = line.substring(1).substringBefore('{').substringBefore(':').trim()
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
            if (matches.isNotEmpty()) matches.joinToString("\n") else null
        }
    }
}
