package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.LinkerContext;

import org.xvm.util.Hash;

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
     * Construct a NotCondition.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constName  specifies the named condition; it is simply a name that is either defined
     *                   or undefined
     */
    public NamedCondition(ConstantPool pool, StringConstant constName)
        {
        super(pool);

        assert constName != null;
        m_constName = constName;
        }

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

    @Override
    protected void resolveConstants()
        {
        m_constName = (StringConstant) getConstantPool().getConstant(m_iName);
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
    public Relation calcRelation(ConditionalConstant that)
        {
        assert that.isTerminal();

        if (that instanceof NamedCondition)
            {
            return this.m_constName.equals(((NamedCondition) that).m_constName)
                    ? Relation.EQUIV
                    : Relation.INDEP;
            }

        return Relation.INDEP;
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ConditionNamed;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constName);
        }

    @Override
    protected Object getLocator()
        {
        return getName();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof NamedCondition))
            {
            return -1;
            }
        return getName().compareTo(((NamedCondition) that).getName());
        }

    @Override
    public String getValueString()
        {
        return "isSpecified(\"" + getName() + "\")";
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constName = (StringConstant) pool.register(m_constName);
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
    public int computeHashCode()
        {
        return Hash.of(m_constName);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the name.
     */
    private transient int m_iName;

    /**
     * The constant representing the name of this named condition.
     */
    private StringConstant m_constName;
    }