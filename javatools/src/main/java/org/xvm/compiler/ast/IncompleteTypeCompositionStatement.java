package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import static org.xvm.asm.ErrorListener.in;

/**
 * A written type whose unfinished header cannot register a component or inheritance facts.
 * Its real body remains syntax for structural consumers, including member-file assembly.
 * Only the selected header type participates in partial analysis, in the enclosing scope.
 */
public final class IncompleteTypeCompositionStatement extends TypeCompositionStatement {
    public IncompleteTypeCompositionStatement(Source source, Token category, Token name,
            long start, long end, List<IncompleteStatement> cursors) {
        super(source, category, name, start, end);
        this.cursors = List.copyOf(cursors);
    }

    public IncompleteTypeCompositionStatement(Source source, Token category, Token name, StatementBlock body,
            long start, long end, List<IncompleteStatement> cursors) {
        this(source, category, name, start, end, cursors);
        this.body = body;
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    /**
     * Recovery syntax is never rewritten by compiler validation. Construct a fresh node instead
     * of using the general AST clone, which replaces child fields and mutable child lists.
     */
    @Override
    public IncompleteTypeCompositionStatement clone() {
        var copy = new IncompleteTypeCompositionStatement(source, category, name, getStartPosition(), getEndPosition(),
                cursors.stream().map(site -> (IncompleteStatement) site.clone()).toList());
        copy.body = body == null ? null : copy.adopt((StatementBlock) body.clone());
        copy.adopt(copy.cursors);
        copy.setParent(getParent());
        copy.setStage(getStage());
        return copy;
    }

    @Override
    public boolean isComponentNode() {
        return false;
    }

    @Override
    public void registerStructures(StageMgr mgr, ErrorListener errs) {
        // Body declarations must not register against the enclosing type instead of this one.
        mgr.deferChildren();
    }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
    }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
        var bindings = mgr.getCursorBindings();
        cursors.forEach(site -> {
            bindings.begin(site);
            if (bindings.isEnabled() && !errs.isAbortDesired()
                    && CursorScope.declarationScope(site).getComponent() instanceof ClassStructure owner) {
                bindings.record(site, new CursorBinding(List.of(), owner.getFormalType(), false)
                        .withTypes(CursorScope.declarationTypes(site, errs)));
            }
            errs.error(Parser.INCOMPLETE_EXPRESSION, in(getSource(), site.getEndPosition(), site.getEndPosition()));
        });
        if (cursors.isEmpty()) {
            errs.error(Parser.INCOMPLETE_EXPRESSION, in(getSource(), getEndPosition(), getEndPosition()));
        }
    }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        return null;
    }

    @Override
    protected boolean emit(Context ctx, boolean reachable, Code code, ErrorListener errs) {
        throw new IllegalStateException("An incomplete type declaration cannot emit code");
    }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs) {
        throw new IllegalStateException("An incomplete type declaration cannot emit code");
    }

    @Override
    public String toString() {
        return toSignatureString() + " <incomplete>";
    }

    // Protected only so the existing reflective child traversal can read it.
    protected final List<IncompleteStatement> cursors;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteTypeCompositionStatement.class, "body", "cursors");
}
