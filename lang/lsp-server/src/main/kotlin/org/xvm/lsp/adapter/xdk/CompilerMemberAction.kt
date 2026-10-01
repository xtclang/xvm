package org.xvm.lsp.adapter.xdk

import org.xvm.asm.ClassStructure
import org.xvm.asm.Component.Format
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.compiler.Lexer
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.LiteralExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.UnaryMinusExpression
import org.xvm.lsp.util.ExecutionTrace

/** Worker-only candidates; compiler constants are detached before the compilation is released. */
internal data class CompilerMemberAction(
    val owner: IdentityConstant,
    val contract: MethodConstant,
    val insertion: SemanticModel.Position,
    val declaration: String,
    val implementation: Boolean,
    val requiredCount: Int,
    val imports: List<XdkMemberActions.Import>,
)

/**
 * Inherited methods from source and immutable dependency artifacts. A proposed declaration still
 * needs a complete graph proof.
 */
internal fun compilerMemberActions(
    nodes: List<AstNode>,
    errors: ErrorListener,
): List<CompilerMemberAction> {
    val literalDefaults =
        nodes
            .filterIsInstance<MethodDeclarationStatement>()
            .mapNotNull { method ->
                val identity =
                    (method.component as? MethodStructure)?.identityConstant
                        ?: return@mapNotNull null
                identity to
                    method
                        .childNodes()
                        .filterIsInstance<Parameter>()
                        .mapNotNull { parameter ->
                            val value = parameter.value
                            val token =
                                when (value) {
                                    is LiteralExpression -> {
                                        value.literal.takeIf {
                                            it.id in
                                                setOf(
                                                    Token.Id.LIT_STRING,
                                                    Token.Id.LIT_CHAR,
                                                    Token.Id.LIT_INT,
                                                )
                                        }
                                    }

                                    is UnaryMinusExpression -> {
                                        (value.childNodes().singleOrNull() as? LiteralExpression)
                                            ?.literal
                                            ?.takeIf {
                                                it.id == Token.Id.LIT_INT
                                            }
                                    }

                                    else -> {
                                        null
                                    }
                                } ?: return@mapNotNull null
                            parameter.name to
                                if (value is UnaryMinusExpression) "-$token" else token.toString()
                        }.toMap()
            }.toMap()
    return nodes.filterIsInstance<TypeCompositionStatement>().flatMap { node ->
        val structure = node.component as? ClassStructure ?: return@flatMap emptyList()
        if (structure.format != Format.CLASS || structure.isSynthetic || errors.isAbortDesired) {
            return@flatMap emptyList()
        }
        val info =
            ExecutionTrace.api("TypeConstant.ensureTypeInfo(member-actions)") {
                structure.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
            }
        if (errors.hasSeriousErrors() || errors.isAbortDesired) return@flatMap emptyList()
        val end = node.ensureBody().endPosition
        val insertion =
            SemanticModel.Position(Source.calculateLine(end), Source.calculateOffset(end) - 1)
        val methods =
            info.methods.values.filter {
                it.identity.isTopLevel && it.isVirtual && !it.isCtorOrValidator
            }
        val external =
            methods
                .flatMap {
                    (it.signature.params + it.signature.returns).flatMap { type ->
                        type.memberClasses()
                    }
                }.filter { it.moduleConstant != structure.identityConstant.moduleConstant }
        val lexicalErrors = ErrorList()
        val names =
            ExecutionTrace.api("Lexer.lex(member-imports)") {
                Lexer(node.source.clone(), lexicalErrors).asSequence().map { it.valueText }.toSet()
            }
        if (lexicalErrors.hasSeriousErrors()) return@flatMap emptyList()
        val aliases =
            external
                .map { it.moduleConstant.name }
                .distinct()
                .sorted()
                .fold(mapOf("ecstasy.xtclang.org" to "ecstasy")) { known, module ->
                    if (module in known) {
                        known
                    } else {
                        val base = module.substringBefore('.').replaceFirstChar { it.lowercase() }
                        val alias =
                            generateSequence(0) { it + 1 }
                                .map { if (it == 0) base else "$base$it" }
                                .first {
                                    it !in names && it !in known.values && XdkRename.identifier(it)
                                }
                        known + (module to alias)
                    }
                }
        val moduleNode =
            generateSequence(node as AstNode) { it.parent }
                .filterIsInstance<TypeCompositionStatement>()
                .lastOrNull { it.category.id == Token.Id.MODULE && it.source === node.source }
        val importAt =
            moduleNode?.ensureBody()?.startPosition?.let {
                SemanticModel.Position(Source.calculateLine(it), Source.calculateOffset(it) + 1)
            } ?: SemanticModel.Position(0, 0)
        methods
            .mapNotNull { method ->
                val declaration = method.getTopmostMethodStructure(info)
                if (
                    declaration.containingClass == structure ||
                    declaration.isSynthetic ||
                    declaration.isNative ||
                    method.isOp ||
                    method.isAuto ||
                    !info.dispatch(method, errors).supported
                ) {
                    return@mapNotNull null
                }
                val signature =
                    memberSignature(
                        method.signature,
                        declaration,
                        structure.identityConstant,
                        aliases,
                        literalDefaults[declaration.identityConstant].orEmpty(),
                    ) ?: return@mapNotNull null
                val access = if (method.access == Access.PROTECTED) "protected " else ""
                CompilerMemberAction(
                    structure.identityConstant,
                    declaration.identityConstant,
                    insertion,
                    "$access$signature",
                    method.isAbstract,
                    info.methods.values.count { it.isAbstract && !it.isCtorOrValidator },
                    (method.signature.params + method.signature.returns)
                        .flatMap { it.memberClasses() }
                        .filter { it.constantPool.getImplicitlyImportedIdentity(it.name) != it }
                        .map { it.moduleConstant.name }
                        .distinct()
                        .filter {
                            it != "ecstasy.xtclang.org" &&
                                it != structure.identityConstant.moduleConstant.name
                        }.map {
                            XdkMemberActions.Import(
                                importAt,
                                "package ${aliases.getValue(it)} import $it;",
                            )
                        },
                )
            }.distinct()
    }
}
