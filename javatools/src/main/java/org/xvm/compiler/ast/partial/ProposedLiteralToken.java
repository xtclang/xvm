package org.xvm.compiler.ast.partial;

import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

/**
 * A disposable argument proposal has an insertion position but no characters in the original
 * source. Literal conversion still needs its exact spelling (not the empty cursor range).
 * These tokens are never installed in the source tree or published as source bindings.
 */
public final class ProposedLiteralToken extends Token {
    private final String spelling;

    public ProposedLiteralToken(long cursor, Id id, Object value, String spelling) {
        super(cursor, cursor, id, value);
        this.spelling = spelling;
    }

    @Override
    public String getString(Source source) {
        return spelling;
    }
}
