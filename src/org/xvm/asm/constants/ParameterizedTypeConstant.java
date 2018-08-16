package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Component;
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
            ? constResolved.resolveGenerics(getConstantPool(), this)
            : constResolved;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveTypedefs();
        boolean      fDiff         = constOriginal != constResolved;

        assert !constResolved.isParamsSpecified();

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
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveGenerics(pool, resolver);
        boolean      fDiff         = constOriginal != constResolved;

        assert !constResolved.isParamsSpecified();

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstResolved = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamResolved = constParamOriginal.resolveGenerics(pool, resolver);
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
                ? pool.ensureParameterizedTypeConstant(constResolved, aconstResolved)
                : this;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        TypeConstant constOriginal = m_constType;

        assert constOriginal instanceof TerminalTypeConstant;

        return constOriginal.adoptParameters(pool, atypeParams == null ? m_atypeParams : atypeParams);
        }

    @Override
    public TypeConstant adoptParentTypeParameters(ConstantPool pool)
        {
        TypeConstant constOriginal = m_constType;

        assert constOriginal instanceof TerminalTypeConstant;

        TypeConstant constResolved = constOriginal.adoptParentTypeParameters(pool);
        if (constResolved == constOriginal)
            {
            return this;
            }

        // TODO: create ParameterizedTC(ChildTC(typeParent, clzChild))
        //       see ClassStructure.getFormalType()
        TypeConstant[] aconstParent   = constResolved.getParamTypesArray();
        TypeConstant[] aconstThis     = m_atypeParams;
        TypeConstant[] aconstResolved = new TypeConstant[aconstParent.length + aconstThis.length];

        System.arraycopy(aconstParent, 0, aconstResolved, 0, aconstParent.length);
        System.arraycopy(aconstThis,   0, aconstResolved, aconstParent.length, aconstThis.length);

        return getConstantPool().ensureParameterizedTypeConstant(constOriginal, aconstResolved);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, TypeConstant typeTarget)
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constResolved = constOriginal.resolveAutoNarrowing(pool, typeTarget);
        boolean      fDiff         = constOriginal != constResolved;

        assert !constResolved.isParamsSpecified();

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstResolved = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamResolved = constParamOriginal.resolveAutoNarrowing(pool, typeTarget);
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
    public TypeConstant inferAutoNarrowing(ConstantPool pool, IdentityConstant constThisClass)
        {
        TypeConstant constOriginal = m_constType;
        TypeConstant constInferred = constOriginal.inferAutoNarrowing(pool, constThisClass);
        boolean      fDiff         = constOriginal != constInferred;

        TypeConstant[] aconstOriginal = m_atypeParams;
        TypeConstant[] aconstInferred = aconstOriginal;
        for (int i = 0, c = aconstOriginal.length; i < c; ++i)
            {
            TypeConstant constParamOriginal = aconstOriginal[i];
            TypeConstant constParamInferred = constParamOriginal.inferAutoNarrowing(pool, constThisClass);
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
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureParameterizedTypeConstant(type, m_atypeParams);
        }


    // ----- type comparison support --------------------------------------------------------------

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
        m_constType   = (TypeConstant) pool.register(m_constType);
        m_atypeParams = (TypeConstant[]) registerConstants(pool, m_atypeParams);
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
            if (!(m_constType.resolveTypedefs()).isExplicitClassIdentity(false))
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
