package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.op.Label;
import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" statement.
 */
public class SwitchStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchStatement(Token keyword, List<AstNode> cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isNaturalShortCircuitStatementTarget()
        {
        return true;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return block.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        // the expressions are in the condition portion
        return true;
        }

    @Override
    protected Label getShortCircuitLabel(Context ctx, Expression exprChild)
        {
        // TODO definite assignment implications
        return getEndLabel();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // validate the switch condition
        m_casemgr = new CaseManager(this);
        fValid   &= m_casemgr.validateCondition(ctx, cond, errs);

        // the case manager enters a new context if the switch condition declares variables
        Context ctxCond = m_casemgr.getSwitchContext();
        if (ctxCond != null)
            {
            ctx = ctxCond;
            }

        List<Statement> listStmts = block.stmts;
        int             cStmts    = listStmts.size();
        boolean         fInCase   = false;
        Context         ctxBlock  = null;
        for (int i = 0; i < cStmts; ++i)
            {
            Statement stmt = listStmts.get(i);
            if (stmt instanceof CaseStatement)
                {
                if (ctxBlock != null)
                    {
                    ctxBlock.exit();
                    ctxBlock = null;
                    }

                fValid &= m_casemgr.validateCase(ctx, (CaseStatement) stmt, errs);
                fInCase = true;
                }
            else
                {
                if (fInCase)
                    {
                    m_casemgr.endCaseGroup(i);
                    fInCase = false;

                    assert ctxBlock == null;
                    ctxBlock = ctx.enter();
                    }

                Statement stmtNew = stmt.validate(ctxBlock, errs);
                if (stmtNew != stmt)
                    {
                    if (stmtNew == null)
                        {
                        fValid = false;
                        }
                    else
                        {
                        block.stmts = listStmts = ensureArrayList(listStmts);
                        listStmts.set(i, stmtNew);
                        }
                    }
                }

            if (errs.isAbortDesired())
                {
                break;
                }
            }

        if (ctxBlock != null)
            {
            ctxBlock.exit();
            }

        // notify the case manager that we're finished collecting everything
        fValid &= m_casemgr.validateEnd(ctx, errs);

        // TODO

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // TODO
        return fReachable;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("switch (");

        if (cond != null)
            {
            sb.append(cond.get(0));
            for (int i = 1, c = cond.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(cond.get(i));
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected List<AstNode>  cond;
    protected StatementBlock block;

    private transient CaseManager m_casemgr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "cond", "block");
    }
