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
import org.xvm.asm.Op;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;

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
        TypeConstant constOriginal1 = getUnderlyingType();
        TypeConstant constOriginal2 = getUnderlyingType2();
        TypeConstant constResolved1 = constOriginal1.resolveTypedefs();
        TypeConstant constResolved2 = constOriginal2.resolveTypedefs();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveGenerics(GenericTypeResolver resolver)
        {
        TypeConstant constOriginal1 = getUnderlyingType();
        TypeConstant constOriginal2 = getUnderlyingType2();
        TypeConstant constResolved1 = constOriginal1.resolveGenerics(resolver);
        TypeConstant constResolved2 = constOriginal2.resolveGenerics(resolver);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(IdentityConstant constThisClass)
        {
        TypeConstant constOriginal1 = getUnderlyingType();
        TypeConstant constOriginal2 = getUnderlyingType2();
        TypeConstant constResolved1 = constOriginal1.resolveAutoNarrowing(constThisClass);
        TypeConstant constResolved2 = constOriginal2.resolveAutoNarrowing(constThisClass);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveEverything(GenericTypeResolver resolver, IdentityConstant constThisClass)
        {
        TypeConstant constOriginal1 = getUnderlyingType();
        TypeConstant constOriginal2 = getUnderlyingType2();
        TypeConstant constResolved1 = constOriginal1.resolveEverything(resolver, constThisClass);
        TypeConstant constResolved2 = constOriginal2.resolveEverything(resolver, constThisClass);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(constResolved1, constResolved2);
        }

    @Override
    public TypeConstant normalizeParameters()
        {
        TypeConstant constOriginal1 = getUnderlyingType();
        TypeConstant constOriginal2 = getUnderlyingType2();
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
        return getUnderlyingType().producesFormalType(sTypeName, access, listParams)
            || getUnderlyingType2().producesFormalType(sTypeName, access, listParams);
        }

    @Override
    public boolean consumesFormalType(String sTypeName, Access access,
                                      List<TypeConstant> listParams)
        {
        return getUnderlyingType().consumesFormalType(sTypeName, access, listParams)
            || getUnderlyingType2().consumesFormalType(sTypeName, access, listParams);
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
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }

        switch (getUnderlyingType().callEquals(frame, hValue1, hValue2, Frame.RET_LOCAL))
            {
            case Op.R_NEXT:
                return completeEquals(frame, hValue1, hValue2, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    completeEquals(frameCaller, hValue1, hValue2, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Completion of the callEquals implementation.
     */
    protected int completeEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle hResult = frame.getFrameLocal();
        return hResult == xBoolean.FALSE
            ? frame.assignValue(iReturn, hResult)
            : getUnderlyingType2().callEquals(frame, hValue1, hValue2, iReturn);
        }

    @Override
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xOrdered.EQUAL);
            }

        switch (getUnderlyingType().callCompare(frame, hValue1, hValue2, Frame.RET_LOCAL))
            {
            case Op.R_NEXT:
                return completeCompare(frame, hValue1, hValue2, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    completeCompare(frameCaller, hValue1, hValue2, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Completion of the callCompare implementation.
     */
    protected int completeCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ObjectHandle hResult = frame.getFrameLocal();
        return hResult != xOrdered.EQUAL
            ? frame.assignValue(iReturn, hResult)
            : getUnderlyingType2().callCompare(frame, hValue1, hValue2, iReturn);
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
        return !isValidated() && (super.validate(errlist) | getUnderlyingType2().validate(errlist));
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
