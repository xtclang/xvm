package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
import java.util.Set;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Handy;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Evaluates if a named condition is defined.
 */
public class NamedCondition
        extends ConditionalConstant
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
    public NamedCondition(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iName = readMagnitude(in);
        }

    /**
     * Construct a NotCondition.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constName  specifies the named condition; it is simply a name that is either defined
     *                   or undefined
     */
    public NamedCondition(ConstantPool pool, CharStringConstant constName)
        {
        super(pool);
        assert constName != null;
        m_constName = constName;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * Obtain the name of the option represented by this conditional.
     *
     * @return the name of the option that is either defined in the container or not
     */
    public String getName()
        {
        return m_constName.getValue();
        }


    // ----- ConditionalConstant methods -----------------------------------------------------------

    @Override
    public boolean evaluate(LinkerContext ctx)
        {
        return ctx.isSpecified(m_constName.getValue());
        }

    @Override
    public boolean isTerminal()
        {
        return true;
        }

    @Override
    public Set<ConditionalConstant> terminals()
        {
        return Collections.singleton(this);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionNamed;
        }

    @Override
    protected Object getLocator()
        {
        return getName();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return getName().compareTo(((org.xvm.asm.constants.NamedCondition) that).getName());
        }

    @Override
    public String getValueString()
        {
        return "isSpecified(\"" + getName() + "\")";
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
        return Handy.hashCode(m_constName);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the name.
     */
    private transient int m_iName;

    /**
     * The constant representing the name of this named condition.
     */
    private CharStringConstant m_constName;
    }
