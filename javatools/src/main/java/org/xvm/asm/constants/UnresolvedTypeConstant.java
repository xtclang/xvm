package org.xvm.asm.constants;


import java.io.DataOutput;

import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ComponentResolver.ResolutionCollector;
import org.xvm.asm.ComponentResolver.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.compiler.Compiler;

import org.xvm.util.Hash;
import org.xvm.util.Severity;


/**
 * Represent a type constant that will eventually be replaced with a real type constant.
 */
public class UnresolvedTypeConstant
        extends TypeConstant
        implements ResolvableConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a place-holder constant that will eventually be replaced with a real type constant.
     *
     * Note, that outside of "equals" and "toString" implementations, the information held by this
     * constant is not used at all. The resolution logic (see
     * {@link org.xvm.compiler.ast.NamedTypeExpression#resolveNames) will use its own state to
     * {@link #resolve(Constant) resolve} it.
     *
     * @param pool  the ConstantPool that this TypeConstant should belong to, even though it will
     *              never contain it while it's unresolved and will immediately replace it as it
     *              becomes resolved
     */
    public UnresolvedTypeConstant(ConstantPool pool, UnresolvedNameConstant constUnresolved)
        {
        super(pool);

        m_constId = constUnresolved;
        }


    // ----- type-specific functionality -----------------------------------------------------------

    @Override
    public Constant resolve()
        {
        return unwrap();
        }

    /**
     * @return true if the UnresolvedTypeConstant has been resolved
     */
    public boolean isTypeResolved()
        {
        return m_type != null;
        }

    /**
     * Mark this unresolved type as recursive.
     */
    public void markRecursive()
        {
        m_fRecursive = true;
        }

    /**
     * @return resolved underlying type
     */
    protected TypeConstant getResolvedType()
        {
        TypeConstant type = m_type;
        if (type != null)
            {
            TypeConstant typeResolved = type.resolveTypedefs();
            if (typeResolved != type)
                {
                m_type = type = typeResolved;
                }
            }
        return type;
        }


    // ----- ResolvableConstant methods ------------------------------------------------------------

    @Override
    public Constant getResolvedConstant()
        {
        return getResolvedType();
        }

    @Override
    public void resolve(Constant constant)
        {
        if (!m_fRecursive)
            {
            assert m_type == null || m_type == constant;
            m_type = (TypeConstant) constant;
            }
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public int getTypeDepth()
        {
        return isTypeResolved() ? getResolvedType().getTypeDepth() : 1;
        }

    @Override
    public boolean isModifyingType()
        {
        return isTypeResolved() && getResolvedType().isModifyingType();
        }

    @Override
    public boolean isRelationalType()
        {
        return isTypeResolved() && getResolvedType().isRelationalType();
        }

    @Override
    public TypeConstant getUnderlyingType()
        {
        if (isTypeResolved())
            {
            return m_type;
            }
        throw new IllegalStateException();
        }

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        if (isTypeResolved())
            {
            return getResolvedType().isComposedOfAny(setIds);
            }

        String sName = m_constId.getName();
        for (IdentityConstant id : setIds)
            {
            if (id.getName().equals(sName))
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public boolean isImmutable()
        {
        return isTypeResolved() && getResolvedType().isImmutable();
        }

    @Override
    public boolean isVirtualChild()
        {
        return isTypeResolved() && getResolvedType().isVirtualChild();
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        return !isTypeResolved() || getResolvedType().isSingleDefiningConstant();
        }

    @Override
    public boolean isTuple()
        {
        if (isTypeResolved())
            {
            return getResolvedType().isTuple();
            }
        String sName = m_constId.isNameResolved()
                        ? m_constId.getResolvedConstant().getValueString()
                        : m_constId.getValueString();
        return sName.equals("Tuple");
        }

    @Override
    public Constant getDefiningConstant()
        {
        return isTypeResolved()
                ? getResolvedType().getDefiningConstant()
                : m_constId.isNameResolved()
                        ? m_constId.getResolvedConstant()
                        : m_constId;
        }

    @Override
    public boolean isConstant()
        {
        return isTypeResolved() && getResolvedType().isConstant();
        }

    @Override
    public boolean isTypeOfType()
        {
        return isTypeResolved() && getResolvedType().isTypeOfType();
        }

    @Override
    public boolean isImmutabilitySpecified()
        {
        return isTypeResolved() && getResolvedType().isImmutabilitySpecified();
        }

    @Override
    public boolean isAccessSpecified()
        {
        return isTypeResolved() && getResolvedType().isAccessSpecified();
        }

    @Override
    public Access getAccess()
        {
        return isTypeResolved()
                ? getResolvedType().getAccess()
                : Access.PUBLIC;
        }

    @Override
    public boolean isNullable()
        {
        return isTypeResolved() && getResolvedType().isNullable();
        }

    @Override
    public TypeConstant removeNullable()
        {
        if (isTypeResolved())
            {
            return getResolvedType().removeNullable();
            }
        throw new IllegalStateException();
        }

    @Override
    public Category getCategory()
        {
        return isTypeResolved() ? getResolvedType().getCategory() : Category.OTHER;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return isTypeResolved() && getResolvedType().isSingleUnderlyingClass(fAllowInterface);
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return isTypeResolved() && getResolvedType().isExplicitClassIdentity(fAllowParams);
        }

    @Override
    public TypeConstant getExplicitClassInto(boolean fResolve)
        {
        if (isTypeResolved())
            {
            return getResolvedType().getExplicitClassInto(fResolve);
            }
        throw new IllegalStateException();
        }

    @Override
    public boolean isIntoMetaData(TypeConstant typeTarget, boolean fStrict)
        {
        return isTypeResolved() && getResolvedType().isIntoMetaData(typeTarget, fStrict);
        }

    @Override
    public String getEcstasyClassName()
        {
        return isTypeResolved() ? getResolvedType().getEcstasyClassName() : "?";
        }

    @Override
    public TypeConstant resolveConstraints()
        {
        return isTypeResolved()
                ? getResolvedType().resolveConstraints()
                : this;
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        return m_type == null
                ? this
                : m_fRecursive
                    ? m_type
                    : m_type.resolveTypedefs();
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return isTypeResolved() && getResolvedType().containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        return isTypeResolved()
                ? getResolvedType().getGenericParamType(sName, listParams)
                : null;
        }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild)
        {
        return isTypeResolved() && getResolvedType().containsAutoNarrowing(fAllowVirtChild);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx)
        {
        return isTypeResolved()
                ? getResolvedType().resolveAutoNarrowing(pool, fRetainParams, typeTarget, idCtx)
                : this;
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        return isTypeResolved()
                ? getResolvedType().resolveGenerics(pool, resolver)
                : this;
        }

    @Override
    public ResolutionResult resolveContributedName(
            String sName, Access access, MethodConstant idMethod, ResolutionCollector collector)
        {
        return isTypeResolved()
                ? getResolvedType().resolveContributedName(sName, access, idMethod, collector)
                : ResolutionResult.POSSIBLE;
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        return isTypeResolved()
                ? getResolvedType().adoptParameters(pool, atypeParams)
                : this;
        }

    @Override
    public TypeConstant[] collectGenericParameters()
        {
        return isTypeResolved()
                ? getResolvedType().collectGenericParameters()
                : null;
        }

    @Override
    public Relation calculateRelation(TypeConstant typeLeft)
        {
        return isTypeResolved()
                ? getResolvedType().calculateRelation(typeLeft)
                : Relation.INCOMPATIBLE;
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return isTypeResolved() &&
                getResolvedType().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

    @Override
    public boolean containsFormalType(boolean fAllowParams)
        {
        return isTypeResolved() && getResolvedType().containsFormalType(fAllowParams);
        }

    @Override
    public boolean containsGenericType(boolean fAllowParams)
        {
        return isTypeResolved() &&
                getResolvedType().containsGenericType(fAllowParams);
        }

    @Override
    public boolean consumesFormalType(String sTypeName, Access access)
        {
        return isTypeResolved() && getResolvedType().consumesFormalType(sTypeName, access);
        }

    @Override
    protected Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return isTypeResolved()
                ? getResolvedType().checkConsumption(sTypeName, access, listParams)
                : Usage.NO;
        }

    @Override
    public boolean producesFormalType(String sTypeName, Access access)
        {
        return isTypeResolved() && getResolvedType().producesFormalType(sTypeName, access);
        }

    @Override
    protected Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        return isTypeResolved()
                ? getResolvedType().checkProduction(sTypeName, access, listParams)
                : Usage.NO;
        }

    @Override
    public boolean isIntoPropertyType()
        {
        return isTypeResolved() && getResolvedType().isIntoPropertyType();
        }

    @Override
    public boolean isIntoVariableType()
        {
        return isTypeResolved() && getResolvedType().isIntoVariableType();
        }

    @Override
    public TypeInfo getTypeInfo()
        {
        if (isTypeResolved())
            {
            return getResolvedType().getTypeInfo();
            }
        throw new IllegalStateException();
        }

    @Override
    public TypeInfo ensureTypeInfo(IdentityConstant idClass, ErrorListener errs)
        {
        if (isTypeResolved())
            {
            return getResolvedType().ensureTypeInfo(idClass, errs);
            }
        throw new IllegalStateException();
        }

    @Override
    public TypeInfo ensureTypeInfo(ErrorListener errs)
        {
        if (isTypeResolved())
            {
            return getResolvedType().ensureTypeInfo(errs);
            }
        throw new IllegalStateException();
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        if (isTypeResolved())
            {
            return getResolvedType().ensureTypeInfoInternal(errs);
            }
        throw new IllegalStateException();
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return isTypeResolved()
                ? getResolvedType().getFormat()
                : Format.UnresolvedType;
        }

    @Override
    public boolean isClass()
        {
        return isTypeResolved() && getResolvedType().isClass();
        }

    @Override
    public boolean containsUnresolved()
        {
        return !m_fRecursive && (m_type == null || m_type.containsUnresolved());
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        if (isTypeResolved())
            {
            visitor.accept(getResolvedType());
            }
        }

    @Override
    protected void setPosition(int iPos)
        {
        throw new IllegalStateException("unresolved: " + this);
        }

    @Override
    public String getValueString()
        {
        return isTypeResolved()
                ? getResolvedType().getValueString()
                : m_constId.getValueString();
        }

    @Override
    protected int compareDetails(Constant that)
        {
        that = that.resolve();

        if (isTypeResolved())
            {
            return getResolvedType().compareDetails(that);
            }

        if (that instanceof UnresolvedTypeConstant thatUnresolved)
            {
            return m_constId.compareDetails(thatUnresolved.m_constId);
            }

        // need to return a value that allows for stable sorts, but unless this==that, the
        // details can never be equal
        return this == that ? 0 : -1;
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        throw new IllegalStateException();
        }

    @Override
    protected void assemble(DataOutput out)
        {
        throw new IllegalStateException(toString());
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        if (isTypeResolved())
            {
            return getResolvedType().validate(errs);
            }

        errs.log(Severity.ERROR, Compiler.NAME_UNRESOLVABLE, new Object[]{getValueString()}, this);
        return true;
        }

    @Override
    public String getDescription()
        {
        return isTypeResolved()
                ? "(resolved) " + getResolvedType().getDescription()
                : m_constId.getDescription();
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        if (isTypeResolved())
            {
            return Hash.of(getResolvedType());
            }

        // calculate a temporary hash code
        int nHash = m_nUnresolvedHash;
        if (nHash == 0)
            {
            nHash = Hash.of(m_constId);
            if (nHash == 0)
                {
                nHash = 1234567891; // prime
                }
            m_nUnresolvedHash = nHash;
            }
        return nHash;
        }

    @Override
    public int computeHashCode()
        {
        return 0;
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The underlying unresolved constant.
     */
    private final UnresolvedNameConstant m_constId;

    /**
     * The resolved type constant.
     */
    private TypeConstant m_type;

    /**
     * Recursive type indicator.
     */
    private boolean m_fRecursive;

    /**
     * A temporary hash code.
     */
    private transient int m_nUnresolvedHash;
    }