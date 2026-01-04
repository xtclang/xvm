package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A property declaration.
 * Placeholder for Phase 6 - full module compilation.
 */
public final class GreenPropertyDecl extends GreenDeclaration {

    private final GreenType type;
    private final GreenToken name;

    private GreenPropertyDecl(GreenType type, GreenToken name) {
        super(SyntaxKind.PROPERTY_DECLARATION, type.getFullWidth() + name.getFullWidth());
        this.type = type;
        this.name = name;
    }

    public static GreenPropertyDecl create(GreenType type, GreenToken name) {
        return intern(new GreenPropertyDecl(type, name));
    }

    public GreenType getType() {
        return type;
    }

    public GreenToken getName() {
        return name;
    }

    @Override
    public int getChildCount() {
        return 2;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> type;
            case 1 -> name;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == type ? this : create((GreenType) child, name);
            case 1 -> child == name ? this : create(type, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
