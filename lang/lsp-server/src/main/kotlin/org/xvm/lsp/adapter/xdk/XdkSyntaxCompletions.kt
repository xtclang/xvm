package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.Token.Id
import org.xvm.compiler.ast.AnnotatedTypeExpression
import org.xvm.compiler.ast.ArrayTypeExpression
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.BiTypeExpression
import org.xvm.compiler.ast.DecoratedTypeExpression
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.ForEachStatement
import org.xvm.compiler.ast.ForStatement
import org.xvm.compiler.ast.FunctionTypeExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.NullableTypeExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.SwitchStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.compiler.ast.WhileStatement
import org.xvm.compiler.ast.partial.PartialSyntax
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.CompletionItem.CompletionKind
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.xdk.PartialSemanticModel.DeclarationName
import org.xvm.lsp.util.ExecutionTrace
import java.util.concurrent.CancellationException

/**
 * Worker-only syntax proposals from the Java parser and lexer. These are editing templates, not
 * semantic bindings. No synthetic declaration is compiled or installed in an editor snapshot.
 */
internal object XdkSyntaxCompletions {
    fun complete(
        text: String,
        position: Position,
        declarationNameType: DeclarationName? = null,
        cancelled: () -> Boolean,
    ): List<CompletionItem> =
        ExecutionTrace.api("Parser.syntaxCompletions") {
            val cursor =
                XdkRename.offset(text, SemanticModel.Position(position.line, position.column))
                    ?: return@api emptyList()
            val errors = ErrorListener.cancellable(ErrorList(), cancelled)

            fun checkCancellation() {
                if (cancelled()) throw CancellationException()
            }

            // Token positions are UTF-16 line/column pairs; index once instead of rescanning the
            // document for every token and node in a large file.
            val lineStarts = listOf(0) + newlines.findAll(text).map { it.range.last + 1 }.toList()

            fun offset(value: Long) = lineStarts[Source.calculateLine(value)] + Source.calculateOffset(value)
            checkCancellation()
            val tokens =
                try {
                    Lexer(Source(text), errors).asSequence().onEach { checkCancellation() }.toList()
                } catch (_: CompilerException) {
                    checkCancellation()
                    return@api emptyList()
                }
            if (errors.hasSeriousErrors()) return@api emptyList()
            val containing = tokens.firstOrNull { offset(it.startPosition) < cursor && cursor <= offset(it.endPosition) }
            val selected = containing?.takeIf { it.id == Id.IDENTIFIER || it.id in keywords }
            if (containing != null && selected == null &&
                (cursor < offset(containing.endPosition) || containing.id in comments || containing.id.name.startsWith("LIT_"))
            ) {
                return@api emptyList()
            }
            val start = selected?.let { offset(it.startPosition) } ?: cursor
            val end = selected?.let { offset(it.endPosition) } ?: cursor
            val prefix = text.substring(start, cursor)
            if (prefix.any { !Lexer.isIdentifierPart(it) }) return@api emptyList()
            val range = selected?.let { XdkAst.spanOf(it.startPosition, it.endPosition) } ?: Range(position, position)
            if (declarationNameType != null) {
                return@api if (selected == null) declarationNames(declarationNameType, tokens, null, "", range) else emptyList()
            }
            val root =
                try {
                    Parser(Source(text), errors).parseSource()
                } catch (_: CompilerException) {
                    checkCancellation()
                    return@api emptyList()
                }
            checkCancellation()

            fun nodes(
                node: AstNode,
                ancestors: List<AstNode>,
            ): List<Entry> {
                checkCancellation()
                return listOf(Entry(node, ancestors)) +
                    PartialSyntax.children(node).use { it.toList() }.flatMap { nodes(it, ancestors + node) }
            }
            val entries = nodes(root, emptyList())
            val declaration = selected?.let { token -> entries.firstOrNull { it.name()?.startPosition == token.startPosition } }
            if (declaration != null) {
                return@api declarationNames(declaration, tokens, selected, prefix, range)
            }

            val block =
                entries
                    .filter { it.node is StatementBlock && it.node !== root }
                    .filter { offset(it.node.startPosition) < start && end <= offset(it.node.endPosition) }
                    .maxByOrNull { it.ancestors.size }
            val context =
                when {
                    block == null && tokens.all { it === selected || it.id in comments } -> Context.FILE

                    block == null -> return@api emptyList()

                    block.ancestors.lastOrNull() is TypeCompositionStatement ||
                        block.ancestors.lastOrNull() is NewExpression -> Context.TYPE

                    block.ancestors
                        .lastOrNull {
                            it is MethodDeclarationStatement || it is TypeCompositionStatement ||
                                it is NewExpression || it is LambdaExpression || it is PropertyDeclarationStatement
                        }.let { it is MethodDeclarationStatement || it is LambdaExpression } -> Context.STATEMENT

                    else -> return@api emptyList()
                }
            val previous = tokens.lastOrNull { offset(it.endPosition) <= start && it !== selected && it.id !in comments }
            if (context != Context.FILE) {
                val body = block!!.node as StatementBlock
                val boundary =
                    previous?.let { token ->
                        (token.id == Id.L_CURLY && token.startPosition == body.startPosition) ||
                            (
                                token.id in setOf(Id.SEMICOLON, Id.R_CURLY) &&
                                    body.statements.any {
                                        it.endPosition == token.endPosition ||
                                            (
                                                token.id == Id.SEMICOLON && offset(it.endPosition) <= offset(token.startPosition) &&
                                                    text.substring(offset(it.endPosition), offset(token.startPosition)).isBlank()
                                            )
                                    }
                            )
                    } == true
                if (!boundary) return@api emptyList()
            }
            // Only offer whole templates at a vacant slot. Completing an existing keyword must not
            // duplicate its written condition, body, arguments or semicolon.
            val next = tokens.firstOrNull { offset(it.startPosition) >= end && it !== selected && it.id !in comments }
            val vacant = next == null || next.id == Id.R_CURLY
            val lineStart = lineStarts[position.line]
            val indent = text.substring(lineStart, start).takeWhile { it == ' ' || it == '\t' }
            val newline = newlines.find(text)?.value ?: "\n"
            val words =
                when (context) {
                    Context.FILE -> {
                        fileKeywords
                    }

                    Context.TYPE -> {
                        typeKeywords
                    }

                    Context.STATEMENT -> {
                        val enclosing =
                            block!!.ancestors.takeLastWhile {
                                it !is MethodDeclarationStatement && it !is LambdaExpression && it !is NewExpression
                            }
                        val loop = enclosing.any { it is ForStatement || it is ForEachStatement || it is WhileStatement }
                        statementKeywords +
                            listOfNotNull(
                                Id.BREAK.takeIf { loop || enclosing.any { it is SwitchStatement } },
                                Id.CONTINUE.takeIf { loop },
                            )
                    }
                }
            return@api buildList {
                words.map { it.TEXT }.filter { it.startsWith(prefix) }.forEach { word ->
                    add(
                        CompletionItem(
                            word,
                            CompletionKind.KEYWORD,
                            "Ecstasy ${context.description} keyword",
                            word,
                            TextEdit(range, word),
                            sortText = "6:$word",
                        ),
                    )
                }
                if (vacant) {
                    templates.filter { it.context == context && it.keyword.startsWith(prefix) }.forEach { template ->
                        fun layout(value: String) = value.replace("\n", "$newline$indent")
                        val plain = layout(template.plain)
                        add(
                            CompletionItem(
                                template.label,
                                CompletionKind.SNIPPET,
                                "Ecstasy ${context.description} template",
                                plain,
                                TextEdit(range, plain),
                                "Syntax template; review its name, condition and body before compiling.",
                                "7:${template.label}",
                                snippet = layout(template.snippet),
                            ),
                        )
                    }
                }
            }
        }

