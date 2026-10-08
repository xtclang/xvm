package org.xvm.compiler.ast.partial;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Parser;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.NameExpression;
import org.xvm.compiler.ast.NamedTypeExpression;
import org.xvm.compiler.ast.NewExpression;
import org.xvm.compiler.ast.PartialQueries;
import org.xvm.compiler.ast.Statement;
import org.xvm.compiler.ast.TypeExpression;

import static org.xvm.asm.ErrorListener.in;

/**
 * A name, member access or call whose intact prefix can be validated in its original scope. It can
 * stand alone or be owned by an {@link IncompleteExpression} in a value position. The marker has
 * no fabricated value or type. Validation always fails after inspecting the receiver and ordinary
 * arguments, preventing method emission. Only the explicit partial-analysis parser creates it.
 * An explicit cursor can select a written member token or a call before its closing parenthesis;
 * the selected operation need not be malformed. The original member token is retained as syntax.
 *
 * <p>Child fields participate in normal AST adoption/cloning; no Context, callback, or separate
 * semantic cache survives validation. Consumers copy facts only from children whose validation
 * succeeded (isValidated and a fitting TypeFit), never a failed child's placeholder type.
 */
public final class IncompleteStatement extends Statement {
    public IncompleteStatement(Expression target, Token operator, List<Expression> arguments, List<Token> separators, long endPosition) {
        this(target, operator, arguments, separators, endPosition, Parser.UNEXPECTED_EOF);
    }

    public IncompleteStatement(Expression target, Token operator, List<Expression> arguments, List<Token> separators, long endPosition, String diagnosticCode) {
        this(target, operator, arguments, separators, endPosition, diagnosticCode, null);
    }

    /** A written member token selected at its end by an explicit cursor probe. */
    public IncompleteStatement(Expression receiver, Token dot, Token memberName, long cursor, String diagnosticCode) {
        this(receiver, dot, List.of(), List.of(), cursor, diagnosticCode, memberName);
    }

    /** An unqualified name prefix at an explicit cursor. */
    public IncompleteStatement(Token name, long cursor, String diagnosticCode) {
        this(new NameExpression(name), name, List.of(), List.of(), cursor, diagnosticCode, name);
    }

    /** A declaration type prefix; retain its qualifiers as syntax and replace only the final token. */
    public static IncompleteStatement forDeclarationType(NamedTypeExpression type, long cursor) {
        Token name = type.getNameToken();
        return new IncompleteStatement(type, name, List.of(), List.of(), cursor, Parser.INCOMPLETE_EXPRESSION, name);
    }

    /** A missing declaration name after a complete written type; never fabricate a name token. */
    @SuppressWarnings("unused")
    public static IncompleteStatement forDeclarationName(NamedTypeExpression type, long cursor) {
        return forDeclarationName(type, type.getNameToken(), cursor);
    }

    /** Retain a complete wrapped or compound type and its last written token, without a new name. */
    public static IncompleteStatement forDeclarationName(TypeExpression type, Token last, long cursor) {
        return new IncompleteStatement(type, last, List.of(), List.of(), cursor, Parser.INCOMPLETE_EXPRESSION, null);
    }

    /** Complete written type at an empty declaration-name slot; syntax only, never a binding. */
    public Optional<TypeExpression> getDeclarationType() {
        return target instanceof TypeExpression type && cursorName == null
                && !isCall() && operator.getId() != Id.DOT
                && operator.getEndPosition() < endPosition && type.getEndPosition() < endPosition
                ? Optional.of(type) : Optional.empty();
    }

    /** The written type's final name, for syntax suggestions only, not a resolved type or binding. */
    public Optional<Token> getDeclarationNameType() {
        return getDeclarationType().filter(NamedTypeExpression.class::isInstance)
                .map(NamedTypeExpression.class::cast).map(NamedTypeExpression::getNameToken);
    }

    /** A call whose last written name is a named argument awaiting its value. */
    public static IncompleteStatement forNamedArgument(Expression callee, Token open,
            List<Expression> arguments, List<Token> separators, long cursor, Token name) {
        return new IncompleteStatement(callee, open, arguments, separators, cursor, Parser.INCOMPLETE_EXPRESSION, name);
    }

    /** A direct argument name prefix; label is null for a positional argument. */
    public static IncompleteStatement forArgumentPrefix(Expression callee, Token open,
            List<Expression> arguments, List<Token> separators, long cursor, Token label, Token prefix) {
        return new IncompleteStatement(callee, open, arguments, separators, cursor,
                Parser.INCOMPLETE_EXPRESSION, label, prefix);
    }

    private IncompleteStatement(Expression target, Token operator, List<Expression> arguments,
                                List<Token> separators, long endPosition, String diagnosticCode,
                                Token cursorName) {
        this(target, operator, arguments, separators, endPosition, diagnosticCode, cursorName, null);
    }

