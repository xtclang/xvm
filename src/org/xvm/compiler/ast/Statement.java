package org.xvm.compiler.ast;


import org.xvm.asm.MethodStructure;


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
     * @param code
     */
    public void emit(MethodStructure.Code code)
        {
        throw new UnsupportedOperationException("statement=" + getClass().getSimpleName());
        }
    }
