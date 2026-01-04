package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A class declaration.
 * Placeholder for Phase 6 - full module compilation.
 */
public final class GreenClassDecl extends GreenDeclaration {

    private final GreenToken classKeyword;
    private final GreenToken name;
    private final GreenList members;

    private GreenClassDecl(GreenToken classKeyword, GreenToken name, GreenList members) {
        super(SyntaxKind.CLASS_DECLARATION,
                classKeyword.getFullWidth() + name.getFullWidth() + members.getFullWidth());
        this.classKeyword = classKeyword;
        this.name = name;
        this.members = members;
    }

    public static GreenClassDecl create(GreenToken classKeyword, GreenToken name, GreenList members) {
        return intern(new GreenClassDecl(classKeyword, name, members));
    }

    public GreenToken getName() {
        return name;
    }

    public String getNameText() {
        return name.getText();
    }

    public GreenList getMembers() {
        return members;
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> classKeyword;
            case 1 -> name;
            case 2 -> members;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == classKeyword ? this : create((GreenToken) child, name, members);
            case 1 -> child == name ? this : create(classKeyword, (GreenToken) child, members);
            case 2 -> child == members ? this : create(classKeyword, name, (GreenList) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
