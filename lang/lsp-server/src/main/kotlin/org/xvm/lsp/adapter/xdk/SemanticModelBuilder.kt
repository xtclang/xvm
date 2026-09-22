package org.xvm.lsp.adapter.xdk

import org.xvm.api.EmbeddingSupport
import org.xvm.asm.Argument
import org.xvm.asm.Constant
import org.xvm.asm.ConstantPool
import org.xvm.asm.MethodStructure
import org.xvm.asm.PropertyStructure
import org.xvm.asm.Register
import org.xvm.asm.constants.IdentityConstant
import org.xvm.asm.constants.MethodConstant
import org.xvm.asm.constants.PropertyConstant
import org.xvm.asm.constants.PseudoConstant
import org.xvm.asm.constants.TypeConstant
import org.xvm.asm.constants.TypeParameterConstant
import org.xvm.compiler.Source
import org.xvm.compiler.Token
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.ComponentStatement
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.InvocationExpression
import org.xvm.compiler.ast.LambdaExpression
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.NamedTypeExpression
import org.xvm.compiler.ast.NewExpression
import org.xvm.compiler.ast.Parameter
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypedefStatement
import org.xvm.compiler.ast.VariableDeclarationStatement
import org.xvm.lsp.adapter.xdk.SemanticModel.ExpressionType
import org.xvm.lsp.adapter.xdk.SemanticModel.Occurrence
import org.xvm.lsp.adapter.xdk.SemanticModel.Position
import org.xvm.lsp.adapter.xdk.SemanticModel.Range
import org.xvm.lsp.adapter.xdk.SemanticModel.Role
import org.xvm.lsp.adapter.xdk.SemanticModel.Signature
import org.xvm.lsp.adapter.xdk.SemanticModel.Status
import org.xvm.lsp.adapter.xdk.SemanticModel.Symbol
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolId
import org.xvm.lsp.adapter.xdk.SemanticModel.SymbolKind
import org.xvm.lsp.adapter.xdk.SemanticModel.Type
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeForm
import org.xvm.lsp.adapter.xdk.SemanticModel.TypeId
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.List.copyOf as immutableList

/**
 * Copy facts while the calling thread exclusively owns the compilation. This does not resume
 * validation or build TypeInfo. The result holds no compiler objects; each call creates a new
 * identity domain, so a host normally keeps one snapshot per document version.
 */
fun EmbeddingSupport.Compilation.semanticSnapshot(): SemanticModel {
    val builder = SemanticModelBuilder()
    val pool = pool() ?: return builder.build(this)
    return ConstantPool.withPool(pool).use { builder.build(this) }
}

/** Compiler-worker extraction. All mutable state dies with the builder. */
private class SemanticModelBuilder {
    private val id = UUID.randomUUID()
    private val captureOrigins = IdentityHashMap<Register, Register>()
    private val capturedProperties = mutableMapOf<PropertyConstant, Register>()
    private val registers = IdentityHashMap<Register, SymbolId>()
    private val constants = linkedMapOf<Constant, SymbolId>()
    private val symbols = linkedMapOf<SymbolId, Symbol>()
    private val typeIds = linkedMapOf<TypeConstant, TypeId>()
    private val types = linkedMapOf<TypeId, Type>()
    private val occurrences = linkedMapOf<Range, Occurrence>()
    private val expressions = linkedMapOf<Range, TypeId>()
    private val callees = IdentityHashMap<NameExpression, Argument>()

