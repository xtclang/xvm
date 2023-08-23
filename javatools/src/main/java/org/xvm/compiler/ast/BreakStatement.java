package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.BreakStmtAST;

import org.xvm.asm.op.Jump;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A break statement represents the "break" keyword.
 */
public class BreakStatement
        extends GotoStatement
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
                log(errs, Severity.ERROR, Compiler.MISSING_GOTO_LABEL, getLabeledName());
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.MISSING_GOTO_TARGET);
                }
            return null;
            }

        setJumpLabel(stmtTarget.ensureBreakLabel(this, ctx));

        // a break statement never advances
        ctx.setReachable(false);

        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        code.add(new Jump(getJumpLabel()));

        ctx.getHolder().setAst(this, new BreakStmtAST<>(getTargetDepth()));
        return false;
        }
    }