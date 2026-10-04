package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotatedTypeExpression
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.ExpressionStatement
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Same-owner expression extraction. Syntax proposes the helper; compiler facts prove the edit. */
internal object XdkMethodExtraction {
    data class Capture(
        val declaration: SemanticModel.SourceLocation,
        val kind: SemanticModel.SymbolKind,
        val name: String,
        val type: String,
        val parameterOffset: Int,
        val argumentOffset: Int,
    )

    data class Candidate(
        val name: String,
        val expression: SemanticModel.SourceLocation,
        val insertion: XdkRename.Edit,
        val replacement: XdkRename.Edit,
        val relocation: XdkRename.Relocation,
        val methodOffset: Int,
        val captures: List<Capture>,
        val statements: Boolean = false,
    ) {
        val edits: List<XdkRename.Edit> get() = listOf(replacement, insertion)
    }

    fun candidate(
        text: String,
        selection: Range,
        model: SemanticModel,
        facts: ExtractMethodFacts,
    ): Candidate? {
        val source = model.sourceName ?: return null
        val start = XdkRename.offset(text, SemanticModel.Position(selection.start.line, selection.start.column)) ?: return null
        val end = XdkRename.offset(text, SemanticModel.Position(selection.end.line, selection.end.column)) ?: return null
        if (start >= end) return null
        val errors = ErrorList()
        val root =
            try {
                ExecutionTrace.api("Parser.parseSource(extract-method)") { Parser(Source(text), errors).parseSource() }
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null

        fun nodes(node: AstNode): Sequence<AstNode> = sequenceOf(node) + node.childNodes().asSequence().flatMap(::nodes)

        fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun offset(value: Long) = XdkRename.offset(text, position(value))

        fun spelling(node: AstNode): String? {
            val from = offset(node.startPosition) ?: return null
            val to = offset(node.endPosition) ?: return null
            return text.substring(from, to)
        }

        // A freshly parsed tree has no adopted parent links. Keep ownership local to this query.
        fun tree(
            node: AstNode,
            parent: AstNode? = null,
        ): Sequence<Pair<AstNode, AstNode?>> = sequenceOf(node to parent) + node.childNodes().asSequence().flatMap { tree(it, node) }
        val parents = tree(root).toMap()
        val all = parents.keys
        val expression =
            all.filterIsInstance<Expression>().singleOrNull { offset(it.startPosition) == start && offset(it.endPosition) == end }

        fun statementEnd(node: AstNode): Int? {
            val end = offset(node.endPosition) ?: return null
            return Regex("[ \t]*;")
                .find(text, end)
                ?.takeIf { it.range.first == end }
                ?.range
                ?.last
                ?.plus(1)
        }
        val statements =
            if (expression != null) {
                emptyList()
            } else {
                all
                    .filterIsInstance<StatementBlock>()
                    .map { block ->
                        block.childNodes().filter { node ->
                            (offset(node.startPosition) ?: -1) >= start && (offset(node.startPosition) ?: Int.MAX_VALUE) < end
                        }
                    }.singleOrNull { selected ->
                        selected.isNotEmpty() && selected.all { it is ExpressionStatement } &&
                            offset(selected.first().startPosition) == start && statementEnd(selected.last()) == end
                    } ?: return null
            }
        val roots = expression?.let(::listOf) ?: statements
        val ancestors = generateSequence(parents[roots.first()]) { parents[it] }.toList()
        val method = ancestors.filterIsInstance<MethodDeclarationStatement>().firstOrNull() ?: return null
        if (ancestors.takeWhile { it !== method }.any { it is LambdaExpression || it is TypeCompositionStatement }) return null
        // A local function has a different closure and cannot acquire a same-owner private method.
        if (generateSequence(parents[method]) { parents[it] }
                .takeWhile { it !is TypeCompositionStatement }
                .any { it is MethodDeclarationStatement || it is LambdaExpression }
        ) {
            return null
        }
        val methodName = method.nameToken ?: return null
        val owner = model.symbolAt(position(methodName.startPosition).line, position(methodName.startPosition).column) ?: return null
        val signature = owner.signature ?: return null
        if (owner.kind != SemanticModel.SymbolKind.METHOD || signature.conditional ||
            signature.parameters.any { it.typeParameter }
        ) {
            return null
        }

        fun at(offset: Int) = XdkRename.position(text, offset).let { SemanticModel.Position(it.line, it.column) }
        val range = SemanticModel.Range(at(start), at(end))
        val selectedLocation = SemanticModel.SourceLocation(source, range)
        val resultType =
            if (expression == null) {
                "void"
            } else {
                when (val statement = parents[expression]) {
                    is ReturnStatement -> {
                        if (statement.expressions?.singleOrNull() === expression) {
                            XdkLocalExtraction.writtenReturnType(text, method)
                        } else {
                            null
                        }
                    }

                    is AssignmentStatement -> {
                        if (statement.rValue === expression) XdkLocalDeclarations.initializer(text, statement)?.type else null
                    }

                    else -> {
                        null
                    }
                } ?: facts.sourceTypes[selectedLocation] ?: return null
            }
        val selectedNodes = roots.flatMap { nodes(it).toList() }
        if (selectedNodes.any {
                it is LambdaExpression || it is TypeCompositionStatement || (it is InvocationExpression && it.isAsync)
            }
        ) {
            return null
        }
        val tokens = ExecutionTrace.api("Lexer.lex(extract-method)") { Lexer(Source(text), errors).asSequence().toList() }
        if (errors.hasSeriousErrors()) return null
        // Ref-taking and super dispatch need a different contract. Preserve bitwise expressions in
        // a later slice once syntax distinguishes them from address-taking at the extraction site.
        val selectedTokens =
            ExecutionTrace.api("Lexer.lex(extract-method-selection)") {
                Lexer(Source(text.substring(start, end)), errors).asSequence().toList()
            }
        if (selectedTokens.any { it.id == Token.Id.BIT_AND || it.valueText == "super" }) return null
        if (expression != null && selectedLocation !in facts.types) return null
        val occurrences = model.occurrences.filter { it.range.start >= range.start && it.range.end <= range.end }
        val inputs =
            occurrences
                .mapNotNull { it.symbol?.let(model::symbol) }
                .filter { it.kind in setOf(SemanticModel.SymbolKind.VARIABLE, SemanticModel.SymbolKind.PARAMETER) }
                .distinctBy { it.id }
        val captured =
            inputs.map { symbol ->
                val declaration = SemanticModel.SourceLocation(symbol.declarationSource, symbol.declaration ?: return null)
                if (declaration.sourceName != source || declaration !in facts.stableValues || declaration !in facts.types) return null
                if (occurrences.filter { it.symbol == symbol.id }.any {
                        it.role != SemanticModel.Role.REFERENCE ||
                            it.usage != SemanticModel.Usage.READ
                    }
                ) {
                    return null
                }
                val written =
                    all.singleOrNull {
                        val token =
                            when (it) {
                                is Parameter -> it.nameToken
                                is VariableDeclarationStatement -> it.nameToken
                                else -> null
                            }
                        token != null && position(token.startPosition) == declaration.range.start &&
                            position(token.endPosition) == declaration.range.end
                    } ?: return null
                val type =
                    when (written) {
                        is Parameter -> {
                            if (parents[written] !== method) return null
                            written.type
                        }

                        is VariableDeclarationStatement -> {
                            written.childNodes().filterIsInstance<TypeExpression>().singleOrNull() ?: return null
                        }

                        else -> {
                            return null
                        }
                    }
                if (type is VariableTypeExpression || type is AnnotatedTypeExpression) return null
                Capture(declaration, symbol.kind, symbol.name, spelling(type) ?: return null, 0, 0)
            }
        val names = tokens.filter { it.id == Token.Id.IDENTIFIER }.map { it.valueText }.toSet() + model.symbols.map { it.name }
        val name = generateSequence(0) { it + 1 }.map { if (it == 0) "extractedMethod" else "extractedMethod$it" }.first { it !in names }
        val methodStart = offset(method.startPosition) ?: return null
        val lineStart = maxOf(text.lastIndexOf('\n', methodStart - 1), text.lastIndexOf('\r', methodStart - 1)) + 1
        val indent = text.substring(lineStart, methodStart)
        if (indent.any { it != ' ' && it != '\t' }) return null
        val insertionAt = offset(method.endPosition) ?: return null
        val tail = text.substring(insertionAt).takeWhile { it != '\r' && it != '\n' }
        if (tail.isNotBlank()) return null
        val newline = Regex("\r\n|\r|\n").find(text)?.value ?: "\n"
        val modifiers = if (SemanticModel.Modifier.STATIC in owner.modifiers) "private static" else "private"
        val header = "$newline$newline$indent$modifiers $resultType "
        val parameterPrefix = "$header$name("
        val parameters = captured.map { "${it.type} ${it.name}" }
        val prefix = "$parameterPrefix${parameters.joinToString(", ")}) {$newline$indent    ${if (expression == null) "" else "return "}"
        val suffix = "${if (expression == null) "" else ";"}$newline$indent}"
        val insertion = XdkRename.Edit(insertionAt, insertionAt, "$prefix${text.substring(start, end)}$suffix")
        val replacement =
            XdkRename.Edit(
                start,
                end,
                "$name(${captured.joinToString(", ") { it.name }})${if (expression == null) ";" else ""}",
            )
        return Candidate(
            name,
            selectedLocation,
            insertion,
            replacement,
            XdkRename.Relocation(start, end, insertion, prefix.length),
            header.length,
            captured.mapIndexed { index, capture ->
                capture.copy(
                    parameterOffset = parameterPrefix.length + parameters.take(index).sumOf { it.length + 2 } + capture.type.length + 1,
                    argumentOffset = name.length + 1 + captured.take(index).sumOf { it.name.length + 2 },
                )
            },
            expression == null,
        )
    }
}
