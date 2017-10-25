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
 * An "if" statement.
 */
public class IfStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this(keyword, cond, block, null);
        }

    public IfStatement(Token keyword, Statement cond, StatementBlock stmtThen, Statement stmtElse)
        {
        this.keyword   = keyword;
        this.cond      = cond;
        this.stmtThen = stmtThen;
        this.stmtElse = stmtElse;
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
        return stmtElse == null ? stmtThen.getEndPosition() : stmtElse.getEndPosition();
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
        Context ctxThen;
        if (cond instanceof ExpressionStatement)
            {
            Expression exprCond = ((ExpressionStatement) cond).expr;
            fValid  = exprCond.validate(ctx, errs);
            fScope  = false;
            ctxThen = ctx.fork();
            }
        else
            {
            // an "if" statement has its own scope if it declares a variable, which it does behind
            // the scenes for a "conditional" invocation; the statement results in assignments that
            // are only assigned for the "then" branch
            cond.markAsIfCondition(stmtElse == null ? this.getEndLabel() : stmtElse.getBeginLabel());
            ctx.enterScope();
            fScope  = true;
            ctxThen = ctx.fork();
            fValid  = cond.validate(ctxThen, errs);
            }

        fValid &= stmtThen.validate(ctxThen, errs);

        if (stmtElse != null)
            {
            Context ctxElse = ctx.fork();
            fValid &= stmtElse.validate(ctxElse, errs);
            ctx.join(ctxThen, ctxElse);
            }

        if (fScope)
            {
            ctx.exitScope();
            }

        return fValid;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        Label   labelElse    = stmtElse == null ? this.getEndLabel() : stmtElse.getBeginLabel();
        boolean fAlwaysTrue  = false;
        boolean fAlwaysFalse = false;
        boolean fCompletes   = fReachable;
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
                fCompletes &= exprCond.canComplete();
                exprCond.generateConditionalJump(code, labelElse, false, errs);
                }
            }
        else
            {
            code.add(new Enter());
            // the conditional jump is actually encoded by the statement representing the condition,
            // based on the label that was provided during the validate stage
            fCompletes = cond.emit(ctx, fCompletes, code, errs);
            }

        boolean fReachesThen   = fCompletes && !fAlwaysFalse;
        boolean fCompletesThen = fReachesThen && stmtThen.emit(ctx, fReachesThen, code, errs);

        boolean fReachesElse   = fCompletes && !fAlwaysTrue;
        boolean fCompletesElse = fReachesElse;
        if (fReachesElse && stmtElse != null)
            {
            code.add(new Jump(getEndLabel()));
            fCompletesElse = stmtElse.emit(ctx, fReachesElse, code, errs);
            }

        return fCompletesThen | fCompletesElse;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("if (")
          .append(cond)
          .append(")\n")
          .append(indentLines(stmtThen.toString(), "    "));

        if (stmtElse != null)
            {
            if (stmtElse instanceof IfStatement)
                {
                sb.append("\nelse ")
                  .append(stmtElse);
                }
            else
                {
                sb.append("\nelse\n")
                  .append(indentLines(stmtElse.toString(), "    "));
                }
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     keyword;
    protected Statement cond;
    protected Statement stmtThen;
    protected Statement stmtElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "cond", "stmtThen", "stmtElse");
    }
