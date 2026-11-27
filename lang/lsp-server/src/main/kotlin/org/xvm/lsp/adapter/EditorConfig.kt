package org.xvm.lsp.adapter

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * The formatting settings that `.editorconfig` files give a source file, read at each formatting request, so that both
 * editors follow one checked-in file.
 *
 * The files are found as the EditorConfig specification says: from the file's directory up to the file system's root,
 * or to the first that says `root = true`; a nearer file's sections, and a later section in one file, win. What they
 * set overrides the editor's settings ([FormattingConfig.resolve]); a property set to `unset` leaves the editor's. Only
 * the properties the formatter acts on are read:
 *
 * - `indent_style`, `indent_size` and `tab_width`, with the specification's defaults between them: `indent_size = tab`
 *   is the tab width, an `indent_style = tab` with no `indent_size` indents by the tab width, and the tab width is the
 *   `indent_size` when `tab_width` is not set
 * - `max_line_length`: the width that code wraps and comments refill at; `off` wraps no line and refills no comment
 * - `trim_trailing_whitespace` and `insert_final_newline`
 * - IntelliJ's names for what the standard does not have, as its EditorConfig export writes them for the Ecstasy
 *   language: `ij_ecstasy_continuation_indent_size` (or `ij_continuation_indent_size`) and `ij_ecstasy_wrap_comments`
 *
 * Others, such as `end_of_line` and `charset`, are the editor's: the formatter keeps a file's line endings and encoding.
 */
internal object EditorConfig {
    const val FILE_NAME = ".editorconfig"

    private val logger = LoggerFactory.getLogger(EditorConfig::class.java)

    /** The values already reported as ignored; formatting runs at every keystroke, a warning once per value. */
    private val reported = ConcurrentHashMap.newKeySet<String>()

    /** The settings one formatting request uses. */
    data class Formatting(
        val config: FormattingConfig,
        val options: FormattingOptions,
    )

    /** A parsed `.editorconfig` file: whether it ends the search, and its sections in order. */
    data class File(
        val root: Boolean,
        val sections: List<Section>,
    )

    /** A section: its glob and its properties, names and values in lowercase. */
    data class Section(
        val glob: Glob,
        val properties: Map<String, String>,
    )

    /**
     * The settings a request on [uri] uses: the editor's ([FormattingConfig.resolve] of [options] and [editor]), with
     * what the `.editorconfig` files of a `file:` URI set over them.
     */
    fun formatting(
        uri: String,
        options: FormattingOptions,
        editor: FormattingConfig?,
    ): Formatting {
        val base = Formatting(FormattingConfig.resolve(options, editor), options)
        val file = runCatching { URI(uri) }.getOrNull()?.takeIf { it.scheme == "file" }?.let { runCatching { Path.of(it) }.getOrNull() }
        return file?.let { apply(properties(it), base, it) } ?: base
    }

    /** The properties the `.editorconfig` files set for [file], with `unset` ones left out. */
    fun properties(file: Path): Map<String, String> {
        val absolute = file.toAbsolutePath().normalize()
        val found =
            sequence {
                for (directory in generateSequence(absolute.parent) { it.parent }) {
                    read(directory.resolve(FILE_NAME))?.let { parsed ->
                        yield(directory to parsed)
                        if (parsed.root) return@sequence
                    }
                }
            }.toList()
        return found
            .asReversed()
            .fold(emptyMap<String, String>()) { properties, (directory, parsed) ->
                val relative = directory.relativize(absolute).joinToString("/")
                parsed.sections.filter { it.glob.matches(relative) }.fold(properties) { all, section -> all + section.properties }
            }.filterValues { it != "unset" }
    }

