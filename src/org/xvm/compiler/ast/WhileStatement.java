package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while" or "do while" statement.
 */
public class WhileStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public WhileStatement(Token keyword, AstNode cond, StatementBlock block)
        {
        this(keyword, cond, block, block.getEndPosition());
        }

    public WhileStatement(Token keyword, AstNode cond, StatementBlock block, long lEndPos)
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
            m_labelContinue = label = new Label("continue_while_" + getLabelCounter());
            }
        return label;
        }

    public Label getRepeatLabel()
        {
        Label label = m_labelRepeat;
        if (label == null)
            {
            m_labelRepeat = label = new Label("repeat_while_" + getLabelCounter());
            }
        return label;
        }

    private int getLabelCounter()
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
        return lEndPos;
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

        ctx = ctx.enterLoop();

        if (isDoWhile())
            {
            // block comes first for "do..while()"
            block.suppressScope();
            Statement blockNew = block.validate(ctx, errs);
            if (blockNew == null)
                {
                fValid = false;
                }
            else
                {
                block = (StatementBlock) blockNew;
                }
            }

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
                cond = stmtNew;
                }
            }
        else
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

        if (!isDoWhile())
            {
            // block comes after for "while()"
            ctx = ctx.enterFork(true);
            Statement blockNew = block.validate(ctx, errs);
            if (blockNew == null)
                {
                fValid = false;
                }
            else
                {
                block = (StatementBlock) blockNew;
                }
            ctx = ctx.exit();
            }

        // if the condition itself required a scope, then complete that scope
        ctx = ctx.exit();

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean fCompletes = fReachable;
        boolean fDoWhile   = isDoWhile();

        if (cond instanceof Expression && ((Expression) cond).isConstantFalse())
            {
            if (!fDoWhile)
                {
                // while(false) {body} - optimized out completely (unreachable)
                block.completes(ctx, false, code, errs);
                return fCompletes;
                }

            // do {body} while(false)
            //
            //   ENTER
            //   [body]
            //   EXIT
            //   Continue:
            //   Break:
            code.add(new Enter());
            fCompletes = block.completes(ctx, fCompletes, code, errs);
            code.add(new Exit());
            code.add(getContinueLabel());
            return fCompletes;
            }

        if (cond instanceof Expression && ((Expression) cond).isConstantTrue())
            {
            // while(true) {body}
            // do {body} while(true)
            //
            //   ENTER
            //   Repeat:
            //   Continue:
            //   [body]
            //   JMP Repeat
            //   EXIT
            //   Break:
            code.add(new Enter());
            code.add(getRepeatLabel());
            code.add(getContinueLabel());
            block.suppressScope();
            fCompletes = block.completes(ctx, fCompletes, code, errs);
            code.add(new Jump(getRepeatLabel()));
            code.add(new Exit());
            return false;     // while(true) never completes naturally
            }

        if (fDoWhile)
            {
            // do {body} while(cond);
            //
            //   ENTER
            //   Repeat:
            //   [body]                 ; body's scope is explicitly suppressed
            //   Continue:
            //   [cond]
            //   JMP_TRUE cond Repeat
            //   EXIT
            //   Break:
            code.add(new Enter());
            code.add(getRepeatLabel());
            fCompletes = block.completes(ctx, fCompletes, code, errs);
            code.add(getContinueLabel());
            if (cond instanceof AssignmentStatement)
                {
                AssignmentStatement stmtCond = (AssignmentStatement) cond;
                fCompletes &= stmtCond.completes(ctx, fCompletes, code, errs);
                code.add(new JumpTrue(stmtCond.getConditionRegister(), getRepeatLabel()));
                }
            else
                {
                Expression exprCond = (Expression) cond;
                exprCond.generateConditionalJump(ctx, code, getRepeatLabel(), true, errs);
                fCompletes &= !exprCond.isAborting();
                }
            code.add(new Exit());
            return fCompletes;
            }

        // while(cond) {body}
        //
        //   ENTER                  ; omitted if no declarations
        //   [cond:decl]            ; omitted if no declarations
        //   JMP Continue
        //   Repeat:
        //   [body]
        //   Continue:
        //   [cond]
        //   JMP_TRUE cond Repeat
        //   EXIT                   ; omitted if no declarations
        //   Break:
        boolean fOwnScope  = false;
        if (cond instanceof AssignmentStatement && ((AssignmentStatement) cond).hasDeclarations())
            {
            fOwnScope = true;
            code.add(new Enter());
            for (VariableDeclarationStatement stmtDecl : ((AssignmentStatement) cond).takeDeclarations())
                {
                fCompletes &= stmtDecl.completes(ctx, fCompletes, code, errs);
                }
            }
        code.add(new Jump(getContinueLabel()));
        code.add(getRepeatLabel());
        fCompletes &= block.completes(ctx, fCompletes, code, errs);
        code.add(getContinueLabel());
        if (cond instanceof AssignmentStatement)
            {
            AssignmentStatement stmtCond = (AssignmentStatement) cond;
            fCompletes &= stmtCond.completes(ctx, fCompletes, code, errs);
            code.add(new JumpTrue(stmtCond.getConditionRegister(), getRepeatLabel()));
            }
        else
            {
            Expression exprCond = (Expression) cond;
            exprCond.generateConditionalJump(ctx, code, getRepeatLabel(), true, errs);
            fCompletes &= !exprCond.isAborting();
            }
        if (fOwnScope)
            {
            code.add(new Exit());
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

    protected Token          keyword;
    protected AstNode        cond;
    protected StatementBlock block;
    protected long           lEndPos;

    private static int s_nLabelCounter;
    private transient int   m_nLabel;
    private transient Label m_labelContinue;
    private transient Label m_labelRepeat;

    private static final Field[] CHILD_FIELDS = fieldsForNames(WhileStatement.class, "cond", "block");
    }
