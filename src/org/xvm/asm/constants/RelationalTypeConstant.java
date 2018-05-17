package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import java.util.function.Consumer;
import java.util.function.Function;

import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
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
    public TypeConstant adoptParameters(TypeConstant[] atypeParams)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.adoptParameters(atypeParams);
        TypeConstant constResolved2 = constOriginal2.adoptParameters(atypeParams);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveAutoNarrowing()
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveAutoNarrowing();
        TypeConstant constResolved2 = constOriginal2.resolveAutoNarrowing();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant inferAutoNarrowing(IdentityConstant constThisClass)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constInferred1 = constOriginal1.inferAutoNarrowing(constThisClass);
        TypeConstant constInferred2 = constOriginal2.inferAutoNarrowing(constThisClass);

        return constInferred1 == constOriginal1 && constInferred2 == constOriginal2
                ? this
                : cloneRelational(constInferred1, constInferred2);
        }

    @Override
    public TypeConstant replaceUnderlying(Function<TypeConstant, TypeConstant> transformer)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = transformer.apply(constOriginal1);
        TypeConstant constResolved2 = transformer.apply(constOriginal2);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public boolean isIntoClassType()
        {
        return false;
        }

    @Override
    public boolean isIntoPropertyType()
        {
        return false;
        }

    @Override
    public TypeConstant getIntoPropertyType()
        {
        return null;
        }


    @Override
    public boolean isIntoMethodType()
        {
        return false;
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected boolean validateContributionFrom(TypeConstant typeRight, Access accessLeft,
                                               ContributionChain chain)
        {
        // there is nothing that could change the result of "collectContributions"
        return true;
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        assert listParams.isEmpty();
        return Usage.valueOf(m_constType1.producesFormalType(sTypeName, access)
                          || m_constType2.producesFormalType(sTypeName, access));
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        assert listParams.isEmpty();
        return Usage.valueOf(m_constType1.consumesFormalType(sTypeName, access)
                          || m_constType2.consumesFormalType(sTypeName, access));
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
