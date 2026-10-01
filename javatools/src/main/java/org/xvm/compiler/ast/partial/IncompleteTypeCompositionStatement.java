package org.xvm.compiler.ast.partial;

import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.Parameter;
import org.xvm.compiler.ast.PartialQueries;
import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.StatementBlock;
import org.xvm.compiler.ast.TypeCompositionStatement;

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
        this(source, category, name, null, null, start, end, cursors, List.of());
    }

    public IncompleteTypeCompositionStatement(Source source, Token category, Token name, List<Token> qualified,
            StatementBlock body, long start, long end, List<IncompleteStatement> cursors, List<Parameter> formals) {
        super(source, category, name, start, end);
        this.cursors = List.copyOf(cursors);
        this.formals = List.copyOf(formals);
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
                cursors.stream().map(site -> (IncompleteStatement) site.clone()).toList(),
                formals.stream().map(formal -> (Parameter) formal.clone()).toList());
        copy.body = body == null ? null : copy.adopt((StatementBlock) body.clone());
        copy.adopt(copy.cursors);
        copy.adopt(copy.formals);
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

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
        var bindings = mgr.getCursorBindings();
        cursors.forEach(site -> {
            bindings.begin(site);
            if (site.isTypeCompletion() && bindings.isEnabled() && !errs.isAbortDesired()) {
                PartialQueries.declarationBinding(site, isComponentNode() ? this : getParent(), formals, errs)
                        .ifPresent(binding -> bindings.record(site, binding));
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

    // Immutable syntax children; clone constructs and adopts fresh lists.
    private final List<IncompleteStatement> cursors;

    private final List<Parameter> formals;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteTypeCompositionStatement.class, "body", "cursors", "formals");
}
