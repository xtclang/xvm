package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.ResolutionCollector;
import org.xvm.asm.Component.ResolutionResult;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

import org.xvm.runtime.OpSupport;
import org.xvm.runtime.TemplateRegistry;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents an instance child of a class; for example:
 * <pre>
 * class Parent<ParentType>
 *     {
 *     class Child <ChildType>
 *         {
 *         }
 *     static void test()
 *         {
 *         Parent<String> p = new Parent();
 *         Child<Int>     c = p.new Child();
 *         ...
 *         }
 *     }
 * </pre>
 *
 * The type of the variable "c" above is:
 *    ParameterizedTypeConstant(T1, Int),
 *      where T1 is VirtualChildTypeConstant(T2, "Child"),
 *      where T2 is ParameterizedTypeConstant(T3, String),
 *      where T3 is TerminalTypeConstant(Parent)
 */
public class VirtualChildTypeConstant
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
    public VirtualChildTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool);

        m_iTypeParent = readIndex(in);
        m_iName       = readIndex(in);
        }

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param sName       the child's name
     */
    public VirtualChildTypeConstant(ConstantPool pool, TypeConstant typeParent, String sName)
        {
        super(pool);

        if (typeParent == null)
            {
            throw new IllegalArgumentException("parent type is required");
            }

        typeParent = typeParent.resolveTypedefs();

        if (typeParent.isAccessSpecified() ||
            typeParent.isImmutabilitySpecified() ||
            typeParent.isAnnotated())
            {
            throw new IllegalArgumentException("parent's immutability, access or annotations cannot be specified");
            }
        if (sName == null)
            {
            throw new IllegalArgumentException("child name is required");
            }

        m_typeParent = typeParent;
        m_constName  = pool.ensureStringConstant(sName);
        }

    /**
     * @return the child name of this {@link VirtualChildTypeConstant}
     */
    public String getChildName()
        {
        return m_constName.getValue();
        }

    /**
     * @return the child ClassStructure associated with this type
     */
    public ClassStructure getChildStructure()
        {
        ClassStructure parent = (ClassStructure)
                m_typeParent.getSingleUnderlyingClass(true).getComponent();
        ClassStructure child = parent.getVirtualChild(getChildName());
        if (child == null)
            {
            throw new IllegalStateException();
            }
        return child;
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

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
    public int getMaxParamsCount()
        {
        ClassConstant  constClz = (ClassConstant) getDefiningConstant();
        ClassStructure clz      = (ClassStructure) constClz.getComponent();
        return clz.getTypeParams().size();
        }

    @Override
    public boolean isAnnotated()
        {
        return false;
        }

    @Override
    public boolean isVirtualChild()
        {
        return true;
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
    public boolean isAutoNarrowing(boolean fAllowVirtChild)
        {
        return fAllowVirtChild;
        }

    @Override
    public ResolutionResult resolveContributedName(String sName, ResolutionCollector collector)
        {
        if (containsUnresolved())
            {
            return ResolutionResult.POSSIBLE;
            }

        ClassConstant constClz = (ClassConstant) getDefiningConstant();
        return ((ClassStructure) constClz.getComponent()).
            resolveContributedName(sName, Access.PUBLIC, collector, true);
        }


    @Override
    public TypeConstant resolveTypedefs()
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal.resolveTypedefs();
        return typeOriginal == typeResolved
                ? this
                : getConstantPool().ensureVirtualChildTypeConstant(typeResolved, m_constName.getValue());
        }

    @Override
    public void bindTypeParameters(MethodConstant idMethod)
        {
        // not applicable
        }

    @Override
    public TypeConstant resolveGenerics(ConstantPool pool, GenericTypeResolver resolver)
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal.resolveGenerics(pool, resolver);
        return typeOriginal == typeResolved
                ? this
                : pool.ensureVirtualChildTypeConstant(typeResolved, m_constName.getValue());
        }

    @Override
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        if (atypeParams == null)
            {
            // this is a "normalization" call
            atypeParams = ConstantPool.NO_TYPES;
            }

        IdentityConstant idClz = getSingleUnderlyingClass(true);
        ClassStructure   clz   = (ClassStructure) idClz.getComponent();
        if (clz.isParameterized())
            {
            return pool.ensureParameterizedTypeConstant(this,
                clz.normalizeParameters(pool, atypeParams));
            }

        // not parameterized
        return this;
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams, TypeConstant typeTarget)
        {
        TypeConstant typeOriginal = m_typeParent;
        TypeConstant typeResolved = typeOriginal;

        if (typeOriginal.isAutoNarrowing(false))
            {
            typeResolved = typeOriginal.resolveAutoNarrowing(pool, fRetainParams, typeTarget);
            }
        else if (typeTarget != null && typeTarget.isA(typeResolved))
            {
            // strip the immutability and access modifiers
            while (typeTarget instanceof ImmutableTypeConstant ||
                   typeTarget instanceof AccessTypeConstant)
                {
                typeTarget = typeTarget.getUnderlyingType();
                }
            typeResolved = typeTarget;
            }
        return typeOriginal == typeResolved
                ? this
                : pool.ensureVirtualChildTypeConstant(typeResolved, m_constName.getValue());
        }

    @Override
    public boolean isNarrowedFrom(TypeConstant typeSuper, TypeConstant typeCtx)
        {
        if (typeSuper instanceof VirtualChildTypeConstant)
            {
            VirtualChildTypeConstant that = (VirtualChildTypeConstant) typeSuper;
            return this.m_constName.equals(that.m_constName) &&
                   this.m_typeParent.isNarrowedFrom(that.m_typeParent, typeCtx);
            }
        return false;
        }

    @Override
    public TypeConstant resolveTypeParameter(TypeConstant typeActual, String sFormalName)
        {
        return null;
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
    public boolean extendsClass(IdentityConstant constClass)
        {
        ClassConstant idClass = (ClassConstant) getDefiningConstant();
        return ((ClassStructure) idClass.getComponent()).extendsClass(constClass);
        }

    @Override
    public Category getCategory()
        {
        ClassConstant  idClass = (ClassConstant) getDefiningConstant();
        ClassStructure clz     = (ClassStructure) idClass.getComponent();
        return clz.getFormat() == Component.Format.INTERFACE
                ? Category.IFACE : Category.CLASS;
        }

    @Override
    public boolean isSingleUnderlyingClass(boolean fAllowInterface)
        {
        return fAllowInterface || getExplicitClassFormat() != Component.Format.INTERFACE;
        }

    @Override
    public IdentityConstant getSingleUnderlyingClass(boolean fAllowInterface)
        {
        assert isSingleUnderlyingClass(fAllowInterface);

        return getChildStructure().getIdentityConstant();
        }

    @Override
    public boolean isExplicitClassIdentity(boolean fAllowParams)
        {
        return true;
        }

    @Override
    public Component.Format getExplicitClassFormat()
        {
        return getChildStructure().getFormat();
        }

    @Override
    public TypeConstant getExplicitClassInto()
        {
        throw new IllegalStateException();
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
    public boolean isConstant()
        {
        return false;
        }

    @Override
    public boolean isTypeOfType()
        {
        return false;
        }


    @Override
    public boolean containsGenericParam(String sName)
        {
        return m_typeParent.containsGenericParam(sName)
            || super.containsGenericParam(sName);
        }

    @Override
    public TypeConstant getGenericParamType(String sName)
        {
        TypeConstant typeParent = m_typeParent;
        if (typeParent.containsGenericParam(sName))
            {
            return typeParent.getGenericParamType(sName);
            }
        return super.getGenericParamType(sName);
        }

    @Override
    public TypeConstant getOuterType()
        {
        return m_typeParent;
        }

    @Override
    protected TypeInfo buildTypeInfo(ErrorListener errs)
        {
        return super.buildTypeInfo(errs);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        ClassConstant  idClass = (ClassConstant) getDefiningConstant();
        ClassStructure clz     = (ClassStructure) idClass.getComponent();

        assert clz.getFormat() == Component.Format.INTERFACE;

        return clz.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               List<TypeConstant> listParams)
        {
        ClassConstant  idClass = (ClassConstant) getDefiningConstant();
        ClassStructure clz     = (ClassStructure) idClass.getComponent();

        return clz.containsSubstitutableMethod(signature, access, listParams);
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!listParams.isEmpty())
            {
            ClassConstant  idClass = (ClassConstant) getDefiningConstant();
            ClassStructure clz     = (ClassStructure) idClass.getComponent();

            Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

            listParams = clz.normalizeParameters(ConstantPool.getCurrentPool(), listParams);

            Iterator<TypeConstant> iterParams = listParams.iterator();
            Iterator<StringConstant> iterNames = mapFormal.keySet().iterator();

            while (iterParams.hasNext())
                {
                TypeConstant constParam = iterParams.next();
                String       sFormal    = iterNames.next().getValue();

                if (constParam.consumesFormalType(sTypeName, access)
                        && clz.producesFormalType(sFormal, access, listParams)
                    ||
                    constParam.producesFormalType(sTypeName, access)
                        && clz.consumesFormalType(sFormal, access, listParams))
                    {
                    return Usage.YES;
                    }
                }
            }
        return Usage.NO;
        }

    @Override
    public Usage checkProduction(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!listParams.isEmpty())
            {
            ClassConstant  idClass = (ClassConstant) getDefiningConstant();
            ClassStructure clz     = (ClassStructure) idClass.getComponent();

            Map<StringConstant, TypeConstant> mapFormal = clz.getTypeParams();

            listParams = clz.normalizeParameters(ConstantPool.getCurrentPool(), listParams);

            Iterator<TypeConstant>   iterParams = listParams.iterator();
            Iterator<StringConstant> iterNames  = mapFormal.keySet().iterator();

            while (iterParams.hasNext())
                {
                TypeConstant constParam = iterParams.next();
                String       sFormal    = iterNames.next().getValue();

                if (constParam.producesFormalType(sTypeName, access)
                        && clz.producesFormalType(sFormal, access, listParams)
                    ||
                    constParam.consumesFormalType(sTypeName, access)
                        && clz.consumesFormalType(sFormal, access, listParams))
                    {
                    return Usage.YES;
                    }
                }
            }
        return Usage.NO;
        }


    // ----- type comparison support ---------------------------------------------------------------


    // ----- run-time support ----------------------------------------------------------------------

    @Override
    public OpSupport getOpSupport(TemplateRegistry registry)
        {
        return registry.getTemplate((ClassConstant) getDefiningConstant());
        }


    // ----- Constant methods ----------------------------------------------------------------------

    @Override
    public Format getFormat()
        {
        return Format.VirtualChildType;
        }

    @Override
    public boolean containsUnresolved()
        {
        return m_typeParent.containsUnresolved();
        }

    @Override
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        visitor.accept(m_typeParent);
        visitor.accept(m_constName);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        VirtualChildTypeConstant that = (VirtualChildTypeConstant) obj;
        int n = this.m_typeParent.compareTo(that.m_typeParent);
        if (n == 0)
            {
            n = this.m_constName.compareTo(that.m_constName);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return m_typeParent.getValueString() + '.' + m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void disassemble(DataInput in)
            throws IOException
        {
        m_typeParent = (TypeConstant) getConstantPool().getConstant(m_iTypeParent);
        m_constName  = (StringConstant) getConstantPool().getConstant(m_iName);
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        m_typeParent = (TypeConstant) pool.register(m_typeParent);
        m_constName  = (StringConstant) pool.register(m_constName);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        out.writeByte(getFormat().ordinal());
        writePackedLong(out, m_typeParent.getPosition());
        writePackedLong(out, m_constName.getPosition());
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_typeParent.hashCode() + m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the underlying TypeConstant.
     */
    private transient int m_iTypeParent;

    /**
     * During disassembly, this holds the index of the StringConstant for the name.
     */
    private transient int m_iName;

    /**
     * The parent's TypeConstant.
     */
    private TypeConstant m_typeParent;

    /**
     * The ChildClassConstant representing this child type.
     */
    private StringConstant m_constName;
    }
