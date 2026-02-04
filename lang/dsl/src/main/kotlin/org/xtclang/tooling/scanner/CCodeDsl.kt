package org.xtclang.tooling.scanner

/*
 * A Kotlin DSL for generating C code.
 *
 * This provides composable building blocks for generating readable, consistent C code
 * without raw string templates. The DSL handles:
 * - Proper indentation management
 * - Common C patterns (if/else, while, function definitions)
 * - Debug statement wrapping
 * - Tree-sitter specific patterns (token emission, lexer operations)
 */

/**
 * Marker annotation for DSL scope control.
 */
@DslMarker
annotation class CCodeDsl

/**
 * Builder for C code blocks with automatic indentation.
 *
 * Note: Some functions are unused but provided for API completeness.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@CCodeDsl
class CCodeBuilder(
    private val indentLevel: Int = 0,
) {
    private val lines = mutableListOf<String>()
    private val indent = "    ".repeat(indentLevel)

    fun line(code: String) {
        lines.add("$indent$code")
    }

    fun emptyLine() {
        lines.add("")
    }

    fun comment(text: String) {
        line("// $text")
    }

    fun sectionComment(title: String) {
        emptyLine()
        line("// " + "=".repeat(77))
        line("// $title")
        line("// " + "=".repeat(77))
    }

    fun raw(code: String) {
        lines.add(code) // No indent for raw code
    }

    /** Build a nested block with increased indentation. */
    fun block(init: CCodeBuilder.() -> Unit): String {
        val nested = CCodeBuilder(indentLevel + 1)
        nested.init()
        return nested.build()
    }

    fun build(): String = lines.joinToString("\n")

    // =========================================================================
    // C Control Structures
    // =========================================================================

    fun ifBlock(
        condition: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        line("if ($condition) {")
        lines.add(block(init))
        line("}")
    }

    fun ifElseBlock(
        condition: String,
        thenBlock: CCodeBuilder.() -> Unit,
        elseBlock: CCodeBuilder.() -> Unit,
    ) {
        line("if ($condition) {")
        lines.add(block(thenBlock))
        line("} else {")
        lines.add(block(elseBlock))
        line("}")
    }

    fun elseIfBlock(
        condition: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        // Remove trailing "}" from previous line and add else if
        if (lines.isNotEmpty() && lines.last().trimEnd() == "$indent}") {
            lines.removeAt(lines.lastIndex)
            line("} else if ($condition) {")
        } else {
            line("else if ($condition) {")
        }
        lines.add(block(init))
        line("}")
    }

    fun elseBlock(init: CCodeBuilder.() -> Unit) {
        // Remove trailing "}" from previous line and add else
        if (lines.isNotEmpty() && lines.last().trimEnd() == "$indent}") {
            lines.removeAt(lines.lastIndex)
            line("} else {")
        } else {
            line("else {")
        }
        lines.add(block(init))
        line("}")
    }

    fun whileBlock(
        condition: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        line("while ($condition) {")
        lines.add(block(init))
        line("}")
    }

    fun forBlock(
        init: String,
        condition: String,
        update: String,
        body: CCodeBuilder.() -> Unit,
    ) {
        line("for ($init; $condition; $update) {")
        lines.add(block(body))
        line("}")
    }

    fun switchBlock(
        expr: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        line("switch ($expr) {")
        lines.add(block(init))
        line("}")
    }

    fun caseBlock(
        value: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        line("case $value:")
        lines.add(block(init))
    }

    // =========================================================================
    // C Declarations
    // =========================================================================

    fun variable(
        type: String,
        name: String,
        init: String? = null,
    ) {
        if (init != null) {
            line("$type $name = $init;")
        } else {
            line("$type $name;")
        }
    }

    fun assign(
        name: String,
        value: String,
    ) {
        line("$name = $value;")
    }

    fun returnStmt(value: String? = null) {
        if (value != null) {
            line("return $value;")
        } else {
            line("return;")
        }
    }

    fun returnTrue() = returnStmt("true")

    fun returnFalse() = returnStmt("false")

    fun breakStmt() = line("break;")

    fun continueStmt() = line("continue;")

    fun call(
        function: String,
        vararg args: String,
    ) {
        line("$function(${args.joinToString(", ")});")
    }

    fun voidCast(varName: String) {
        line("(void)$varName;")
    }

    // =========================================================================
    // Debug Helpers
    // =========================================================================

    /** Wrap code in #ifdef SCANNER_DEBUG guard. */
    fun debugBlock(init: CCodeBuilder.() -> Unit) {
        raw("#ifdef SCANNER_DEBUG")
        val nested = CCodeBuilder(indentLevel)
        nested.init()
        lines.add(nested.build())
        raw("#endif")
    }

    fun debugPrint(
        format: String,
        vararg args: String,
    ) {
        debugBlock {
            val argsStr = if (args.isNotEmpty()) ", ${args.joinToString(", ")}" else ""
            line("fprintf(stderr, \"[SCANNER] $format\\n\"$argsStr);")
        }
    }

    // =========================================================================
    // Tree-sitter Lexer Helpers
    // =========================================================================

    fun advance() = call("advance", "lexer")

    fun markEnd() = line("lexer->mark_end(lexer);")

    fun peek(): String = "peek(lexer)"

    fun atEof(): String = "at_eof(lexer)"

    fun notAtEof(): String = "!at_eof(lexer)"

    /** Common pattern: while (!at_eof(lexer)) { ... }. */
    fun whileNotEof(init: CCodeBuilder.() -> Unit) {
        whileBlock(notAtEof(), init)
    }

    /** Check if current character matches. */
    fun ifPeek(
        char: Char,
        init: CCodeBuilder.() -> Unit,
    ) {
        ifBlock("${peek()} == '${escapeChar(char)}'", init)
    }

    fun ifPeekVar(
        varName: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        ifBlock("${peek()} == $varName", init)
    }

    /** Check if token is valid. */
    fun ifValidSymbol(
        token: String,
        init: CCodeBuilder.() -> Unit,
    ) {
        ifBlock("valid_symbols[$token]", init)
    }

    fun validSymbol(token: String): String = "valid_symbols[$token]"

    /** Emit a token and return true. */
    fun emitToken(tokenName: String) {
        markEnd()
        line("lexer->result_symbol = $tokenName;")
        returnTrue()
    }

    /** Emit a token with advance first. */
    fun emitTokenAfterAdvance(tokenName: String) {
        advance()
        emitToken(tokenName)
    }

    /** Handle escape sequence: consume backslash and next char. */
    fun handleEscape() {
        advance()
        ifBlock(notAtEof()) {
            advance()
        }
    }

    private fun escapeChar(c: Char): String =
        when (c) {
            '\'' -> "\\'"
            '\\' -> "\\\\"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            '"' -> "\\\""
            else -> c.toString()
        }
}

