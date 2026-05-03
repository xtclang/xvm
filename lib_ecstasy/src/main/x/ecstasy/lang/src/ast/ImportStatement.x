import io.TextPosition;

import Lexer.Token;


/**
 * Represents a child of a NamedTypeExpression, for example:
 *
 *     ecstasy.collections.List<String>.Cursor
 */
const ImportStatement(Token   keyword,
                      Token[] names,
                      Token?  alias)
        extends Statement {
    @Override
    TextPosition start.get() {
        return keyword.start;
    }

    @Override
    TextPosition end.get() {
        return (alias ?: names[names.size-1]).end;
    }

    /**
     * The alias name for the import.
     */
    String aliasName.get() {
        return (alias ?: names[names.size-1]).valueText;
    }

    /**
     * The qualified name for the import.
     */
    @Lazy String qualifiedName.calc() {
        return toDotDelimString(names);
    }

    @Override
    String toString() {
        val alias = this.alias;
        if (alias?.valueText != names[names.size - 1].valueText) {
            return $"import {qualifiedName} as {alias.valueText};";
        }
        return $"import {qualifiedName};";
    }
}
