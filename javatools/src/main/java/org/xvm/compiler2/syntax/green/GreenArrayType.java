package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * An array type: Int[]
 */
public final class GreenArrayType extends GreenType {

    private final GreenType elementType;
    private final GreenToken openBracket;
    private final GreenToken closeBracket;

    private GreenArrayType(GreenType elementType, GreenToken openBracket, GreenToken closeBracket) {
        super(SyntaxKind.ARRAY_TYPE,
                elementType.getFullWidth() + openBracket.getFullWidth() + closeBracket.getFullWidth());
        this.elementType = elementType;
        this.openBracket = openBracket;
        this.closeBracket = closeBracket;
    }

    public static GreenArrayType create(GreenType elementType, GreenToken openBracket,
                                        GreenToken closeBracket) {
        return intern(new GreenArrayType(elementType, openBracket, closeBracket));
    }

    public static GreenArrayType create(GreenType elementType) {
        return create(elementType,
                GreenToken.create(SyntaxKind.LBRACKET, "["),
                GreenToken.create(SyntaxKind.RBRACKET, "]"));
    }

    public GreenType getElementType() {
        return elementType;
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> elementType;
            case 1 -> openBracket;
            case 2 -> closeBracket;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == elementType ? this : create((GreenType) child, openBracket, closeBracket);
            case 1 -> child == openBracket ? this : create(elementType, (GreenToken) child, closeBracket);
            case 2 -> child == closeBracket ? this : create(elementType, openBracket, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "ArrayType[" + elementType + "[]]";
    }
}
