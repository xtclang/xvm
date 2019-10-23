package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Collections;
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

import org.xvm.util.Severity;

import static org.xvm.util.Handy.readIndex;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A TypeConstant that represents an instance child of a class; for example:
 *
 * <pre>
 * class Parent&lt;ParentType>
 *     {
 *     class Child&lt;ChildType>
 *         {
 *         }
 *     static void test()
 *         {
 *         Parent&lt;String> p = new Parent();
 *         Child&lt;Int>     c = p.new Child();
 *         ...
 *         }
 *     }
 * </pre>
 *
 * The type of the variable "c" above is:
 *   {@code ParameterizedTypeConstant(T1, Int)}
 * <br/>where T1 is {@code VirtualChildTypeConstant(T2, "Child")},
 * <br/>where T2 is {@code ParameterizedTypeConstant(T3, String)},
 * <br/>where T3 is {@code TerminalTypeConstant(Parent)}
 */
public class VirtualChildTypeConstant
        extends AbstractDependantTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a data type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param sName       the child's name
     */
    public VirtualChildTypeConstant(ConstantPool pool, TypeConstant typeParent, String sName)
        {
        super(pool, typeParent);

        if (typeParent.isAccessSpecified() ||
            typeParent.isAnnotated())
            {
            throw new IllegalArgumentException("parent's immutability, access or annotations cannot be specified");
            }
        if (sName == null)
            {
            throw new IllegalArgumentException("name is required");
            }
        m_constName = pool.ensureStringConstant(sName);
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
    public VirtualChildTypeConstant(ConstantPool pool, Format format, DataInput in)
            throws IOException
        {
        super(pool, format, in);

        m_iName = readIndex(in);
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();

        m_constName = (StringConstant) getConstantPool().getConstant(m_iName);
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
        return parent.getVirtualChild(getChildName());
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public int getMaxParamsCount()
        {
        return getChildStructure().getTypeParams().size();
        }

    @Override
    public boolean isVirtualChild()
        {
        return true;
        }

    @Override
    public boolean isPhantom()
        {
        TypeConstant typeParent = m_typeParent;
        if (typeParent.isVirtualChild() && typeParent.isPhantom())
            {
            // a child of a phantom is a phantom
            return true;
            }

        ClassStructure parent = (ClassStructure)
                typeParent.getSingleUnderlyingClass(true).getComponent();
        return parent.getChild(getChildName()) == null;
        }

    @Override
    public Constant getDefiningConstant()
        {
        return getSingleUnderlyingClass(true);
        }

    @Override
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return pool.ensureVirtualChildTypeConstant(type, m_constName.getValue());
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

        return getChildStructure().resolveName(sName, Access.PUBLIC, collector);
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
    public TypeConstant adoptParameters(ConstantPool pool, TypeConstant[] atypeParams)
        {
        if (atypeParams == null)
            {
            // this is a "normalization" call
            atypeParams = ConstantPool.NO_TYPES;
            }

        ClassStructure clz = getChildStructure();
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
                : cloneSingle(pool, typeResolved);
        }

    @Override
    public boolean extendsClass(IdentityConstant constClass)
        {
        return getChildStructure().extendsClass(constClass);
        }

    @Override
    public Category getCategory()
        {
        ClassStructure clz = getChildStructure();
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
        ClassStructure struct = getChildStructure();
        if (struct == null || struct.getFormat() != Component.Format.MIXIN)
            {
            throw new IllegalStateException("mixin=" + struct);
            }

        return struct.getTypeInto();
        }

    @Override
    public boolean containsGenericParam(String sName)
        {
        return getChildStructure().containsGenericParamType(sName)
            || m_typeParent.containsGenericParam(sName);
        }

    @Override
    protected TypeConstant getGenericParamType(String sName, List<TypeConstant> listParams)
        {
        ConstantPool pool = getConstantPool();
        TypeConstant type = getChildStructure().getGenericParamType(pool, sName, listParams);
        if (type != null)
            {
            return type.isGenericType()
                    ? type.resolveGenerics(pool, m_typeParent)
                    : type;
            }

        // the passed in list represents the "child" and should not be used by the parent
        return m_typeParent.getGenericParamType(sName, Collections.EMPTY_LIST);
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Set<SignatureConstant> isInterfaceAssignableFrom(TypeConstant typeRight, Access accessLeft,
                                                               List<TypeConstant> listLeft)
        {
        ClassStructure clz = getChildStructure();

        assert clz.getFormat() == Component.Format.INTERFACE;

        return clz.isInterfaceAssignableFrom(typeRight, accessLeft, listLeft);
        }

    @Override
    public boolean containsSubstitutableMethod(SignatureConstant signature, Access access,
                                               boolean fFunction, List<TypeConstant> listParams)
        {
        return getChildStructure().containsSubstitutableMethod(signature, access, fFunction, listParams);
        }

    @Override
    public Usage checkConsumption(String sTypeName, Access access, List<TypeConstant> listParams)
        {
        if (!listParams.isEmpty())
            {
            ClassStructure clz = getChildStructure();

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
            ClassStructure clz = getChildStructure();

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
    public void forEachUnderlying(Consumer<Constant> visitor)
        {
        super.forEachUnderlying(visitor);

        visitor.accept(m_constName);
        }

    @Override
    protected int compareDetails(Constant obj)
        {
        int n = super.compareDetails(obj);
        if (n == 0)
            {
            VirtualChildTypeConstant that = (VirtualChildTypeConstant) obj;
            return this.m_constName.compareTo(that.m_constName);
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
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_constName = (StringConstant) pool.register(m_constName);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        super.assemble(out);

        writePackedLong(out, m_constName.getPosition());
        }

    @Override
    public boolean validate(ErrorListener errs)
        {
        if (!isValidated())
            {
            // the child must exists
            if (getChildStructure() == null)
                {
                log(errs, Severity.ERROR, VE_VIRTUAL_CHILD_MISSING,
                                m_constName.getValue(), m_typeParent.getValueString());
                return true;
                }
            return super.validate(errs);
            }

        return false;
        }

    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode()
        {
        return m_typeParent.hashCode() + m_constName.hashCode();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * During disassembly, this holds the index of the StringConstant for the name.
     */
    private transient int m_iName;

    /**
     * The StringConstant representing this virtual child's name.
     */
    protected StringConstant m_constName;
    }
