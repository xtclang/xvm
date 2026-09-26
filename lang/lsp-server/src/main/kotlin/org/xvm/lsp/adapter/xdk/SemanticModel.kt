package org.xvm.lsp.adapter.xdk

import java.util.UUID
import java.util.List.copyOf as immutableList
import java.util.Map.copyOf as immutableMap

/**
 * Immutable semantic facts copied from one compilation, with no compiler or LSP protocol objects.
 * Queries can run concurrently without an ambient constant pool or error listener. IDs belong to
 * this compilation snapshot; document views from one extraction share IDs. The adapter associates
 * them with source versions. Missing facts are null, never inferred from spelling.
 */
class SemanticModel internal constructor(
    val id: UUID,
    val status: Status,
    val sourceName: String?,
    private val facts: Facts,
    occurrences: List<Occurrence>,
    expressions: List<ExpressionType>,
    calls: List<CallSite> = emptyList(),
    functionCalls: List<FunctionCallSite> = emptyList(),
) {
    enum class Status { UNAVAILABLE, PARTIAL, COMPLETE }

    enum class SymbolKind { MODULE, PACKAGE, TYPE, METHOD, PROPERTY, VARIABLE, PARAMETER, TYPE_PARAMETER }

    enum class Role { DECLARATION, REFERENCE }

    enum class Usage { READ, WRITE, READ_WRITE }

    enum class Modifier { READONLY, STATIC, ABSTRACT }

    enum class TypeForm {
        NAMED,
        FORMAL,
        PARAMETERIZED,
        IMMUTABLE,
        SERVICE,
        ACCESS,
        ANNOTATED,
        UNION,
        INTERSECTION,
        DIFFERENCE,
        RECURSIVE,
        OTHER,
    }

    /** Zero-based line and UTF-16 column. */
    data class Position(
        val line: Int,
        val column: Int,
    ) : Comparable<Position> {
        init {
            require(line >= 0 && column >= 0) { "Negative source position" }
        }

        override fun compareTo(other: Position): Int = compareValuesBy(this, other, Position::line, Position::column)
    }

    /** Half-open range within [sourceName]. */
    data class Range(
        val start: Position,
        val end: Position,
    ) {
        init {
            require(start <= end) { "Reversed source range" }
        }

        operator fun contains(position: Position): Boolean = start <= position && position < end
    }

    data class SymbolId(
        val snapshot: UUID,
        val index: Int,
    )

    data class TypeId(
        val snapshot: UUID,
        val index: Int,
    )

    /** A declaration location in the source tree; the name follows the compiler's Source identity. */
    data class SourceLocation(
        val sourceName: String?,
        val range: Range,
    )

    /**
     * Arguments are generic arguments; underlying types are a modifier's base or a relational
     * type's ordered operands. Display text is for presentation, not identity or assignability.
     */
    @ConsistentCopyVisibility
    data class Type internal constructor(
        val id: TypeId,
        val displayName: String,
        val form: TypeForm,
        val arguments: List<TypeId>,
        val underlying: List<TypeId>,
        val nullable: Boolean,
    )

    data class Parameter(
        val name: String?,
        val type: TypeId,
        val typeParameter: Boolean,
        val defaulted: Boolean,
    )

    /** Declared signature, including formal type parameters and conditional returns. */
    @ConsistentCopyVisibility
    data class Signature internal constructor(
        val parameters: List<Parameter>,
        val returns: List<TypeId>,
        val conditional: Boolean,
    )

    data class Symbol(
        val id: SymbolId,
        val name: String,
        val kind: SymbolKind,
        val declaration: Range?,
        val type: TypeId?,
        val signature: Signature?,
        val declarationSource: String?,
        val modifiers: Set<Modifier> = emptySet(),
        val inferred: Boolean = false,
        val dependency: XdkDependency.SymbolKey? = null,
        val renameable: Boolean = false,
    )

    /** A written name; a null symbol explicitly represents an unresolved occurrence. */
    data class Occurrence(
        val range: Range,
        val name: String,
        val role: Role,
        val symbol: SymbolId?,
        val type: TypeId?,
        val usage: Usage? = null,
    )

    data class ExpressionType(
        val range: Range,
        val type: TypeId,
    )

    data class CallArgument(
        val range: Range,
        val parameterIndex: Int,
        val named: Boolean = false,
    )

    /** Selected signature after inference; parameter indices exclude hidden type parameters. */
    @ConsistentCopyVisibility
    data class CallSite internal constructor(
        val range: Range,
        val callee: Range,
        val method: SymbolId,
        val signature: Signature,
        val arguments: List<CallArgument>,
        val caller: SymbolId? = null,
    )

    /** Validated function signature without an invented runtime target or parameter names. */
    @ConsistentCopyVisibility
    data class FunctionCallSite internal constructor(
        val range: Range,
        val callee: Range,
        val signature: Signature,
        val arguments: List<CallArgument>,
    )

    /** Source callable boundaries, including lambdas whose compiler methods have synthetic names. */
    data class Callable(
        val symbol: SymbolId,
        val location: SourceLocation,
        val selection: Range,
    )

    data class Supertype(
        val symbol: SymbolId,
        val type: TypeId,
    )

    /** Direct declared extends/implements relationships, copied only from a successful compilation. */
    data class TypeDeclaration(
        val symbol: SymbolId,
        val category: String,
        val location: SourceLocation,
        val parents: List<Supertype>,
    )

    val symbols: List<Symbol> = facts.symbols
    val types: List<Type> = facts.types
    val occurrences: List<Occurrence> = immutableList(occurrences)
    val expressions: List<ExpressionType> = immutableList(expressions)
    val calls: List<CallSite> = immutableList(calls)
    val functionCalls: List<FunctionCallSite> = immutableList(functionCalls)
    val typeDeclarations: Map<SymbolId, TypeDeclaration> = facts.typeDeclarations
    val callables: Map<SymbolId, Callable> = facts.callables

    /** One immutable symbol/type table shared by every source view of the compilation. */
    internal class Facts(
        symbols: Map<SymbolId, Symbol>,
        types: Map<TypeId, Type>,
        typeDeclarations: Map<SymbolId, TypeDeclaration> = emptyMap(),
        typeDefinitions: Map<TypeId, List<SymbolId>> = emptyMap(),
        implementations: Map<SymbolId, List<SymbolId>> = emptyMap(),
        callables: Map<SymbolId, Callable> = emptyMap(),
    ) {
        val symbolsById = immutableMap(symbols)
        val typesById = immutableMap(types)
        val symbols = immutableList(symbols.values)
        val types = immutableList(types.values)
        val typeDeclarations = immutableMap(typeDeclarations)
        val typeDefinitions = immutableMap(typeDefinitions.mapValues { immutableList(it.value) })
        val implementations = immutableMap(implementations.mapValues { immutableList(it.value) })
        val callables = immutableMap(callables)
    }

    /** IDs from another snapshot return no result. */
    fun symbol(id: SymbolId): Symbol? = facts.symbolsById[id]

    /** IDs from another snapshot return no result. */
    fun type(id: TypeId): Type? = facts.typesById[id]

    fun occurrenceAt(
        line: Int,
        column: Int,
    ): Occurrence? {
        val position = Position(line, column)
        return occurrences.filter { position in it.range }.minWithOrNull(compareBy(INNERMOST) { it.range })
    }

    fun symbolAt(
        line: Int,
        column: Int,
    ): Symbol? = occurrenceAt(line, column)?.symbol?.let(::symbol)

    /** An unresolved written name must not borrow an enclosing call's type. */
    fun typeAt(
        line: Int,
        column: Int,
    ): Type? {
        occurrenceAt(line, column)?.let { return it.type?.let(::type) }
        val position = Position(line, column)
        return expressions
            .filter { position in it.range }
            .minWithOrNull(compareBy(INNERMOST) { it.range })
            ?.type
            ?.let(::type)
    }

    fun definitionAt(
        line: Int,
        column: Int,
    ): Range? = definitionLocationAt(line, column)?.takeIf { it.sourceName == sourceName }?.range

    /** Resolve a declaration in any source of this compilation, retaining its source identity. */
    fun definitionLocationAt(
        line: Int,
        column: Int,
    ): SourceLocation? = symbolAt(line, column)?.let { symbol -> symbol.declaration?.let { SourceLocation(symbol.declarationSource, it) } }

    /** Nominal types, flow-narrowed values and selected call results; never follow generic arguments. */
    fun typeDefinitionLocationsAt(
        line: Int,
        column: Int,
    ): List<SourceLocation> {
        val symbol = symbolAt(line, column)
        symbol?.takeIf { it.kind == SymbolKind.TYPE || it.kind == SymbolKind.TYPE_PARAMETER }?.let { return locations(listOf(it.id)) }
        val position = Position(line, column)
        val resultTypes =
            if (symbol?.kind == SymbolKind.METHOD) {
                val signature =
                    calls.firstOrNull { position in it.callee }?.signature
                        ?: symbol.signature.takeIf { occurrenceAt(line, column)?.role == Role.DECLARATION }
                signature?.returns.orEmpty()
            } else {
                listOfNotNull(typeAt(line, column)?.id)
            }
        return locations(resultTypes.flatMap { facts.typeDefinitions[it].orEmpty() })
    }

    /** Worker inspection copies type/member implementations, including property fields and accessors. */
    fun implementationLocationsAt(
        line: Int,
        column: Int,
    ): List<SourceLocation> = locations(facts.implementations[symbolAt(line, column)?.id].orEmpty())

    private fun locations(ids: List<SymbolId>): List<SourceLocation> =
        ids
            .mapNotNull { id -> symbol(id)?.let { symbol -> symbol.declaration?.let { SourceLocation(symbol.declarationSource, it) } } }
            .distinct()

    fun referencesAt(
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Range> {
        val symbol = symbolAt(line, column) ?: return emptyList()
        return occurrences
            .asSequence()
            .filter { it.symbol == symbol.id && (includeDeclaration || it.role != Role.DECLARATION) }
            .map { it.range }
            .distinct()
            .sortedWith(SOURCE_ORDER)
            .toList()
    }

    internal companion object {
        /** Merge detached tables after the compiler worker has proven cross-module identity aliases. */
        fun joined(
            models: List<SemanticModel>,
            aliases: Map<SymbolId, SymbolId>,
        ): List<SemanticModel> {
            fun canonical(id: SymbolId): SymbolId = aliases[id] ?: id
            val tables = models.distinctBy { it.id }.map { it.facts }
            val symbols = tables.flatMap { it.symbols }.associateBy { it.id }
            val facts =
                Facts(
                    symbols.values.groupBy { canonical(it.id) }.mapValues { (id, candidates) ->
                        symbols[id] ?: candidates.first().copy(id = id)
                    },
                    tables.flatMap { it.types }.associateBy { it.id },
                    tables.flatMap { it.typeDeclarations.values }.associate { declaration ->
                        val id = canonical(declaration.symbol)
                        id to declaration.copy(symbol = id, parents = declaration.parents.map { it.copy(symbol = canonical(it.symbol)) })
                    },
                    tables.flatMap { it.typeDefinitions.entries }.associate { (type, ids) -> type to ids.map(::canonical).distinct() },
                    tables
                        .flatMap { it.implementations.entries }
                        .groupBy({ canonical(it.key) }, { it.value })
                        .mapValues { (_, groups) -> groups.flatten().map(::canonical).distinct() },
                    tables.flatMap { it.callables.values }.associate { callable ->
                        val id = canonical(callable.symbol)
                        id to callable.copy(symbol = id)
                    },
                )
            return models.map { model ->
                SemanticModel(
                    model.id,
                    model.status,
                    model.sourceName,
                    facts,
                    model.occurrences.map { it.copy(symbol = it.symbol?.let(::canonical)) },
                    model.expressions,
                    model.calls.map { it.copy(method = canonical(it.method), caller = it.caller?.let(::canonical)) },
                    model.functionCalls,
                )
            }
        }

        private val SOURCE_ORDER = compareBy(Range::start, Range::end)
        private val INNERMOST = compareByDescending(Range::start).thenBy(Range::end)
    }
}
