package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.CursorBinding;
import org.xvm.compiler.Parser;
import org.xvm.compiler.Token;

import static org.xvm.asm.ErrorListener.in;

/**
 * Written declaration syntax whose unfinished header cannot register a compiler component.
 * Only a selected type prefix participates in partial analysis; the body and unfinished
 * parameters have no fabricated signatures, registers or validation contexts.
 */
public final class IncompleteDeclarationStatement extends Statement {
    public enum Kind { METHOD, PROPERTY }

    public IncompleteDeclarationStatement(Kind kind, Token name, long start, long end,
                                          List<IncompleteStatement> cursors) {
        this.kind = kind;
        this.name = name;
        this.start = start;
        this.end = end;
        this.cursors = new ArrayList<>(cursors);
    }

    public Kind getKind() {
        return kind;
    }

    /** A written name only; an absent declaration name does not acquire a placeholder. */
    public Optional<Token> getNameToken() {
        return Optional.ofNullable(name);
    }

    @Override
    public long getStartPosition() {
        return start;
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
    public void resolveNames(StageMgr mgr, ErrorListener errs) {
        // A prefix is a query, not an unresolved name in an otherwise valid declaration.
        mgr.deferChildren();
    }

    @Override
    public void validateContent(StageMgr mgr, ErrorListener errs) {
        mgr.deferChildren();
        var bindings = mgr.getCursorBindings();
        for (var site : cursors) {
            bindings.begin(site);
            if (bindings.isEnabled() && !errs.isAbortDesired()
                    && getComponent() instanceof ClassStructure owner) {
                bindings.record(site, new CursorBinding(List.of(), owner.getFormalType(), false)
                        .withTypes(CursorScope.declarationTypes(site, errs)));
            }
            errs.error(Parser.INCOMPLETE_EXPRESSION, in(getSource(), site.getEndPosition(), site.getEndPosition()));
        }
        if (cursors.isEmpty()) {
            errs.error(Parser.INCOMPLETE_EXPRESSION, in(getSource(), end, end));
        }
    }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        return null;
    }

    @Override
    protected boolean emit(Context ctx, boolean reachable, Code code, ErrorListener errs) {
        throw new IllegalStateException("An incomplete declaration cannot emit code");
    }

    @Override
    public void generateCode(StageMgr mgr, ErrorListener errs) {
        throw new IllegalStateException("An incomplete declaration cannot emit code");
    }

    @Override
    public String toString() {
        return kind + getNameToken().map(token -> " " + token.getValueText()).orElse("") + " <incomplete>";
    }

    // Only real syntax children use the AST's ordinary adoption and clone mechanism.
    protected List<IncompleteStatement> cursors;

    private final Kind  kind;
    private final Token name;
    private final long  start;
    private final long  end;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteDeclarationStatement.class, "cursors");
}
