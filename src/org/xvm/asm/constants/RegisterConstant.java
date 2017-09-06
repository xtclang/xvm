package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

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
        m_iMethod = readMagnitude(in);
        m_iReg    = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a register identifier.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param iReg  the register number
     */
    public RegisterConstant(ConstantPool pool, MethodConstant constMethod, int iReg)
        {
        super(pool);

        if (constMethod == null)
            {
            throw new IllegalArgumentException("method required");
            }

        if (iReg < 0 || iReg > 0xFF)    // arbitrary limit; basically just a sanity assertion
            {
            throw new IllegalArgumentException("register (" + iReg + ") out of range");
            }

        m_constMethod = constMethod;
        m_iReg        = iReg;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Register;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constMethod);
        }

    @Override
    protected Object getLocator()
        {
        return m_iReg == 0
                ? m_constMethod
                : null;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = m_constMethod.compareTo(((RegisterConstant) that).m_constMethod);
        if (n == 0)
            {
            n = this.m_iReg - ((RegisterConstant) that).m_iReg;;
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        MethodStructure method = (MethodStructure) m_constMethod.getComponent();
        return method == null
                ? "#" + m_iReg
                : method.getParam(m_iReg).getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constMethod = (MethodConstant) getConstantPool().getConstant(m_iMethod);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constMethod = (MethodConstant) pool.register(m_constMethod);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constMethod.getPosition());
        writePackedLong(out, m_iReg);
        }

    @Override
    public String getDescription()
        {
        return "method=" + m_constMethod + ", register=" + m_iReg;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constMethod.hashCode() + m_iReg;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The index of the MethodConstant while the RegisterConstant is being deserialized.
     */
    private transient int m_iMethod;

    /**
     * The MethodConstant for the method containing the register.
     */
    private MethodConstant m_constMethod;

    /**
     * The register index.
     */
    private int m_iReg;
    }
