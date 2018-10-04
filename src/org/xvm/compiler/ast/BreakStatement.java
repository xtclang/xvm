package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Jump;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A break statement represents the "break" keyword.
 */
public class BreakStatement
        extends ShortCircuitStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public BreakStatement(Token keyword, Token name)
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
                log(errs, Severity.ERROR, Compiler.MISSING_GROUND_LABEL, getLabeledName());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_GROUND_STATEMENT);
                }
            return null;
            }

        setJumpLabel(stmtTarget.ensureBreakLabel(ctx));

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        code.add(new Jump(getJumpLabel()));
        return false;
        }
    }
