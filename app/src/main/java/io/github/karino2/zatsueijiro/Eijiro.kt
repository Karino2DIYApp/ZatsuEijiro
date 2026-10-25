package io.github.karino2.zatsueijiro

import android.content.res.AssetManager
import net.jpountz.lz4.LZ4BlockInputStream
import net.jpountz.lz4.LZ4FrameInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class Eijiro(private val assetManager: AssetManager) {

    fun <T> lookupIndex(query: String, block: (BufferedReader) -> T): T? {
        if (query.isEmpty()) return null
        val targetFileName = findTargetFileName("split_index", query) ?: return null
        return readBufferedReader(targetFileName, block)
    }

    fun <T> lookupEntry(word: String, block: (BufferedReader) -> T): T? {
        if (word.isEmpty()) return null
        val targetFileName = findTargetFileName("split", word) ?: return null
        return readBufferedReader(targetFileName, block)
    }

    private fun findTargetFileName(folder: String, query: String): String? {
        val maxLen = minOf(query.length, 4)
        for (len in maxLen downTo 1) {
            val prefix = query.substring(0, len)
            val fileName = "$folder/$prefix.txt.lz4"
            try {
                assetManager.open(fileName).close()
                return fileName
            } catch (_: Exception) {
                // File does not exist, try shorter prefix
            }
        }
        return null
    }

    private fun <T> readBufferedReader(fileName: String, block: (BufferedReader) -> T): T {
        return assetManager.open(fileName).use { inputStream ->
            openDecompressedStream(inputStream).use { decompressedIn ->
                BufferedReader(InputStreamReader(decompressedIn, Charsets.UTF_8)).use { reader ->
                    block(reader)
                }
            }
        }
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
