package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.Utils;

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

    /**
     * @return clone this relational type based on the underlying types
     */
    protected abstract TypeConstant cloneRelational(TypeConstant type1, TypeConstant type2);


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
        return m_constType1.isImmutabilitySpecified()
                && m_constType2.isImmutabilitySpecified();
        }

    @Override
    public boolean isAccessSpecified()
        {
        return m_constType1.isAccessSpecified()
            && m_constType2.isAccessSpecified()
            && m_constType1.getAccess() == m_constType2.getAccess();
        }

    @Override
    public Access getAccess()
        {
        Access access = m_constType1.getAccess();

        if (m_constType1.getAccess() != access)
            {
            throw new UnsupportedOperationException("relational access mismatch");
            }

        return access;
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        return m_constType1.extendsClass(constClass)
            || m_constType2.extendsClass(constClass);
        }

    @Override
    public boolean isClassType()
        {
        return m_constType1.isClassType()
            || m_constType2.isClassType();
        }

    @Override
    public boolean isSingleUnderlyingClass()
        {
        return m_constType1.isSingleUnderlyingClass()
             ^ m_constType2.isSingleUnderlyingClass();
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass()
        {
        assert isClassType() && isSingleUnderlyingClass();

        IdentityConstant clz = m_constType1.getSingleUnderlyingClass();
        if (clz == null)
            {
            clz = m_constType2.getSingleUnderlyingClass();
            }
        return clz;
        }

    public Set<IdentityConstant> underlyingClasses()
        {
        Set<IdentityConstant> set = m_constType1.underlyingClasses();
        Set<IdentityConstant> set2 = m_constType2.underlyingClasses();
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
        return m_constType1.isAutoNarrowing()
            && m_constType2.isAutoNarrowing();
        }

    @Override
    public boolean isConstant()
        {
        return m_constType1.isConstant()
            && m_constType2.isConstant();
        }

    @Override
    public boolean isOnlyNullable()
        {
        return m_constType1.isOnlyNullable()
            && m_constType2.isOnlyNullable();
        }

    @Override
    public <T extends TypeConstant> T findFirst(Class<T> clz)
        {
        if (clz == getClass())
            {
            return (T) this;
            }
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveTypedefs();
        TypeConstant constResolved2 = constOriginal2.resolveTypedefs();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveGenerics(GenericTypeResolver resolver)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveGenerics(resolver);
        TypeConstant constResolved2 = constOriginal2.resolveGenerics(resolver);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(IdentityConstant constThisClass)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveAutoNarrowing(constThisClass);
        TypeConstant constResolved2 = constOriginal2.resolveAutoNarrowing(constThisClass);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveEverything(GenericTypeResolver resolver, IdentityConstant constThisClass)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveEverything(resolver, constThisClass);
        TypeConstant constResolved2 = constOriginal2.resolveEverything(resolver, constThisClass);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant normalizeParameters()
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.normalizeParameters();
        TypeConstant constResolved2 = constOriginal2.normalizeParameters();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected boolean validateContributionFrom(TypeConstant thatRight, Access accessLeft,
                                               ContributionChain chain)
        {
        // there is nothing that could change the result of "collectContributions"
        return true;
        }

    @Override
    public boolean producesFormalType(String sTypeName, Access access,
                                      List<TypeConstant> listParams)
        {
        return m_constType1.producesFormalType(sTypeName, access, listParams)
            || m_constType2.producesFormalType(sTypeName, access, listParams);
        }

    @Override
    public boolean consumesFormalType(String sTypeName, Access access,
                                      List<TypeConstant> listParams)
        {
        return m_constType1.consumesFormalType(sTypeName, access, listParams)
            || m_constType2.consumesFormalType(sTypeName, access, listParams);
        }

    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callEqualsSequence(frame,
            m_constType1, m_constType2, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return Utils.callCompareSequence(frame,
            m_constType1, m_constType2, hValue1, hValue2, iReturn);
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

    @Override
    public boolean validate(ErrorListener errlist)
        {
        return !isValidated() && (super.validate(errlist) | m_constType2.validate(errlist));
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
