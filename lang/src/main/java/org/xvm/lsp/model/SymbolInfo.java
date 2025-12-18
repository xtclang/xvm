package org.xvm.lsp.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable symbol information extracted from compiled XTC.
 */
public record SymbolInfo(
        @NonNull String name,
        @NonNull String qualifiedName,
        @NonNull SymbolKind kind,
        @NonNull Location location,
        @Nullable String documentation,
        @Nullable String typeSignature,
        @NonNull List<SymbolInfo> children
) {
    public enum SymbolKind {
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
        CONSTRUCTOR
    }

    public SymbolInfo {
        children = List.copyOf(children);
    }

    public static SymbolInfo of(
            final @NonNull String name,
            final @NonNull SymbolKind kind,
            final @NonNull Location location) {
        return new SymbolInfo(name, name, kind, location, null, null, List.of());
    }

    public SymbolInfo withChildren(final @NonNull List<SymbolInfo> newChildren) {
        return new SymbolInfo(name, qualifiedName, kind, location, documentation, typeSignature, newChildren);
    }

    public SymbolInfo withDocumentation(final @Nullable String doc) {
        return new SymbolInfo(name, qualifiedName, kind, location, doc, typeSignature, children);
    }

    public SymbolInfo withTypeSignature(final @Nullable String sig) {
        return new SymbolInfo(name, qualifiedName, kind, location, documentation, sig, children);
    }
}
