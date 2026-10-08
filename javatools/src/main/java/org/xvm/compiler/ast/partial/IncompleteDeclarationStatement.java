package org.xvm.compiler.ast.partial;

import java.lang.reflect.Field;

import java.util.List;
import java.util.Optional;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Token;
import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.Parameter;
import org.xvm.compiler.ast.PartialQueries;
import org.xvm.compiler.ast.StageMgr;
import org.xvm.compiler.ast.Statement;

import static org.xvm.asm.ErrorListener.in;

/**
 * Written declaration syntax whose unfinished header cannot register a compiler component.
 * A selected type prefix participates in partial analysis; missing names retain only written
 * type syntax. The body and unfinished parameters have no fabricated signatures, registers or
 * validation contexts.
 */
public final class IncompleteDeclarationStatement extends Statement {
    public enum Kind { METHOD, PROPERTY }

    public IncompleteDeclarationStatement(Kind kind, Token name, long start, long end, List<IncompleteStatement> cursors) {
        this(kind, name, start, end, cursors, List.of());
    }

    public IncompleteDeclarationStatement(Kind kind, Token name, long start, long end, List<IncompleteStatement> cursors, List<Parameter> formals) {
        this.kind = kind;
        this.name = name;
        this.start = start;
        this.end = end;
        this.cursors = List.copyOf(cursors);
        this.formals = List.copyOf(formals);
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
    @SuppressWarnings("MethodDoesntCallSuperMethod") // Preserve the covariant compatibility entry point.
    public IncompleteDeclarationStatement clone() {
        return copyTree();
    }

    @Override
    public IncompleteDeclarationStatement copyTree() {
        var copy = new IncompleteDeclarationStatement(kind, name, start, end,
                cursors.stream().map(IncompleteStatement::copyTree).toList(),
                formals.stream().map(formal -> (Parameter) formal.copyTree()).toList());
        copy.adopt(copy.cursors);
        copy.adopt(copy.formals);
        return copyTreeMetadataTo(copy);
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
        cursors.forEach(site -> {
            bindings.begin(site);
            if (site.isTypeCompletion() && bindings.isEnabled() && !errs.isAbortDesired()) {
                PartialQueries.declarationBinding(site, site, formals, errs)
                        .ifPresent(binding -> bindings.record(site, binding));
            }
            errs.error(Parser.INCOMPLETE_EXPRESSION, in(getSource(), site.getEndPosition(), site.getEndPosition()));
        });
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

    // Only real syntax children participate in adoption and tree copying.
    private final List<IncompleteStatement> cursors;

    private final Kind        kind;
    private final Token       name;
    private final long        start;
    private final long        end;
    private final List<Parameter> formals;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteDeclarationStatement.class, "cursors", "formals");
}
