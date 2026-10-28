package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ErrorList
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.TypeCompositionStatement

/** Fresh, capture-free module aliases for a proposed ownership change. No compiler state escapes. */
internal object XdkMoveImports {
    data class Imports(
        val aliases: Map<String, String>,
        val edit: XdkRename.Edit,
    )

    fun plan(
        root: String,
        texts: Collection<String>,
        modules: Set<String>,
    ): Imports? {
        val errors = ErrorList()
        val names = texts.flatMap { Lexer(Source(it), errors).asSequence().map { token -> token.valueText }.toList() }.toSet()
        val aliases = XdkMemberActions.moduleAliases(modules, emptyMap(), names)

        fun nodes(node: AstNode): Sequence<AstNode> = sequenceOf(node) + node.childNodes().asSequence().flatMap(::nodes)
        val parsed = Parser(Source(root), errors).parseSource() ?: return null
        val module =
            nodes(parsed).filterIsInstance<TypeCompositionStatement>().singleOrNull { it.category.id == Token.Id.MODULE } ?: return null
        if (errors.hasSeriousErrors()) return null
        val start = module.ensureBody().startPosition
        val position = SemanticModel.Position(Source.calculateLine(start), Source.calculateOffset(start) + 1)
        val declarations = aliases.map { (name, alias) -> XdkMemberActions.Import(position, "    package $alias import $name;") }
        return Imports(aliases, XdkMemberActions.importEdits(root, declarations)?.singleOrNull() ?: return null)
    }
}
