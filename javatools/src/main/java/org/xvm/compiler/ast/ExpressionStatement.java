package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * An expression statement is just an expression that someone stuck a semicolon on the end of.
 *
 * <p/>REVIEW what expression types are allowed? is that the parser's job? or validate()'s job?
 */
public class ExpressionStatement
        extends Statement {
    // ----- constructors --------------------------------------------------------------------------

    public ExpressionStatement(Expression expr) {
        this(expr, true);
    }

    public ExpressionStatement(Expression expr, boolean standalone) {
        this.expr = expr;
        this.term = standalone;
    }

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "expr" - deep copied by AstNode.clone()</li>
     *   <li>No transient fields in this class</li>
     * </ul>
     *
     * @param original  the ExpressionStatement to copy from
     */
    protected ExpressionStatement(@NotNull ExpressionStatement original) {
        super(Objects.requireNonNull(original));

        // Deep copy child field
        this.expr = original.expr == null ? null : original.expr.copy();
        adopt(this.expr);

        // Copy non-child structural fields
        this.term = original.term;
    }

    @Override
    public ExpressionStatement copy() {
        return new ExpressionStatement(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the underlying expression
     */
    public Expression getExpression() {
        return expr;
    }

    @Override
    public long getStartPosition() {
        return expr.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return expr.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    @Override
    public boolean isCompletable() {
        return expr.isCompletable();
    }

    @Override
    public boolean isTodo() {
        return expr.isTodo();
    }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs) {
        boolean    fValid  = true;
        Expression exprNew = expr.validate(ctx, null, errs);
        if (exprNew != expr) {
            fValid &= exprNew != null;
            if (exprNew != null) {
                expr = exprNew;
            }
        }

        if (fValid && !expr.isStandalone()) {
            log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_STATEMENT);
            fValid = false;
        }

        return fValid
                ? this
                : null;
    }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs) {
        boolean fCompletes = fReachable & expr.isCompletable();

        // so an expression is being used as a statement; blackhole the results
        expr.generateAssignments(ctx, code, Expression.NO_LVALUES, errs);

        ctx.getHolder().setAst(this, expr.getExprAST(ctx));
        return fCompletes;
    }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild) {
        return nodeChild == expr;
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(expr);
        if (!term) {
            sb.append(';');
        }
        return sb.toString();
    }

    @Override
    public String getDumpDesc() {
        String s  = toString();
        int    of = s.indexOf('\n');
        return (of < 0) ? s : s.substring(0, of) + " ...";
    }


    // ----- fields --------------------------------------------------------------------------------

    @ChildNode(index = 0, description = "Expression")
    protected Expression expr;
    protected boolean    term;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ExpressionStatement.class, "expr");
}