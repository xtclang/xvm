package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;

import org.xvm.util.Hash;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Constant whose purpose is to represent a run-time register.
 */
public class RegisterConstant
        extends FrameDependentConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param reg   the register
     */
    public RegisterConstant(ConstantPool pool, Register reg)
        {
        super(pool);

        m_reg  = reg;
        f_nReg = reg.getIndex();
        }

    /**
     * Constructor used for deserialization.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param in    the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public RegisterConstant(ConstantPool pool, DataInput in)
            throws IOException
        {
        super(pool);

        f_nReg = readPackedInt(in);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the register represented by this RegisterConstant (only available at compile time)
     */
    public Register getRegister()
        {
        return m_reg;
        }

    /**
     * @return the register index represented by this RegisterConstant
     */
    public int getRegisterIndex()
        {
        return m_reg == null
                ? f_nReg
                : m_reg.getIndex();
        }


    // ----- FrameDependentConstant methods --------------------------------------------------------

    @Override
    public ObjectHandle getHandle(Frame frame)
        {
        try
            {
            int nReg = getRegisterIndex();
            return nReg == Op.A_DEFAULT ? ObjectHandle.DEFAULT : frame.getArgument(nReg);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return new DeferredCallHandle(e.getExceptionHandle());
            }
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Register;
        }

    @Override
    public boolean containsUnresolved()
        {
        return getRegisterIndex() >= Register.UNKNOWN;
        }

    @Override
    protected int compareDetails(Constant constant)
        {
        assert getRegisterIndex() < Register.UNKNOWN;
        return constant instanceof RegisterConstant that
                ? this.getRegisterIndex() - that.getRegisterIndex()
                : -1;
        }

    @Override
    public TypeConstant getType()
        {
        TypeConstant type = m_reg == null ? null : m_reg.getType();
        return type == null ? getConstantPool().typeObject() : type;
        }

    @Override
    public String getValueString()
        {
        int nReg = getRegisterIndex();

        return "Register " + (nReg >= Register.UNKNOWN ? "?" : String.valueOf(nReg));
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        assert !m_reg.isUnknown();

        writePackedLong(out, m_reg.getIndex());
        }

    @Override
    public String getDescription()
        {
        return getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(f_nReg);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The register index.
     */
    private final int f_nReg;

    /**
     * The register.
     */
    private transient Register m_reg;
    }