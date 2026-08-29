package org.xvm.asm.constants;


import org.xvm.asm.ConstantPool;


/**
 * Represent a binary floating point constant.
 */
public abstract sealed class FloatConstant
        extends ValueConstant<Float>
        permits Float16Constant, Float32Constant, Float8e4Constant, Float8e5Constant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool the ConstantPool that will contain this Constant
     */
    protected FloatConstant(ConstantPool pool) {
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
