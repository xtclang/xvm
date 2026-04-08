package org.xvm.lsp.model

/**
 * Immutable symbol information extracted from compiled XTC.
 */
data class SymbolInfo(
    val name: String,
    val qualifiedName: String,
    val kind: SymbolKind,
    val location: Location,
    val documentation: String? = null,
    val typeSignature: String? = null,
    val children: List<SymbolInfo> = emptyList(),
) {
    enum class SymbolKind {
        MODULE,
        PACKAGE,
        CLASS,
        INTERFACE,
        ENUM,
        MIXIN,
        SERVICE,
        CONST,
        METHOD,
        PROPERTY,
        PARAMETER,
        TYPE_PARAMETER,
        CONSTRUCTOR,
        ;

        companion object
    }

    companion object {
        fun of(
            name: String,
            kind: SymbolKind,
            location: Location,
        ): SymbolInfo = SymbolInfo(name, name, kind, location)
    }

    fun withChildren(newChildren: List<SymbolInfo>): SymbolInfo = copy(children = newChildren)

    fun withDocumentation(doc: String?): SymbolInfo = copy(documentation = doc)

    fun withTypeSignature(sig: String?): SymbolInfo = copy(typeSignature = sig)
}
