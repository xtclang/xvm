package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Label;


/**
 * An expression statement is just an expression that someone stuck a semicolon on the end of.
 */
public class ExpressionStatement
        extends ConditionalStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ExpressionStatement(Expression expr)
        {
        this(expr, true);
        }

    public ExpressionStatement(Expression expr, boolean standalone)
        {
        this.expr = expr;
        this.term = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean hasExpression()
        {
        return true;
        }

    /**
     * @return the underlying expression
     */
    public Expression getExpression()
        {
        return expr;
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expr.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- ConditionalStatement methods ----------------------------------------------------------

    @Override
    public void markConditional(Usage usage, Label label)
        {
        assert !term;
        super.markConditional(usage, label);
        }

    @Override
    public boolean isAlwaysFalse()
        {
        assert m_rte != RuntimeEval.Initial;
        return m_rte == RuntimeEval.AlwaysFalse;
        }

    @Override
    public boolean isAlwaysTrue()
        {
        assert m_rte != RuntimeEval.Initial;
        return m_rte == RuntimeEval.AlwaysTrue;
        }

    @Override
    public boolean isScopeRequired()
        {
        // expression statements never require their own scope
        return false;
        }

    @Override
    protected void split(Context ctx, ErrorListener errs)
        {
        // there's no declaration portion, so just use a "no-op" for that statement
        long      lPos    = getStartPosition();
        Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
        configureSplit(stmtNOP, this, errs);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean    fValid = true;
        Expression exprNew;
        switch (getUsage())
            {
            case Standalone:
            case Switch:
                exprNew = expr.validate(ctx, null, errs);
                break;

            default:
                {
                m_rte = RuntimeEval.RequiresEval;
                exprNew = expr.validate(ctx, pool().typeBoolean(), errs);

                // handle situations in which the expression is always true or always false
                if (exprNew != null && exprNew.isConstant())
                    {
                    // there are only two values that we're interested in; assume anything else
                    // indicates a compiler error, and that's someone else's problem to deal with
                    Constant constVal = exprNew.toConstant();
                    m_rte = constVal.equals(pool().valTrue())
                                ? RuntimeEval.AlwaysTrue
                                : RuntimeEval.AlwaysFalse;
                    }
                }
                break;
            }

        if (exprNew != expr)
            {
            fValid &= exprNew != null;
            if (exprNew != null)
                {
                expr = exprNew;
                }
            }

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable & !expr.isAborting();

        if (getUsage() == Usage.Standalone)
            {
            // so an expression is being used as a statement; blackhole the results
            expr.generateAssignments(code, Expression.NO_LVALUES, errs);
            }
        else if (m_rte == RuntimeEval.RequiresEval)
            {
            boolean fJumpIfTrue;
            switch (getUsage())
                {
                case If:
                    fJumpIfTrue = false;
                    break;

                case While:
                    fJumpIfTrue = true;
                    break;

                case For:
                    // TODO no idea .. maybe this looks like While?
                    throw new UnsupportedOperationException();

                case Switch:
                    // the caller should NOT be calling this; the caller should be getting the
                    // underlying expression and evaluating it directly
                default:
                    throw new IllegalStateException();
                }

            expr.generateConditionalJump(code, getLabel(), fJumpIfTrue, errs);
            }

        return fCompletes;
        }

    @Override
    protected Label getShortCircuitLabel(Expression exprChild)
        {
        assert exprChild == expr;
        switch (getUsage())
            {
            case Switch:
                return getLabel();

            case If:
            case While:
            case For:
                // TODO?
            default:
                return super.getShortCircuitLabel(exprChild);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr);
        if (term)
            {
            sb.append(';');
            }
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        String s  = toString();
        int    of = s.indexOf('\n');
        return (of < 0) ? s : s.substring(0, of) + " ...";
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The manner in which the ConditionalStatement is used. When it is not being used as a
     * conditional, the usage is Standalone.
     */
    public static enum RuntimeEval {Initial, AlwaysTrue, AlwaysFalse, RequiresEval}

    protected Expression expr;
    protected boolean    term;

    /**
     * The determination of how the runtime should evaluate the expression, if this statement is
     * being used as a conditional statement.
     */
    private RuntimeEval m_rte = RuntimeEval.Initial;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ExpressionStatement.class, "expr");
    }
