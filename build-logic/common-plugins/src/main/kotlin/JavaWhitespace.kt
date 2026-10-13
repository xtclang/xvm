/**
 * Repairs padded declaration/assignment groups without imposing a full Java formatter on the code.
 * Existing padding signals alignment intent; groups with ordinary single spaces stay unchanged.
 * Comments and literals, including text blocks, are masked before matching and copied verbatim.
 */
object JavaWhitespace {
    fun format(source: String): String {
        val code = maskNonCode(source)?.split('\n') ?: return source
        val lines = source.split('\n').toMutableList()
        val entries = code.mapIndexed { line, text ->
            // A comment or literal inside the matched prefix must be copied verbatim as well.
            entry(text)?.takeIf { lines[line].take(it.end) == text.take(it.end) }
        }
        val formatted = BooleanArray(lines.size)
        for (index in lines.indices) {
            val first = entries[index] ?: continue
            if (formatted[index]) continue
            val group = mutableListOf(index)
            var next = statementEnd(code, index) + 1
            while (next < lines.size) {
                val entry = entries[next] ?: break
                // An existing variable may be indented to the name column of adjacent declarations.
                val members = group.map { requireNotNull(entries[it]) }
                val indentedName = entry.type == null && members.any { it.type != null && it.nameColumn == entry.indent.length }
                if (entry.indent != first.indent && !indentedName) break
                // Do not absorb a nearby declaration/assignment merely because it is adjacent.
                // Mixed groups must already share a name or equals column; like-typed groups may
                // have a missing padding space that this formatter is meant to repair.
                val related = members.any {
                    it.type == entry.type || it.equalsColumn == entry.equalsColumn ||
                        it.type != null && entry.type != null && it.nameColumn == entry.nameColumn
                }
                if (!related || formatted[next]) break
                group.add(next)
                next = statementEnd(code, next) + 1
            }
            align(group.map { requireNotNull(entries[it]) }).forEachIndexed { offset, prefix ->
                val line = group[offset]
                lines[line] = prefix + lines[line].substring(requireNotNull(entries[line]).end)
                formatted[line] = true
            }
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

    /** A prefix ends before the assignment operator or semicolon; its initializer is copied unchanged. */
    private data class Entry(
        val indent: String, val type: String?, val typeGap: String, val name: String,
        val nameGap: String, val operator: String, val end: Int
    ) {
        val nameColumn: Int get() = indent.length + (type?.length ?: 0) + typeGap.length
        val equalsColumn: Int get() = end + operator.length - 1
        val padded: Boolean get() = typeGap.length > 1 || nameGap.length > 1 || "  " in name
        val prefix: String get() = indent + type.orEmpty() + typeGap + name + nameGap
    }

    /** Align equals signs, including compound assignments, without rewriting spacing inside targets. */
    private fun align(group: List<Entry>): List<String> {
        if (group.none { it.padded }) return group.map { it.prefix }
        val alignNames = group.any { it.typeGap.length > 1 }
        val typeWidth = group.maxOf { it.type?.length ?: 0 }
        val indentWidth = group.minOf { it.indent.length }
        val prefixes = group.map { entry ->
            if (entry.type == null) {
                val indent = if (alignNames && entry.indent.length > indentWidth) {
                    " ".repeat(indentWidth + typeWidth + 1)
                } else entry.indent
                indent + entry.name
            } else {
                val gap = if (alignNames) typeWidth - entry.type.length + 1 else 1
                entry.indent + entry.type + " ".repeat(gap) + entry.name
            }
        }
        val equalsColumn = group.indices.filter { group[it].operator != ";" }
            .maxOfOrNull { prefixes[it].length + group[it].operator.length } ?: 0
        return group.mapIndexed { index, entry ->
            prefixes[index] + if (entry.operator == ";") "" else {
                " ".repeat(equalsColumn - prefixes[index].length - entry.operator.length + 1)
            }
        }
    }

    /** Keep a wrapped initializer with the declarations after it; nested bodies are formatted separately. */
    private fun statementEnd(code: List<String>, start: Int): Int {
        var depth = 0
        for (line in start until code.size) {
            for (char in code[line]) {
                when (char) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> if (--depth < 0) return start
                    ';' -> if (depth == 0) return line
                }
            }
        }
        return start
    }

    private fun entry(line: String): Entry? {
        DECLARATION.find(line)?.let { match ->
            val (indent, type, typeGap, name, nameGap, operator) = match.destructured
            if (type.substringBefore(' ') !in NON_TYPES) {
                return Entry(indent, type, typeGap, name, nameGap, operator, match.range.last + 1 - operator.length)
            }
        }
        return ASSIGNMENT.find(line)?.let { match ->
            val (indent, name, nameGap, operator) = match.destructured
            Entry(indent, null, "", name, nameGap, operator, match.range.last + 1 - operator.length)
        }
    }

    /**
     * Preserve offsets and newlines while hiding comments, character literals and string contents.
     * Return null for Unicode escapes, which Java expands before recognizing lexical boundaries.
     */
    internal fun maskNonCode(source: String): String? {
        if (UNICODE_ESCAPE.containsMatchIn(source)) return null
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

    // Only consider indented statements; leave top-level field layouts alone.
    private const val IDENTIFIER = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
    private const val QUALIFIED_NAME = "$IDENTIFIER(?:\\.$IDENTIFIER)*"
    private const val OPERATOR = "(?:>>>|<<|>>|[+*/%&|^\\-])?=(?!=)"
    private val DECLARATION = Regex("^( {8,})((?:final +)?$QUALIFIED_NAME(?:<[^;=(){}]+>)?(?:\\[\\])*)( +)($IDENTIFIER)( *)($OPERATOR|;)")
    private val ASSIGNMENT = Regex("^( {8,})($IDENTIFIER(?:\\.$IDENTIFIER| *\\[[^]\\r\\n]+\\])*)( +)($OPERATOR)")
    private val NON_TYPES = setOf("return", "throw", "yield", "break", "continue", "assert", "case")
    private val UNICODE_ESCAPE = Regex("\\\\u+[0-9a-fA-F]{4}")
}