    private data class Entry(
        val node: AstNode,
        val ancestors: List<AstNode>,
    ) {
        fun name(): Token? =
            when (node) {
                is PropertyDeclarationStatement -> node.nameToken
                is VariableDeclarationStatement -> node.nameToken
                is Parameter -> node.nameToken
                is MethodDeclarationStatement -> node.nameToken
                is TypeCompositionStatement -> node.nameToken
                else -> null
            }
    }

    private fun declarationNames(
        entry: Entry,
        tokens: List<Token>,
        selected: Token,
        prefix: String,
        range: Range,
    ): List<CompletionItem> {
        val type =
            when (val node = entry.node) {
                is PropertyDeclarationStatement -> node.type
                is Parameter -> node.type
                is VariableDeclarationStatement -> node.childNodes().filterIsInstance<TypeExpression>().singleOrNull()
                else -> null
            } ?: return emptyList()
        val initializer = (entry.ancestors.lastOrNull() as? AssignmentStatement)?.rValue
        return declarationNames(declarationName(type, initializer) ?: return emptyList(), tokens, selected, prefix, range)
    }

    /** Copy syntax while on the compiler worker. Presentation policy stays outside the AST. */
    internal fun declarationName(
        type: TypeExpression,
        initializer: Expression? = null,
    ): DeclarationName? {
        fun base(node: TypeExpression): String? =
            when (node) {
                is NamedTypeExpression -> {
                    node.nameToken?.valueText
                }

                is FunctionTypeExpression -> {
                    "fn"
                }

                is BiTypeExpression -> {
                    "value"
                }

                is ArrayTypeExpression -> {
                    node
                        .childNodes()
                        .filterIsInstance<TypeExpression>()
                        .singleOrNull()
                        ?.let(::base)
                        ?.let { "${it}Array" }
                }

                is NullableTypeExpression, is DecoratedTypeExpression, is AnnotatedTypeExpression -> {
                    node
                        .childNodes()
                        .filterIsInstance<TypeExpression>()
                        .singleOrNull()
                        ?.let(::base)
                }

                else -> {
                    null
                }
            }
        if (type is VariableTypeExpression) {
            val suggestion =
                when (initializer) {
                    is NewExpression -> {
                        initializer
                            .childNodes()
                            .filterIsInstance<TypeExpression>()
                            .firstOrNull()
                            ?.let(::base)
                    }

                    is LiteralExpression -> {
                        when (initializer.literal.id) {
                            Id.LIT_STRING -> "text"
                            Id.LIT_CHAR -> "character"
                            Id.LIT_INT, Id.LIT_DEC -> "number"
                            Id.LIT_BINSTR -> "bytes"
                            else -> null
                        }
                    }

                    is NameExpression -> {
                        initializer.name.takeUnless { it == "Null" }?.let {
                            if (it == "True" || it == "False") "flag" else it
                        }
                    }

                    else -> {
                        null
                    }
                }
            return suggestion?.let { DeclarationName(it, "written $type initializer") }
        }
        return base(type)?.let { DeclarationName(it, "written type $type") }
    }

