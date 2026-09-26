package org.xvm.lsp.adapter.xdk

import org.xvm.asm.Constant
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.asm.constants.ClassConstant
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.ModuleConstant
import org.xvm.asm.constants.PackageConstant
import org.xvm.asm.constants.TypedefConstant
import org.xvm.compiler.CompilerException
import org.xvm.compiler.Lexer
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.TypeCompositionStatement

/** Detached import candidates; only a successful whole-graph repair proof can publish an edit. */
internal object XdkAutoImports {
    data class Target(
        val name: String,
        val module: String,
        val path: String,
    )

    private val bundled by lazy {
        XdkLibraries.moduleNames
            .flatMap { module ->
                XdkLibraries
                    .module(module)
                    .constantPool.constants
                    .mapNotNull(::target)
                    .filter { it.module == module }
            }.distinct()
            .groupBy { it.name }
    }

    fun targets(
        name: String,
        facts: CompilerRenameFacts,
    ): List<Target> =
        (
            facts.constants.values
                .mapNotNull(::target)
                .filter { it.name == name } + bundled[name].orEmpty()
        ).distinct()
            .sortedWith(compareBy(Target::module, Target::path))

    private fun target(constant: Constant): Target? {
        if (constant !is ClassConstant && constant !is TypedefConstant) return null
        val identity = constant as IdentityConstant
        if (identity.path.any {
                (it !is ModuleConstant && it !is PackageConstant && it !is ClassConstant && it !is TypedefConstant) ||
                    it.component?.access != Access.PUBLIC
            }
        ) {
            return null
        }
        return Target(identity.name, identity.moduleConstant.name, identity.pathString)
    }

    fun edit(
        text: String,
        owner: String,
        target: Target,
    ): XdkRename.Edit? {
        val errors = ErrorList()
        val root =
            try {
                Parser(Source(text), errors).parseSource()
            } catch (_: CompilerException) {
                return null
            }
        if (errors.hasSeriousErrors()) return null
        val tokens = Lexer(Source(text), errors).asSequence().toList()
        if (errors.hasSeriousErrors()) return null
        val type = root.childNodes().filterIsInstance<TypeCompositionStatement>().singleOrNull() ?: return null
        val newline = if ("\r\n" in text) "\r\n" else "\n"
        val insertion =
            if (type.category.id == Token.Id.MODULE) {
                val position = type.ensureBody().startPosition
                val at =
                    XdkRename.offset(text, SemanticModel.Position(Source.calculateLine(position), Source.calculateOffset(position)))
                        ?: return null
                if (text.getOrNull(at) != '{') return null
                at + 1
            } else {
                // A member file may declare imports before its class; the compiler owns their scope.
                0
            }
        val prefix: String
        val declaration: String
        when (target.module) {
            owner -> {
                prefix = ""
                declaration = ""
            }

            "ecstasy.xtclang.org" -> {
                prefix = "ecstasy."
                declaration = ""
            }

            else -> {
                val names = tokens.mapTo(hashSetOf()) { it.valueText }
                val base = target.module.substringBefore('.').replaceFirstChar { it.lowercase() }
                val alias =
                    generateSequence(0) { it + 1 }
                        .map { if (it == 0) base else "$base$it" }
                        .first { it !in names && XdkRename.identifier(it) }
                prefix = "$alias."
                declaration = "package $alias import ${target.module};$newline"
            }
        }
        val imports = declaration + "import $prefix${target.path};$newline"
        return XdkRename.Edit(insertion, insertion, if (insertion == 0) imports else "$newline$imports")
    }
}
