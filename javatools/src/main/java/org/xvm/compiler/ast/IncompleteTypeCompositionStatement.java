package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.List;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.xvm.asm.ErrorListener.in;

/**
 * A written type whose unfinished header cannot register inheritance or body facts.
 * Only a root module registers its written namespace/core import, providing the query scope.
 * Its real body remains syntax for structural consumers, including member-file assembly.
 * Only the selected header type participates in partial analysis, in the enclosing scope.
 */
public final class IncompleteTypeCompositionStatement extends TypeCompositionStatement {
    public IncompleteTypeCompositionStatement(Source source, Token category, Token name,
            long start, long end, List<IncompleteStatement> cursors) {
        this(source, category, name, null, null, start, end, cursors, Set.of());
    }

    public IncompleteTypeCompositionStatement(Source source, Token category, Token name, List<Token> qualified,
            StatementBlock body, long start, long end, List<IncompleteStatement> cursors, Set<String> formals) {
        super(source, category, name, start, end);
        this.cursors = List.copyOf(cursors);
        this.formals = Set.copyOf(formals);
        this.qualified = qualified == null ? null : List.copyOf(qualified);
        this.body = body;
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
        var copy = new IncompleteTypeCompositionStatement(source, category, name, qualified, null,
                getStartPosition(), getEndPosition(),
                cursors.stream().map(site -> (IncompleteStatement) site.clone()).toList(), formals);
        copy.body = body == null ? null : copy.adopt((StatementBlock) body.clone());
        copy.adopt(copy.cursors);
        copy.setParent(getParent());
        copy.setStage(getStage());
        return copy;
    }

    @Override
    public boolean isComponentNode() {
        return category.getId() == Id.MODULE;
    }

    @Override
    public void registerStructures(StageMgr mgr, ErrorListener errs) {
        // Defer before the base class explicitly visits children, not just before returning.
        mgr.deferChildren();
        // A root's written module name supplies the actual compilation namespace and core
        // import. Its incomplete compositions and body still register no declarations.
        if (category.getId() == Id.MODULE) {
            super.registerStructures(mgr, errs);
        }
    }

    @Override
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
        if (category.getId() == Id.MODULE) {
            super.resolveNames(mgr, errs);
        }
    }

    Set<String> formalNames() {
        return formals;
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

    private final Set<String> formals;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteTypeCompositionStatement.class, "body", "cursors");
}
