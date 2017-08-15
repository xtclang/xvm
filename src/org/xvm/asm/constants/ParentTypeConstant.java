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
 * Represent a type constant for the parent class of a nested non-static ("instance") inner class.
 */
public class ParentTypeConstant
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
    public ParentTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iChild = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a parent type.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  a TypeConstant that this constant represents the enclosing parent of
     */
    public ParentTypeConstant(ConstantPool pool, TypeConstant constType)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }
        if (!(constType instanceof ParentTypeConstant) && !(constType instanceof ClassTypeConstant
                && ((ClassTypeConstant) constType).getClassConstant() instanceof ThisClassConstant))
            {
            throw new IllegalArgumentException("type must be \"this:type\" or a parent type thereof"
                    + " (type=" + constType + ')');
            }

        m_constChild = constType;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the TypeConstant that this type is the parent type of
     */
    public TypeConstant getChildType()
        {
        return m_constChild;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return true;
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParentType;
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
        return this.m_constChild.compareTo(((ParentTypeConstant) that).m_constChild);
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
        m_constChild = (TypeConstant) getConstantPool().getConstant(m_iChild);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constChild = (TypeConstant) pool.register(m_constChild);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constChild));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constChild.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the type constant.
     */
    private int m_iChild;

    /**
     * The type that this is a parent type of.
     */
    private TypeConstant m_constChild;
    }