    fun build(compilation: EmbeddingSupport.Compilation): SemanticModel {
        val root =
            compilation.parsed()
                ?: return SemanticModel(id, Status.UNAVAILABLE, null, symbols, types, emptyList(), emptyList())
        val nodes = nodesIn(root)
        nodes.filterIsInstance<NewExpression>().forEach {
            capturedProperties.putAll(it.captureOrigins)
            it.sourceBindings?.let { bindings -> captureOrigins.putAll(bindings.captureOrigins) }
        }

        val bindings = nodes.filterIsInstance<LambdaExpression>().mapNotNull { it.sourceBindings }
        bindings.forEach { captureOrigins.putAll(it.captureOrigins) }
        bindings.forEach { lambda ->
            lambda.parameters.forEach { declare(it.name(), it.register(), SymbolKind.PARAMETER) }
        }
        // Parameters precede synthetic properties that share their source tokens.
        nodes.filterIsInstance<Parameter>().forEach {
            declare(
                it.nameToken,
                it.resolvedTarget,
                if (it.resolvedTarget is Register) SymbolKind.PARAMETER else kind(it.resolvedTarget),
            )
        }
        nodes.forEach { node ->
            when (node) {
                is VariableDeclarationStatement -> declare(node.nameToken, node.register, SymbolKind.VARIABLE)
                is MethodDeclarationStatement -> declare(node.nameToken, identity(node), SymbolKind.METHOD)
                is PropertyDeclarationStatement -> declare(node.nameToken, identity(node), SymbolKind.PROPERTY)
                is TypeCompositionStatement -> declare(node.nameToken, identity(node), kind(identity(node)))
                is TypedefStatement -> declare(node.nameToken, identity(node), SymbolKind.TYPE)
            }
            if (node is InvocationExpression) {
                val callee = node.invokedExpression
                val method = node.resolvedMethod
                if (callee is NameExpression && method != null) callees[callee] = method
            }
        }
        nodes.forEach { node ->
            val expressionType = (node as? Expression)?.takeIf { it.isValidated }?.type
            type(expressionType)?.let { expressions[range(node.startPosition, node.endPosition)] = it }
            when (node) {
                is NameExpression -> {
                    refer(node.nameToken, callees[node] ?: node.resolvedTarget, expressionType)
                }

                is NamedTypeExpression -> {
                    node.nameBindings.forEach {
                        refer(it.name(), it.target(), expressionType.takeIf { _ -> it.name() === node.nameToken })
                    }
                }
            }
        }
        return SemanticModel(
            id,
            if (compilation.succeeded()) Status.COMPLETE else Status.PARTIAL,
            root.source?.fileName,
            symbols,
            types,
            occurrences.values.sortedBy { it.range.start },
            expressions.map { (range, type) -> ExpressionType(range, type) },
        )
    }

    private fun declare(
        token: Token?,
        target: Argument?,
        kind: SymbolKind,
    ) {
        if (token == null) return
        val range = range(token.startPosition, token.endPosition)
        if (range in occurrences) return
        val symbol = symbol(target, token.valueText, kind, range)
        occurrences[range] = Occurrence(range, token.valueText, Role.DECLARATION, symbol, symbols[symbol]?.type)
    }

    private fun refer(
        token: Token?,
        target: Argument?,
        expressionType: TypeConstant?,
    ) {
        if (token == null) return
        val range = range(token.startPosition, token.endPosition)
        if (range in occurrences) return
        val symbol = symbol(target, token.valueText, kind(target))
        // Failed name validation can leave a required/placeholder type on the expression.
        val type = if (symbol == null) null else type(expressionType) ?: symbols[symbol]?.type
        occurrences[range] = Occurrence(range, token.valueText, Role.REFERENCE, symbol, type)
    }

    private fun symbol(
        argument: Argument?,
        name: String,
        kind: SymbolKind,
        declaration: Range? = null,
    ): SymbolId? {
        val target = normalized(argument) ?: return null
        val existing = if (target is Register) registers[target] else constants[target as Constant]
        if (existing != null) return existing
        val symbol = SymbolId(id, symbols.size)
        if (target is Register) registers[target] = symbol else constants[target as Constant] = symbol
        symbols[symbol] = Symbol(symbol, name, kind, declaration, type(declaredType(target)), signature(target))
        return symbol
    }

    private fun normalized(argument: Argument?): Argument? {
        if (argument is PropertyConstant && argument in capturedProperties) return normalized(capturedProperties[argument])
        if (argument is Register) {
            var register = argument.originalRegister
            val seen = Collections.newSetFromMap(IdentityHashMap<Register, Boolean>())
            while (seen.add(register)) {
                val origin = captureOrigins[register]?.originalRegister
                if (origin == null) {
                    val type = register.type
                    val formal = if (type.isTypeOfType && type.isParamsSpecified) type.getParamType(0) else null
                    if (formal != null && !formal.containsUnresolved() && formal.isSingleDefiningConstant) {
                        val parameter = formal.definingConstant
                        if (parameter is TypeParameterConstant && parameter.register == register.index) return parameter
                    }
                    return register
                }
                register = origin
            }
            return register
        }
        var target = argument
        if (target is TypeConstant && !target.containsUnresolved() && target.isSingleDefiningConstant) target = target.definingConstant
        if (target is Constant && target.containsUnresolved()) return null
        if (target is PseudoConstant) {
            target =
                when (target.format) {
                    Constant.Format.ThisClass, Constant.Format.ParentClass, Constant.Format.ChildClass -> target.declarationLevelClass
                    else -> return null
                }
        }
        return (target as? IdentityConstant)?.takeUnless { it.containsUnresolved() || (it is MethodConstant && it.isNascent) }
    }

