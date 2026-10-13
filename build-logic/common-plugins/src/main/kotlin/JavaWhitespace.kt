/**
 * Trims leftover local-variable padding without imposing a full Java formatter on existing code.
 * Consecutive declarations or assignments retain column alignment. Comments and literals, including
 * text blocks, are masked before matching, so formatting never changes embedded text.
 */
object JavaWhitespace {
    fun format(source: String): String {
        // Java expands Unicode escapes before tokenization, including escaped quote delimiters.
        // Leave those files alone rather than infer their lexical structure from unexpanded text.
        if (UNICODE_ESCAPE.containsMatchIn(source)) return source
        val lines = source.split('\n').toMutableList()
        val code = maskNonCode(source).split('\n')
        val entries = code.mapIndexed { line, text ->
            // A comment or literal inside the matched prefix must be copied verbatim as well.
            entry(text)?.takeIf { lines[line].take(it.end) == text.take(it.end) }
        }
        var index = 0
        while (index < lines.size) {
            val first = entries[index]
            if (first == null) {
                index++
                continue
            }
            var end = index + 1
            while (end < lines.size && entries[end]?.let { it.indent == first.indent && (it.type == null) == (first.type == null) } == true) {
                end++
            }
            val group = entries.subList(index, end).filterNotNull()
            val typeWidth = group.maxOf { it.type?.length ?: 0 }
            val nameWidth = group.maxOf { it.name.length }
            for (line in index until end) {
                val entry = requireNotNull(entries[line])
                val typeGap = entry.type?.let { entry.typeGap.take(typeWidth - it.length + 1) }.orEmpty()
                val nameGap = entry.nameGap.take(if (code[line][entry.end] == ';') 0 else nameWidth - entry.name.length + 1)
                lines[line] = entry.indent + entry.type.orEmpty() + typeGap + entry.name + nameGap + lines[line].substring(entry.end)
            }
            index = end
        }
        return lines.filterIndexed { line, text ->
            if (!text.isBlank() || line == 0 || line == lines.lastIndex) {
                true
            } else {
                val previous = code[line - 1]
                val next = code[line + 1]
                val bareJump = next.trim() in setOf("break;", "continue;", "return;") &&
                    previous.takeWhile(Char::isWhitespace) == next.takeWhile(Char::isWhitespace)
                val statementEnd = previous.trimEnd().endsWith(';')
                val blockEnd = previous.trim() == "}"
                !((statementEnd || blockEnd) && bareJump || statementEnd && next.trimStart().startsWith("} else"))
            }
        }.joinToString("\n")
    }

    /** A prefix ends before the equals sign or semicolon; its initializer is copied unchanged. */
    private data class Entry(val indent: String, val type: String?, val typeGap: String, val name: String, val nameGap: String, val end: Int)

    private fun entry(line: String): Entry? {
        DECLARATION.find(line)?.let { match ->
            val (indent, type, typeGap, name, nameGap) = match.destructured
            if (type.substringBefore(' ') !in NON_TYPES) {
                return Entry(indent, type, typeGap, name, nameGap, match.range.last + 1)
            }
        }
        return ASSIGNMENT.find(line)?.let { match ->
            val (indent, name, nameGap) = match.destructured
            Entry(indent, null, "", name, nameGap, match.range.last + 1)
        }
    }

    /** Preserve offsets and newlines while hiding comments, character literals and string contents. */
    private fun maskNonCode(source: String): String {
        val masked = StringBuilder(source)
        var index = 0
        while (index < source.length) {
            val start = index
            val delimiter = when {
                source.startsWith("//", index) -> "\n"
                source.startsWith("/*", index) -> "*/"
                source.startsWith("\"\"\"", index) -> "\"\"\""
                source[index] == '"' -> "\""
                source[index] == '\'' -> "'"
                else -> null
            }
            if (delimiter == null) {
                index++
                continue
            }
            index += if (delimiter == "\n" || delimiter == "*/") 2 else delimiter.length
            while (index < source.length) {
                if (delimiter != "\n" && delimiter != "*/" && source[index] == '\\') {
                    index = (index + 2).coerceAtMost(source.length)
                } else if (source.startsWith(delimiter, index)) {
                    if (delimiter != "\n") index += delimiter.length
                    break
                } else {
                    index++
                }
            }
            for (position in start until index) {
                if (source[position] != '\n' && source[position] != '\r') masked.setCharAt(position, ' ')
            }
        }
        return masked.toString()
    }

    // Restrict alignment changes to method-local indentation; field layouts remain untouched.
    private val DECLARATION = Regex("^( {8,})((?:final +)?[\\w.$]+(?:<[^;=(){}]+>)?(?:\\[\\])*)( +)([\\w$]+)( *)(?==|;)")
    private val ASSIGNMENT = Regex("^( {8,})([\\w$]+(?:\\.[\\w$]+)*(?:\\[[^]\\r\\n]+\\])*)( +)(?==[^=])")
    private val NON_TYPES = setOf("return", "throw", "yield", "break", "continue", "assert", "case")
    private val UNICODE_ESCAPE = Regex("\\\\u+[0-9a-fA-F]{4}")
}
