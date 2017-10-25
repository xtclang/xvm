package org.xvm.compiler.ast;


import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while" or "do while" statement.
 *
 * @author cp 2017.04.09
 */
public class WhileStatement
        extends Statement
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
            cond.markAsIfCondition(getEndLabel());
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
        Label   labelExit      = getEndLabel();
        boolean fAlwaysTrue    = false;
        boolean fAlwaysFalse   = false;
        boolean fCompletesCond = fReachable;
        if (cond instanceof ExpressionStatement)
            {
            Expression exprCond = ((ExpressionStatement) cond).expr;

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
            // else if (...) TODO make sure ConditionalConstant is handled correctly (JMP_COND)
            else
                {
                fCompletesCond &= exprCond.canComplete();
                exprCond.generateConditionalJump(code, labelExit, false, errs);
                }
            }
        else
            {
            code.add(new Enter());
            // the conditional jump is actually encoded by the statement representing the condition,
            // based on the label that was provided during the validate stage
            fCompletesCond = cond.emit(ctx, fCompletesCond, code, errs);
            }

        boolean fReachesBody   = fCompletesCond && !fAlwaysFalse;
        boolean fCompletesBody = fReachesBody && block.emit(ctx, fReachesBody, code, errs);

        return fCompletesBody || fCompletesCond && fAlwaysFalse;
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(WhileStatement.class, "cond", "block");
    }
