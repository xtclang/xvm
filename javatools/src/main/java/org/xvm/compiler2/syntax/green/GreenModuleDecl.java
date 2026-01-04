package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A module declaration.
 * Placeholder for Phase 6 - full module compilation.
 */
public final class GreenModuleDecl extends GreenDeclaration {

    private final GreenToken moduleKeyword;
    private final GreenToken name;
    private final GreenList members;

    private GreenModuleDecl(GreenToken moduleKeyword, GreenToken name, GreenList members) {
        super(SyntaxKind.MODULE_DECLARATION,
                moduleKeyword.getFullWidth() + name.getFullWidth() + members.getFullWidth());
        this.moduleKeyword = moduleKeyword;
        this.name = name;
        this.members = members;
    }

    public static GreenModuleDecl create(GreenToken moduleKeyword, GreenToken name, GreenList members) {
        return intern(new GreenModuleDecl(moduleKeyword, name, members));
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
            case 0 -> moduleKeyword;
            case 1 -> name;
            case 2 -> members;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == moduleKeyword ? this : create((GreenToken) child, name, members);
            case 1 -> child == name ? this : create(moduleKeyword, (GreenToken) child, members);
            case 2 -> child == members ? this : create(moduleKeyword, name, (GreenList) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
