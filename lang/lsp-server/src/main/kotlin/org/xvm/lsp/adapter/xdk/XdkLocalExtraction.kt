package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.ReturnStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.util.ExecutionTrace

/**
 * Move a complete return expression or typed local initializer to an immediately preceding local
 * without changing evaluation order. Initializers retain their written expected type. The graph proof must
 * preserve relocated references and calls as well as every unaffected binding and dispatch chain.
 */
internal object XdkLocalExtraction {
    data class Candidate(
        val title: String,
        val edits: List<XdkRename.Edit>,
        val relocation: XdkRename.Relocation,
    )

    private data class Selected(
        val statement: AstNode,
        val expression: Expression,
        val type: String?,
    )

    fun candidate(
        text: String,
        selection: Range,
    ): Candidate? {
        val (start, end) = XdkRefactoringSyntax.selectionOffsets(text, selection) ?: return null
        val errors = ErrorList()
        val root = XdkRefactoringSyntax.parse(text, errors, "extract-local") ?: return null

        fun offset(position: Long): Int? =
            XdkRename.offset(text, SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)))

        fun expressions(
            node: AstNode,
            method: MethodDeclarationStatement? = null,
        ): List<Selected> =
            node.childNodes().flatMap { child ->
                val owner =
                    when (node) {
                        is MethodDeclarationStatement -> node
                        is LambdaExpression -> null
                        else -> method
                    }
                val candidate =
                    if (node is StatementBlock) {
                        when (child) {
                            is ReturnStatement -> {
                                child.expressions?.singleOrNull()?.let {
                                    Selected(child, it, writtenReturnType(text, owner))
                                }
                            }

                            is AssignmentStatement -> {
                                XdkLocalDeclarations.initializer(text, child)?.let {
                                    Selected(child, child.rValue, it.type)
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    } else {
                        null
                    }
                listOfNotNull(candidate) + expressions(child, owner)
            }
        val selected =
            expressions(root).singleOrNull {
                offset(it.expression.startPosition) == start && offset(it.expression.endPosition) == end
            } ?: return null
        val literal =
            (selected.expression as? LiteralExpression)?.literal?.id in setOf(Token.Id.LIT_INT, Token.Id.LIT_STRING, Token.Id.LIT_CHAR)
        val type =
            if (literal && selected.statement is ReturnStatement) {
                "val"
            } else {
                selected.type ?: return null
            }
        val insertion = offset(selected.statement.startPosition) ?: return null
        // Do not rewrite same-line siblings, labels, comments or expression-bodied declarations.
        val indent = XdkRefactoringSyntax.indentBefore(text, insertion) ?: return null
        val names =
            ExecutionTrace.api("Lexer.lex(extract-local)") {
                Lexer(Source(text), errors)
                    .asSequence()
                    .filter { it.id == Token.Id.IDENTIFIER }
                    .map { it.valueText }
                    .toSet()
            }
        if (errors.hasSeriousErrors()) return null
        val name = generateSequence(0) { it + 1 }.map { if (it == 0) "extractedValue" else "extractedValue$it" }.first { it !in names }
        val newline = Regex("\\r\\n|\\r|\\n").find(text)?.value ?: "\n"
        val prefix = "$type $name = "
        val declaration = XdkRename.Edit(insertion, insertion, "$prefix${text.substring(start, end)};$newline$indent")
        return Candidate(
            if (literal) "Extract literal to local variable" else "Extract expression to local variable",
            listOf(declaration, XdkRename.Edit(start, end, name)),
            XdkRename.Relocation(start, end, declaration, prefix.length),
        )
    }

    fun writtenReturnType(
        text: String,
        declaration: MethodDeclarationStatement?,
    ): String? {
        val method = declaration?.takeUnless { it.isReturnConditional } ?: return null
        val name = method.nameToken ?: return null
        val result =
            method
                .childNodes()
                .filterIsInstance<Parameter>()
                .singleOrNull { it.type != null && it.endPosition <= name.startPosition } ?: return null

        fun offset(position: Long) =
            XdkRename.offset(
                text,
                SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)),
            )
        return text.substring(
            offset(result.type.startPosition) ?: return null,
            offset(result.type.endPosition) ?: return null,
        )
    }
}
