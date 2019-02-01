package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import java.util.function.Consumer;
import java.util.function.Function;

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
    protected abstract TypeConstant cloneRelational(ConstantPool pool,
                                                    TypeConstant type1, TypeConstant type2);


    // ----- type algebra methods ------------------------------------------------------------------

    /**
     * Combine the specified types and attempt to produce a minimal representation of the
     * type that is known to be assignable to both specified types.
     *
     * @return a reduction for the union of the specified types
     */
    public static TypeConstant combineWith(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        if (type1.isA(type2))
            {
            return type1;
            }
        if (type2.isA(type1))
            {
            return type2;
            }
        return pool.ensureUnionTypeConstant(type1, type2);
        }

    /**
     * Produce a minimal representation the type that is known to be assignable to the first but
     * is also known not to be the second.
     *
     * @return a reduction for the difference between the first and second types
     */
    public static TypeConstant combineWithout(ConstantPool pool, TypeConstant type1, TypeConstant type2)
        {
        return type1;
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
        return m_constType1.isImmutabilitySpecified()
                && m_constType2.isImmutabilitySpecified();
        }

    @Override
    public boolean isImmutable()
        {
        return m_constType1.isImmutable()
                && m_constType2.isImmutable();
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
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return m_constType1.isAutoNarrowing(fAllowVirtChild)
            || m_constType2.isAutoNarrowing(fAllowVirtChild);
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
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveTypedefs();
        TypeConstant constResolved2 = constOriginal2.resolveTypedefs();

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(getConstantPool(), constResolved1, constResolved2);
        }

    @Override
    public void bindTypeParameters(MethodConstant idMethod)
        {
        m_constType1.bindTypeParameters(idMethod);
        m_constType2.bindTypeParameters(idMethod);
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveGenerics(pool, resolver);
        TypeConstant constResolved2 = constOriginal2.resolveGenerics(pool, resolver);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(pool, constResolved1, constResolved2);
        }

    @Override
    public TypeConstant getGenericParamType(String sName)
        {
        // for both Intersection and Union types, either side needs to find it,
        // but if both do, the results must be identical;
        // for example: for an intersection type Array<String>? the formal name "ElementType"
        // would resolve to the String type
        TypeConstant typeActual1 = m_constType1.getGenericParamType(sName);
        TypeConstant typeActual2 = m_constType2.getGenericParamType(sName);

        return typeActual1 == null
                ? typeActual2
                : typeActual2 == null || typeActual1.equals(typeActual2)
                    ? typeActual1
                    : null;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.adoptParameters(pool, atypeParams);
        TypeConstant constResolved2 = constOriginal2.adoptParameters(pool, atypeParams);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(pool, constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = constOriginal1.resolveAutoNarrowing(pool, fRetainParams, typeTarget);
        TypeConstant constResolved2 = constOriginal2.resolveAutoNarrowing(pool, fRetainParams, typeTarget);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(pool, constResolved1, constResolved2);
        }

    @Override
    public boolean isNarrowedFrom(TypeConstant typeSuper, TypeConstant typeCtx)
        {
        return false;
        }

    @Override
    public TypeConstant replaceUnderlying(ConstantPool pool, Function<TypeConstant, TypeConstant> transformer)
        {
        TypeConstant constOriginal1 = m_constType1;
        TypeConstant constOriginal2 = m_constType2;
        TypeConstant constResolved1 = transformer.apply(constOriginal1);
        TypeConstant constResolved2 = transformer.apply(constOriginal2);

        return constResolved1 == constOriginal1 && constResolved2 == constOriginal2
                ? this
                : cloneRelational(pool, constResolved1, constResolved2);
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        typeActual = typeActual.resolveTypedefs();

        if (getFormat() != typeActual.getFormat())
            {
            return null;
            }

        RelationalTypeConstant that = (RelationalTypeConstant) typeActual;

        TypeConstant constThis1 = this.m_constType1;
        TypeConstant constThis2 = this.m_constType2;
        TypeConstant constThat1 = that.m_constType1;
        TypeConstant constThat2 = that.m_constType2;

        String sTopologyThis = getSign(constThis1) + getSign(constThis2);
        String sTopologyThat = getSign(constThat1) + getSign(constThat2);

        TypeConstant typeResult;
        if (sTopologyThat.equals(sTopologyThis))
            {
            // branches are the same: try 1 to 1 and 2 to 2 first
            typeResult = constThis1.resolveTypeParameter(constThat1, sFormalName);
            if (typeResult != null)
                {
                return typeResult;
                }
            typeResult = constThis2.resolveTypeParameter(constThat2, sFormalName);
            if (typeResult != null)
                {
                return typeResult;
                }
            }

        // now check if we should try 1 to 2 and 2 to 1
        switch (sTopologyThis)
            {
            case "rr":
            case "ss":
                if (!sTopologyThat.equals(sTopologyThis))
                    {
                    return null;
                    }
                break;

            case "rs":
            case "sr":
                switch (sTopologyThat)
                    {
                    case "rr":
                    case "ss":
                        return null;
                    }
                break;
            }

        typeResult = constThis1.resolveTypeParameter(constThat2, sFormalName);
        if (typeResult != null)
            {
            return typeResult;
            }
        return constThis2.resolveTypeParameter(constThat1, sFormalName);
        }

    private static String getSign(TypeConstant type)
        {
        return type.isRelationalType() ? "r" : "s";
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

    @Override
    public boolean isIntoVariableType()
        {
        return false;
        }

    @Override
    public TypeConstant getIntoVariableType()
        {
        return null;
        }

    @Override
    public boolean isTypeOfType()
        {
        return false;
        }


    // ----- type comparison support ---------------------------------------------------------------

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
        if (!(that instanceof RelationalTypeConstant))
            {
            return -1;
            }

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