    /** Parses a `.editorconfig` file's text; lines that are neither a section, a property nor a comment are skipped. */
    fun parse(text: String): File =
        text
            .removePrefix("")
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && it[0] != '#' && it[0] != ';' }
            .fold(File(root = false, sections = emptyList())) { parsed, line ->
                val last = parsed.sections.lastOrNull()
                when {
                    line.startsWith('[') && line.endsWith(']') -> {
                        parsed.copy(sections = parsed.sections + Section(Glob(line.substring(1, line.length - 1)), emptyMap()))
                    }

                    '=' !in line -> {
                        parsed
                    }

                    else -> {
                        val name = line.substringBefore('=').trim().lowercase()
                        val value = line.substringAfter('=').trim().lowercase()
                        when {
                            last != null ->
                                parsed.copy(
                                    sections =
                                        parsed.sections.dropLast(1) + last.copy(properties = last.properties + (name to value)),
                                )

                            name == "root" -> parsed.copy(root = value == "true")

                            else -> parsed
                        }
                    }
                }
            }

    /** [base] with what [properties], found for [file], set over it. */
    fun apply(
        properties: Map<String, String>,
        base: Formatting,
        file: Path,
    ): Formatting {
        val values = Values(properties, file)
        val spaces = values.choice("indent_style", "space" to true, "tab" to false)
        val (indent, tab) = indentation(values, spaces, base.options.tabSize)
        val width = if (values["max_line_length"] == "off") 0 else values.integer("max_line_length", MAX_LINE_WIDTH)
        val continuation =
            values.integer("ij_ecstasy_continuation_indent_size", MAX_INDENT) ?: values.integer("ij_continuation_indent_size", MAX_INDENT)
        val config = base.config
        val options = base.options
        return Formatting(
            config.copy(
                indentSize = indent ?: config.indentSize,
                continuationIndentSize = continuation ?: config.continuationIndentSize,
                insertSpaces = spaces ?: config.insertSpaces,
                maxLineWidth = width ?: config.maxLineWidth,
                wrapComments = values.boolean("ij_ecstasy_wrap_comments") ?: config.wrapComments,
            ),
            options.copy(
                tabSize = tab ?: options.tabSize,
                insertSpaces = spaces ?: config.insertSpaces,
                trimTrailingWhitespace = values.boolean("trim_trailing_whitespace") ?: options.trimTrailingWhitespace,
                insertFinalNewline = values.boolean("insert_final_newline") ?: options.insertFinalNewline,
            ),
        )
    }

    /**
     * The indentation and the tab width that [values] set, with the specification's defaults between them: an
     * `indent_size` of `tab`, or an `indent_style = tab` with no `indent_size`, is the tab width ([editorTab] when no
     * `tab_width` is set), and the tab width is the `indent_size` when `tab_width` is not set.
     */
    private fun indentation(
        values: Values,
        spaces: Boolean?,
        editorTab: Int,
    ): Pair<Int?, Int?> {
        val byTab = values["indent_size"] == "tab" || (spaces == false && values["indent_size"] == null)
        val size = if (values["indent_size"] == "tab") null else values.integer("indent_size", MAX_INDENT)
        val tab = values.integer("tab_width", MAX_INDENT) ?: size
        return (if (byTab) tab ?: editorTab else size) to tab
    }

    /** One request's properties, read so that a value the formatter cannot use is ignored with a warning. */
    private class Values(
        private val properties: Map<String, String>,
        private val file: Path,
    ) {
        operator fun get(name: String) = properties[name]

        fun integer(
            name: String,
            maximum: Int,
        ): Int? =
            properties[name]?.let { value ->
                value.toIntOrNull()?.takeIf { it in 1..maximum }
                    ?: ignored(name, "an integer from 1 to $maximum")
            }

        fun boolean(name: String) = choice(name, "true" to true, "false" to false)

        fun <T : Any> choice(
            name: String,
            vararg choices: Pair<String, T>,
        ): T? =
            properties[name]?.let { value ->
                choices.firstOrNull { it.first == value }?.second
                    ?: ignored(name, choices.joinToString(" or ") { it.first })
            }

        private fun ignored(
            name: String,
            expected: String,
        ): Nothing? {
            val value = properties[name]
            if (reported.add("$name=$value")) {
                logger.warn("Ignoring {} = {} in the {} files for {}: expected {}", name, value, FILE_NAME, file, expected)
            }
            return null
        }
    }

    private fun read(path: Path): File? =
        if (!Files.isRegularFile(path)) {
            null
        } else {
            try {
                parse(Files.readString(path))
            } catch (failure: IOException) {
                logger.warn("Cannot read {}; formatting goes without it: {}", path, failure.message)
                null
            }
        }

    /** The editors' own bounds for an indentation and a line width. */
    private const val MAX_INDENT = 32
    private const val MAX_LINE_WIDTH = 1000
}

