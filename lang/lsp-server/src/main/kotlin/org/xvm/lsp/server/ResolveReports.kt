package org.xvm.lsp.server

import com.google.gson.JsonPrimitive
import java.util.UUID
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode

/** Bounded detached results, owned by one connection and guarded by the document lifecycle lock. */
internal class ResolveReports<T>(
    private val limit: Int = 512,
    private val characterLimit: Int = 2_000_000,
) {
    private data class Entry<T>(
        val revision: Long,
        val label: String,
        val value: T,
        val characters: Int,
    )

    private val entries = linkedMapOf<String, Entry<T>>()

    fun remember(revision: Long, label: String, value: T, characters: Int): String? {
        // Oversized values remain eager rather than returning a handle which is already evicted.
        if (characters > characterLimit) return null
        val id = UUID.randomUUID().toString()
        entries[id] = Entry(revision, label, value, characters)
        while (
            entries.size > limit || entries.values.sumOf { it.characters } > characterLimit
        ) entries.remove(entries.keys.first())
        return id
    }

    fun resolve(data: Any?, revision: Long, label: String): T {
        val id =
            when (data) {
                is String -> data
                is JsonPrimitive -> data.takeIf { it.isString }?.asString
                else -> null
            }
        val entry = entries[id]
        if (entry == null || entry.revision != revision || entry.label != label)
            throw ResponseErrorException(
                ResponseError(
                    ResponseErrorCode.ContentModified,
                    "Result expired or changed; request a fresh list",
                    null,
                )
            )
        return entry.value
    }

    fun clear() = entries.clear()
}
