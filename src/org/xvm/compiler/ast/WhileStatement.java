package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.ConditionalStatement.Usage;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while" or "do while" statement.
 */
public class WhileStatement
        extends Statement
        implements Statement.Breakable, Statement.Continuable
    {
    // ----- constructors --------------------------------------------------------------------------

    public WhileStatement(Token keyword, ConditionalStatement cond, StatementBlock block)
        {
        this(keyword, cond, block, block.getEndPosition());
        }

    public WhileStatement(Token keyword, ConditionalStatement cond, StatementBlock block, long lEndPos)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this is a do-while loop, and not just a while loop
     */
    public boolean isDoWhile()
        {
        return keyword.getId() == Token.Id.DO;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- Breakable interface -------------------------------------------------------------------

    @Override
    public Label getBreakLabel()
        {
        Label label = m_labelBreak;
        if (label == null)
            {
            m_labelBreak = label = new Label();
            }
        return label;
        }


    // ----- Continuable interface -----------------------------------------------------------------

    @Override
    public Label getContinueLabel()
        {
        Label label = m_labelContinue;
        if (label == null)
            {
            m_labelContinue = label = new Label();
            }
        return label;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        // let the conditional statement know that it is indeed being used as a condition
        cond.markConditional(Usage.While, new Label());

        // a "while" or "do-while" statement has its own scope if it declares a variable, which
        // it does behind the scenes for a "conditional" invocation
        boolean fScope = cond.isScopeRequired();
        if (fScope)
            {
            ctx = ctx.enterScope();
            }

        boolean fValid = cond.validate(ctx, errs);

        // TODO consider what to do when the condition is always true (is a fork still required?)
        Context ctxBlock = ctx.fork();
        fValid &= block.validate(ctxBlock, errs);
        ctx.join(ctxBlock);

        if (fScope)
            {
            ctx = ctx.exitScope();
            }

        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean    fDoWhile      = isDoWhile();
        boolean    fOwnScope     = cond.isScopeRequired();
        boolean    fAlwaysTrue   = cond.isAlwaysTrue();
        boolean    fAlwaysFalse  = cond.isAlwaysFalse();
        Label      labelRepeat   = cond.getLabel();
        Label      labelContinue = m_labelContinue == null ? new Label() : m_labelContinue;
        Label      labelBreak    = m_labelBreak    == null ? new Label() : m_labelBreak;

        boolean fCompletes = fReachable;
        if (fAlwaysFalse)
            {
            // while(false) is optimized out altogether.
            //
            // do-while(false) is assembled as:
            //   [body]
            //   Continue:
            //   Break:
            fCompletes &= block.completes(ctx, fReachable & fDoWhile, code, errs);
            if (fDoWhile)
                {
                code.add(labelContinue);
                code.add(labelBreak);
                }
            }
        else if (fAlwaysTrue)
            {
            // while(true) and do-while(true) are both assembled as:
            //
            //   Repeat:
            //   Continue:
            //   [body]
            //   JMP Repeat
            //   Break:
            code.add(labelRepeat);
            code.add(labelContinue);
            block.completes(ctx, fReachable, code, errs);
            code.add(new Jump(labelRepeat));
            code.add(labelBreak);
            fCompletes = false;     // while true never completes naturally
            }
        else if (!fDoWhile && fOwnScope)
            {
            // while(declAndOrAssign) is assembled as:
            //
            //   ENTER
            //   [decl]
            //   JMP Continue
            //   Repeat:
            //   [body]
            //   Continue:
            //   [assign]
            //   JMP_TRUE cond Repeat   // this line or similar would be generated as part of [assign]
            //   Break:
            //   EXIT
            code.add(new Enter());
            fCompletes &= cond.onlyDeclarations().completes(ctx, fReachable, code, errs);
            code.add(new Jump(labelContinue));
            code.add(labelRepeat);
            fCompletes &= block.completes(ctx, fReachable, code, errs);
            code.add(labelContinue);
            fCompletes &= cond.nonDeclarations().completes(ctx, fReachable, code, errs);
            code.add(labelBreak);
            code.add(new Exit());
            }
        else
            {
            // while(cond)              do-while(cond)              do-while(declAndOrAssign)
            //
            //   JMP Continue
            //   Repeat:                  Repeat:                     Repeat:
            //   [body]                   [body]                      [body]
            //   Continue:                Continue:                   Continue:
            //                                                        ENTER
            //   [cond]                   [cond]                      [declAndOrAssign]
            //  +JMP_TRUE cond Repeat    +JMP_TRUE cond Repeat        JMP_TRUE cond Repeat
            //   Break:                   Break:                      Break:
            //                                                        EXIT
            if (!fDoWhile)
                {
                code.add(new Jump(labelContinue));
                }
            code.add(labelRepeat);
            fCompletes &= block.completes(ctx, fReachable, code, errs);
            code.add(labelContinue);
            if (fOwnScope)
                {
                code.add(new Enter());
                }
            fCompletes &= cond.completes(ctx, fReachable, code, errs);
            code.add(labelBreak);
            if (fOwnScope)
                {
                code.add(new Exit());
                }
            }

        return fCompletes;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (keyword.getId() == Token.Id.WHILE || keyword.getId() == Token.Id.FOR)
            {
            sb.append(keyword.getId().TEXT)
              .append(" (");

            sb.append(cond)
              .append(")\n");

            sb.append(indentLines(block.toString(), "    "));
            }
        else
            {
            assert keyword.getId() == Token.Id.DO;

            sb.append("do")
              .append('\n')
              .append(indentLines(block.toString(), "    "))
              .append("\nwhile (");

            sb.append(cond)
                    .append(");");
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return keyword.getId().TEXT;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                     keyword;
    protected ConditionalStatement      cond;
    protected StatementBlock            block;
    protected long                      lEndPos;

    private Label m_labelBreak;
    private Label m_labelContinue;

    private static final Field[] CHILD_FIELDS = fieldsForNames(WhileStatement.class, "cond", "block");
    }
