package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * An "Iterable"-based "for" statement.
 */
public class ForEachStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForEachStatement(Token keyword, AssignmentStatement cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond = cond;
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
            m_labelContinue = label = new Label("continue_foreach_" + (++s_nLabelCounter));
            }
        return label;
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

        // ultimately, the condition has to be re-written, because it is inevitably short-hand for
        // a far more convoluted measure of syntactic sugar
        //
        //   1) L: for (T value : container.as(Iterator<T>)) {...}
        //
        //      VAR_I  #0 Iterator<T> ...       ; hidden variable that holds the Iterator
        //      VAR    #1 Boolean               ; hidden variable that holds the conditional result
        //      VAR_N  #2 T value;              ; the value
        //      VAR_IN #3 Boolean L.first True  ; optional label variable
        //      VAR_IN #4 Int L.count 0         ; optional label variable
        //      Repeat:
        //      Iterator.next() #0 -> #1 #2     ; assign the conditional result and the value
        //      JMP_F #1 Exit                   ; exit when the conditional result is false
        //      {...}
        //      Continue:
        //      MOV False #3                    ; no longer the L.first
        //      IP_INC #4                       ; increment the L.count
        //      JMP Repeat                      ; loop
        //      Exit:
        //
        //   2) for (T value : container.as(Range<T>)) {...}
        //
        //      VAR    #0 Boolean               ; hidden variable that holds the Range direction
        //      P_GET  ? Interval.reversed #0
        //      VAR    #1 T                     ; hidden variable that holds the Range limit
        //      VAR_N  #2 T value;              ; the current value
        //      JMP_T  #0 InitRev
        //      P_GET  ? Interval.upperBound #1 ; get the Range limit (for going forwards)
        //      P_GET  ? Interval.lowerBound #2 ; get the starting value
        //      JMP    InitDone
        //      InitRev:
        //      P_GET  ? Interval.lowerBound #1 ; get the Range limit (for going in reverse)
        //      P_GET  ? Interval.upperBound #2 ; get the starting value
        //      InitDone:
        //      VAR_IN #3 Boolean L.first True  ; optional label variable
        //      VAR_IN #4 Boolean L.last False  ; optional label variable
        //      VAR_IN #5 Int L.count 0         ; optional label variable
        //      Repeat:
        //      JMP_NE #2 #1 NotLast            ; compare current value to last value
        //      MOV True #4                     ; this is now the L.last
        //      NotLast:
        //      {...}
        //      Continue:
        //      JMP_T #4 Exit                   ; exit after the L.last
        //      MOV False #3                    ; no longer the L.first
        //      IP_INC #5                       ; increment the L.count
        //      JMP_T #0 Rev
        //      IP_INC #2                       ; increment the current value
        //      JMP Repeat                      ; loop
        //      Rev:
        //      IP_DEC #2                       ; decrement the current value
        //      JMP Repeat                      ; loop
        //      Exit:
        //
        //   3) for (T value          : container.as(Sequence<T>  )) {...}
        //
        //      TODO
        //
        //   4) for (K key            : container.as(Map     <K,T>)) {...}
        //
        //      TODO
        //
        //   5) for ((K key, T value) : container.as(Map     <K,T>)) {...}
        //
        //      TODO
        //
        //   6) for (T value          : container.as(Iterator<T>  )) {...}
        //
        //      TODO
        //

        AssignmentStatement stmtAsn = cond;
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

        // TODO decl goes here

        fCompletes = decl.completes(ctx, fCompletes, code, errs);

        Label labelRepeat = new Label("loop_for_" + getLabelId());
        code.add(labelRepeat);

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

        sb.append(keyword.getId().TEXT)
          .append(" (")
          .append(cond)
          .append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token               keyword;
    protected AssignmentStatement cond;
    protected StatementBlock      block;

    private static int   s_nLabelCounter;
    private        Label m_labelContinue;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForEachStatement.class, "cond", "block");
    }
