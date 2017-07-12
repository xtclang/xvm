package org.xvm.asm.constants;


import org.xvm.asm.Constant;


/**
 * A constant that starts as an unresolved constant, but is eventually resolved.
 *
 * @author cp 2017.07.12
 */
public interface ResolvableConstant
    {
    /**
     * @return the constant, iff it has been resolved; otherwise null
     */
    Constant getResolvedConstant();

    /**
     * Use the specified constant to resolve the ResolvableConstant.
     *
     * @param constant  the actual, underlying constant
     */
    void resolve(Constant constant);
    }
