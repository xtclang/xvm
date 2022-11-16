package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Class constant, but one that may be decorated with additional annotations, type
 * parameters, and other information in addition to the underlying ClassConstant.
 *
 * @see TypeConstant#isDecoratedClass()
 */
public class DecoratedClassConstant
        extends IdentityConstant
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
    public DecoratedClassConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);
        m_iType = readMagnitude(in);
        }

    /**
     * Construct a constant whose value is a class identifier.
     *
     * @param pool  the ConstantPool that will contain this Constant
     * @param type  the Class type
     */
    public DecoratedClassConstant(ConstantPool pool, TypeConstant type)
        {
        super(pool);

        assert type.isSingleDefiningConstant();
        m_type = type;
        }

    @Override
    protected void resolveConstants()
        {
        m_type = (TypeConstant) getConstantPool().getConstant(m_iType);
        super.resolveConstants();
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    /**
     * @return the IdentityConstant that this DecoratedClassConstant represents a class of
     */
    IdentityConstant getClassIdentityConstant()
        {
        return (IdentityConstant) m_type.getDefiningConstant();
        }

    @Override
    public String getName()
        {
        return getClassIdentityConstant().getName();
        }

    /**
     * @return true iff this class is a virtual child class
     */
    public boolean isVirtualChild()
        {
        return m_type.isVirtualChild();
        }

    @Override
    public Component getComponent()
        {
        return getClassIdentityConstant().getComponent();
        }

    @Override
    public IdentityConstant getParentConstant()
        {
        return isVirtualChild()
                ? getConstantPool().ensureClassConstant(m_type.getParentType())
                : getClassIdentityConstant().getParentConstant();
        }

    @Override
    public TypeConstant getFormalType()
        {
        return getType();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.DecoratedClass;
        }

    @Override
    public boolean isClass()
        {
        return true;
        }

    @Override
    public TypeConstant getType()
        {
        return m_type;
        }

    @Override
    public IdentityConstant appendTrailingSegmentTo(IdentityConstant that)
        {
        ConstantPool pool = that.getConstantPool();
        return pool.ensureClassConstant(that, getName());
        }

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && m_type.containsUnresolved();
        }

    @Override
    protected Object getLocator()
        {
        return m_type;
        }

    @Override
    protected int compareDetails(Constant that)
        {
        if (!(that instanceof DecoratedClassConstant))
            {
            return -1;
            }

        return this.m_type.compareTo(((DecoratedClassConstant) that).m_type);
        }

    @Override
    public String getValueString()
        {
        return getType().getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_type = (TypeConstant) pool.register(m_type);
        }

    @Override
    protected void assemble(DataOutput out)
        throws IOException
        {
        super.assemble(out);
        writePackedLong(out, m_type.getPosition());
        }

    @Override
    public String getDescription()
        {
        Constant constParent = getNamespace();
        while (constParent instanceof ClassConstant)
            {
            constParent = ((ClassConstant) constParent).getNamespace();
            }

        return constParent == null
            ? "class=" + getValueString()
            : "class=" + getValueString() + ", " + constParent.getDescription();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_type);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the constant that specifies the type of the
     * class identified by this constant.
     */
    private int m_iType;

    /**
     * The Class type.
     */
    TypeConstant m_type;
    }
