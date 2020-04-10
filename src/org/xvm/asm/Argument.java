package org.xvm.asm;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ServiceContext;

/**
 * Represents any argument for an op, including constants, registers, and pre-defined
 * references like "this".
 */
public interface Argument
    {
    /**
     * @return the type of the argument, which is the value of the Referent type parameter from the
     *         implicit Ref/Var that this argument represents
     */
    TypeConstant getType();

    /**
     * @return true iff the argument refers to the local stack in the frame
     */
    boolean isStack();

    /**
     * Register all constants that this Argument depends on into the passed registry.
     *
     * @param registry  the code-local constant registry
     *
     * @return the Argument to use in place of this Argument
     */
    Argument registerConstants(Op.ConstantRegistry registry);

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
        return toIdString(arg, nArg, null);
        }

    /**
     * For debugging purposes, format the optional "arg" and arg index.
     *
     * @param arg     an optional Argument (could be null)
     * @param nArg    an argument index
     * @param aconst  (optional) an array of constants to retrieve constants by index from
     *
     * @return a String useful for debugging purposes
     */
    static String toIdString(Argument arg, int nArg, Constant[] aconst)
        {
        if (arg instanceof Constant)
            {
            return ((Constant) arg).getValueString();
            }

        if (arg instanceof Register)
            {
            return ((Register) arg).getIdString();
            }

        try
            {
            if (nArg <= Op.CONSTANT_OFFSET)
                {
                int ix = Op.CONSTANT_OFFSET - nArg;
                if (aconst != null)
                    {
                    return aconst[ix].getValueString();
                    }

                ServiceContext context = ServiceContext.getCurrentContext();
                if (context != null)
                    {
                    return context.getCurrentFrame().localConstants()[ix].getValueString();
                    }
                }
            }
        catch (Throwable e) {}

        return Register.getIdString(nArg);
        }
    }
