package org.xvm.asm.constants;


import org.xvm.asm.ConstantPool;


/**
 * Represent a binary floating point constant.
 */
public abstract class FloatConstant
        extends ValueConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool the ConstantPool that will contain this Constant
     */
    protected FloatConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return  the constant's value as a Java Float
     */
    @Override
    public abstract Float getValue();
    }
