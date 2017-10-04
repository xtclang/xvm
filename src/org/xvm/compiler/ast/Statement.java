package org.xvm.compiler.ast;


import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.compiler.ErrorListener;


/**
 * Base class for all Ecstasy statements.
 *
 * @author cp 2017.03.28
 */
public abstract class Statement
        extends AstNode
    {
    /**
     * Generate assembly code for the statement.
     *
     * @param code  the code object to which the assembly is added
     * @param errs  the error listener to log to
     */
    public void emit(Code code, ErrorListener errs)
        {
        throw new UnsupportedOperationException("statement=" + getClass().getSimpleName());
        }
    }
