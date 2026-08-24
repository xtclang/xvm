package org.xvm.asm.constants;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * Represent a constant whose purpose is to represent a constant value.
 */
public abstract sealed class ValueConstant
        extends Constant
        permits ArrayConstant, BFloat16Constant, ByteConstant, CharConstant,
                DecimalAutoConstant, DecimalConstant, FPNConstant, FSNodeConstant,
                FileStoreConstant, Float128Constant, Float64Constant, FloatConstant,
                IntConstant, LiteralConstant, MapConstant, MatchAnyConstant,
                RangeConstant, RegExConstant, SingletonConstant, StringConstant,
                UInt8ArrayConstant {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool    the ConstantPool that will contain this Constant
     */
    protected ValueConstant(ConstantPool pool) {
        super(pool);
    }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public TypeConstant getType() {
        // default implementation assumes that the Ecstasy class name is the same as the format name
        return getFormat().getType(getConstantPool());
    }

    /**
     * Obtain the value represented by this ValueConstant.
     *
     * @return the value of the constant (type-specific)
     */
    public abstract Object getValue();

    // ----- XvmStructure operations ---------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p/>
     * This method must be overridden by constant types which reference other constants.
     */
    @Override
    protected void registerConstants(ConstantPool pool) {
        pool.register(getType());
    }
}
