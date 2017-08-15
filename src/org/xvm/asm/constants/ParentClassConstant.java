package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent an auto-narrowing class constant for the parent class of a nested non-static
 * ("instance") inner class.
 */
public class ParentClassConstant
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
    public ParentClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iChild = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is an auto-narrowable parent class identity.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constChild  a TypeConstant that this constant represents the enclosing parent of
     */
    public ParentClassConstant(ConstantPool pool, IdentityConstant constChild)
        {
        super(pool);

        if (constChild == null)
            {
            throw new IllegalArgumentException("child class required");
            }

        if (!(constChild instanceof ParentClassConstant || constChild instanceof ThisClassConstant))
            {
            throw new IllegalArgumentException("child must be an auto-narrowable class identity," +
                    " either \"this:class\" or a parent class thereof" + " (child=" + constChild + ')');
            }

        m_constChild = constChild;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the IdentityConstant that this represents the parent class of
     */
    public IdentityConstant getChildClass()
        {
        return m_constChild;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    @Override
    public IdentityConstant getParentConstant()
        {
        return null;
        }

    @Override
    public String getName()
        {
        return m_constChild.getName() + ":parent";
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParentClass;
        }

    @Override
    protected Object getLocator()
        {
        return m_constChild;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constChild);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        return this.m_constChild.compareTo(((ParentClassConstant) that).m_constChild);
        }

    @Override
    public String getValueString()
        {
        return m_constChild.getValueString() + ":parent";
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constChild = (IdentityConstant) getConstantPool().getConstant(m_iChild);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constChild = (IdentityConstant) pool.register(m_constChild);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constChild));
        }

    @Override
    public String getDescription()
        {
        return "child=" + m_constChild;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constChild.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the child class constant.
     */
    private int m_iChild;

    /**
     * The child class that this is a parent class of.
     */
    private IdentityConstant m_constChild;
    }
