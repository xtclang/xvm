package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a Class constant, but one that  may be decorated with additional annotations, type
 * parameters, and other information in addition to the underlying ClassConstant.
 *
 * @see TypeConstant#isDecoratedClass()
 */
public class DecoratedClassConstant
        extends ClassConstant
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
        super(pool, format, in);
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
        super(pool,
                ((ClassConstant) type.getDefiningConstant()).getParentConstant(),
                ((ClassConstant) type.getDefiningConstant()).getName());

        assert type.isExplicitClassIdentity(true);
        m_type = type;
        }

    @Override
    protected void resolveConstants()
        {
        m_type = (TypeConstant) getConstantPool().getConstant(m_iType);
        super.resolveConstants();
        }


    // ----- ClassConstant methods -----------------------------------------------------------------

    /**
     * @return true iff this class is a virtual child class
     */
    public boolean isVirtualChild()
        {
        return m_type.isVirtualChild();
        }

    @Override
    public IdentityConstant getParentConstant()
        {
        return isVirtualChild()
                ? getConstantPool().ensureClassConstant(m_type.getParentType())
                : super.getParentConstant();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.DecoratedClass;
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


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_type.hashCode();
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
