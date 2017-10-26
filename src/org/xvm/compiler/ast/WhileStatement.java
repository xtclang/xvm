package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.SingletonConstant;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while" or "do while" statement.
 */
public class WhileStatement
        extends Statement
        implements Statement.Breakable, Statement.Continuable
    {
    // ----- constructors --------------------------------------------------------------------------

    public WhileStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this(keyword, cond, block, block.getEndPosition());
        }

    public WhileStatement(Token keyword, Statement cond, StatementBlock block, long lEndPos)
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
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        // the "cond" statement is one of:
        // ExpressionStatement - contains the expression that the "if" is evaluating
        // AssignmentStatement - contains an assignment in the form "expr : expr"
        // VariableDeclarationStatement - contains a declaration in the form "type name : expr"
        // TODO MultipleDeclarationStatement
        boolean fValid;
        boolean fScope;
        Context ctxBody;
        if (cond instanceof ExpressionStatement)
            {
            Expression exprCond = ((ExpressionStatement) cond).expr;
            fValid  = exprCond.validate(ctx, errs);
            fScope  = false;
            ctxBody = ctx.fork();
            }
        else
            {
            // an "if" statement has its own scope if it declares a variable, which it does behind
            // the scenes for a "conditional" invocation; the statement results in assignments that
            // are only assigned for the "then" branch
            m_labelRepeat = new Label();
            cond.markAsWhileCondition(m_labelRepeat);
            ctx.enterScope();
            fScope  = true;
            ctxBody = ctx.fork();
            fValid  = cond.validate(ctxBody, errs);
            }

        fValid &= block.validate(ctxBody, errs);

        if (fScope)
            {
            ctx.exitScope();
            }

        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        boolean    fDoWhile       = isDoWhile();
        boolean    fAlwaysTrue    = false;
        boolean    fAlwaysFalse   = false;
        Label      labelRepeat    = new Label();
        Label      labelContinue  = m_labelContinue == null ? new Label() : m_labelContinue;
        Label      labelBreak     = m_labelBreak    == null ? new Label() : m_labelBreak;
        Expression exprCond       = null;
        Statement  stmtDeclAssign = null;

        if (cond instanceof ExpressionStatement)
            {
            exprCond = ((ExpressionStatement) cond).expr;

            // handle situations in which the expression is always true or always false
            if (exprCond.isConstant())
                {
                Argument arg = exprCond.generateConstant(getConstantPool()
                        .ensureEcstasyTypeConstant("Boolean"), errs);

                // there are only two values that we're interested in; assume anything else
                // indicates a compiler error, and that's someone else's problem to deal with
                if (arg instanceof SingletonConstant)
                    {
                    String sClass = ((SingletonConstant) arg).getValue().getName();
                    fAlwaysTrue  = sClass.equals("True");
                    fAlwaysFalse = sClass.equals("False");
                    }
                }
            }
        else
            {
            stmtDeclAssign = cond;
            }

        boolean fCompletes = fReachable;
        if (fAlwaysFalse)
            {
            // while(false) is optimized out altogether.
            //
            // do-while(false) is assembled as:
            //   [body]
            //   Continue:
            //   Break:
            if (fDoWhile)
                {
                fCompletes &= block.emit(ctx, fReachable, code, errs);
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
            block.emit(ctx, fReachable, code, errs);
            code.add(new Jump(labelRepeat));
            code.add(labelBreak);
            fCompletes = false;     // while true never completes naturally
            }
        else if (exprCond != null)
            {
            // while(cond) is assembled as:
            //
            //   JMP Continue
            //   Repeat:
            //   [body]
            //   Continue:
            //   [cond]
            //   JMP_TRUE cond Repeat   // this line or similar would be generated as part of [cond]
            //   Break:
            //
            // do-while(cond) is assembled the same way, except without the initial unconditional JMP:
            //
            //   Repeat:
            //   [body]
            //   Continue:
            //   [cond]
            //   JMP_TRUE cond Repeat   // this line or similar would be generated as part of [assign]
            //   Break:
            if (!fDoWhile)
                {
                code.add(new Jump(labelContinue));
                }
            code.add(labelRepeat);
            fCompletes &= block.emit(ctx, fReachable, code, errs);            // TODO figure out "completes"
            code.add(labelContinue);
            exprCond.generateConditionalJump(code, labelRepeat, true, errs);
            code.add(labelBreak);
            }
        else if (fDoWhile)
            {
            // do-while(declAndOrAssign) is assembled as:
            //
            //   Repeat:
            //   [body]
            //   Continue:
            //   ENTER
            //   [declAndOrAssign]
            //   JMP_TRUE cond Repeat   // this line or similar would be generated as part of [decl..]
            //   Break:
            //   EXIT
            code.add(labelRepeat);
            fCompletes &= block.emit(ctx, fReachable, code, errs);
            code.add(labelContinue);
            code.add(new Enter());
            fCompletes &= stmtDeclAssign.emit(ctx, fReachable, code, errs);
            code.add(labelBreak);
            code.add(new Exit());
            }
        else
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
            // TODO decl portion
            code.add(new Jump(labelContinue));
            code.add(labelRepeat);
            fCompletes &= block.emit(ctx, fReachable, code, errs);
            code.add(labelContinue);
            // TODO assign portion
            code.add(labelBreak);
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
    protected Statement      cond;
    protected StatementBlock block;
    protected long           lEndPos;

    private Label m_labelRepeat;
    private Label m_labelBreak;
    private Label m_labelContinue;
    
    private static final Field[] CHILD_FIELDS = fieldsForNames(WhileStatement.class, "cond", "block");
    }
