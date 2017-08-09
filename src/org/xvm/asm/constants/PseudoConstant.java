package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant whose purpose is to represent a level of indirection.
 */
public abstract class PseudoConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool    the ConstantPool that will contain this Constant
     */
    protected PseudoConstant(ConstantPool pool)
        {
        super(pool);
        }
    }
