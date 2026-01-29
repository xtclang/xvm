package org.xtclang.tooling.generators

import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Shared utilities for syntax generators.
 */

// =============================================================================
// Regex/Pattern Escaping
// =============================================================================

/** Characters that need escaping in standard regex patterns (TextMate, Sublime, etc.) */
private const val REGEX_SPECIAL = """.+*?|\\^$()[]{}"""

/** Characters that need escaping in Vim patterns (magic mode).
 *  Note: In Vim's magic mode, ? and + are LITERAL - escaping them creates quantifiers. */
private const val VIM_SPECIAL = """\\/|^$.*[]()"""

/** Escape special characters by prepending backslash */
private fun String.escapeChars(special: String) = map { if (it in special) "\\$it" else "$it" }.joinToString("")

/** Escape for standard regex patterns (TextMate, Sublime, etc.) */
fun escapeRegex(s: String) = s.escapeChars(REGEX_SPECIAL)

/** Escape for Vim regex patterns */
fun escapeVimPattern(s: String) = s.escapeChars(VIM_SPECIAL)

/** Escape for single-quoted JavaScript strings */
fun escapeJsString(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

// =============================================================================
// JSON Builder Extensions
// =============================================================================

/**
 * Add a JSON array with the given key using a builder block.
 */
fun JsonObjectBuilder.putJsonArray(key: String, block: JsonArrayBuilder.() -> Unit) {
    put(key, buildJsonArray(block))
}

/**
 * Add a nested JSON object with the given key using a builder block.
 */
fun JsonObjectBuilder.putJsonObject(key: String, block: JsonObjectBuilder.() -> Unit) {
    put(key, buildJsonObject(block))
}

/**
 * Add a JSON object to an array using a builder block.
 */
fun JsonArrayBuilder.addJsonObject(block: JsonObjectBuilder.() -> Unit) {
    add(buildJsonObject(block))
}

/**
 * Add a nested JSON array to an array using a builder block.
 */
fun JsonArrayBuilder.addJsonArray(block: JsonArrayBuilder.() -> Unit) {
    add(buildJsonArray(block))
}

/**
 * Add a string value to a JSON array (convenience wrapper for JsonPrimitive).
 */
fun JsonArrayBuilder.addString(value: String) {
    add(JsonPrimitive(value))
}

/**
 * Add an include reference to a TextMate patterns array.
 */
fun JsonArrayBuilder.include(ref: String) {
    addJsonObject { put("include", JsonPrimitive("#$ref")) }
}

/**
 * Add multiple include references to a TextMate patterns array.
 */
fun JsonArrayBuilder.includeAll(vararg refs: String) {
    refs.forEach { include(it) }
}

// =============================================================================
// StringBuilder Helpers for Code Generation
// =============================================================================

/**
 * Append multiple lines at once.
 */
fun StringBuilder.appendLines(vararg lines: String) {
    lines.forEach { appendLine(it) }
}

/**
 * Append a blank line followed by the given lines.
 */
fun StringBuilder.appendSection(vararg lines: String) {
    appendLine()
    lines.forEach { appendLine(it) }
}
