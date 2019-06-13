package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Jump;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A break statement represents the "continue" keyword.
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
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.MISSING_GROUND_LABEL, getLabeledName());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_GROUND_STATEMENT);
                }
            return null;
            }
        else if (!stmtTarget.isNaturalGotoStatementTarget())
            {
            log(errs, Severity.ERROR, Compiler.ILLEGAL_CONTINUE_TARGET);
            return null;
            }

        setJumpLabel(stmtTarget.ensureContinueLabel(ctx));

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        code.add(new Jump(getJumpLabel()));
        return false;
        }
    }
