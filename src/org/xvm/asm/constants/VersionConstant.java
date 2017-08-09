package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Version;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a version number.
 */
public class VersionConstant
        extends ValueConstant
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
    public VersionConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iVer = readIndex(in);
        }

    /**
     * Construct a constant whose value is a PackedInteger.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param ver   the version
     */
    public VersionConstant(ConstantPool pool, Version ver)
        {
        super(pool);

        assert ver != null;
        m_ver = ver;
        }


    // ----- ValueConstant methods -----------------------------------------------------------------

    /**
     * {@inheritDoc}
     * @return the fully qualified version number
     */
    @Override
    public Version getValue()
        {
        return m_ver;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Version;
        }

    @Override
    public Object getLocator()
        {
        return m_ver.toString();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_ver.compareTo(((VersionConstant) that).m_ver);
        }

    @Override
    public String getValueString()
        {
        return m_ver.toString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_ver = new Version(((StringConstant) pool.getConstant(m_iVer)).getValue());
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        pool.register(pool.ensureCharStringConstant(m_ver.toString()));
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, getConstantPool().ensureCharStringConstant(m_ver.toString()).getPosition());
        }

    @Override
    public String getDescription()
        {
        return "version=" + getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_ver.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the version String
     * for this version.
     */
    private int m_iVer;

    /**
     * The version indicator for this version.
     */
    private Version m_ver;
    }