    private fun identity(statement: ComponentStatement): IdentityConstant? = statement.component?.identityConstant

    private fun declaredType(target: Argument): TypeConstant? =
        when (target) {
            is Register -> target.type

            is PropertyConstant -> (target.component as? PropertyStructure)?.type

            // its declared callable signature carries parameter/result types
            is MethodConstant -> null

            is IdentityConstant -> if (target.format.isTypeable) target.type else null

            else -> null
        }

    private fun kind(target: Argument?): SymbolKind =
        when (target) {
            is Register -> {
                SymbolKind.VARIABLE
            }

            is Constant -> {
                when (target.format) {
                    Constant.Format.Module -> {
                        SymbolKind.MODULE
                    }

                    Constant.Format.Package -> {
                        SymbolKind.PACKAGE
                    }

                    Constant.Format.Method, Constant.Format.MultiMethod -> {
                        SymbolKind.METHOD
                    }

                    Constant.Format.Property -> {
                        if ((target as PropertyConstant).isFormalType) SymbolKind.TYPE_PARAMETER else SymbolKind.PROPERTY
                    }

                    Constant.Format.TypeParameter, Constant.Format.FormalTypeChild, Constant.Format.DynamicFormal -> {
                        SymbolKind.TYPE_PARAMETER
                    }

                    else -> {
                        SymbolKind.TYPE
                    }
                }
            }

            else -> {
                SymbolKind.VARIABLE
            }
        }

    private fun signature(target: Argument): Signature? {
        val method = ((target as? MethodConstant)?.component as? MethodStructure) ?: return null
        val parameters =
            method.paramArray.map {
                SemanticModel.Parameter(it.name, type(it.type) ?: return null, it.isTypeParameter, it.hasDefaultValue())
            }
        val returns = method.returnArray.map { type(it.type) ?: return null }
        return Signature(immutableList(parameters), immutableList(returns), method.isConditionalReturn)
    }

    private fun type(constant: TypeConstant?): TypeId? {
        if (constant == null || constant.containsUnresolved()) return null
        typeIds[constant]?.let { return it }
        val id = TypeId(id, typeIds.size)
        typeIds[constant] = id // intern before following recursive type relationships
        val arguments =
            if (constant.isParamsSpecified &&
                !constant.isRelationalType
            ) {
                constant.paramTypes.map { type(it)!! }
            } else {
                emptyList()
            }
        val underlying =
            when {
                constant.isRelationalType -> listOf(type(constant.underlyingType)!!, type(constant.underlyingType2)!!)
                constant.isModifyingType -> listOf(type(constant.underlyingType)!!)
                else -> emptyList()
            }
        val form =
            when (constant.format) {
                Constant.Format.TerminalType -> if (constant.isFormalType) TypeForm.FORMAL else TypeForm.NAMED
                Constant.Format.ParameterizedType -> TypeForm.PARAMETERIZED
                Constant.Format.ImmutableType -> TypeForm.IMMUTABLE
                Constant.Format.ServiceType -> TypeForm.SERVICE
                Constant.Format.AccessType -> TypeForm.ACCESS
                Constant.Format.AnnotatedType -> TypeForm.ANNOTATED
                Constant.Format.UnionType -> TypeForm.UNION
                Constant.Format.IntersectionType -> TypeForm.INTERSECTION
                Constant.Format.DifferenceType -> TypeForm.DIFFERENCE
                Constant.Format.RecursiveType -> TypeForm.RECURSIVE
                else -> TypeForm.OTHER
            }
        types[id] = Type(id, constant.valueString, form, immutableList(arguments), immutableList(underlying), constant.isNullable)
        return id
    }

    private fun nodesIn(root: AstNode): List<AstNode> {
        val nodes = mutableListOf(root)
        val seen = Collections.newSetFromMap(IdentityHashMap<AstNode, Boolean>())
        seen.add(root)
        var index = 0
        while (index < nodes.size) {
            nodes[index++].children().forEachRemaining { if (seen.add(it)) nodes.add(it) }
        }
        return nodes
    }

    private fun range(
        start: Long,
        end: Long,
    ): Range =
        Range(
            Position(Source.calculateLine(start), Source.calculateOffset(start)),
            Position(Source.calculateLine(end), Source.calculateOffset(end)),
        )
}
