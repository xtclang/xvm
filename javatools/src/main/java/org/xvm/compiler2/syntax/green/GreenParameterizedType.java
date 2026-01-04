package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A parameterized type (generic type with type arguments): List&lt;Int&gt;
 */
public final class GreenParameterizedType extends GreenType {

    private final GreenType baseType;
    private final GreenToken openAngle;
    private final GreenList typeArgs;
    private final GreenToken closeAngle;

    private GreenParameterizedType(GreenType baseType, GreenToken openAngle,
                                   GreenList typeArgs, GreenToken closeAngle) {
        super(SyntaxKind.PARAMETERIZED_TYPE,
                baseType.getFullWidth() + openAngle.getFullWidth() +
                typeArgs.getFullWidth() + closeAngle.getFullWidth());
        this.baseType = baseType;
        this.openAngle = openAngle;
        this.typeArgs = typeArgs;
        this.closeAngle = closeAngle;
    }

    public static GreenParameterizedType create(GreenType baseType, GreenToken openAngle,
                                                GreenList typeArgs, GreenToken closeAngle) {
        return intern(new GreenParameterizedType(baseType, openAngle, typeArgs, closeAngle));
    }

    public static GreenParameterizedType create(GreenType baseType, GreenType... typeArgs) {
        return create(baseType,
                GreenToken.create(SyntaxKind.LT, "<"),
                GreenList.create(SyntaxKind.ARGUMENT, typeArgs),
                GreenToken.create(SyntaxKind.GT, ">"));
    }

    public GreenType getBaseType() {
        return baseType;
    }

    public GreenList getTypeArgs() {
        return typeArgs;
    }

    @Override
    public int getChildCount() {
        return 4;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> baseType;
            case 1 -> openAngle;
            case 2 -> typeArgs;
            case 3 -> closeAngle;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == baseType ? this : create((GreenType) child, openAngle, typeArgs, closeAngle);
            case 1 -> child == openAngle ? this : create(baseType, (GreenToken) child, typeArgs, closeAngle);
            case 2 -> child == typeArgs ? this : create(baseType, openAngle, (GreenList) child, closeAngle);
            case 3 -> child == closeAngle ? this : create(baseType, openAngle, typeArgs, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "ParameterizedType[" + baseType + "<...>]";
    }
}
