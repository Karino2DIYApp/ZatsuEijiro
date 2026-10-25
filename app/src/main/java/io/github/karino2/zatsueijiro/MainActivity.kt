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

class MainActivity : AppCompatActivity() {

    private lateinit var editTextQuery: EditText
    private lateinit var listViewCandidates: ListView

    private val searchScope = CoroutineScope(Dispatchers.Main + Job())
    private var searchJob: Job? = null
    private val eijiro by lazy { Eijiro(assets) }

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
        val matches = mutableListOf<String>()
        eijiro.lookupIndex(query) { reader ->
            var line = reader.readLine()
            while (line != null && matches.size < 500) {
                if (line.startsWith(query, ignoreCase = true)) {
                    matches.add(line)
                }
                line = reader.readLine()
            }
        }
        return matches
    }
}
