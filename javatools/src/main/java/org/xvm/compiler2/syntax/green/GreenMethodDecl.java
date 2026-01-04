package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A method declaration.
 * Placeholder for Phase 4 - statements.
 */
public final class GreenMethodDecl extends GreenDeclaration {

    private final GreenType returnType;
    private final GreenToken name;
    private final GreenList parameters;
    private final GreenBlockStmt body;

    private GreenMethodDecl(GreenType returnType, GreenToken name,
                           GreenList parameters, GreenBlockStmt body) {
        super(SyntaxKind.METHOD_DECLARATION,
                returnType.getFullWidth() + name.getFullWidth() +
                parameters.getFullWidth() + body.getFullWidth());
        this.returnType = returnType;
        this.name = name;
        this.parameters = parameters;
        this.body = body;
    }

    public static GreenMethodDecl create(GreenType returnType, GreenToken name,
                                         GreenList parameters, GreenBlockStmt body) {
        return intern(new GreenMethodDecl(returnType, name, parameters, body));
    }

    public GreenType getReturnType() {
        return returnType;
    }

    public GreenToken getName() {
        return name;
    }

    public String getNameText() {
        return name.getText();
    }

    public GreenList getParameters() {
        return parameters;
    }

    public GreenBlockStmt getBody() {
        return body;
    }

    @Override
    public int getChildCount() {
        return 4;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> returnType;
            case 1 -> name;
            case 2 -> parameters;
            case 3 -> body;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == returnType ? this : create((GreenType) child, name, parameters, body);
            case 1 -> child == name ? this : create(returnType, (GreenToken) child, parameters, body);
            case 2 -> child == parameters ? this : create(returnType, name, (GreenList) child, body);
            case 3 -> child == body ? this : create(returnType, name, parameters, (GreenBlockStmt) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }
}
