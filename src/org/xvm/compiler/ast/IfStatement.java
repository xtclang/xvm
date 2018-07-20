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
 * An "if" statement.
 */
public class IfStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public IfStatement(Token keyword, ConditionalStatement cond, StatementBlock block)
        {
        this(keyword, cond, block, null);
        }

    public IfStatement(Token keyword, ConditionalStatement cond, StatementBlock stmtThen, Statement stmtElse)
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
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // let the conditional statement know that it is indeed being used as a condition
        cond.markConditional(Usage.If, new Label());

        boolean fScope = cond.isScopeRequired();
        if (fScope)
            {
            ctx = ctx.enterScope();
            }

        Context              ctxThen = ctx.fork();
        ConditionalStatement condNew = (ConditionalStatement) cond.validate(ctxThen, errs);
        if (condNew == null)
            {
            fValid = false;
            }
        else
            {
            cond = condNew;
            }

        Statement stmtThenNew = stmtThen.validate(ctxThen, errs);
        if (stmtThenNew == null)
            {
            fValid = false;
            }
        else
            {
            stmtThen = stmtThenNew;
            }

        Context ctxElse = ctx.fork();
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
        boolean fCompletes;
        if (cond.isAlwaysFalse() || cond.isAlwaysTrue())
            {
            // "if (false) {stmtThen}" is optimized out altogether.
            // "if (false) {stmtThen} else {stmtElse}" is compiled as "{stmtElse}"
            // "if (true) {stmtThen}" is compiled as "{stmtThen}"
            // "if (true) {stmtThen} else {stmtElse}" is compiled as "{stmtThen}"

            // the condition shouldn't produce any code, but it's checked here just in case it has
            // any errors to report
            cond.completes(ctx, false, code, errs);

            fCompletes = stmtThen.completes(ctx, fReachable & cond.isAlwaysTrue(), code, errs);

            if (stmtElse != null)
                {
                fCompletes |= stmtElse.completes(ctx, fReachable & cond.isAlwaysFalse(), code, errs);
                }
            }
        else
            {
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
            Label labelElse = cond.getLabel();
            Label labelExit = new Label();

            if (cond.isScopeRequired())
                {
                code.add(new Enter());
                }
            boolean fCompletesCond = cond.completes(ctx, fReachable, code, errs);

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
            if (cond.isScopeRequired())
                {
                code.add(new Exit());
                }

            fCompletes = fCompletesThen | fCompletesElse;
            }

        return fCompletes;
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

    protected Token                keyword;
    protected ConditionalStatement cond;
    protected Statement            stmtThen;
    protected Statement            stmtElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IfStatement.class, "cond", "stmtThen", "stmtElse");
    }
