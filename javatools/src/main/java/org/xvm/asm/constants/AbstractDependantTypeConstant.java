package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.Register;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A base class for TypeConstants based on the parent's type.
 */
public abstract class AbstractDependantTypeConstant
        extends TypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     */
    public AbstractDependantTypeConstant(ConstantPool pool, TypeConstant typeParent)
        {
        super(pool);

        if (typeParent == null)
            {
            throw new IllegalArgumentException("parent type is required");
            }

        m_typeParent = typeParent.resolveTypedefs();
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
    public AbstractDependantTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iTypeParent = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        m_typeParent = (TypeConstant) getConstantPool().getConstant(m_iTypeParent);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isShared(ConstantPool poolOther)
        {
        return m_typeParent.isShared(poolOther);
        }

    @Override
    public boolean isComposedOfAny(Set<IdentityConstant> setIds)
        {
        return setIds.contains(getSingleUnderlyingClass(true));
        }

    @Override
    public boolean isImmutabilitySpecified()
        {
        return false;
        }

    @Override
    public boolean isImmutable()
        {
        return false;
        }

    @Override
    public boolean isAccessSpecified()
        {
        return false;
        }

    @Override
    public Access getAccess()
        {
        return Access.PUBLIC;
        }

    @Override
    public boolean isParamsSpecified()
        {
        return false;
        }

    @Override
    public int getTypeDepth()
        {
        return 1 + m_typeParent.getTypeDepth();
        }

    @Override
    public boolean isAnnotated()
        {
        return false;
        }

    @Override
    public TypeConstant getParentType()
        {
        return m_typeParent;
        }

    @Override
    public boolean isSingleDefiningConstant()
        {
        return true;
        }

    @Override
    public Constant getDefiningConstant()
        {
        return getSingleUnderlyingClass(true);
        }

    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal.resolveTypedefs();
        return typeOriginal == typeResolved
                ? this
                : cloneSingle(getConstantPool(), typeResolved);
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal.resolveGenerics(pool, resolver);
        return typeOriginal == typeResolved
                ? this
                : cloneSingle(pool, typeResolved);
        }

    @Override
    public TypeConstant resolveConstraints(boolean fPendingOnly)
        {
        TypeConstant constOriginal = getParentType();
        TypeConstant constResolved = constOriginal.resolveConstraints(fPendingOnly);
        return constResolved == constOriginal
                ? this
                : cloneSingle(getConstantPool(), constResolved);
        }

    @Override
    public TypeConstant resolveDynamicConstraints(Register register)
        {
        TypeConstant constOriginal = getParentType();
        TypeConstant constResolved = constOriginal.resolveDynamicConstraints(register);
        return constResolved == constOriginal
                ? this
                : cloneSingle(getConstantPool(), constResolved);
        }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild)
        {
        return false;
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return false;
        }

    @Override
    public TypeConstant ensureAutoNarrowing()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx)
        {
        return this;
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        return typeActual.isVirtualChild() || typeActual.isAnonymousClass()
                ? getParentType().resolveTypeParameter(typeActual.getParentType(), sFormalName)
                : null;
        }

    @Override
    public boolean containsFormalType(boolean fAllowParams)
        {
        return getParentType().containsFormalType(fAllowParams);
        }

    @Override
    public void collectFormalTypes(boolean fAllowParams, Set<TypeConstant> setFormal)
        {
        getParentType().collectFormalTypes(fAllowParams, setFormal);
        }

    @Override
    public boolean containsDynamicType(Register register)
        {
        return getParentType().containsDynamicType(register);
        }

    @Override
    public boolean containsGenericType(boolean fAllowParams)
        {
        return getParentType().containsGenericType(fAllowParams);
        }

    @Override
    public boolean containsTypeParameter(boolean fAllowParams)
        {
        return getParentType().containsTypeParameter(fAllowParams);
        }

    @Override
    public boolean containsRecursiveType()
        {
        return getParentType().containsRecursiveType();
        }

    @Override
    public boolean containsFunctionType()
        {
        return false;
        }

    @Override
    public boolean isTuple()
        {
        return false;
        }

    @Override
    public boolean isOnlyNullable()
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
    public boolean isIntoMetaData(TypeConstant typeTarget, boolean fStrict)
        {
        return false;
        }

    @Override
    public boolean isTypeOfType()
        {
        return false;
        }


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public ClassTemplate getTemplate(Container container)
        {
        return container.getTemplate((ClassConstant) getDefiningConstant());
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public boolean containsUnresolved()
        {
        return !isHashCached() && m_typeParent.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_typeParent);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        if (!(obj instanceof AbstractDependantTypeConstant that))
            {
            return -1;
            }

        return this.m_typeParent.compareTo(that.m_typeParent);
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_typeParent = (TypeConstant) pool.register(m_typeParent);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_typeParent.getPosition());
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The parent's TypeConstant.
     */
    protected TypeConstant m_typeParent;

    /**
     * During disassembly, this holds the index of the underlying TypeConstant.
     */
    private transient int m_iTypeParent;
    }