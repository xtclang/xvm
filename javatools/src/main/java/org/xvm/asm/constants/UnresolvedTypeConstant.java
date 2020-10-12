package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import java.util.List;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.compiler.Compiler;

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
            assert constant instanceof TypeConstant;
            m_type = (TypeConstant) constant;
            }
        }

    // ----- TypeConstant methods ------------------------------------------------------------------

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
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return isTypeResolved() && getResolvedType().isExplicitClassIdentity(fAllowParams);
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
        return isTypeResolved() && getResolvedType().isComposedOfAny(setIds);
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
    public void bindTypeParameters(MethodConstant idMethod)
        {
        if (isTypeResolved())
            {
            getResolvedType().bindTypeParameters(idMethod);
            }
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
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return isTypeResolved() && getResolvedType().isAutoNarrowing(fAllowVirtChild);
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        return isTypeResolved()
                ? getResolvedType().resolveAutoNarrowing(pool, fRetainParams, typeTarget)
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
    public ResolutionResult resolveContributedName(String sName, Access access, ResolutionCollector collector)
        {
        return isTypeResolved()
                ? getResolvedType().resolveContributedName(sName, access, collector)
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
        if (that instanceof ResolvableConstant)
            {
            that = ((ResolvableConstant) that).unwrap();
            }

        if (isTypeResolved())
            {
            return getResolvedType().compareDetails(that);
            }

        if (that instanceof UnresolvedTypeConstant)
            {
            return m_constId.compareDetails(
                        ((UnresolvedTypeConstant) that).m_constId);
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
            throws IOException
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
        return isTypeResolved()
            ? getResolvedType().hashCode()
            : m_constId.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The underlying unresolved constant.
     */
    private UnresolvedNameConstant m_constId;

    /**
     * The resolved type constant.
     */
    private TypeConstant m_type;

    /**
     * Recursive type indicator.
     */
    private boolean m_fRecursive;
    }
