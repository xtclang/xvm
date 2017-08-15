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
 * Represent a type constant for a named instance child of an auto-narrowing type.
 */
public class ChildTypeConstant
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
    public ChildTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iParent = readMagnitude(in);
        m_iChild  = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is an auto-narrowing child type.
     *
     * @param pool         the ConstantPool that will contain this Constant
     * @param constParent  the auto-narrowing parent type of this type
     * @param sChild       the name of this child
     */
    public ChildTypeConstant(ConstantPool pool, TypeConstant constParent, String sChild)
        {
        super(pool);

        if (constParent == null)
            {
            throw new IllegalArgumentException("parent type required");
            }
        if (!(constParent instanceof ChildTypeConstant) && !(constParent instanceof ClassTypeConstant
                && ((ClassTypeConstant) constParent).getClassConstant() instanceof ThisClassConstant))
            {
            throw new IllegalArgumentException("type must be \"this:type\" or a parent type thereof"
                    + " (type=" + constParent + ')');
            }

        m_constParent = constParent;
        m_constChild  = pool.ensureCharStringConstant(sChild);
        }


    // ----- type-specific functionality -----------------------------------------------------------

    /**
     * @return the TypeConstant that this type is the parent type of
     */
    public TypeConstant getParentType()
        {
        return m_constParent;
        }

    public String getName()
        {
        return (String) m_constChild.getValue();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ChildType;
        }

    @Override
    protected Object getLocator()
        {
        // cache the child constants for children of the this:type:public constant
        return m_constParent instanceof ClassTypeConstant && ((ClassTypeConstant) m_constParent).getAccess() == Access.PUBLIC
                ? m_constChild.getValue()
                : null;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constParent);
        visitor.accept(m_constChild);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constParent.compareTo(((ChildTypeConstant) that).m_constParent);
        if (n == 0)
            {
            n = this.m_constChild.compareTo(((ChildTypeConstant) that).m_constChild);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_constParent.getValueString() + '.' + getName();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constParent = (TypeConstant      ) getConstantPool().getConstant(m_iParent);
        m_constChild  = (StringConstant) getConstantPool().getConstant(m_iChild );
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constParent = (TypeConstant      ) pool.register(m_constParent);
        m_constChild  = (StringConstant) pool.register(m_constChild );
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constParent));
        writePackedLong(out, indexOf(m_constChild));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_constParent.hashCode() ^ m_constChild.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the parent type constant.
     */
    private int m_iParent;

    /**
     * During disassembly, this holds the index of the child name constant.
     */
    private int m_iChild;

    /**
     * The auto-narrowing type that is a parent type of this.
     */
    private TypeConstant m_constParent;

    /**
     * The name of the child.
     */
    private StringConstant m_constChild;
    }
