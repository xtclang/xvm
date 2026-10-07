package org.xvm.lsp.adapter.xdk

import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AnnotatedTypeExpression
import org.xvm.compiler.ast.AssignmentStatement
import org.xvm.compiler.ast.TypeExpression
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.compiler.ast.VariableTypeExpression
import org.xvm.lsp.adapter.Range

/** Plain, explicitly typed local initializers shared by conservative local refactorings. */
internal object XdkLocalDeclarations {
    /** Select only a local declared in this source view, never a member or dependency symbol. */
    fun selectedSymbol(
        model: SemanticModel,
        selection: Range,
    ): SemanticModel.Symbol? {
        val source = model.sourceName ?: return null
        return model
            .symbolAt(selection.start.line, selection.start.column)
            ?.takeIf { it.kind == SemanticModel.SymbolKind.VARIABLE && it.declarationSource == source }
    }

    data class Initializer(
        val statement: AssignmentStatement,
        val local: VariableDeclarationStatement,
        val type: String,
    )

    fun initializer(
        text: String,
        statement: AssignmentStatement,
    ): Initializer? {
        if (statement.op.id != Token.Id.ASN) return null
        val local = statement.lValue as? VariableDeclarationStatement ?: return null
        val type = local.childNodes().filterIsInstance<TypeExpression>().singleOrNull() ?: return null
        // Inference and Ref/Var annotations can change construction and storage semantics.
        if (type is VariableTypeExpression || type is AnnotatedTypeExpression) return null

        fun offset(position: Long) =
            XdkRename.offset(
                text,
                SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)),
            )
        val start = offset(statement.startPosition) ?: return null
        val typeStart = offset(type.startPosition) ?: return null
        val typeEnd = offset(type.endPosition) ?: return null
        val nameStart = offset(local.nameToken.startPosition) ?: return null
        val nameEnd = offset(local.nameToken.endPosition) ?: return null
        val valueStart = offset(statement.rValue.startPosition) ?: return null
        if (text.substring(start, typeStart).isNotBlank() || text.substring(typeEnd, nameStart).isNotBlank() ||
            !Regex("\\s*=\\s*").matches(text.substring(nameEnd, valueStart))
        ) {
            return null
        }
        return Initializer(statement, local, text.substring(typeStart, typeEnd))
    }
}
