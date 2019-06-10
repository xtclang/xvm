package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Assignment;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Assert;
import org.xvm.asm.op.Break;
import org.xvm.asm.op.JumpNCond;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


/**
 * An assert statement.
 */
public class AssertStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssertStatement(Token keyword, Expression exprInterval, List<AstNode> conds, long lEndPos)
        {
        this.keyword  = keyword;
        this.interval = exprInterval;
        this.conds    = conds == null ? Collections.emptyList() : conds;
        this.lEndPos  = lEndPos;

        switch (keyword.getId())
            {
            case ASSERT:
            case ASSERT_RND:
            case ASSERT_ARG:
            case ASSERT_BOUNDS:
            case ASSERT_TODO:
            case ASSERT_ONCE:
            case ASSERT_TEST:
            case ASSERT_DBG:
                break;

            default:
                throw new IllegalArgumentException("keyword=" + keyword);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return conds.isEmpty()
                ? keyword.getEndPosition()
                : conds.get(conds.size()-1).getEndPosition();
        }

    /**
     * @return the number of conditions
     */
    public int getConditionCount()
        {
        return conds.size();
        }

    /**
     * @param i  a value between 0 and {@link #getConditionCount()}-1
     *
     * @return the condition, which is either an Expression or an AssignmentStatement
     */
    public AstNode getCondition(int i)
        {
        return conds.get(i);
        }

    /**
     * @param exprChild  an expression that is a child of this statement
     *
     * @return the index of the expression in the list of conditions within this statement, or -1
     */
    public int findCondition(Expression exprChild)
        {
        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            if (conds.get(i) == exprChild)
                {
                return i;
                }
            }
        return -1;
        }

    /**
     * @return true iff the assertion occurs explicitly within conditional "debug" mode
     */
    public boolean isDebugOnly()
        {
        return keyword.getId() == Id.ASSERT_DBG;
        }

    /**
     * @return true iff the assertion occurs explicitly within conditional "test" mode
     */
    public boolean isTestOnly()
        {
        return keyword.getId() == Id.ASSERT_TEST;
        }

    /**
     * @return true iff the assertion occurs explicitly within a conditional mode
     */
    public boolean isLinktimeConditional()
        {
        return isDebugOnly() | isTestOnly();
        }

    /**
     * @return true iff the assertion does not occur each time the execution reaches it
     */
    public boolean isSampling()
        {
        return keyword.getId() == Id.ASSERT_RND;
        }

    /**
     * @return the inverse rate of assertion evaluation; for example "5" means (on average) 1/5 of
     *         the time
     */
    public Expression getSampleInterval()
        {
        return interval;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        return true;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid  = true;
        boolean fAborts = conds.isEmpty();

        for (int i = 0, c = getConditionCount(); i < c; ++i)
            {
            AstNode cond = getCondition(i);
            // the condition is either a boolean expression or an assignment statement whose R-value
            // is a multi-value with the first value being a boolean
            if (cond instanceof AssignmentStatement)
                {
                AssignmentStatement stmtOld = (AssignmentStatement) cond;
                AssignmentStatement stmtNew = (AssignmentStatement) stmtOld.validate(ctx, errs);
                if (stmtNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (stmtNew != stmtOld)
                        {
                        cond = stmtNew;
                        conds.set(i, cond);
                        }
                    }
                }
            else if (cond instanceof Expression)
                {
                ctx = new AssertContext(ctx);

                Expression exprOld = (Expression) cond;
                Expression exprNew = exprOld.validate(ctx, pool().typeBoolean(), errs);
                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (exprNew != exprOld)
                        {
                        cond = exprNew;
                        conds.set(i, cond);
                        }

                    if (exprNew.isConstantFalse())
                        {
                        fAborts = true;
                        }
                    }

                ctx = ctx.exit();
                }
            }

        if (fAborts)
            {
            ctx.markNonCompleting();
            }

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        if (isLinktimeConditional())
            {
            // for "assert:debug", the assertion only is evaluated if the "debug" named condition
            // exists; similarly, for "assert:test", it is evaluated only if "test" is defined
            code.add(new JumpNCond(pool().ensureNamedCondition(isDebugOnly() ? "debug" : "test"),
                    getEndLabel()));
            }

        int cConds = getConditionCount();
        if (cConds == 0)
            {
            code.add(isDebugOnly() ? new Break() : new Assert(pool().valFalse()));
            return false;
            }

        boolean fCompletes = fReachable;
        for (int i = 0; i < cConds; ++i)
            {
            AstNode cond = getCondition(i);
            if (cond instanceof AssignmentStatement)
                {
                AssignmentStatement stmtCond = (AssignmentStatement) cond;
                fCompletes &= stmtCond.completes(ctx, fCompletes, code, errs);
                code.add(new Assert(stmtCond.getConditionRegister()));
                }
            else
                {
                Expression exprCond = (Expression) cond;
                if (exprCond.isConstantFalse())
                    {
                    code.add(isDebugOnly() ? new Break() : new Assert(pool().valFalse()));
                    fCompletes = false;
                    }
                else if (!exprCond.isConstantTrue())
                    {
                    fCompletes &= exprCond.isCompletable();
                    code.add(new Assert(exprCond.generateArgument(ctx, code, true, true, errs)));
                    }
                }
            }

        return fCompletes;
        }

    /**
     * A custom context implementation to provide type-narrowing as a natural side-effect of an
     * assertion.
     */
    static class AssertContext
            extends Context
        {
        public AssertContext(Context outer)
            {
            super(outer, true);
            }

        @Override
        protected Assignment promote(String sName, Assignment asnInner, Assignment asnOuter)
            {
            return asnInner.whenTrue();
            }

        @Override
        protected void promoteNarrowedType(String sName, Argument arg, Branch branch)
            {
            super.promoteNarrowedType(sName, arg, branch);

            // promote our "true" into the parent's "always" branch
            if (branch == Branch.WhenTrue)
                {
                getOuterContext().narrowLocalRegister(sName, arg); // Always
                }
            }

        @Override
        protected void promoteNarrowedFormalType(String sName, TypeConstant typeNarrowed, Branch branch)
            {
            super.promoteNarrowedFormalType(sName, typeNarrowed, branch);

            // promote our "true" into the parent's "always" branch
            if (branch == Branch.WhenTrue)
                {
                getOuterContext().ensureFormalTypeMap(Branch.Always).put(sName, typeNarrowed);
                }
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);
        if (interval != null)
            {
            sb.append('(')
              .append(interval)
              .append(')');
            }

        if (conds != null && !conds.isEmpty())
            {
            sb.append(' ')
              .append(conds.get(0));
            for (int i = 1, c = conds.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(conds.get(i));
                }
            }

        sb.append(';');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token         keyword;
    protected Expression    interval;
    protected List<AstNode> conds;
    protected long          lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssertStatement.class, "interval", "conds");
    }
