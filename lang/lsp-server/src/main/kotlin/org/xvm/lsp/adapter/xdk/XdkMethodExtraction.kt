package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
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
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TryStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/** Same-owner expression extraction. Syntax proposes the helper; compiler facts prove the edit. */
internal object XdkMethodExtraction {
    /** Value captured by an extracted method, with declaration identity, type and argument/parameter offsets. */
    data class Capture(
        val declaration: SemanticModel.SourceLocation,
        val kind: SemanticModel.SymbolKind,
        val name: String,
        val type: String,
        val parameterOffset: Int,
        val argumentOffset: Int,
        val written: Boolean = false,
    )

    /** Proposed helper method, replacement and captured values to verify in the trial compilation. */
    data class Candidate(
        val name: String,
        val expression: SemanticModel.SourceLocation,
        val insertion: XdkRename.Edit,
        val replacement: XdkRename.Edit,
        val relocation: XdkRename.Relocation,
        val methodOffset: Int,
        val captures: List<Capture>,
        val statements: Boolean = false,
        /** The captured local the statements write, which the helper returns and the call assigns back. */
        val result: Capture? = null,
        /** Where the call starts in the replacement, after any assignment back to [result]. */
        val callOffset: Int = 0,
        /** Where the name in the helper's `return` of [result] starts in the inserted text. */
        val returnOffset: Int = 0,
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
        val (start, end) = XdkRefactoringSyntax.selectionOffsets(text, selection) ?: return null
        val errors = ErrorList()
        val root = XdkRefactoringSyntax.parse(text, errors, "extract-method") ?: return null

        fun position(value: Long) = SemanticModel.Position(Source.calculateLine(value), Source.calculateOffset(value))

        fun offset(value: Long) = XdkRename.offset(text, position(value))

        fun spelling(node: AstNode): String? {
            val from = offset(node.startPosition) ?: return null
            val to = offset(node.endPosition) ?: return null
            return text.substring(from, to)
        }

        // A freshly parsed tree has no adopted parent links. Keep ownership local to this query.
        val parents = XdkRefactoringSyntax.tree(root).toMap()
        val all = parents.keys
        val expression =
            all.filterIsInstance<Expression>().singleOrNull { offset(it.startPosition) == start && offset(it.endPosition) == end }

        fun statementEnd(node: AstNode): Int? {
            val end = offset(node.endPosition) ?: return null
            if (text.getOrNull(end - 1) == ';') return end
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
                        // Locals declared here and field writes may move; a write to an outer local is refused below.
                        selected.isNotEmpty() &&
                            selected.all { it is ExpressionStatement || it is AssignmentStatement || it is VariableDeclarationStatement } &&
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
                        if (statement.rValue === expression) {
                            XdkLocalDeclarations.initializer(text, statement)?.type
                        } else {
                            null
                        }
                    }

                    else -> null
                } ?: facts.sourceTypes[selectedLocation] ?: return null
            }
        val selectedNodes = roots.flatMap { XdkRefactoringSyntax.tree(it).map { entry -> entry.first }.toList() }
        if (selectedNodes.any {
                it is LambdaExpression || it is TypeCompositionStatement || (it is InvocationExpression && it.isAsync)
            }
        ) {
            return null
        }
        val tokens = ExecutionTrace.api("Lexer.lex(extract-method)") { Lexer(Source(text), errors).asSequence().toList() }
        if (errors.hasSeriousErrors()) return null
        // Ref-taking (&name) and super dispatch need a different contract; a bitwise & is an ordinary operator.
        val selectedTokens =
            ExecutionTrace.api("Lexer.lex(extract-method-selection)") {
                Lexer(Source(text.substring(start, end)), errors).asSequence().toList()
            }
        if (selectedNodes.any { it is NameExpression && it.isSuppressDeref } || selectedTokens.any { it.valueText == "super" }) {
            return null
        }
        if (expression != null && selectedLocation !in facts.types) return null
        val occurrences = model.occurrences.filter { it.range.start >= range.start && it.range.end <= range.end }
        val variables =
            occurrences
                .mapNotNull { it.symbol?.let(model::symbol) }
                .filter { it.kind in setOf(SemanticModel.SymbolKind.VARIABLE, SemanticModel.SymbolKind.PARAMETER) }
                .distinctBy { it.id }
        // A local declared in the selection moves with it, so nothing after the selection may use it.
        val (declared, inputs) =
            variables.partition { symbol ->
                symbol.declarationSource == source && symbol.declaration?.let { it.start >= range.start && it.end <= range.end } == true
            }
        if (declared.any { symbol -> model.occurrences.any { it.symbol == symbol.id && it.range.start >= range.end } }) return null
        val captured =
            inputs.map { symbol ->
                val declaration = SemanticModel.SourceLocation(symbol.declarationSource, symbol.declaration ?: return null)
                // Passing the current value is reading it: nothing outside the selection can change a plain value while
                // the selection runs, because no Ref or Var reaches it.
                if (declaration.sourceName != source || declaration !in facts.plainValues || declaration !in facts.types) return null
                val uses = occurrences.filter { it.symbol == symbol.id }
                if (uses.any { it.role != SemanticModel.Role.REFERENCE }) return null
                // Only statements may write a captured value: the helper returns it and the call assigns it back.
                val writes = uses.any { it.usage != SemanticModel.Usage.READ }
                if (writes && expression != null) return null
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

                        is VariableDeclarationStatement ->
                            written.childNodes().filterIsInstance<TypeExpression>().singleOrNull()
                                ?: return null

                        else -> return null
                    }
                if (type is AnnotatedTypeExpression) return null
                val spelled =
                    if (type is VariableTypeExpression) {
                        // val/var writes no type: spell the initializer's compiler type. The proof checks that the
                        // parameter has the declaration's own type, so a narrower or wider spelling is refused.
                        val value =
                            (parents[written] as? AssignmentStatement)
                                ?.takeIf { it.lValue === written && it.op.id == Token.Id.ASN }
                                ?.rValue ?: return null
                        facts.sourceTypes[
                            SemanticModel.SourceLocation(
                                source,
                                SemanticModel.Range(position(value.startPosition), position(value.endPosition)),
                            ),
                        ] ?: return null
                    } else {
                        spelling(type) ?: return null
                    }
                Capture(declaration, symbol.kind, symbol.name, spelled, 0, 0, writes)
            }
        val results = captured.filter { it.written }
        if (results.size > 1) return null
        results.singleOrNull()?.let { result ->
            val symbol = inputs.first { it.name == result.name }
            // A lambda or anonymous class in the method sees the local itself, so it could see the old value while the
            // helper runs. Inside try or using, a throw from the helper would drop writes the original kept.
            val closures =
                all.filter { node ->
                    (node is LambdaExpression || node is TypeCompositionStatement) &&
                        generateSequence(parents[node]) { parents[it] }.any { it === method }
                }
            if (closures.any { closure ->
                    val from = position(closure.startPosition)
                    val to = position(closure.endPosition)
                    model.occurrences.any { it.symbol == symbol.id && it.range.start >= from && it.range.end <= to }
                }
            ) {
                return null
            }
            if (ancestors.takeWhile { it !== method }.any { it is TryStatement }) return null
        }
        val names = tokens.filter { it.id == Token.Id.IDENTIFIER }.map { it.valueText }.toSet() + model.symbols.map { it.name }
        val name = generateSequence(0) { it + 1 }.map { if (it == 0) "extractedMethod" else "extractedMethod$it" }.first { it !in names }
        val methodStart = offset(method.startPosition) ?: return null
        val indent = XdkRefactoringSyntax.indentBefore(text, methodStart) ?: return null
        val insertionAt = offset(method.endPosition) ?: return null
        val tail = text.substring(insertionAt).takeWhile { it != '\r' && it != '\n' }
        if (tail.isNotBlank()) return null
        val newline = Regex("\r\n|\r|\n").find(text)?.value ?: "\n"
        val modifiers = if (SemanticModel.Modifier.STATIC in owner.modifiers) "private static" else "private"
        val result = results.singleOrNull()
        val header = "$newline$newline$indent$modifiers ${result?.type ?: resultType} "
        val parameterPrefix = "$header$name("
        val parameters = captured.map { "${it.type} ${it.name}" }
        val prefix = "$parameterPrefix${parameters.joinToString(", ")}) {$newline$indent    ${if (expression == null) "" else "return "}"
        val returning = "$newline$indent    return "
        val suffix =
            when {
                expression != null -> ";$newline$indent}"
                result != null -> "$returning${result.name};$newline$indent}"
                else -> "$newline$indent}"
            }
        val insertion = XdkRename.Edit(insertionAt, insertionAt, "$prefix${text.substring(start, end)}$suffix")
        val assignment = result?.let { "${it.name} = " }.orEmpty()
        val replacement =
            XdkRename.Edit(
                start,
                end,
                "$assignment$name(${captured.joinToString(", ") { it.name }})${if (expression == null) ";" else ""}",
            )
        val captures =
            captured.mapIndexed { index, capture ->
                capture.copy(
                    parameterOffset = parameterPrefix.length + parameters.take(index).sumOf { it.length + 2 } + capture.type.length + 1,
                    argumentOffset = name.length + 1 + captured.take(index).sumOf { it.name.length + 2 },
                )
            }
        return Candidate(
            name,
            selectedLocation,
            insertion,
            replacement,
            XdkRename.Relocation(start, end, insertion, prefix.length),
            header.length,
            captures,
            expression == null,
            captures.firstOrNull { it.written },
            assignment.length,
            prefix.length + (end - start) + returning.length,
        )
    }
}
