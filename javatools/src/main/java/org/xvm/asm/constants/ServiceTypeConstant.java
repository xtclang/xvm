package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.util.Hash;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Represent a constant that specifies that the underlying type is a service.
 */
public class ServiceTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a service type.
     *
     * @param pool       the ConstantPool that will contain this Constant
     * @param constType  a TypeConstant that this constant modifies to be a service
     */
    public ServiceTypeConstant(ConstantPool pool, TypeConstant constType)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }

        m_constType = constType;
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
    public ServiceTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iType = readMagnitude(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_constType = (TypeConstant) getConstantPool().getConstant(m_iType);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isModifyingType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType;
        }

    @Override
    public boolean isService()
        {
        return true;
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureServiceTypeConstant(type);
        }


    // ----- TypeInfo support ----------------------------------------------------------------------

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        // the "service" keyword does not affect the TypeInfo, even though the type itself is
        // slightly different
        return m_constType.ensureTypeInfoInternal(errs);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected boolean isDuckTypeAbleFrom(TypeConstant typeRight)
        {
        return typeRight.isImmutable() && super.isDuckTypeAbleFrom(typeRight);
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ServiceType;
        }

    @Override
    protected Object getLocator()
        {
        return m_constType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_constType.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        return obj instanceof ServiceTypeConstant that
                ? this.m_constType.compareTo(that.m_constType)
                : -1;
        }

    @Override
    public String getValueString()
        {
        return "service " + m_constType.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constType));
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_constType);
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the type constant.
     */
    private int m_iType;

    /**
     * The type referred to.
     */
    private TypeConstant m_constType;
    }