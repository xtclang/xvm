package org.xvm.compiler.ast;


import java.util.Collections;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;

import static org.xvm.util.Handy.indentLines;


/**
 * The traditional "for" statement.
 * <p/>
 * TODO lots of short-circuit support. for expr condition, it goes to the for statement's exit label. for init & update, the short-circuit just advances to next. 
 */
public class ForStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForStatement(Token keyword, List<Statement> init, Expression expr,
                        List<Statement> update, StatementBlock block)
        {
        this.keyword = keyword;
        this.init    = init == null ? Collections.EMPTY_LIST : init;
        this.expr    = expr;
        this.update  = update == null ? Collections.EMPTY_LIST : update;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean canBreak()
        {
        return true;
        }

    @Override
    public boolean canContinue()
        {
        return true;
        }

    @Override
    public Label getContinueLabel()
        {
        Label label = m_labelContinue;
        if (label == null)
            {
            m_labelContinue = label = new Label("continue_for_" + getLabelId());
            }
        return label;
        }

    private int getLabelId()
        {
        int n = m_nLabel;
        if (n == 0)
            {
            m_nLabel = n = ++s_nLabelCounter;
            }
        return n;
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // the for() statement will represent its own scope
        ctx = ctx.enterScope();

        List<Statement> listInit = init;
        int             cInit    = listInit.size();
        for (int i = 0; i < cInit; ++i)
            {
            Statement stmtOld = listInit.get(i);
            Statement stmtNew = stmtOld.validate(ctx, errs);
            if (stmtNew == null)
                {
                fValid = false;
                }
            else if (stmtNew != stmtOld)
                {
                listInit.set(i, stmtNew);
                }
            }

        // TODO at some point, this will be changed to a list of AstNodes instead of a single expression
        Expression exprOld = expr;
        Expression exprNew = exprOld == null ? null : exprOld.validate(ctx, pool().typeBoolean(), errs);
        if (exprNew != exprOld)
            {
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                expr = exprNew;
                }
            }

        // the statement block does not need its own scope (because the for() statement is a scope)
        StatementBlock blockOld = block;
        blockOld.suppressScope();
        StatementBlock blockNew = (StatementBlock) blockOld.validate(ctx, errs);
        if (blockNew != blockOld)
            {
            if (blockNew == null)
                {
                fValid = false;
                }
            else
                {
                block = blockNew;
                }
            }

        List<Statement> listUpdate = update;
        int             cUpdate    = listUpdate.size();
        for (int i = 0; i < cUpdate; ++i)
            {
            Statement stmtOld = listUpdate.get(i);
            Statement stmtNew = stmtOld.validate(ctx, errs);
            if (stmtNew == null)
                {
                fValid = false;
                }
            else if (stmtNew != stmtOld)
                {
                listUpdate.set(i, stmtNew);
                }
            }

        // leaving the scope of the for() statement
        ctx = ctx.exitScope();

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;
        
        code.add(new Enter());
        
        List<Statement> listInit = init;
        int             cInit    = listInit.size();
        for (int i = 0; i < cInit; ++i)
            {
            fCompletes = listInit.get(i).completes(ctx, fCompletes, code, errs);
            }

        Label labelRepeat = new Label("loop_for_" + getLabelId());
        code.add(labelRepeat);

        if (expr != null)
            {
            expr.generateConditionalJump(ctx, code, getEndLabel(), false, errs);
            fCompletes &= !expr.isAborting();
            }

        fCompletes = block.completes(ctx, fCompletes, code, errs);

        code.add(getContinueLabel());

        List<Statement> listUpdate = update;
        int             cUpdate    = listUpdate.size();
        for (int i = 0; i < cUpdate; ++i)
            {
            fCompletes = listUpdate.get(i).completes(ctx, fCompletes, code, errs);
            }

        code.add(new Jump(labelRepeat));

        code.add(new Exit());

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("for (");

        if (init != null)
            {
            boolean first = true;
            for (Statement stmt : init)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append("; ");

        if (expr != null)
            {
            sb.append(expr);
            }

        sb.append("; ");

        if (update != null)
            {
            boolean first = true;
            for (Statement stmt : update)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token           keyword;
    protected List<Statement> init;
    protected Expression      expr;
    protected List<Statement> update;
    protected StatementBlock  block;

    private static int s_nLabelCounter;
    private transient int   m_nLabel;
    private transient Label m_labelRepeat;
    private transient Label m_labelContinue;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForStatement.class, "init", "expr", "update", "block");
    }