    private fun declarationNames(
        type: DeclarationName,
        tokens: List<Token>,
        selected: Token?,
        prefix: String,
        range: Range,
    ): List<CompletionItem> {
        // Acronyms retain their word boundary: HTTPClient -> httpClient, URL -> url.
        val written = type.base
        val capitals = written.takeWhile(Char::isUpperCase).length
        val count = if (capitals > 1 && capitals < written.length) capitals - 1 else capitals.coerceAtLeast(1)
        val base = written.take(count).lowercase() + written.drop(count)
        if (!Lexer.isValidIdentifier(base)) return emptyList()
        val occupied =
            tokens
                .filter { it.id == Id.IDENTIFIER && it.startPosition != selected?.startPosition }
                .map { it.valueText }
                .toSet()
        val name = generateSequence(0) { it + 1 }.map { if (it == 0) base else "$base$it" }.first { it !in occupied }
        if (!name.startsWith(prefix) || name == selected?.valueText) return emptyList()
        return listOf(
            CompletionItem(
                name,
                CompletionKind.VARIABLE,
                "Name from ${type.basis}",
                name,
                TextEdit(range, name),
                "Declaration name suggestion, not a resolved reference or rename.",
                "0:$name",
            ),
        )
    }

    private enum class Context(
        val description: String,
    ) {
        FILE("file"),
        TYPE("member"),
        STATEMENT("statement"),
    }

