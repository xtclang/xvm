package org.xvm.compiler.ast.partial;

import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.compiler.Parser;
import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.Statement;

import static org.xvm.asm.ErrorListener.in;

/** A missing local name and its written initializer; no register or inferred type is invented. */
public final class IncompleteLocalDeclaration extends Statement {
    public IncompleteLocalDeclaration(IncompleteStatement cursor, Expression initializer, long end) {
        this.cursor = cursor;
        this.initializer = initializer;
        this.end = end;
    }

    public Expression getInitializer() {
        return initializer;
    }

    @Override
    public String toString() {
        return cursor + " = " + initializer;
    }

    @Override
    public long getStartPosition() {
        return cursor.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return end;
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    @Override
    public IncompleteLocalDeclaration clone() {
        var copy = new IncompleteLocalDeclaration((IncompleteStatement) cursor.clone(),
                (Expression) initializer.clone(), end);
        copy.adopt(copy.cursor);
        copy.adopt(copy.initializer);
        copy.setParent(getParent());
        copy.setStage(getStage());
        return copy;
    }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
    }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
        errs.error(Parser.INCOMPLETE_EXPRESSION, in(getSource(), cursor.getEndPosition(), cursor.getEndPosition()));
    }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        return null;
    }

    @Override
    protected boolean emit(Context ctx, boolean reachable, Code code, ErrorListener errs) {
        throw new IllegalStateException("An incomplete local declaration cannot emit code");
    }

    private final IncompleteStatement cursor;
    private final Expression initializer;
    private final long end;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteLocalDeclaration.class, "cursor", "initializer");
}
