package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;

import java.util.function.Function;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ContributionChain;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents a parameterized type.
 */
public class ParameterizedTypeConstant
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
    public ParameterizedTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iType = readIndex(in);

        int cTypes  = readMagnitude(in);
        if (cTypes > 0)
            {
            int[] aiType = new int[cTypes];
            for (int i = 1; i <= cTypes; ++i)
                {
                aiType[i] = readIndex(in);
                }
            m_aiTypeParams = aiType;
            }
        }

    /**
     * Construct a constant whose value is a type-parameterized type.
     *
     * @param pool             the ConstantPool that will contain this Constant
     * @param constType        a TypeConstant representing the parameterized type
     * @param constTypeParams  a number of TypeConstants representing the type parameters
     */
    public ParameterizedTypeConstant(ConstantPool pool, TypeConstant constType,
            TypeConstant... constTypeParams)
        {
        super(pool);

        if (constType == null)
            {
            throw new IllegalArgumentException("type required");
            }
        if (constType.isParamsSpecified())
            {
            throw new IllegalArgumentException("type is already parameterized");
            }
        if (!(constType instanceof TerminalTypeConstant))
            {
            throw new IllegalArgumentException("must refer to a terminal type");
            }
        if (constTypeParams == null)
            {
            throw new IllegalArgumentException("must have parameters");
            }

        m_constType   = constType;
        m_atypeParams = constTypeParams;
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
    public boolean isParamsSpecified()
        {
        return true;
        }

    @Override
    public List<TypeConstant> getParamTypes()
        {
        return m_atypeParams.length == 0
                ? Collections.EMPTY_LIST
                : Arrays.asList(m_atypeParams);
        }

    @Override
    public TypeConstant[] getParamTypesArray()
        {
        return m_atypeParams;
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return fAllowParams && getUnderlyingType().isExplicitClassIdentity(false);
        }

    @Override
    public Component.Format getExplicitClassFormat()
        {
        return getUnderlyingType().getExplicitClassFormat();
        }

    @Override
    public TypeConstant getExplicitClassInto()
        {
        TypeConstant constResolved = m_constType.getExplicitClassInto();

        return constResolved.isParamsSpecified()
            ? constResolved.resolveGenerics(this)
            : constResolved;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        boolean      fDiff         = constOriginal != constResolved;

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstResolved = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamResolved = constParamOriginal.resolveTypedefs();
            if (constParamOriginal != constParamResolved)
                {
                if (aconstResolved == aconstOriginal)
                    {
                    aconstResolved = aconstOriginal.clone();
                    }
                aconstResolved[i] = constParamResolved;
                fDiff = true;
                }
            }

        return fDiff
                ? getConstantPool().ensureParameterizedTypeConstant(constResolved, aconstResolved)
                : this;
        }

    @Override
    public TypeConstant resolveGenerics(GenericTypeResolver resolver)
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveGenerics(resolver);
        boolean      fDiff         = constOriginal != constResolved;

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstResolved = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamResolved = constParamOriginal.resolveGenerics(resolver);
            if (constParamOriginal != constParamResolved)
                {
                if (constParamResolved instanceof TupleElementsTypeConstant)
                    {
                    // we are replacing tuple's "ElementTypes"
                    assert constOriginal.isTuple() && aconstOriginal.length == 1;
                    aconstResolved = constParamResolved.getParamTypesArray();
                    }
                else
                    {
                    if (aconstResolved == aconstOriginal)
                        {
                        aconstResolved = aconstOriginal.clone();
                        }
                    aconstResolved[i] = constParamResolved;
                    }
                fDiff = true;
                }
            }

        return fDiff
                ? getConstantPool().ensureParameterizedTypeConstant(constResolved, aconstResolved)
                : this;
        }

    @Override
    public TypeConstant adoptParameters(TypeConstant[] atypeParams)
        {
        TypeConstant constOriginal = m_constType;

        assert constOriginal instanceof TerminalTypeConstant;

        return constOriginal.adoptParameters(atypeParams == null ? m_atypeParams : atypeParams);
        }

    @Override
    public TypeConstant resolveAutoNarrowing()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveAutoNarrowing();
        boolean      fDiff         = constOriginal != constResolved;

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstResolved = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamResolved = constParamOriginal.resolveAutoNarrowing();
            if (constParamOriginal != constParamResolved)
                {
                if (aconstResolved == aconstOriginal)
                    {
                    aconstResolved = aconstOriginal.clone();
                    }
                aconstResolved[i] = constParamResolved;
                fDiff = true;
                }
            }

        return fDiff
                ? getConstantPool().ensureParameterizedTypeConstant(constResolved, aconstResolved)
                : this;
        }

    @Override
    public TypeConstant inferAutoNarrowing(IdentityConstant constThisClass)
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constInferred = constOriginal.inferAutoNarrowing(constThisClass);
        boolean      fDiff         = constOriginal != constInferred;

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstInferred = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamInferred = constParamOriginal.inferAutoNarrowing(constThisClass);
            if (constParamOriginal != constParamInferred)
                {
                if (aconstInferred == aconstOriginal)
                    {
                    aconstInferred = aconstOriginal.clone();
                    }
                aconstInferred[i] = constParamInferred;
                fDiff = true;
                }
            }

        return fDiff
            ? getConstantPool().ensureParameterizedTypeConstant(constInferred, aconstInferred)
            : this;
        }

    @Override
    public TypeConstant transform(Function<TypeConstant, TypeConstant> transformer)
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = transformer.apply(constOriginal);
        boolean      fDiff         = constOriginal != constResolved;

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstResolved = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamResolved =transformer.apply(constParamOriginal);
            if (constParamOriginal != constParamResolved)
                {
                if (aconstResolved == aconstOriginal)
                    {
                    aconstResolved = aconstOriginal.clone();
                    }
                aconstResolved[i] = constParamResolved;
                fDiff = true;
                }
            }

        return fDiff
                ? getConstantPool().ensureParameterizedTypeConstant(constResolved, aconstResolved)
                : this;
        }

    @Override
    protected TypeConstant cloneSingle(TypeConstant type)
        {
        return getConstantPool().ensureParameterizedTypeConstant(type, m_atypeParams);
        }


    // ----- type comparison support --------------------------------------------------------------

    @Override
    public List<ContributionChain> collectContributions(
            TypeConstant typeLeft, List<TypeConstant> listRight, List<ContributionChain> chains)
        {
        assert listRight.isEmpty();

        listRight = getParamTypes();

        chains = super.collectContributions(typeLeft, listRight, chains);
        if (chains.isEmpty())
            {
            return chains;
            }

        nextChain:
        for (Iterator<ContributionChain> iter = chains.iterator(); iter.hasNext();)
            {
            ContributionChain chain = iter.next();

            switch (chain.first().getComposition())
                {
                case MaybeDuckType:
                    // will be resolved later via interface method matching
                    continue nextChain;

                case Equal:
                    assert chain.getLength() == 1;
                    break;

                case Delegates:
                case Implements:
                    // Note: relational types split the contributions into multiple chains,
                    //       so the chain can only be represented by a single defining constant
                    assert chain.first().getTypeConstant().isSingleDefiningConstant();
                case Extends:
                case Incorporates:
                case Into:
                    {
                    ClassStructure clzRight = (ClassStructure)
                        getSingleUnderlyingClass(true).getComponent();

                    listRight = chain.propagateActualTypes(clzRight, listRight);
                    break;
                    }

                default:
                    throw new IllegalStateException();
                }

            // by now we know that the "contrib" and "that" have equivalent terminal types
            if (listRight == null)
                {
                // a conditional contribution didn't apply
                iter.remove();
                continue;
                }

            if (!typeLeft.isParamsSpecified())
                {
                // "that" type is not parameterized, nothing else to check here;
                // assignment C = C<T> is always allowed
                continue;
                }

            ClassStructure clzLeft = (ClassStructure)
                typeLeft.getSingleUnderlyingClass(true).getComponent();

            if (!validateAssignability(clzLeft, typeLeft.getParamTypes(),
                    typeLeft.getAccess(), listRight, chain))
                {
                iter.remove();
                }
            }

        return chains;
        }

    @Override
    protected boolean validateContributionFrom(TypeConstant typeRight, Access accessLeft,
                                               ContributionChain chain)
        {
        // we know that from "that" perspective "that" is assignable to "this"
        if (typeRight.isParamsSpecified() || typeRight.isRelationalType())
            {
            // the type correspondence have already been checked
            return true;
            }

        List<TypeConstant> listLeft  = getParamTypes();
        List<TypeConstant> listRight = Collections.EMPTY_LIST;

        // r-value is not parameterized, while l-value is; e.g.
        //      C<T> = C
        // which is only allowed for producing types
        // and then all consuming methods (if any) must be "wrapped"

        switch (chain.first().getComposition())
            {
            case MaybeDuckType:
                // will be resolved later via interface method matching
                return true;

            case Equal:
                assert chain.getLength() == 1;
                break;

            case Delegates:
            case Implements:
                assert chain.first().getTypeConstant().isSingleDefiningConstant();

            case Extends:
            case Incorporates:
            case Into:
                {
                ClassStructure clzRight = (ClassStructure)
                    typeRight.getSingleUnderlyingClass(true).getComponent();

                listRight = chain.propagateActualTypes(clzRight, listRight);
                break;
                }

            default:
                throw new IllegalStateException();
            }

        ClassStructure clzLeft = (ClassStructure)
            this.getSingleUnderlyingClass(true).getComponent();

        return validateAssignability(clzLeft, listLeft, accessLeft, listRight, chain);
        }

    /**
     * Check if the specified class is assignable for the specified parameters.
     *
     *  C<L1, L2, ...> lvalue = (C<R1, R2, ...>) rvalue;
     */
    protected boolean validateAssignability(
            ClassStructure clz, List<TypeConstant> listLeft, Access accessLeft,
            List<TypeConstant> listRight, ContributionChain chain)
        {
        ConstantPool pool = getConstantPool();

        int cParamsLeft  = listLeft.size();
        int cParamsRight = listRight.size();
        boolean fTuple   = clz.getIdentityConstant().equals(pool.clzTuple());

        if (!fTuple)
            {
            assert Math.max(cParamsRight, cParamsLeft) <= clz.getTypeParams().size();
            }

        // we only have to check all the parameters on the left side,
        // since if an assignment C<L1> = C<R1> is allowed, then
        // an assignment C<L1> = C<R1, R2> is allowed for any R2

        List<Map.Entry<StringConstant, TypeConstant>> listFormalEntries =
            clz.getTypeParamsAsList();

        for (int i = 0; i < cParamsLeft; i++)
            {
            String sName;
            TypeConstant typeCanonical;

            if (fTuple)
                {
                sName         = null;
                typeCanonical = pool.typeObject();
                }
            else
                {
                Map.Entry<StringConstant, TypeConstant> entryFormal = listFormalEntries.get(i);

                sName         = entryFormal.getKey().getValue();
                typeCanonical = entryFormal.getValue();
                }

            TypeConstant typeLeft = listLeft.get(i);
            TypeConstant typeRight;
            boolean fProduce;
            boolean fLeftIsRight = false;

            if (i < cParamsRight)
                {
                typeRight = listRight.get(i);

                if (typeLeft.equals(typeRight))
                    {
                    continue;
                    }

                fProduce = fTuple || clz.producesFormalType(sName, accessLeft, listLeft);
                fLeftIsRight = typeLeft.isA(typeRight);

                if (fLeftIsRight && !fProduce)
                    {
                    // consumer only methods; rule 1.2.1
                    continue;
                    }
                }
            else
                {
                // assignment  C<L1, L2> = C<R1> is not the same as
                //             C<L1, L2> = C<R1, [canonical type for R2]>;
                // the former is only allowed if class C produces L2
                // and then all L2 consuming methods (if any) must be "wrapped"
                typeRight = typeCanonical;
                fProduce  = fTuple || clz.producesFormalType(sName, accessLeft, listLeft);
                }

            if (typeRight.isA(typeLeft))
                {
                if (fLeftIsRight)
                    {
                    // both hold true:
                    //   typeLeft.isA(typeRight), and
                    //   typeRight.isA(typeLeft)
                    // we take it that the types are congruent
                    // (e,g. "this:class", but with different declaration levels)
                    continue;
                    }

                if (fProduce)
                    {
                    // there are some producing methods; rule 1.2.2.2
                    // consuming methods will need to be "wrapped"
                    if (fTuple || clz.consumesFormalType(sName, accessLeft, listLeft))
                        {
                        chain.markWeakMatch();
                        }
                    continue;
                    }
                }

            // didn't match; remove
            return false;
            }
        return true;
        }

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        assert listLeft.isEmpty();
        return super.isInterfaceAssignableFrom(typeRight, accessLeft, getParamTypes());
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        assert listParams.isEmpty();
        return super.checkConsumption(sTypeName, access, getParamTypes());
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        assert listParams.isEmpty();
        return super.checkProduction(sTypeName, access, getParamTypes());
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        assert listParams.isEmpty();
        return super.containsSubstitutableMethod(signature, access, getParamTypes());
        }

    @Override
    public boolean isConstant()
        {
        for (TypeConstant type : m_atypeParams)
            {
            if (!type.isConstant())
                {
                return false;
                }
            }

        return super.isConstant();
        }

    @Override
    public boolean isNullable()
        {
        assert !m_constType.isNullable();
        return false;
        }

    @Override
    public boolean isCongruentWith(TypeConstant that)
        {
        that = that.unwrapForCongruence();
        if (that instanceof ParameterizedTypeConstant)
            {
            ParameterizedTypeConstant thatP = (ParameterizedTypeConstant) that;
            if (this.m_constType.isCongruentWith(thatP.m_constType))
                {
                TypeConstant[] atypeThis = this.m_atypeParams;
                TypeConstant[] atypeThat = thatP.m_atypeParams;
                int cParams = atypeThis.length;
                if (atypeThat.length == cParams)
                    {
                    for (int i = 0; i < cParams; ++i)
                        {
                        if (!atypeThis[i].isCongruentWith(atypeThat[i]))
                            {
                            return false;
                            }
                        }
                    return true;
                    }
                }
            }

        return false;
        }

    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.ParameterizedType;
        }

    @Override
    public boolean containsUnresolved()
        {
        if (m_constType.containsUnresolved())
            {
            return true;
            }
        for (Constant param : m_atypeParams)
            {
            if (param.containsUnresolved())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public Constant simplify()
        {
        checkDepth(true);

        m_constType = (TypeConstant) m_constType.simplify();

        TypeConstant[] atypeParams = m_atypeParams;
        for (int i = 0, c = atypeParams.length; i < c; ++i)
            {
            TypeConstant constOld = atypeParams[i];
            TypeConstant constNew = (TypeConstant) constOld.simplify();
            if (constNew != constOld)
                {
                atypeParams[i] = constNew;
                }
            }
        checkDepth(false);
        return this;
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_constType);
        for (Constant param : m_atypeParams)
            {
            visitor.accept(param);
            }
        }

    @Override
    protected Object getLocator()
        {
        return m_atypeParams.length == 0
                ? m_constType
                : null;
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        ParameterizedTypeConstant that = (ParameterizedTypeConstant) obj;
        int n = this.m_constType.compareTo(that.m_constType);
        if (n == 0)
            {
            TypeConstant[] atypeThis = this.m_atypeParams;
            TypeConstant[] atypeThat = that.m_atypeParams;
            for (int i = 0, c = Math.min(atypeThis.length, atypeThat.length); i < c; ++i)
                {
                n = atypeThis[i].compareTo(atypeThat[i]);
                if (n != 0)
                    {
                    return n;
                    }
                }
            n = atypeThis.length - atypeThat.length;
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(m_constType.getValueString())
          .append('<');

        boolean first = true;
        for (TypeConstant type : m_atypeParams)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(type.getValueString());
            }

        sb.append('>');

        return sb.toString();
        }

    /**
     * Temporary to prevent stack overflow.
     *
     * @throws IllegalStateException if it appears that there is an infinite recursion
     */
    protected void checkDepth(boolean fBefore)
        {
        if (fBefore)
            {
            if (++m_cDepth > 20)
                {
                throw new IllegalStateException();
                }
            }
        else
            {
            --m_cDepth;
            }
        }
    private static int m_cDepth;


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        ConstantPool pool = getConstantPool();

        m_constType = (TypeConstant) pool.getConstant(m_iType);

        if (m_aiTypeParams == null)
            {
            m_atypeParams = ConstantPool.NO_TYPES;
            }
        else
            {
            int            cParams     = m_aiTypeParams.length;
            TypeConstant[] atypeParams = new TypeConstant[cParams];
            for (int i = 0; i < cParams; ++i)
                {
                atypeParams[i] = (TypeConstant) pool.getConstant(m_aiTypeParams[i]);
                }
            m_atypeParams  = atypeParams;
            m_aiTypeParams = null;
            }
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_constType = (TypeConstant) pool.register(m_constType);

        TypeConstant[] atypeParams = m_atypeParams;
        for (int i = 0, c = atypeParams.length; i < c; ++i)
            {
            TypeConstant constOld = atypeParams[i];
            TypeConstant constNew = (TypeConstant) pool.register(constOld);
            if (constNew != constOld)
                {
                atypeParams[i] = constNew;
                }
            }
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, indexOf(m_constType));
        writePackedLong(out, m_atypeParams.length);
        for (TypeConstant constType : m_atypeParams)
            {
            writePackedLong(out, constType.getPosition());
            }
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        boolean fHalt = false;

        if (!isValidated())
            {
            fHalt |= super.validate(errs);

            // a parameterized type constant has to be followed by a terminal type constant
            // specifying a class/interface identity
            if (!((TypeConstant) m_constType.simplify()).isExplicitClassIdentity(false))
                {
                fHalt |= log(errs, Severity.ERROR, VE_PARAM_TYPE_ILLEGAL, m_constType.getValueString());
                }
            }

        return fHalt;
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        int n = m_constType.hashCode() + m_atypeParams.length;
        for (TypeConstant type : m_atypeParams)
            {
            n ^= type.hashCode();
            }
        return n;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the underlying TypeConstant.
     */
    private transient int m_iType;

    /**
     * During disassembly, this holds the index of the the type parameters.
     */
    private transient int[] m_aiTypeParams;

    /**
     * The underlying TypeConstant.
     */
    private TypeConstant m_constType;

    /**
     * The type parameters.
     */
    private TypeConstant[] m_atypeParams;
    }
