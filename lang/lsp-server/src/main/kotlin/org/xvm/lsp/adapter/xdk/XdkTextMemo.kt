package org.xvm.lsp.adapter.xdk

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat

/**
 * What a pure function of a text gave, by the text's SHA-256 digest, for the [capacity] texts used last. Each capture
 * of a module reads its files again, and a file whose text is unchanged reuses what the text gave before, with no
 * lexing or parsing. The memo keeps no text alive.
 */
internal class XdkTextMemo<T : Any>(
    /** The texts kept, more than the files of a large workspace. */
    private val capacity: Int = 4096,
) {
    /** The results by text, the least recently used first; guarded by itself. */
    private val results =
        object : LinkedHashMap<Key, T>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, T>): Boolean = size > capacity
        }

    /** A text, by its digest, and what else the result depends on. */
    private data class Key(
        val qualifier: String,
        val digest: String,
    )

    /**
     * What [compute] gives for [text], computed once for each text and [qualifier], which names what else the result
     * depends on.
     */
    fun of(
        text: String,
        qualifier: String = "",
        compute: (String) -> T,
    ): T {
        val key = Key(qualifier, digest(text))
        return synchronized(results) { results[key] }
            ?: compute(text).also { synchronized(results) { results[key] = it } }
    }

    private companion object {
        /** The digest of the text's UTF-16 code units, which, unlike an encoding, keeps every text apart. */
        fun digest(text: String): String =
            HexFormat.of().formatHex(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(ByteBuffer.allocate(text.length * Char.SIZE_BYTES).apply { asCharBuffer().put(text) }.array()),
            )
    }
}
