package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.compiler.Token;


/**
 * Represent a constant that will eventually be replaced with a value constant.
 */
public class DeferredValueConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real constant.
     *
     * @param pool  the ConstantPool that this Constant should belong to, even though it will never
     *              contain it while it's unresolved and will immediately replace it as it becomes
     *              resolved
     */
    public DeferredValueConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.DeferredValue;
        }

    @Override
    public boolean containsUnresolved()
        {
        return true;
        }

    @Override
    public Constant apply(Token.Id op, Constant that)
        {
        return this;
        }

    @Override
    public Constant convertTo(TypeConstant typeOut)
        {
        return this;
        }

    @Override
    protected void setPosition(int iPos)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeConstant getType()
        {
        return getConstantPool().typeObject();
        }

    @Override
    public String getValueString()
        {
        return "";
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this == that ? 0 : -1;
        }

    @Override
    public int computeHashCode()
        {
        return 0;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        throw new IllegalStateException();
        }

    @Override
    public String getDescription()
        {
        return getValueString();
        }
    }