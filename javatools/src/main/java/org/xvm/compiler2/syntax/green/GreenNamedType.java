package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A named type reference (simple or qualified name).
 */
public final class GreenNamedType extends GreenType {

    private final GreenToken name;

    private GreenNamedType(GreenToken name) {
        super(SyntaxKind.NAMED_TYPE, name.getFullWidth());
        this.name = name;
    }

    public static GreenNamedType create(GreenToken name) {
        return intern(new GreenNamedType(name));
    }

    public static GreenNamedType create(String name) {
        return create(GreenToken.identifier(name));
    }

    public GreenToken getName() {
        return name;
    }

    public String getNameText() {
        return name.getText();
    }

    @Override
    public int getChildCount() {
        return 1;
    }

    @Override
    public GreenNode getChild(int index) {
        if (index == 0) {
            return name;
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (index == 0) {
            return child == name ? this : create((GreenToken) child);
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public String toString() {
        return "Type[" + name.getText() + "]";
    }
}
