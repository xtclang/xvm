package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Parameter constant. A Parameter is a combination of a type and a name, representing a
 * type parameter, a method invocation parameter, and a return value.
 * <p/>
 * Note that there is no separate "parameter constant" structure defined in the XVM file format
 * itself, so a ParameterConstant does not have either a "format" or a "position". The choice to use
 * the Constant interface as the basis for managing parameter information was made simply for
 * consistency, and because parameter information is a sub-component of an actual constant
 * structure.
 *
 * REVIEW should there be a "default value"?
 * REVIEW should there be a "type constraint" capability? e.g. param'd types
 */
public class ParameterConstant
        extends Constant
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
    public ParameterConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iType  = readMagnitude(in);
        m_iName  = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a Parameter definition.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  the type of the parameter
     * @param sName      the parameter name
     */
    public ParameterConstant(ConstantPool pool, TypeConstant constType, String sName)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("parameter type required");
            }

        if (sName == null)
            {
            throw new IllegalArgumentException("parameter name required");
            }

        m_constType = constType;
        m_constName = pool.ensureCharStringConstant(sName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Get the type of the parameter.
     *
     * @return the parameter type
     */
    public TypeConstant getParameterType()
        {
        return m_constType;
        }

    /**
     * Get the name of the parameter.
     *
     * @return the parameter name
     */
    public String getName()
        {
        return m_constName.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.Parameter;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constType.compareDetails(((ParameterConstant) that).m_constType);
        if (n == 0)
            {
            n = this.m_constName.compareDetails(((ParameterConstant) that).m_constName);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_constType.getValueString() + ' ' + m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        final ConstantPool pool = getConstantPool();
        m_constType = (TypeConstant) pool.getConstant(m_iType);
        m_constName = (CharStringConstant) pool.getConstant(m_iName);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);
        m_constName = (CharStringConstant) pool.register(m_constName);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constType.getPosition());
        writePackedLong(out, m_constName.getPosition());
        }

    @Override
    public String getDescription()
        {
        return "parameter=" + m_constName.getValue() + ", type=" + m_constType.getValueString();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constName.hashCode() * 17 + m_constType.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the type of this
     * parameter.
     */
    private int m_iType;

    /**
     * During disassembly, this holds the index of the constant that specifies the name of this
     * parameter.
     */
    private int m_iName;

    /**
     * The constant that represents the type of this parameter.
     */
    private TypeConstant m_constType;

    /**
     * The constant that holds the name of the parameter.
     */
    private CharStringConstant m_constName;
    }