/**
 * A section's glob, in the EditorConfig specification's syntax: `*`, `**`, `?`, `[name]`, `[!name]`, `{a,b}` and
 * `{num1..num2}`, with `\` quoting the next character. A glob with a `/` matches a path below its file's directory from
 * there, one without matches a file's name at any depth.
 */
internal class Glob(
    pattern: String,
) {
    private val ranges = mutableListOf<LongRange>()
    private val regex = Regex((if ('/' in pattern) "" else "(?:.*/)?") + translate(pattern.removePrefix("/")))

    /** Whether [path], relative to the glob's directory and with `/` between its names, matches. */
    fun matches(path: String): Boolean {
        val match = regex.matchEntire(path) ?: return false
        return ranges.withIndex().all { (index, range) ->
            match.groups[index + 1]?.value.let { it == null || it.toLongOrNull()?.let(range::contains) == true }
        }
    }

    override fun toString() = regex.pattern

    private fun translate(glob: String): String =
        buildString {
            var index = 0
            while (index < glob.length) {
                val char = glob[index]
                val end =
                    when (char) {
                        '[' -> closing(glob, index, '[', ']')
                        '{' -> closing(glob, index, '{', '}')
                        else -> -1
                    }
                val (part, length) =
                    when {
                        char == '\\' && index + 1 < glob.length -> literal(glob[index + 1]) to 2
                        glob.startsWith("**/", index) -> "(?:.*/)?" to "**/".length
                        glob.startsWith("**", index) -> ".*" to "**".length
                        char == '*' -> "[^/]*" to 1
                        char == '?' -> "[^/]" to 1
                        char == '[' && end > index + 1 -> characters(glob.substring(index + 1, end)) to end + 1 - index
                        char == '{' && end > index -> braces(glob.substring(index + 1, end)) to end + 1 - index
                        else -> literal(char) to 1
                    }
                append(part)
                index += length
            }
        }

    private fun characters(names: String): String {
        val negated = names.startsWith('!')
        val body = names.removePrefix("!")
        if (body.isEmpty()) return literal('[') + names.map(::literal).joinToString("") + literal(']')
        return "[" + (if (negated) "^" else "") + body.map { if (it in "\\[]^&") "\\$it" else "$it" }.joinToString("") + "]"
    }

    private fun braces(inner: String): String {
        val choices = split(inner)
        if (choices.size > 1) return choices.joinToString("|", "(?:", ")") { translate(it) }
        val range = NUMBERS.matchEntire(inner) ?: return literal('{') + translate(inner) + literal('}')
        val (from, to) = range.destructured.toList().map { it.toLongOrNull() ?: return literal('{') + translate(inner) + literal('}') }
        ranges += minOf(from, to)..maxOf(from, to)
        return "([+-]?\\d+)"
    }

    private companion object {
        val NUMBERS = Regex("""([+-]?\d+)\.\.([+-]?\d+)""")

        fun literal(char: Char) = if (char.isLetterOrDigit()) "$char" else "\\$char"

        /** The index of the bracket closing the one at [start], or -1; braces nest, brackets do not. */
        fun closing(
            glob: String,
            start: Int,
            open: Char,
            close: Char,
        ): Int {
            var depth = 0
            var index = start
            while (index < glob.length) {
                when (glob[index]) {
                    '\\' -> index++
                    open -> if (open == '{' || index == start) depth++
                    close -> if (--depth == 0) return index
                }
                index++
            }
            return -1
        }

        /** [inner]'s choices: split at its commas outside nested braces. */
        fun split(inner: String): List<String> =
            buildList {
                var depth = 0
                var start = 0
                var index = 0
                while (index < inner.length) {
                    when (inner[index]) {
                        '\\' -> index++
                        '{' -> depth++
                        '}' -> depth--
                        ',' -> if (depth == 0) add(inner.substring(start, index)).also { start = index + 1 }
                    }
                    index++
                }
                add(inner.substring(start))
            }
    }
}
