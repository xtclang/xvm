package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Enter;
import org.xvm.asm.op.Exit;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * An "if" statement.
 */
public class IfStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, AstNode cond, StatementBlock block)
        {
        this(keyword, cond, block, null);
        }

    public IfStatement(Token keyword, AstNode cond, StatementBlock stmtThen, Statement stmtElse)
        {
        this.keyword  = keyword;
        this.cond     = cond;
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
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;
        boolean fScope = cond instanceof AssignmentStatement && ((AssignmentStatement) cond).hasDeclarations();

        if (fScope)
            {
            ctx = ctx.enterScope();
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
                fScope = stmtNew.hasDeclarations();
                if (stmtNew != stmtOld)
                    {
                    cond = stmtNew;
                    }
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

        Context   ctxThen     = ctx.fork(true);
        Statement stmtThenNew = stmtThen.validate(ctxThen, errs);
        if (stmtThenNew == null)
            {
            fValid = false;
            }
        else
            {
            stmtThen = stmtThenNew;
            }

        Context ctxElse = ctx.fork(false);
        if (stmtElse != null)
            {
            Statement stmtElseNew = stmtElse.validate(ctxElse, errs);
            if (stmtElseNew == null)
                {
                fValid = false;
                }
            else
                {
                stmtElse = stmtElseNew;
                }
            }

        // merge the contexts through the "then" and through the "else" (and note that the "else"
        // context always exists, even if we don't use it)
        ctx.join(ctxThen, ctxElse);

        // if the condition itself required a scope, then complete that scope
        if (fScope)
            {
            ctx = ctx.exitScope();
            }

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        if (cond instanceof Expression && ((Expression) cond).isConstant())
            {
            // "if (false) {stmtThen}" is optimized out altogether.
            // "if (false) {stmtThen} else {stmtElse}" is compiled as "{stmtElse}"
            // "if (true) {stmtThen}" is compiled as "{stmtThen}"
            // "if (true) {stmtThen} else {stmtElse}" is compiled as "{stmtThen}"
            if (((Expression) cond).isConstantTrue())
                {
                return stmtThen.completes(ctx, fReachable, code, errs);
                }
            else
                {
                assert ((Expression) cond).isConstantFalse();
                return stmtElse == null
                        ? fReachable
                        : stmtElse.completes(ctx, fReachable, code, errs);
                }
            }

        // "if (cond) {stmtThen}" is compiled as:
        //
        //   ENTER                  // iff cond specifies that it needs a scope
        //   [cond]
        //   JMP_FALSE cond Else    // this line or similar would be generated as part of [cond]
        //   [stmtThen]
        //   Else:
        //   Exit:
        //   EXIT                   // iff cond specifies that it needs a scope
        //
        // "if (cond) {stmtThen} else {stmtElse}" is compiled as:
        //
        //   ENTER                  // iff cond specifies that it needs a scope
        //   [cond]
        //   JMP_FALSE cond Else    // this line or similar would be generated as part of [cond]
        //   [stmtThen]
        //   JMP Exit
        //   Else:
        //   [stmtElse]
        //   Exit:
        //   EXIT                   // iff cond specifies that it needs a scope
        Label labelElse = new Label();        // TODO make this a field and use as the short circuit label for cond
        Label labelExit = new Label();

        boolean fScope = cond instanceof AssignmentStatement && ((AssignmentStatement) cond).hasDeclarations();
        if (fScope)
            {
            code.add(new Enter());
            }

        boolean fCompletesCond;
        if (cond instanceof AssignmentStatement)
            {
            AssignmentStatement stmtCond = (AssignmentStatement) cond;
            fCompletesCond = stmtCond.completes(ctx, fReachable, code, errs);
            code.add(new JumpFalse(stmtCond.getConditionRegister(), labelElse));
            }
        else
            {
            Expression exprCond = (Expression) cond;
            fCompletesCond = !exprCond.isAborting();
            exprCond.generateConditionalJump(ctx, code, labelElse, false, errs);
            }

        boolean fCompletesThen = stmtThen.completes(ctx, fCompletesCond, code, errs);
        if (stmtElse != null)
            {
            code.add(new Jump(labelExit));
            }

        code.add(labelElse);
        boolean fCompletesElse = stmtElse == null
                ? fCompletesCond
                : stmtElse.completes(ctx, fCompletesCond, code, errs);

        code.add(labelExit);
        if (fScope)
            {
            code.add(new Exit());
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
    protected AstNode   cond;
    protected Statement stmtThen;
    protected Statement stmtElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "cond", "stmtThen", "stmtElse");
    }
