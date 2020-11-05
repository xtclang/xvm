package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Register;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;


/**
 * Constant whose purpose is to represent a run-time register.
 */
public class RegisterConstant
        extends Constant
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
        m_nReg = reg.isUnknown() ? Register.UNKNOWN : reg.getIndex();
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

        m_nReg = in.readUnsignedShort();
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
        return m_nReg == Register.UNKNOWN
                ? m_reg.isUnknown()
                    ? Register.UNKNOWN
                    : m_reg.getIndex()
                : m_nReg;
        }

    /**
     * Obtain the ObjectHandle for this RegisterConstant.
     *
     * @return the ObjectHandle (can be DeferredCallHandle)
     */
    public ObjectHandle getHandle(Frame frame)
        {
        try
            {
            return frame.getArgument(getRegisterIndex());
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
        return getRegisterIndex() == Register.UNKNOWN;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        assert getRegisterIndex() != Register.UNKNOWN;
        return that instanceof RegisterConstant
                ? this.getRegisterIndex() - ((RegisterConstant) that).getRegisterIndex()
                : -1;
        }

    @Override
    public String getValueString()
        {
        int nReg = getRegisterIndex();

        return "Register " + (nReg == Register.UNKNOWN ? "?" : String.valueOf(nReg));
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        out.writeShort(m_reg.getIndex());
        }

    @Override
    public String getDescription()
        {
        return getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return 1;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The register index.
     */
    private int m_nReg;

    /**
     * The register.
     */
    private transient Register m_reg;
    }
