package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Assert;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * An assert statement.
 */
public class AssertStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssertStatement(Token keyword, AstNode cond)
        {
        this.keyword = keyword;
        this.cond = cond;
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
        return cond == null ? keyword.getEndPosition() : cond.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // the condition is either a boolean expression or an assignment statement whose R-value is
        // a multi-value with the first value being a boolean
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
                    }
                }
            }
        else if (cond instanceof Expression)
            {
            Expression exprOld = (Expression) cond;
            Expression exprNew = exprOld.validate(ctx, pool().typeBoolean(), errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else  if (exprNew != exprOld)
                {
                cond = exprNew;
                }
            }

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        if (cond instanceof Expression && ((Expression) cond).isConstantTrue())
            {
            return fReachable;
            }

        if (cond == null || (cond instanceof Expression && ((Expression) cond).isConstantFalse()))
            {
            code.add(new Assert(pool().valFalse()));
            return fReachable;
            }

        boolean fCompletes;
        if (cond instanceof AssignmentStatement)
            {
            AssignmentStatement stmtCond = (AssignmentStatement) cond;
            fCompletes = stmtCond.completes(ctx, fReachable, code, errs);
            code.add(new Assert(stmtCond.getConditionRegister()));
            }
        else
            {
            Expression exprCond = (Expression) cond;
            fCompletes = !exprCond.isAborting();
            exprCond.generateArgument(ctx, code, true, true, errs);
            }

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (cond != null)
            {
            sb.append(' ')
              .append(cond);
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token   keyword;
    protected AstNode cond;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssertStatement.class, "cond");
    }
