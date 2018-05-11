package org.xvm.asm;

import org.xvm.asm.constants.TypeConstant;

/**
 * Represents any argument for an op, including constants, registers, and pre-defined
 * references like "this".
 */
public interface Argument
    {
    /**
     * @return the type of the argument, which is the value of the RefType type parameter from the
     *         implicit Ref/Var that this argument represents
     */
    TypeConstant getType();

    /**
     * For debugging purposes, format the optional "arg" and arg index.
     *
     * @param arg   an optional Argument (could be null)
     * @param nArg  an argument index
     *
     * @return a String useful for debugging purposes
     */
    static String toIdString(Argument arg, int nArg)
        {
        if (arg instanceof Constant)
            {
            return ((Constant) arg).getValueString();
            }

        if (arg instanceof Register)
            {
            return ((Register) arg).getIdString();
            }

        return Register.getIdString(nArg);
        }
    }
