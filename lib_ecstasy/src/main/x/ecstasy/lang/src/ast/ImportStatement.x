import io.TextPosition;

import Lexer.Token;


/**
 * Represents a child of a NamedTypeExpression, for example:
 *
 *     ecstasy.collections.HashMap<String?, IntLiteral>.Entry
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
        StringBuffer buf = new StringBuffer();

        "import ".appendTo(buf);

        Loop: for (Token token : names) {
            if (!Loop.first) {
                buf.add('.');
            }
            token.valueText.appendTo(buf);
        }

        Token? alias = this.alias;
        if (alias?.valueText != names[names.size-1].valueText) {
            " as ".appendTo(buf);
            alias.valueText.appendTo(buf);
        }

        buf.add(';');

        return buf.toString();
    }
}