    private data class Template(
        val context: Context,
        val keyword: String,
        val label: String,
        val plain: String,
        val snippet: String,
    )

    private val comments = setOf(Id.EOL_COMMENT, Id.ENC_COMMENT)
    private val newlines = Regex("\\r\\n|\\r|\\n")
    private val fileKeywords = listOf(Id.MODULE, Id.PACKAGE, Id.CLASS, Id.INTERFACE, Id.SERVICE, Id.CONST, Id.ENUM, Id.MIXIN)
    private val typeKeywords =
        listOf(
            Id.CLASS,
            Id.INTERFACE,
            Id.SERVICE,
            Id.CONST,
            Id.ENUM,
            Id.MIXIN,
            Id.TYPEDEF,
            Id.IMPORT,
            Id.VOID,
            Id.PUBLIC,
            Id.PROTECTED,
            Id.PRIVATE,
            Id.STATIC,
            Id.PACKAGE,
            Id.ANNOTATION,
            Id.CONSTRUCT,
            Id.CONDITIONAL,
            Id.FUNCTION,
            Id.IMMUTABLE,
        )
    private val statementKeywords =
        listOf(
            Id.IF,
            Id.WHILE,
            Id.FOR,
            Id.SWITCH,
            Id.RETURN,
            Id.THROW,
            Id.TRY,
            Id.ASSERT,
            Id.VAL,
            Id.VAR,
            Id.DO,
            Id.USING,
            Id.FUNCTION,
            Id.IMMUTABLE,
        )
    private val keywords = (fileKeywords + typeKeywords + statementKeywords + listOf(Id.BREAK, Id.CONTINUE)).toSet()
    private val templates =
        listOf(
            Template(
                Context.FILE,
                "module",
                "module declaration",
                "module Example {\n    \n}",
                "module ${'$'}{1:Example} {\n    ${'$'}0\n}",
            ),
            Template(
                Context.TYPE,
                "class",
                "class declaration",
                "class Example {\n    \n}",
                "class ${'$'}{1:Example} {\n    ${'$'}0\n}",
            ),
            Template(
                Context.TYPE,
                "void",
                "void method",
                "void run() {\n    \n}",
                "void ${'$'}{1:run}() {\n    ${'$'}0\n}",
            ),
            Template(
                Context.STATEMENT,
                "if",
                "if block",
                "if (True) {\n    \n}",
                "if (${'$'}{1:True}) {\n    ${'$'}0\n}",
            ),
            Template(
                Context.STATEMENT,
                "while",
                "while loop",
                "while (False) {\n    \n}",
                "while (${'$'}{1:False}) {\n    ${'$'}0\n}",
            ),
            Template(
                Context.STATEMENT,
                "return",
                "return value",
                "return value;",
                "return ${'$'}{1:value};${'$'}0",
            ),
            Template(
                Context.TYPE,
                "interface",
                "interface declaration",
                "interface Example {\n    \n}",
                "interface ${'$'}{1:Example} {\n    ${'$'}0\n}",
            ),
            Template(
                Context.TYPE,
                "service",
                "service declaration",
                "service Example {\n    \n}",
                "service ${'$'}{1:Example} {\n    ${'$'}0\n}",
            ),
            Template(
                Context.STATEMENT,
                "for",
                "for range",
                "for (Int i : 0..<1) {\n    \n}",
                "for (Int i : ${'$'}{1:0..<1}) {\n    ${'$'}0\n}",
            ),
            Template(
                Context.STATEMENT,
                "do",
                "do loop",
                "do {\n    \n} while (False);",
                "do {\n    ${'$'}0\n} while (${'$'}{1:False});",
            ),
            Template(
                Context.STATEMENT,
                "try",
                "try catch",
                "try {\n    \n} catch (Exception e) {\n}",
                "try {\n    ${'$'}0\n} catch (${'$'}{1:Exception} e) {\n}",
            ),
        )
}