    private IncompleteStatement(Expression target, Token operator, List<Expression> arguments,
                                List<Token> separators, long endPosition, String diagnosticCode,
                                Token cursorName, Token argumentPrefix) {
        this.target         = target;
        this.operator       = operator;
        this.arguments      = new ArrayList<>(arguments);
        this.separators     = List.copyOf(separators);
        this.endPosition    = endPosition;
        this.diagnosticCode = diagnosticCode;
        this.cursorName     = cursorName;
        this.argumentPrefix = argumentPrefix;
    }

    /** The written receiver, callee or declaration type; a call is never overload-resolved. */
    public Expression getTarget() {
        return target;
    }

    /** The dot or opening call/dimension delimiter, at its original source position. */
    public Token getOperator() {
        return operator;
    }

    /** The original typed member token, if present; its text and range are syntax, not a binding. */
    public Optional<Token> getMemberName() {
        return isCall() ? Optional.empty() : Optional.ofNullable(cursorName);
    }

    /** Typed text before the cursor; the original token still supplies the whole replacement range. */
    public String getCompletionPrefix() {
        return getArgumentPrefix().or(this::getMemberName).map(name ->
                endPosition < name.getEndPosition()
                        ? getSource().toString(name.getStartPosition(), endPosition)
                        : name.getValueText()).orElse("");
    }

    /** The named argument at the cursor, whose value is absent or retained only as a name prefix. */
    public Optional<Token> getPendingArgumentName() {
        return isCall() ? Optional.ofNullable(cursorName) : Optional.empty();
    }

    /** Original token to replace when completing an argument; it is never validated as a value. */
    public Optional<Token> getArgumentPrefix() {
        return Optional.ofNullable(argumentPrefix);
    }

    /** The real containing call for a direct, labeled or parenthesized argument cursor. */
    public Optional<IncompleteStatement> getArgumentCall() {
        return PartialSyntax.argumentCall(this);
    }

    /** Complete written arguments; excludes the missing value or cursor-selected argument prefix. */
    public List<Expression> getArguments() {
        return List.copyOf(arguments);
    }

    /** Written array dimensions preceding this call's opening parenthesis. */
    public List<Expression> getLeadingArguments() {
        return target instanceof NewExpression creation ? creation.getArguments() : List.of();
    }

    /** Top-level commas only; nested calls and strings do not contribute separators. */
    public List<Token> getSeparators() {
        return separators;
    }

    public boolean isCall() {
        return operator.getId() == Id.L_PAREN || operator.getId() == Id.ASYNC_PAREN
                || operator.getId() == Id.L_SQUARE && target instanceof NewExpression;
    }

    public boolean isNameCompletion() {
        return operator.getId() == Id.IDENTIFIER && getDeclarationType().isEmpty();
    }

    /** A type query in an unfinished declaration, with no value or parameter-name completion. */
    public boolean isTypeCompletion() {
        return getDeclarationType().isEmpty()
                && (getParent() instanceof IncompleteDeclarationStatement
                        || getParent() instanceof IncompleteTypeCompositionStatement);
    }

    /** Explicit receiver only; an unqualified call does not invent an implicit receiver. */
    public Optional<Expression> getReceiver() {
        if (isNameCompletion() || getDeclarationType().isPresent()) {
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
    public IncompleteStatement copyTree() {
        var copy = new IncompleteStatement((Expression) target.copyTree(), operator,
                arguments.stream().map(argument -> (Expression) argument.copyTree()).toList(),
                separators, endPosition, diagnosticCode, cursorName, argumentPrefix);
        copy.adopt(copy.target);
        copy.adopt(copy.arguments);
        return copyTreeMetadataTo(copy);
    }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        return inspect(ctx, null, errs);
    }

    /** Forward the enclosing expression's required type without storing it on the syntax node. */
    @SuppressWarnings("UnusedReturnValue")
    Statement validate(Context ctx, TypeConstant required, ErrorListener errs) {
        return validate(ctx, errs, () -> inspect(ctx, required, errs));
    }

    private Statement inspect(Context ctx, TypeConstant required, ErrorListener errs) {
        PartialQueries.inspect(this, ctx, required, errs);
        errs.error(diagnosticCode, in(getSource(), endPosition, endPosition));
        return null;
    }

    @Override
    protected boolean emit(Context ctx, boolean reachable, Code code, ErrorListener errs) {
        throw new IllegalStateException("An incomplete statement cannot emit code");
    }

    @Override
    public String toString() {
        if (getDeclarationType().isPresent()) {
            return target + " <missing declaration name>";
        }
        String syntax = isNameCompletion() ? getMemberName().map(Token::getValueText).orElse("")
                : target + (isCall() ? (operator.getId() == Id.L_SQUARE ? "[" : "(") + arguments
                        : "." + getMemberName().map(Token::getValueText).orElse(""));
        return syntax + " <incomplete>";
    }

    private final Expression       target;
    private final List<Expression> arguments;

    private final Token       operator;
    private final List<Token> separators;
    private final long        endPosition;
    private final String      diagnosticCode;
    private final Token       cursorName;
    private final Token       argumentPrefix;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteStatement.class, "target", "arguments");
}
