package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a register constant, which specifies a particular virtual machine register.
 */
public class RegisterConstant
        extends PseudoConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor used for deserialization.
     *
     * @param pool    the ConstantPool that will contain this Constant
     * @param format  the format of the Constant in the stream
     * @param in      the DataInput stream to read the Constant value from
     *
     * @throws IOException  if an issue occurs reading the Constant value
     */
    public RegisterConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iReg = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a register identifier.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param iReg  the register number
     */
    public RegisterConstant(ConstantPool pool, int iReg)
        {
        super(pool);

        if (iReg < 0 || iReg > 0xFF)    // arbitrary limit; basically just a sanity assertion
            {
            throw new IllegalArgumentException("register (" + iReg + ") out of range");
            }

        m_iReg = iReg;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Register;
        }

    @Override
    protected Object getLocator()
        {
        return Integer.valueOf(m_iReg);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_iReg - ((RegisterConstant) that).m_iReg;
        }

    @Override
    public String getValueString()
        {
        // unfortunately, there's no syntax for "register number", so use this pseudo-syntax
        return "#" + m_iReg;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_iReg);
        }

    @Override
    public String getDescription()
        {
        return "register=" + m_iReg;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_iReg;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The register index.
     */
    private int m_iReg;
    }
