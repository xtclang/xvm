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
) {
    enum class Status { UNAVAILABLE, PARTIAL, COMPLETE }

    enum class SymbolKind { MODULE, PACKAGE, TYPE, METHOD, PROPERTY, VARIABLE, PARAMETER, TYPE_PARAMETER }

    enum class Role { DECLARATION, REFERENCE }

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
    )

    /** A written name; a null symbol explicitly represents an unresolved occurrence. */
    data class Occurrence(
        val range: Range,
        val name: String,
        val role: Role,
        val symbol: SymbolId?,
        val type: TypeId?,
    )

    data class ExpressionType(
        val range: Range,
        val type: TypeId,
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
    val typeDeclarations: Map<SymbolId, TypeDeclaration> = facts.typeDeclarations

    /** One immutable symbol/type table shared by every source view of the compilation. */
    internal class Facts(
        symbols: Map<SymbolId, Symbol>,
        types: Map<TypeId, Type>,
        typeDeclarations: Map<SymbolId, TypeDeclaration> = emptyMap(),
    ) {
        val symbolsById = immutableMap(symbols)
        val typesById = immutableMap(types)
        val symbols = immutableList(symbols.values)
        val types = immutableList(types.values)
        val typeDeclarations = immutableMap(typeDeclarations)
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

    private companion object {
        val SOURCE_ORDER = compareBy(Range::start, Range::end)
        val INNERMOST = compareByDescending(Range::start).thenBy(Range::end)
    }
}
