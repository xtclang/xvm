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
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;


/**
 * An assert statement.
 */
public class AssertStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssertStatement(Token keyword, List<AstNode> conds)
        {
        this.keyword = keyword;
        this.conds   = conds == null ? Collections.emptyList() : conds;
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
    protected Label getShortCircuitLabel(Context ctx, Expression exprChild)
        {
        int cConds = getConditionCount();
        int iCond  = findCondition(exprChild);
        assert iCond >= 0;
        if (iCond == cConds - 1)
            {
            return getEndLabel();
            }

        Label[] alabel = m_alabelCond;
        if (alabel == null)
            {
            m_alabelCond = alabel = new Label[cConds];
            }

        Label label = alabel[iCond+1];
        if (label == null)
            {
            alabel[iCond] = label = new Label("assert[" + iCond + "]");
            }
        return label;
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
                // if (keyword.getId() == Token.Id.ASSERT_ALL)
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
        int cConds = getConditionCount();
        if (cConds == 0)
            {
            code.add(new Assert(pool().valFalse()));
            return false;
            }

        boolean fCompletes = fReachable;
        Label[] alabel     = m_alabelCond;
        for (int i = 0; i < cConds; ++i)
            {
            if (alabel != null)
                {
                Label label = alabel[i];
                if (label != null)
                    {
                    code.add(label);
                    }
                }

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
                    code.add(new Assert(pool().valFalse()));
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

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token         keyword;
    protected List<AstNode> conds;

    private Label[] m_alabelCond;
    private static final Field[] CHILD_FIELDS = fieldsForNames(AssertStatement.class, "conds");
    }
