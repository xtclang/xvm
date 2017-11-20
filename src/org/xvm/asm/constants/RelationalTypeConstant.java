package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A common base for relational types.
 */
public abstract class RelationalTypeConstant
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
    protected RelationalTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iType1 = readMagnitude(in);
        m_iType2 = readMagnitude(in);
        }

    /**
     * Construct a relational constant based on two specified types.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param constType1  the first TypeConstant to union
     * @param constType2  the second TypeConstant to union
     */
    protected RelationalTypeConstant(ConstantPool pool, TypeConstant constType1, TypeConstant constType2)
        {
        super(pool);

        if (constType1 == null || constType2 == null)
            {
            throw new IllegalArgumentException("types required");
            }

        m_constType1 = constType1;
        m_constType2 = constType2;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isRelationalType()
        {
        return true;
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        return m_constType1;
        }

    @Override
    public TypeConstant getUnderlyingType2()
        {
        return m_constType2;
        }

    @Override
    public boolean isImmutabilitySpecified()
        {
        return getUnderlyingType().isImmutabilitySpecified()
                && getUnderlyingType2().isImmutabilitySpecified();
        }

    @Override
    public boolean isAccessSpecified()
        {
        return getUnderlyingType().isAccessSpecified()
            && getUnderlyingType2().isAccessSpecified()
            && getUnderlyingType().getAccess() == getUnderlyingType2().getAccess();
        }

    @Override
    public Access getAccess()
        {
        Access access = getUnderlyingType().getAccess();

        if (getUnderlyingType().getAccess() != access)
            {
            throw new UnsupportedOperationException("relational access mismatch");
            }

        return access;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        return getUnderlyingType().extendsClass(constClass)
            || getUnderlyingType2().extendsClass(constClass);
        }

    @Override
    public boolean impersonatesClass(IdentityConstant constClass)
        {
        return getUnderlyingType().impersonatesClass(constClass)
            || getUnderlyingType2().impersonatesClass(constClass);
        }

    @Override
    public boolean extendsOrImpersonatesClass(IdentityConstant constClass)
        {
        return getUnderlyingType().extendsOrImpersonatesClass(constClass)
            || getUnderlyingType2().extendsOrImpersonatesClass(constClass);
        }

    @Override
    public boolean isClassType()
        {
        return getUnderlyingType().isClassType()
            || getUnderlyingType2().isClassType();
        }

    @Override
    public boolean isSingleUnderlyingClass()
        {
        return getUnderlyingType().isSingleUnderlyingClass()
             ^ getUnderlyingType2().isSingleUnderlyingClass();
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass()
        {
        assert isClassType() && isSingleUnderlyingClass();

        IdentityConstant clz = getUnderlyingType().getSingleUnderlyingClass();
        if (clz == null)
            {
            clz = getUnderlyingType2().getSingleUnderlyingClass();
            }
        return clz;
        }

    public Set<IdentityConstant> underlyingClasses()
        {
        Set<IdentityConstant> set = getUnderlyingType().underlyingClasses();
        Set<IdentityConstant> set2 = getUnderlyingType2().underlyingClasses();
        if (set.isEmpty())
            {
            set = set2;
            }
        else if (!set2.isEmpty())
            {
            set = new HashSet<>(set);
            set.addAll(set2);
            }
        return set;
        }

    @Override
    public Constant getDefiningConstant()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return getUnderlyingType().isAutoNarrowing()
            && getUnderlyingType2().isAutoNarrowing();
        }

    @Override
    public boolean isConstant()
        {
        return getUnderlyingType().isConstant()
            && getUnderlyingType2().isConstant();
        }

    @Override
    public boolean isOnlyNullable()
        {
        return getUnderlyingType().isOnlyNullable()
            && getUnderlyingType2().isOnlyNullable();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved()
        {
        return m_constType1.containsUnresolved() || m_constType2.containsUnresolved();
        }

    @Override
    public Constant simplify()
        {
        m_constType1 = (TypeConstant) m_constType1.simplify();
        m_constType2 = (TypeConstant) m_constType2.simplify();
        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType1);
        visitor.accept(m_constType2);
        }

    @Override
    protected int compareDetails(Constant that)
        {
        int n = this.m_constType1.compareTo(((RelationalTypeConstant) that).m_constType1);
        if (n == 0)
            {
            n = this.m_constType2.compareTo(((RelationalTypeConstant) that).m_constType2);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_constType1.getValueString() + " - " + m_constType2.getValueString();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_constType1 = (TypeConstant) getConstantPool().getConstant(m_iType1);
        m_constType2 = (TypeConstant) getConstantPool().getConstant(m_iType2);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType1 = (TypeConstant) pool.register(m_constType1);
        m_constType2 = (TypeConstant) pool.register(m_constType2);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constType1));
        writePackedLong(out, indexOf(m_constType2));
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the first type constant.
     */
    private int m_iType1;

    /**
     * During disassembly, this holds the index of the second type constant.
     */
    private int m_iType2;

    /**
     * The first type referred to.
     */
    protected TypeConstant m_constType1;

    /**
     * The second type referred to.
     */
    protected TypeConstant m_constType2;
    }
