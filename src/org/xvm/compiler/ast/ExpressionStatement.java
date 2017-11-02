package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.SingletonConstant;

import org.xvm.asm.op.Label;

import org.xvm.compiler.ErrorListener;


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
    protected void split()
        {
        // there's no declaration portion, so just use a "no-op" for that statement
        long      lPos    = getStartPosition();
        Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
        configureSplit(stmtNOP, this);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = expr.validate(ctx, errs);

        if (getUsage() != Usage.Standalone)
            {
            m_rte = RuntimeEval.RequiresEval;

            // handle situations in which the expression is always true or always false
            if (expr.isConstant())
                {
                // there are only two values that we're interested in; assume anything else
                // indicates a compiler error, and that's someone else's problem to deal with
                Argument arg = expr.generateConstant(code, pool().typeBoolean(), errs);
                m_rte = arg instanceof SingletonConstant
                        && ((SingletonConstant) arg).getValue().getName().equals("True")
                            ? RuntimeEval.AlwaysTrue
                            : RuntimeEval.AlwaysFalse;
                }
            }

        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable & expr.isCompletable();

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

            expr.generateConditionalJump(code, getLabel(), false, errs);
            }

        return fCompletes;
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
