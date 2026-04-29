package org.xvm.lsp.server

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Shared dispatch over the two shapes LSP4J hands us for `initializationOptions` and
 * `workspace/configuration` payloads.
 *
 * LSP4J de-serialises options either as a Java `Map<*, *>` (when gson saw a JSON object
 * literal in the request) or as a Gson `JsonObject` / `JsonElement` (when the client
 * payload is opaque). Each consumer of these options has to defensively dispatch over
 * both shapes to read primitive values. Two callers used to re-implement this dispatch:
 * [SourceRootResolver.parseInitOptions] (an array of strings) and
 * [XtcLanguageServer.parseFormattingConfig] (named ints/booleans). They now both
 * delegate to this helper so the dispatch lives in one place.
 *
 * Returns `null` when the requested key is absent, the value is the wrong shape, or the
 * value cannot be coerced -- callers supply their own defaults.
 */
internal object LspJsonOptions {
    /** Look up [key] on a Map / JsonObject / JsonElement. Returns the raw value or null. */
    fun lookup(
        raw: Any?,
        key: String,
    ): Any? =
        when (raw) {
            null -> null
            is Map<*, *> -> raw[key]
            is JsonObject -> raw.get(key)
            is JsonElement -> if (raw.isJsonObject) raw.asJsonObject.get(key) else null
            else -> null
        }

    /** Read [key] as a list of non-blank strings; returns `emptyList()` if missing or wrong shape. */
    fun stringList(
        raw: Any?,
        key: String,
    ): List<String> {
        val value = lookup(raw, key) ?: return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            is JsonArray -> value.mapNotNull { stringOrNull(it) }
            is JsonElement -> if (value.isJsonArray) value.asJsonArray.mapNotNull { stringOrNull(it) } else emptyList()
            else -> emptyList()
        }
    }

    /** Read [key] as an [Int]; coerces from numeric primitives or numeric strings, else returns null. */
    fun int(
        raw: Any?,
        key: String,
    ): Int? =
        when (val value = lookup(raw, key)) {
            is Number -> {
                value.toInt()
            }

            is String -> {
                value.toIntOrNull()
            }

            is JsonElement -> {
                value.takeUnless { it.isJsonNull }?.let {
                    when {
                        it.isJsonPrimitive && it.asJsonPrimitive.isNumber -> it.asInt
                        it.isJsonPrimitive && it.asJsonPrimitive.isString -> it.asString.toIntOrNull()
                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }

    /** Read [key] as a [Boolean]; coerces from boolean primitives or `"true"`/`"false"` strings. */
    fun boolean(
        raw: Any?,
        key: String,
    ): Boolean? =
        when (val value = lookup(raw, key)) {
            is Boolean -> {
                value
            }

            is String -> {
                value.toBooleanStrictOrNull()
            }

            is JsonElement -> {
                value.takeUnless { it.isJsonNull }?.let {
                    when {
                        it.isJsonPrimitive && it.asJsonPrimitive.isBoolean -> it.asBoolean
                        it.isJsonPrimitive && it.asJsonPrimitive.isString -> it.asString.toBooleanStrictOrNull()
                        else -> null
                    }
                }
            }

            else -> {
                null
            }
        }

    private fun stringOrNull(elem: JsonElement?): String? =
        elem
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf(String::isNotEmpty)
}