// =============================================================================
// Top-Level Builders
// =============================================================================

/** Entry point for building C code. */
fun cCode(init: CCodeBuilder.() -> Unit): String {
    val builder = CCodeBuilder()
    builder.init()
    return builder.build()
}

/** Builder for complete C function definitions. */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@CCodeDsl
class CFunctionBuilder(
    private val returnType: String,
    private val name: String,
) {
    private val params = mutableListOf<String>()
    private var body: (CCodeBuilder.() -> Unit)? = null
    private var isStatic = false
    private var isInline = false

    fun param(
        type: String,
        paramName: String,
    ) {
        params.add("$type $paramName")
    }

    fun static() {
        isStatic = true
    }

    fun inline() {
        isInline = true
    }

    fun body(init: CCodeBuilder.() -> Unit) {
        body = init
    }

    fun build(): String =
        buildString {
            val modifiers =
                buildList {
                    if (isStatic) add("static")
                    if (isInline) add("inline")
                }.joinToString(" ")

            val prefix = if (modifiers.isNotEmpty()) "$modifiers " else ""
            val paramList = params.joinToString(", ")

            appendLine("$prefix$returnType $name($paramList) {")
            body?.let {
                val bodyBuilder = CCodeBuilder(1)
                bodyBuilder.it()
                appendLine(bodyBuilder.build())
            }
            append("}")
        }
}

