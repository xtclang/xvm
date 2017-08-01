package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.compiler.Lexer.isValidIdentifier;
import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents the type that is specified by a type parameter.
 *
 * @author cp 2017.06.27
 */
public class ParameterTypeConstant
        extends TypeConstant
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
    public ParameterTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iName = readIndex(in);
        }

    /**
     * Construct a constant whose value is a name that represents a data type parameter (which is a
     * specific data type at runtime).
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param sParamName  the simple name of the type parameter
     */
    public ParameterTypeConstant(ConstantPool pool, String sParamName)
        {
        super(pool);

        assert isValidIdentifier(sParamName);
        m_constName = pool.ensureCharStringConstant(sParamName);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the name of the type parameter
     */
    public String getName()
        {
        return m_constName.getValue();
        }

    /**
     * @return the name of the type parameter, as a CharStringConstant
     */
    public CharStringConstant getNameConstant()
        {
        return m_constName;
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParameterType;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constName);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ParameterTypeConstant that = (ParameterTypeConstant) obj;
        return this.m_constName.compareDetails(that.m_constName);
        }

    @Override
    public String getValueString()
        {
        return m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constName = (CharStringConstant) getConstantPool().getConstant(m_iName);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constName = (CharStringConstant) pool.register(m_constName);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_constName.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return -m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the parameter name constant.
     */
    private int m_iName;

    /**
     * The constant that holds the name of the type parameter.
     */
    private CharStringConstant m_constName;
    }
