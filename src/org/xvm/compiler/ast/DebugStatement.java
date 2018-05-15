package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.op.Break;

import org.xvm.compiler.Token;


/**
 * A "DEBUG" statement is a temporary construct that generates the BREAK op.
 */
public class DebugStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public DebugStatement(Token keyword)
        {
        this.keyword = keyword;
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
        return keyword.getEndPosition();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        return this;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        code.add(new Break());
        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return keyword.getValueText();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token keyword;
    }
