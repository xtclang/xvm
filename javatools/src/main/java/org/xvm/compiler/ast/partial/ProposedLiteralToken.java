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

    /**
     * Create a zero-width trial literal with an explicit source spelling.
     *
     * @param cursor    the selected position in the owning Source snapshot
     * @param id        the token kind for the proposed literal
     * @param value     the proposed literal's parsed value
     * @param spelling  the source spelling to use for literal conversion
     */
    public ProposedLiteralToken(long cursor, Id id, Object value, String spelling) {
        super(cursor, cursor, id, value);
        this.spelling = spelling;
    }

    @Override
    public String getString(Source source) {
        return spelling;
    }
}
