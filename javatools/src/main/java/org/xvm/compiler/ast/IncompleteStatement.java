package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.xvm.asm.ErrorListener.in;

/**
 * A name, member access or call whose intact prefix can be validated in its original scope. It can
 * stand alone or be owned by an {@link IncompleteExpression} in a value position. The marker has
 * no fabricated value or type. Validation always fails after inspecting the receiver and ordinary
 * arguments, preventing method emission. Only the explicit partial-analysis parser creates it.
 * An explicit cursor can select a written member token or a call before its closing parenthesis;
 * the selected operation need not be malformed. The original member token is retained as syntax.
 *
 * Child fields participate in normal AST adoption/cloning; no Context, callback, or separate
 * semantic cache survives validation. Consumers copy facts only from children whose validation
 * succeeded (isValidated and a fitting TypeFit), never a failed child's placeholder type.
 */
public final class IncompleteStatement extends Statement {
    public IncompleteStatement(Expression target, Token operator, List<Expression> arguments,
                               List<Token> separators, long endPosition) {
        this(target, operator, arguments, separators, endPosition, Parser.UNEXPECTED_EOF);
    }

    public IncompleteStatement(Expression target, Token operator, List<Expression> arguments,
                               List<Token> separators, long endPosition, String diagnosticCode) {
        this(target, operator, arguments, separators, endPosition, diagnosticCode, null);
    }

    /** A written member token selected at its end by an explicit cursor probe. */
    public IncompleteStatement(Expression receiver, Token dot, Token memberName,
                               long cursor, String diagnosticCode) {
        this(receiver, dot, List.of(), List.of(), cursor, diagnosticCode, memberName);
    }

    /** An unqualified name prefix at an explicit cursor. */
    public IncompleteStatement(Token name, long cursor, String diagnosticCode) {
        this(new NameExpression(name), name, List.of(), List.of(), cursor, diagnosticCode, name);
    }

    /** A call whose last written name is a named argument awaiting its value. */
    public static IncompleteStatement forNamedArgument(Expression callee, Token open,
            List<Expression> arguments, List<Token> separators, long cursor, Token name) {
        return new IncompleteStatement(callee, open, arguments, separators, cursor, Parser.INCOMPLETE_EXPRESSION, name);
    }

    private IncompleteStatement(Expression target, Token operator, List<Expression> arguments,
                                List<Token> separators, long endPosition, String diagnosticCode,
                                Token cursorName) {
        this.target         = target;
        this.operator       = operator;
        this.arguments      = new ArrayList<>(arguments);
        this.separators     = List.copyOf(separators);
        this.endPosition    = endPosition;
        this.diagnosticCode = diagnosticCode;
        this.cursorName     = cursorName;
    }

    /** The written receiver (member access) or callee (call); a call is never overload-resolved. */
    public Expression getTarget() {
        return target;
    }

    /** The dot or opening parenthesis, at its original source position. */
    public Token getOperator() {
        return operator;
    }

    /** The original typed member token, if present; its text and range are syntax, not a binding. */
    public Optional<Token> getMemberName() {
        return isCall() ? Optional.empty() : Optional.ofNullable(cursorName);
    }

    /** The named argument at the cursor, whose value is absent and has no expression/type. */
    public Optional<Token> getPendingArgumentName() {
        return isCall() ? Optional.ofNullable(cursorName) : Optional.empty();
    }

    /** Complete written arguments; excludes the missing argument after a trailing comma. */
    public List<Expression> getArguments() {
        return List.copyOf(arguments);
    }

    /** Top-level commas only; nested calls and strings do not contribute separators. */
    public List<Token> getSeparators() {
        return separators;
    }

    public boolean isCall() {
        return operator.getId() == Id.L_PAREN || operator.getId() == Id.ASYNC_PAREN;
    }

    public boolean isNameCompletion() {
        return operator.getId() == Id.IDENTIFIER;
    }

    /** Explicit receiver only; an unqualified call does not invent an implicit receiver. */
    public Optional<Expression> getReceiver() {
        if (isNameCompletion()) {
            return Optional.empty();
        }
        return isCall()
                ? target instanceof NameExpression name
                        ? Optional.ofNullable(name.getLeftExpression())
                        : Optional.empty()
                : Optional.of(target);
    }

    @Override
    public long getStartPosition() {
        return target.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return endPosition;
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        var bindings = ctx.getCursorBindings();
        bindings.begin(this);
        if (bindings.isEnabled() && !errs.isAbortDesired()) {
            var scope = ctx.cursorBinding();
            bindings.record(this, isNameCompletion() ? scope.withTypes(CursorScope.types(this, ctx, errs)) : scope);
        }
        // The cursor-selected member name/overload is not validated by this probe.
        // The receiver and complete arguments still use the real lexical and flow context.
        getReceiver().ifPresent(receiver -> {
            Expression validated = receiver.validate(ctx, null, errs);
            if (validated != null) {
                if (isCall()) {
                    ((NameExpression) target).left = validated;
                } else {
                    target = validated;
                }
            }
        });
        if (bindings.isEnabled() && isCall() && !errs.isAbortDesired()) {
            bindings.record(this, PartialCallResolver.inspect(this, ctx, errs));
        }
        for (int i = 0; i < arguments.size() && !errs.isAbortDesired(); ++i) {
            Expression argument = arguments.get(i);
            // No single expected type is selected. Candidate fitting inspects trial copies above.
            Expression value = argument instanceof LabeledExpression labeled
                    ? labeled.getUnderlyingExpression() : argument;
            if (value instanceof NonBindingExpression || value instanceof LambdaExpression) {
                continue;
            }
            Expression validated = argument.validate(ctx, null, errs);
            if (validated != null) {
                arguments.set(i, validated);
            }
        }
        errs.error(diagnosticCode, in(getSource(), endPosition, endPosition));
        return null;
    }

    @Override
    protected boolean emit(Context ctx, boolean reachable, Code code, ErrorListener errs) {
        throw new IllegalStateException("An incomplete statement cannot emit code");
    }

    @Override
    public String toString() {
        String syntax = isNameCompletion() ? getMemberName().map(Token::getValueText).orElse("")
                : target + (isCall() ? "(" + arguments
                        : "." + getMemberName().map(Token::getValueText).orElse(""));
        return syntax + " <incomplete>";
    }

    protected Expression       target;
    protected List<Expression> arguments;

    private final Token       operator;
    private final List<Token> separators;
    private final long        endPosition;
    private final String      diagnosticCode;
    private final Token       cursorName;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteStatement.class, "target", "arguments");
}