fun cFunction(
    returnType: String,
    name: String,
    init: CFunctionBuilder.() -> Unit,
): String {
    val builder = CFunctionBuilder(returnType, name)
    builder.init()
    return builder.build()
}

/**
 * Builder for C enums.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@CCodeDsl
class CEnumBuilder(
    private val name: String,
) {
    private val entries = mutableListOf<String>()
    private var enumComment: String? = null

    fun comment(text: String) {
        this.enumComment = text
    }

    fun entry(entryName: String) {
        entries.add(entryName)
    }

    fun entries(vararg names: String) {
        entries.addAll(names)
    }

    fun build(): String =
        buildString {
            enumComment?.let { appendLine("// $it") }
            appendLine("enum $name {")
            entries.forEachIndexed { index, entryName ->
                val comma = if (index < entries.size - 1) "," else ""
                appendLine("    $entryName$comma")
            }
            append("};")
        }
}

fun cEnum(
    name: String,
    init: CEnumBuilder.() -> Unit,
): String {
    val builder = CEnumBuilder(name)
    builder.init()
    return builder.build()
}

/**
 * Builder for C struct definitions.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@CCodeDsl
class CStructBuilder(
    private val name: String,
) {
    private val fields = mutableListOf<Pair<String, String>>()

    fun field(
        type: String,
        fieldName: String,
    ) {
        fields.add(type to fieldName)
    }

    fun build(): String =
        buildString {
            appendLine("typedef struct {")
            fields.forEach { (type, fieldName) ->
                appendLine("    $type $fieldName;")
            }
            append("} $name;")
        }
}

fun cStruct(
    name: String,
    init: CStructBuilder.() -> Unit,
): String {
    val builder = CStructBuilder(name)
    builder.init()
    return builder.build()
}

/** Builder for complete C files with sections. */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@CCodeDsl
class CFileBuilder {
    private val sections = mutableListOf<String>()

    fun header(content: String) {
        sections.add(content.trimIndent())
    }

    fun includes(vararg headers: String) {
        sections.add(headers.joinToString("\n") { "#include $it" })
    }

    fun define(
        name: String,
        value: String? = null,
    ) {
        if (value != null) {
            sections.add("#define $name $value")
        } else {
            sections.add("#define $name")
        }
    }

    fun enum(
        name: String,
        init: CEnumBuilder.() -> Unit,
    ) {
        sections.add(cEnum(name, init))
    }

    fun struct(
        name: String,
        init: CStructBuilder.() -> Unit,
    ) {
        sections.add(cStruct(name, init))
    }

    fun function(
        returnType: String,
        name: String,
        init: CFunctionBuilder.() -> Unit,
    ) {
        sections.add(cFunction(returnType, name, init))
    }

    fun raw(code: String) {
        sections.add(code.trimIndent())
    }

    fun section(
        title: String,
        init: CFileBuilder.() -> Unit,
    ) {
        val nested = CFileBuilder()
        nested.init()
        val sectionComment =
            """
            // ${"=".repeat(77)}
            // $title
            // ${"=".repeat(77)}
            """.trimIndent()
        sections.add(sectionComment)
        sections.addAll(nested.sections)
    }

    fun build(): String = sections.joinToString("\n\n")
}

fun cFile(init: CFileBuilder.() -> Unit): String {
    val builder = CFileBuilder()
    builder.init()
    return builder.build()
}
