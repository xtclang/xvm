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

    /** How every header line of a DAP message starts, as `Content-Length:` does. */
    private val headerStart = "Content-".toByteArray(Charsets.US_ASCII)

    fun frame(json: String): String = "Content-Length: ${json.toByteArray(Charsets.UTF_8).size}\r\n\r\n$json"

    /**
     * Collects written bytes and passes on each complete message's JSON text, in order. Bytes that do not start a
     * message where one would start are a line that the session's console wrote to the same stream, as a line
     * typed there is written: once its newline comes, it goes to [typed], and the messages go on after it.
     */
    class Decoder(
        private val message: (String) -> Unit,
        private val typed: (String) -> Unit = { throw IOException("Not a DAP message: $it") },
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
                if (data.isEmpty()) return
                if (!startsHeader(data)) {
                    val newline = data.indexOf('\n'.code.toByte())
                    if (newline < 0) return
                    consume(data, newline + 1)
                    typed(String(data, 0, newline + 1, Charsets.UTF_8))
                    continue
                }
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
                consume(data, start + size)
                message(String(data, start, size, Charsets.UTF_8))
            }
        }

        /** Whether [data] starts as a message's header does, or as much of it as has come. */
        private fun startsHeader(data: ByteArray): Boolean {
            val compared = minOf(data.size, headerStart.size)
            return String(
                data,
                0,
                compared,
                Charsets.ISO_8859_1,
            ).equals(String(headerStart, 0, compared, Charsets.ISO_8859_1), ignoreCase = true)
        }

        /** Takes the first [count] bytes of [data], the pending bytes, as handled. */
        private fun consume(
            data: ByteArray,
            count: Int,
        ) {
            pending.reset()
            pending.write(data, count, data.size - count)
        }

        private fun indexOf(
            data: ByteArray,
            target: ByteArray,
        ): Int = (0..data.size - target.size).firstOrNull { at -> target.indices.all { data[at + it] == target[it] } } ?: -1
    }
}
