package org.xvm.asm.constants;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.function.Consumer;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;

import org.xvm.util.Hash;

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
        extends AbstractDependantChildTypeConstant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a constant whose value is a virtual child type.
     *
     * @param pool        the ConstantPool that will contain this Constant
     * @param typeParent  the parent's type
     * @param sName       the child's name
     * @param fThisClass  if true, this type should allow auto narrowing of the child type
     */
    public VirtualChildTypeConstant(ConstantPool pool, TypeConstant typeParent, String sName,
                                    boolean fThisClass)
        {
        super(pool, typeParent);

        if (sName == null)
            {
            throw new IllegalArgumentException("name is required");
            }
        m_constName        = pool.ensureStringConstant(sName);
        m_fThisClass       = fThisClass;
        m_typeOriginParent = m_typeParent;
        }

    /**
     * Construct a constant whose value is a virtual child type.
     *
     * Note: this constructor is only used for transient virtual child types that are used only by
     *       the isA() and TypeInfo calculations.
     *
     * @param pool              the ConstantPool that will contain this Constant
     * @param typeParent        the parent's type (representing the actual type)
     * @param sName             the child's name
     * @param typeOriginParent  the virtual origin parent's type
     */
    public VirtualChildTypeConstant(ConstantPool pool, TypeConstant typeParent, String sName,
                                    TypeConstant typeOriginParent)
        {
        super(pool, typeParent);

         if (typeParent.isAnnotated())
             {
             throw new IllegalArgumentException("parent's annotations cannot be specified");
             }
        if (sName == null)
            {
            throw new IllegalArgumentException("name is required");
            }
        if (!typeOriginParent.isA(typeParent))
            {
            throw new IllegalArgumentException("origin parent must extend the parent");
            }
        m_constName        = pool.ensureStringConstant(sName);
        m_fThisClass       = false;
        m_typeOriginParent = typeOriginParent;
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

        m_iName      = readIndex(in);
        m_fThisClass = in.readBoolean();
        }

    @Override
    protected void resolveConstants()
        {
        super.resolveConstants();

        m_constName        = (StringConstant) getConstantPool().getConstant(m_iName);
        m_typeOriginParent = m_typeParent;
        }

    /**
     * @return the child name of this {@link VirtualChildTypeConstant}
     */
    public String getChildName()
        {
        return m_constName.getValue();
        }

    @Override
    protected ClassStructure getChildStructure()
        {
        if (m_clzChild != null)
            {
            return m_clzChild;
            }

        TypeConstant typeParent = getParentType();
        String       sChild     = getChildName();

        if (typeParent.isSingleUnderlyingClass(true))
            {
            ClassStructure parent = (ClassStructure) typeParent.
                                        getSingleUnderlyingClass(true).getComponent();
            if (parent != null)
                {
                ClassStructure clzChild = (ClassStructure) parent.findChildDeep(sChild);
                if (clzChild != null)
                    {
                    return m_clzChild = clzChild;
                    }
                }
            }

        // there is a possibility of an intersection type contribution, e.g.:
        //      mixin ListFreezer<Element extends ImmutableAble>
        //        into List<Element> + CopyableCollection<Element>
        // in which case parent.getVirtualChild(sChild) fails to find the child;
        // TODO it could be too early in the compilation cycle to use the TypeInfo
        // so the logic below may need to be removed and getVirtualChild() made more accommodating
        ChildInfo info = typeParent.ensureTypeInfo().getChildInfosByName().get(sChild);
        if (info != null)
            {
            Component child = info.getComponent();
            if (child instanceof ClassStructure clzChild)
                {
                return m_clzChild = clzChild;
                }
            }

        throw new IllegalStateException("unknown child " + sChild + " of type " + typeParent);
        }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    public boolean isVirtualChild()
        {
        return true;
        }

    @Override
    public TypeConstant getOriginParentType()
        {
        return m_typeOriginParent;
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
    protected TypeConstant cloneSingle(ConstantPool pool, TypeConstant type)
        {
        return m_fThisClass
            ? pool.ensureThisVirtualChildTypeConstant(type, m_constName.getValue())
            : pool.ensureVirtualChildTypeConstant    (type, m_constName.getValue());
        }

    @Override
    public boolean containsAutoNarrowing(boolean fAllowVirtChild)
        {
        return fAllowVirtChild && m_fThisClass || m_typeParent.containsAutoNarrowing(false);
        }

    @Override
    public boolean isAutoNarrowing()
        {
        return m_fThisClass;
        }

    @Override
    public TypeConstant ensureAutoNarrowing()
        {
        return m_fThisClass
                ? this
                : getConstantPool().
                    ensureThisVirtualChildTypeConstant(m_typeParent, m_constName.getValue());
        }

    @Override
    public TypeConstant resolveAutoNarrowing(ConstantPool pool, boolean fRetainParams,
                                             TypeConstant typeTarget, IdentityConstant idCtx)
        {
        TypeConstant typeParentOriginal = m_typeParent;
        TypeConstant typeParentResolved = typeParentOriginal;

        if (typeParentOriginal.containsAutoNarrowing(false))
            {
            typeParentResolved = typeParentOriginal.
                                    resolveAutoNarrowing(pool, fRetainParams, typeTarget, idCtx);
            }
        else if (typeTarget != null && m_fThisClass)
            {
            // strip the immutability and access modifiers
            while (typeTarget instanceof ImmutableTypeConstant ||
                   typeTarget instanceof AccessTypeConstant)
                {
                typeTarget = typeTarget.getUnderlyingType();
                }

            if (typeTarget.isA(this))
                {
                return typeTarget;
                }

            if (typeTarget.isA(typeParentOriginal))
                {
                typeParentResolved = typeTarget;
                }
            }

        return typeParentOriginal == typeParentResolved
            ? this
            : m_fThisClass
                ? pool.ensureThisVirtualChildTypeConstant(typeParentResolved, m_constName.getValue())
                : pool.ensureVirtualChildTypeConstant(typeParentResolved, m_constName.getValue());
        }


    // ----- type comparison support ---------------------------------------------------------------

    @Override
    protected Relation calculateRelationToLeft(TypeConstant typeLeft)
        {
        TypeConstant typeParent = getParentType();
        if (typeParent instanceof IntersectionTypeConstant typeInter)
            {
            // it's possible that the parent was narrowed to an intersection type; extract the
            // original parent from the intersection
            typeParent = typeInter.extractParent(getChildName());
            return cloneSingle(getConstantPool(), typeParent).calculateRelation(typeLeft);
            }
        return super.calculateRelationToLeft(typeLeft);
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

        CompareNext:
        if (n == 0)
            {
            if (!(obj instanceof VirtualChildTypeConstant that))
                {
                return -1;
                }

            n = this.m_constName.compareTo(that.m_constName);
            if (n != 0)
                {
                break CompareNext;
                }

            if (this.m_typeParent != this.m_typeOriginParent ||
                that.m_typeParent != that.m_typeOriginParent)
                {
                n = this.m_typeOriginParent.compareDetails(that.m_typeOriginParent);
                if (n != 0)
                    {
                    break CompareNext;
                    }
                }
            n = Boolean.compare(this.m_fThisClass, that.m_fThisClass);
            }
        return n;
        }

    @Override
    public String getValueString()
        {
        return (m_fThisClass ? "this:" : "") +
                (m_typeParent == m_typeOriginParent ? "" : "virtual ") +
                m_typeOriginParent.getValueString() + '.' + m_constName.getValue();
        }


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);

        m_constName        = (StringConstant) pool.register(m_constName);
        m_typeOriginParent = (TypeConstant)   pool.register(m_typeOriginParent);

        // invalidate cached structure
        m_clzChild = null;
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        assert m_typeOriginParent == m_typeParent;

        super.assemble(out);

        writePackedLong(out, m_constName.getPosition());
        out.writeBoolean(m_fThisClass);
        }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int computeHashCode()
        {
        return Hash.of(m_typeParent,
               Hash.of(m_constName,
               Hash.of(m_fThisClass)));
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

    /**
     * Having this flag set means that this VirtualChildTypeConstant will "auto narrow" in a manner
     * that is analogous to a TerminalTypeConstant around ThisClassConstant.
     */
    protected boolean m_fThisClass;

    /**
     * The origin parent's type. It can only be different from the parent type during isA() and
     * TypeInfo calculations.
     */
    private transient TypeConstant m_typeOriginParent;

    /**
     * Cached child structure.
     */
    private transient ClassStructure m_clzChild;
    }