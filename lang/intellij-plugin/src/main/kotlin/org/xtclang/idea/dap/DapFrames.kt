package org.xtclang.idea.dap

import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The `Content-Length` framing of Debug Adapter Protocol messages on a byte stream, which LSP4IJ's DAP client
 * speaks with a debug adapter process: [frame] gives the framed text of one JSON message, and [Decoder] splits the
 * bytes the client writes into JSON messages. Lengths count UTF-8 bytes.
 */
internal object DapFrames {
    private val headerEnd = "\r\n\r\n".toByteArray(Charsets.US_ASCII)

    fun frame(json: String): String = "Content-Length: ${json.toByteArray(Charsets.UTF_8).size}\r\n\r\n$json"

    /** Collects written bytes and passes on each complete message's JSON text, in order. */
    class Decoder(
        private val message: (String) -> Unit,
    ) {
        private val pending = ByteArrayOutputStream()

        @Synchronized
        fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            pending.write(bytes, offset, length)
            while (true) {
                val data = pending.toByteArray()
                val end = indexOf(data, headerEnd)
                if (end < 0) return
                val header = String(data, 0, end, Charsets.US_ASCII)
                val size =
                    header
                        .split("\r\n")
                        .firstNotNullOfOrNull { line ->
                            line
                                .takeIf { it.startsWith("Content-Length:", ignoreCase = true) }
                                ?.substringAfter(':')
                                ?.trim()
                                ?.toIntOrNull()
                        } ?: throw IOException("A DAP message without its Content-Length: $header")
                val start = end + headerEnd.size
                if (data.size < start + size) return
                message(String(data, start, size, Charsets.UTF_8))
                pending.reset()
                pending.write(data, start + size, data.size - start - size)
            }
        }

        private fun indexOf(
            data: ByteArray,
            target: ByteArray,
        ): Int = (0..data.size - target.size).firstOrNull { at -> target.indices.all { data[at + it] == target[it] } } ?: -1
    }
}
