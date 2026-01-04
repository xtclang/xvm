package org.xvm.compiler2.syntax.green;

import java.util.Objects;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A terminal (token) node in the green tree.
 * <p>
 * Tokens are leaf nodes that contain actual text from the source.
 * They may also carry a computed value for literals (numbers, strings, etc.).
 */
public final class GreenToken extends GreenNode {

    /**
     * The source text of this token.
     */
    private final String text;

    /**
     * Leading trivia (whitespace, comments before this token).
     */
    private final String leadingTrivia;

    /**
     * Trailing trivia (whitespace, comments after this token).
     */
    private final String trailingTrivia;

    /**
     * The computed value for literals (Long, Double, String, etc.), or null.
     */
    private final Object value;

    /**
     * Private constructor - use factory methods.
     */
    private GreenToken(SyntaxKind kind, String text, String leadingTrivia,
                       String trailingTrivia, Object value) {
        super(kind, computeWidth(text, leadingTrivia, trailingTrivia));
        this.text = text;
        this.leadingTrivia = leadingTrivia;
        this.trailingTrivia = trailingTrivia;
        this.value = value;
    }

    private static int computeWidth(String text, String leadingTrivia, String trailingTrivia) {
        int width = text != null ? text.length() : 0;
        if (leadingTrivia != null) {
            width += leadingTrivia.length();
        }
        if (trailingTrivia != null) {
            width += trailingTrivia.length();
        }
        return width;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Create a token with no trivia.
     *
     * @param kind the syntax kind
     * @param text the token text
     * @return the interned token
     */
    public static GreenToken create(SyntaxKind kind, String text) {
        return intern(new GreenToken(kind, text, "", "", null));
    }

    /**
     * Create a token with trivia.
     *
     * @param kind           the syntax kind
     * @param text           the token text
     * @param leadingTrivia  whitespace/comments before the token
     * @param trailingTrivia whitespace/comments after the token
     * @return the interned token
     */
    public static GreenToken create(SyntaxKind kind, String text,
                                    String leadingTrivia, String trailingTrivia) {
        return intern(new GreenToken(kind, text, leadingTrivia, trailingTrivia, null));
    }

    /**
     * Create an identifier token.
     *
     * @param name the identifier name
     * @return the interned token
     */
    public static GreenToken identifier(String name) {
        return intern(new GreenToken(SyntaxKind.IDENTIFIER, name, "", "", null));
    }

    /**
     * Create an identifier token with trivia.
     *
     * @param name           the identifier name
     * @param leadingTrivia  whitespace/comments before
     * @param trailingTrivia whitespace/comments after
     * @return the interned token
     */
    public static GreenToken identifier(String name, String leadingTrivia, String trailingTrivia) {
        return intern(new GreenToken(SyntaxKind.IDENTIFIER, name, leadingTrivia, trailingTrivia, null));
    }

    /**
     * Create an integer literal token.
     *
     * @param value the integer value
     * @return the interned token
     */
    public static GreenToken intLiteral(long value) {
        return intern(new GreenToken(SyntaxKind.INT_LITERAL, String.valueOf(value), "", "", value));
    }

    /**
     * Create an integer literal token with trivia.
     *
     * @param value          the integer value
     * @param leadingTrivia  whitespace/comments before
     * @param trailingTrivia whitespace/comments after
     * @return the interned token
     */
    public static GreenToken intLiteral(long value, String leadingTrivia, String trailingTrivia) {
        return intern(new GreenToken(SyntaxKind.INT_LITERAL, String.valueOf(value),
                leadingTrivia, trailingTrivia, value));
    }

    /**
     * Create a floating-point literal token.
     *
     * @param value the double value
     * @return the interned token
     */
    public static GreenToken fpLiteral(double value) {
        return intern(new GreenToken(SyntaxKind.FP_LITERAL, String.valueOf(value), "", "", value));
    }

    /**
     * Create a string literal token.
     *
     * @param text  the literal text including quotes
     * @param value the string value (without quotes)
     * @return the interned token
     */
    public static GreenToken stringLiteral(String text, String value) {
        return intern(new GreenToken(SyntaxKind.STRING_LITERAL, text, "", "", value));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * @return the token text (not including trivia)
     */
    public String getText() {
        return text;
    }

    /**
     * @return leading trivia, or empty string
     */
    public String getLeadingTrivia() {
        return leadingTrivia;
    }

    /**
     * @return trailing trivia, or empty string
     */
    public String getTrailingTrivia() {
        return trailingTrivia;
    }

    /**
     * @return the computed value for literals, or null
     */
    public Object getValue() {
        return value;
    }

    /**
     * @return the value as a Long, or null
     */
    public Long getIntValue() {
        return value instanceof Long l ? l : null;
    }

    /**
     * @return the value as a Double, or null
     */
    public Double getFpValue() {
        return value instanceof Double d ? d : null;
    }

    /**
     * @return the value as a String, or null
     */
    public String getStringValue() {
        return value instanceof String s ? s : null;
    }

    /**
     * @return the width of just the token text (excluding trivia)
     */
    public int getTextWidth() {
        return text != null ? text.length() : 0;
    }

    // -------------------------------------------------------------------------
    // GreenNode overrides
    // -------------------------------------------------------------------------

    @Override
    public boolean isToken() {
        return true;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public GreenNode getChild(int index) {
        throw new IndexOutOfBoundsException("Tokens have no children: index=" + index);
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        throw new IndexOutOfBoundsException("Tokens have no children: index=" + index);
    }

    @Override
    public <R> R accept(GreenVisitor<R> visitor) {
        return visitor.visitToken(this);
    }

    @Override
    protected int computeHash() {
        int h = getKind().hashCode();
        h = 31 * h + Objects.hashCode(text);
        h = 31 * h + Objects.hashCode(leadingTrivia);
        h = 31 * h + Objects.hashCode(trailingTrivia);
        h = 31 * h + Objects.hashCode(value);
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GreenToken other)) {
            return false;
        }
        return getKind() == other.getKind()
                && Objects.equals(text, other.text)
                && Objects.equals(leadingTrivia, other.leadingTrivia)
                && Objects.equals(trailingTrivia, other.trailingTrivia)
                && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        return "Token[" + getKind() + ", \"" + text + "\"]";
    }
}
