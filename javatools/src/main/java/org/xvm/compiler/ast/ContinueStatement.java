package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Jump;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A continue statement represents the "continue" keyword.
 */
public class ContinueStatement
        extends GotoStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ContinueStatement(Token keyword, Token name)
        {
        super(keyword, name);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        Statement stmtTarget = getTargetStatement();
        if (stmtTarget == null)
            {
            if (isLabeled())
                {
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.MISSING_GOTO_LABEL, getLabeledName());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_GOTO_TARGET);
                }
            return null;
            }
        else if (!stmtTarget.isNaturalGotoStatementTarget())
            {
            log(errs, Severity.ERROR, Compiler.ILLEGAL_CONTINUE_TARGET);
            return null;
            }

        setJumpLabel(stmtTarget.ensureContinueLabel(this, ctx));

        // a continue statement never advances; while it may _appear_ to do so in a switch
        // statement, that is actually a jump to the next case group
        ctx.setReachable(false);

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, AstHolder holder,
                           ErrorListener errs)
        {
        code.add(new Jump(getJumpLabel()));
        return false;
        }
    }
