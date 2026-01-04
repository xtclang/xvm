package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A nullable type: Int?
 */
public final class GreenNullableType extends GreenType {

    private final GreenType baseType;
    private final GreenToken questionMark;

    private GreenNullableType(GreenType baseType, GreenToken questionMark) {
        super(SyntaxKind.NULLABLE_TYPE,
                baseType.getFullWidth() + questionMark.getFullWidth());
        this.baseType = baseType;
        this.questionMark = questionMark;
    }

    public static GreenNullableType create(GreenType baseType, GreenToken questionMark) {
        return intern(new GreenNullableType(baseType, questionMark));
    }

    public static GreenNullableType create(GreenType baseType) {
        return create(baseType, GreenToken.create(SyntaxKind.COND, "?"));
    }

    public GreenType getBaseType() {
        return baseType;
    }

    @Override
    public int getChildCount() {
        return 2;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> baseType;
            case 1 -> questionMark;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == baseType ? this : create((GreenType) child, questionMark);
            case 1 -> child == questionMark ? this : create(baseType, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "NullableType[" + baseType + "?]";
    }
}
