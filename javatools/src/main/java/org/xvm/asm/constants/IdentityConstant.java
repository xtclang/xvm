package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.XvmStructure;

import org.xvm.util.Handy;


/**
 * An IdentityConstant identifies a Module, Package, Class, Typedef, Property, MultiMethod, or
 * Method structure.
 */
public abstract class IdentityConstant
        extends Constant
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param pool   the ConstantPool
     */
    protected IdentityConstant(ConstantPool pool)
        {
        super(pool);
        }


    // ----- IdentityConstant methods --------------------------------------------------------------

    /**
     * @return the IdentityConstant that identifies the structure that contains the structure
     *         identified by this identity constant, or null if this is a module constant
     */
    public abstract IdentityConstant getParentConstant();

    /**
     * Determine the constant within which the name of this constant is registered. In most cases,
     * the namespace is the parent, but in the case of the MethodConstant, the namespace is the
     * grandparent, because the parent is the MultiMethodConstant.
     *
     * @return the constant for the namespace containing (directly or indirectly) this constant, or
     *         null if this is a module constant
     */
    public IdentityConstant getNamespace()
        {
        return getParentConstant();
        }

    /**
     * Determine the name for this identity constant. In the case of the MethodConstant, the name
     * is the name of the MultiMethodConstant.
     *
     * @return the name for this identity constant
     */
    public abstract String getName();

    /**
     * @return the module constant, which is the "root" of the identity constant path
     */
    public ModuleConstant getModuleConstant()
        {
        return getParentConstant().getModuleConstant();
        }

    /**
     * @return the number of elements in the identity constant path
     */
    public int getPathElementCount()
        {
        int c = 0;
        IdentityConstant id = this;
        do
            {
            ++c;
            id = id.getParentConstant();
            }
        while (id != null);
        return c;
        }

    /**
     * @return a List of IdentityConstants that makes up the path to this IdentityConstant
     */
    public List<IdentityConstant> getPath()
        {
        List<IdentityConstant> list = getParentConstant().getPath();
        list.add(this);
        return list;
        }

    /**
     * @return a dot-delimited string of IdentityConstant names that makes up the path to
     *         this IdentityConstant
     */
    public String getPathString()
        {
        return buildPath().substring(1);
        }

    /**
     * Support for {@link #getPathString()}; overridden at {@link ModuleConstant}.
     */
    protected StringBuilder buildPath()
        {
        return getParentConstant().buildPath()
                .append('.')
                .append(getPathElementString());
        }

    /**
     * @return an Object that represents the path element for this IdentityConstant, and which
     *         implements {@link Object#hashCode()} and {@link Object#equals(Object)} accordingly
     */
    public Object getPathElement()
        {
        return getName();
        }

    /**
     * @return a String representation of the path element for this IdentityConstant
     */
    public String getPathElementString()
        {
        return getName();
        }

    /**
     * Test for a child visibility. This child (A) is said to be a "nest mate" of the specified
     * class (B) iff
     * <ul>
     *   <li> both A and B have have the same outermost underlying class, or
     *   <li> the outermost underlying class of B extends the outermost underlying class of A.
     * </ul>
     * In other words, this class is a nest mate of the specified class if this class is "visible"
     * from the context of the specified class and could be privately accessed in that context.
     * <p/>
     * For example, Map.Entry is a nest mate of both HashMap and HashMap.EntrySet.
     *
     * @param idClass  the class to test nest the visibility from; note that it can represent
     *                 a non-virtual (e.g. anonymous) inner class
     *
     * @return true if this class is a child "privately" visible from the specified class context
     */
    public boolean isNestMateOf(IdentityConstant idClass)
        {
        if (this.equals(idClass))
            {
            return true;
            }

        if (getFormat() == Format.Class && idClass.getFormat() == Format.Class)
            {
            ClassConstant idThis = (ClassConstant) this;
            ClassConstant idThat = (ClassConstant) idClass;

            ClassStructure clzThis = (ClassStructure) idThis.getComponent();
            if (clzThis.isAnonInnerClass())
                {
                return false;
                }

            // check with every parent class on the way up to the outermost
            ClassConstant idBaseThis = idThis.getAutoNarrowingBase();
            ClassConstant idBaseThat = idThat;
            do
                {
                if (idBaseThis.equals(idBaseThat))
                    {
                    return true;
                    }

                ClassStructure clzThat = (ClassStructure) idBaseThat.getComponent();
                if (clzThat.hasContribution(idBaseThis))
                    {
                    return true;
                    }
                idBaseThat = idBaseThat.getParentClass();
                }
            while (idBaseThat != null);
            }
        return false;
        }

    /**
     * @return true iff this constant represents a component nested within a class, but not a class
     *         itself
     */
    public boolean isNested()
        {
        Boolean fNested = m_fNested;
        return fNested == null ? m_fNested = computeIsNested() : fNested;
        }

    /**
     * Compute the value for {@link #isNested}.
     *
     * @return {@code true} if nested
     */
    private Boolean computeIsNested()
        {
        return switch (getFormat())
            {
            case Typedef, Property, MultiMethod, Method -> true;
            default                                     -> false;
            };
        }

    /**
     * @return the number of identity segments that need to be traversed to find a class in the
     *         IdentityConstant's path; a member nested immediately within a class is at depth 1
     */
    public int getNestedDepth()
        {
        int              c  = 0;
        IdentityConstant id = this;
        while (id.isNested())
            {
            id = id.getParentConstant();
            ++c;
            }
        return c;
        }

    /**
     * @return the first class encountered when traversing the IdentityConstant's path (which could
     *         be this IdentityConstant if this is the identity of class)
     */
    public IdentityConstant getClassIdentity()
        {
        IdentityConstant id = this;
        while (id.isNested())
            {
            id = id.getParentConstant();
            }
        return id;
        }

    /**
     * @return a dot-delimited sequence of names that identify this constant _within_ a class, or
     *         null if there is a method interposed between this constant and the containing class
     */
    public String getNestedName()
        {
        IdentityConstant idParent = getParentConstant();
        return switch (idParent.getFormat())
            {
            case Module, Package, Class -> getName();
            case MultiMethod            -> idParent.getNestedName();
            case Property               -> idParent.getNestedName() + '.' + getName();

            // nothing else is supposed to be visible inside a method from the outside
            default -> null;
            };
        }

    /**
     * @return an object that identifies this constant relative to the class within which it nests,
     *         or null if this constant refers to a class structure
     */
    public Object getNestedIdentity()
        {
        return isNested()
                ? getCanonicalNestedIdentity()
                : null;
        }

    /**
     * Obtain an object that identifies this constant relative to the class within which it nests,
     * with generic types resolved using the specified resolver.
     *
     * @param pool      the ConstantPool to use
     * @param resolver  the {@link GenericTypeResolver} to resolve generic types
     *
     * @return an identifying object or null if this constant refers to a class structure
     */
    public Object resolveNestedIdentity(ConstantPool pool, GenericTypeResolver resolver)
        {
        return isNested()
                ? resolver == null
                    ? getCanonicalNestedIdentity()
                    : new NestedIdentity(resolver)
                : null;
        }

    /**
     * @return a canonical (not based on any resolver) nested identity
     */
    protected NestedIdentity getCanonicalNestedIdentity()
        {
        NestedIdentity nid = m_canonicalNid;
        if (nid == null)
            {
            m_canonicalNid = nid = new NestedIdentity();
            }
        return nid;
        }

    /**
     * Determine the nesting depth of a particular nested identity.
     *
     * @param oid  a nested identity
     *
     * @return the depth of the nested identity, in the same measure as {@link #getNestedDepth()}
     */
    public int getNestedDepth(Object oid)
        {
        return oid instanceof NestedIdentity nid
                ? nid.getIdentityConstant().getNestedDepth()
                : oid == null ? 0 : 1;
        }

    /**
     * Determine if two nested identities refer to members that are nested within the same
     * component container.
     * <p/>
     * Note: Makes some big assumptions, e.g. like that the two nids both refer to methods (since
     * depths for methods are +1 compared to properties).
     *
     * @param oid1  the first nested identity
     * @param oid2  the second nested identity
     *
     * @return true if the two nested identities refer to members within the same container
     */
    public static boolean isNestedSibling(Object oid1, Object oid2)
        {
        if (oid1 == null || oid2 == null)
            {
            return oid1 == oid2;
            }

        if (oid1 instanceof NestedIdentity nid1)
            {
            if (oid2 instanceof NestedIdentity nid2)
                {
                IdentityConstant id1 = nid1.getIdentityConstant();
                IdentityConstant id2 = nid2.getIdentityConstant();
                return id1.getNestedDepth() == id2.getNestedDepth()
                        && Handy.equals(id1.getParentConstant().getNestedIdentity(),
                                        id2.getParentConstant().getNestedIdentity());
                }
            else
                {
                // nid1 must be at depth 1 (since nid1 is at depth 1)
                return nid1.getIdentityConstant().getNestedDepth() == 1;
                }
            }
        else
            {
            if (oid2 instanceof NestedIdentity nid2)
                {
                // nid2 must be at depth 1 (since nid1 is at depth 1)
                return nid2.getIdentityConstant().getNestedDepth() == 1;
                }
            else
                {
                // if neither is a nested identity, then they're automatically siblings
                // (i.e. immediately nested within a class)
                return true;
                }
            }
        }

    /**
     * Given a ClassStructure, use the nested identity from this IdentityConstant to find the
     * corresponding Component within that ClassStructure.
     *
     * @param clz  the ClassStructure to find the corresponding nested Component within
     *
     * @return the corresponding nested Component, or null
     */
    public Component relocateNestedIdentity(ClassStructure clz)
        {
        assert !isNested();
        return clz;
        }

    /**
     * Apply the specified "relative" identity starting from this identity, and return the resulting
     * identity.
     *
     * @param pool  the ConstantPool to place a potentially created new constant into
     * @param oid   the id
     *
     * @return a resulting nested identity
     */
    public IdentityConstant appendNestedIdentity(ConstantPool pool, Object oid)
        {
        if (oid instanceof String s)
            {
            return pool.ensurePropertyConstant(this, s);
            }
        else if (oid instanceof SignatureConstant sig)
            {
            return pool.ensureMethodConstant(this, sig);
            }
        else if (oid instanceof NestedIdentity nid)
            {
            return nid.getIdentityConstant().ensureNestedIdentity(pool, this);
            }
        else if (oid == null)
            {
            return this;
            }

        throw new IllegalArgumentException("illegal nid: " + oid);
        }

    protected IdentityConstant ensureNestedIdentity(ConstantPool pool, IdentityConstant that)
        {
        return that;
        }

    /**
     * A class used to as a nested identity for members not directly nested (or in the case of
     * methods, methods whose multi-method parent is not directly nested).
     */
    public class NestedIdentity
        {
        public NestedIdentity()
            {
            this(null);
            }

        public NestedIdentity(GenericTypeResolver resolver)
            {
            m_resolver = resolver;
            }

        /**
         * @return true iff this NestedIdentity could be used as a cache key
         */
        public boolean isCacheable()
            {
            return m_resolver == null || m_resolver instanceof TypeConstant;
            }

        /**
         * @return the IdentityConstant that created this NestedIdentity
         */
        public IdentityConstant getIdentityConstant()
            {
            return IdentityConstant.this;
            }

        @Override
        public String toString()
            {
            // for member "m" of class "c", the string is "m"
            IdentityConstant id     = IdentityConstant.this;
            String           sClass = id.getClassIdentity().getPathString();

            return id.getPathString().substring(sClass.isEmpty() ? 0 : sClass.length() + 1);
            }

        @Override
        public int hashCode()
            {
            int              iHash = 0;
            IdentityConstant id    = IdentityConstant.this;
            boolean          fTop  = true;
            while (id.isNested())
                {
                Object oPath = id.getPathElement();
                if (fTop)
                    {
                    oPath = resolve(oPath);
                    fTop  = false;
                    }
                iHash ^= oPath.hashCode();
                id     = id.getNamespace();
                }
            return iHash;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (!(obj instanceof NestedIdentity that))
                {
                return false;
                }

            IdentityConstant idThis = this.getIdentityConstant();
            IdentityConstant idThat = that.getIdentityConstant();
            boolean          fTop   = true;
            while (idThis.isNested() && idThat.isNested())
                {
                Object oThis = idThis.getPathElement();
                Object oThat = idThat.getPathElement();
                if (fTop)
                    {
                    oThis = this.resolve(oThis);
                    oThat = that.resolve(oThat);
                    fTop  = false;
                    }
                if (!oThis.equals(oThat))
                    {
                    return false;
                    }

                idThis = idThis.getNamespace();
                idThat = idThat.getNamespace();
                }

            return idThis.isNested() == idThat.isNested();
            }

        private Object resolve(Object element)
            {
            ConstantPool pool = ConstantPool.getCurrentPool();
            return m_resolver != null && element instanceof SignatureConstant sig
                    ? sig.resolveGenericTypes(pool, m_resolver)
                    : element;
            }

        private final GenericTypeResolver m_resolver;
        }

    /**
     * Considering the IdentityConstant as a path of identity segments, determine if the last
     * <i>{@code cSegments}</i> segments of this IdentityConstant are the same as the corresponding
     * last segments of another specified IdentityConstant.
     *
     * @param that       another IdentityConstant
     * @param cSegments  the number of segments to compare
     *
     * @return true iff the last segment of both IdentityConstants is the same
     */
    public boolean trailingPathEquals(IdentityConstant that, int cSegments)
        {
        return cSegments <= 0 || this == that || trailingSegmentEquals(that) &&
                this.getParentConstant().trailingPathEquals(that.getParentConstant(), cSegments - 1);
        }

    /**
     * Append the last {@code cSegments} segments of this IdentityConstant to the passed in
     * IdentityConstant.
     *
     * @param that       another IdentityConstant
     * @param cSegments  the number of segments to append
     *
     * @return the resulting IdentityConstant
     */
    public IdentityConstant appendTrailingPathTo(IdentityConstant that, int cSegments)
        {
        switch (cSegments)
            {
            default:
                assert cSegments > 0;
                that = appendTrailingPathTo(that, cSegments - 1);
                // fall through
            case 1:
                return appendTrailingSegmentTo(that);

            case 0:
                return that;
            }
        }

    /**
     * Considering the IdentityConstant as a path of identity segments, determine if the last
     * segment of this IdentityConstant is the same as the last segment of another specified
     * IdentityConstant.
     *
     * @param that  another IdentityConstant
     *
     * @return true iff the last segment of both IdentityConstants is the same
     */
    public boolean trailingSegmentEquals(IdentityConstant that)
        {
        return this.getClass() == that.getClass() && this.getName().equals(that.getName());
        }

    /**
     * Append the last segment from this IdentityConstant to the end of the passed in
     * IdentityConstant.
     *
     * @param that  another IdentityConstant
     *
     * @return the IdentityConstant that results from appending the last segment from this
     *         IdentityConstant to the end of the passed in IdentityConstant
     */
    public abstract IdentityConstant appendTrailingSegmentTo(IdentityConstant that);

    /**
     * Determine if this IdentityConstant references a structure that is shared between its pool
     * and the specified pool.
     *
     * @param poolOther  the constant pool to check
     *
     * @return true iff this IdentityConstant references a structure that is shared with the
     *        specified constant pool
     */
    public boolean isShared(ConstantPool poolOther)
        {
        return poolOther == getConstantPool() ||
                poolOther.getFileStructure().getChild(getModuleConstant()) != null;
        }

    /**
     * @return the Component structure that is identified by this IdentityConstant
     */
    public Component getComponent()
        {
        Component component = m_component;
        if (component == null)
            {
            Component parent = getParentConstant().getComponent();
            m_component = component = parent == null ? null : parent.getChild(this);
            }
        return component;
        }

    /**
     * Determine if this is a class that is or that extends the specified super class.
     *
     * @param clzSuper  the class to test if this class extends
     *
     * @return true iff this constant refers to a class, and the class is or extends the specified
     *         super class
     */
    public boolean extendsClass(ClassConstant clzSuper)
        {
        return this.isClass() && ((ClassStructure) this.getComponent()).extendsClass(clzSuper);
        }

    /**
     * @return true iff this constant is the specified parent itself, or a child of that parent or
     *         any of its children (recursively)
     */
    public boolean isDescendant(IdentityConstant idAscendant)
        {
        if (this.equals(idAscendant))
            {
            return true;
            }
        IdentityConstant idParent = getParentConstant();
        return idParent != null && idParent.isDescendant(idAscendant);
        }

    /**
     * If an identity constant can be embedded into assembly and referred to as a _value_ in that
     * assembly code, then at runtime, the code execution is responsible for turning the identity
     * constant into an object that corresponds to that identity; this method calculates the type
     * of that runtime value.
     *
     * @param pool        the ConstantPool to use
     * @param typeTarget  the target type (null if the identity is itself the target)
     *
     * @return a TypeConstant
     */
    public TypeConstant getValueType(ConstantPool pool, TypeConstant typeTarget)
        {
        if (isClass())
            {
            // if a class name is specified in code, and it resolves to a class constant, then the type
            // of the expression that yields this constant is the Class type:
            //  Class<PublicType, ProtectedType, PrivateType, StructType>
            TypeConstant type      = getType().removeAccess().normalizeParameters();
            Component    component = getComponent();
            Annotation[] aAnnos;

            switch (component.getFormat())
                {
                case ENUM:
                    return pool.ensureParameterizedTypeConstant(pool.typeEnumeration(), type);

                case ENUMVALUE:
                    return pool.ensureParameterizedTypeConstant(pool.typeEnumValue(), type);

                case PROPERTY:
                    // this is a DecoratedClassConstant
                    aAnnos = Annotation.NO_ANNOTATIONS;
                    break;

                default:
                    aAnnos = ((ClassStructure) component).collectAnnotations(true);
                    break;
                }

            TypeConstant typeClz = pool.ensureParameterizedTypeConstant(pool.typeClass(), type,
                pool.ensureAccessTypeConstant(type, Access.PROTECTED),
                pool.ensureAccessTypeConstant(type, Access.PRIVATE),
                pool.ensureAccessTypeConstant(type, Access.STRUCT));

            return aAnnos.length == 0
                    ? typeClz
                    : pool.ensureAnnotatedTypeConstant(typeClz, aAnnos);
            }

        throw new UnsupportedOperationException("constant-class=" + getClass().getSimpleName());
        }

    /**
     * @return a formal type for the class represented by this constant
     */
    public TypeConstant getFormalType()
        {
        Component component = getComponent();
        if (component instanceof ClassStructure struct)
            {
            return struct.getFormalType();
            }
        throw new IllegalStateException("not a class type: " + this);
        }

    /**
     * Reset any of the cached info.
     */
    public void resetCachedInfo()
        {
        m_component = null;
        }


    // ----- constant methods ----------------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        return switch (getFormat())
            {
            case Module, Package, Class, Typedef, NativeClass, Property,
                 TypeParameter, FormalTypeChild, DynamicFormal ->
                getConstantPool().ensureTerminalTypeConstant(this);

            default ->
                throw new IllegalStateException("not a class type: " + this);
            };
        }

    @Override
    public boolean containsUnresolved()
        {
        if (isHashCached())
            {
            return false;
            }

        IdentityConstant parent = getParentConstant();
        return parent != null && parent.containsUnresolved();
        }

    @Override
    public Constant convertTo(TypeConstant typeOut)
        {
        ConstantPool pool = getConstantPool();
        return typeOut.equals(pool.typeClass())
                ? new DecoratedClassConstant(pool, this.getType())
                : super.convertTo(typeOut);
        }

    @Override
    protected Object getLocator()
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        return super.getLocator();
        }

    @Override
    protected abstract int compareDetails(Constant that);


    // ----- XvmStructure methods ------------------------------------------------------------------

    @Override
    protected void setContaining(XvmStructure xsParent)
        {
        super.setContaining(xsParent);

        resetCachedInfo();
        }

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        super.registerConstants(pool);
        }

    @Override
    protected void assemble(DataOutput out)
            throws IOException
        {
        // this protected method must be present here to make it accessible to other classes in this
        // package
        super.assemble(out);
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * Cached component this identity points to.
     */
    private transient Component m_component;

    /**
     * Cached canonical NestedIdentity.
     */
    private transient NestedIdentity m_canonicalNid;

    /**
     * Cached result for {@link #isNested()}.
     */
    private transient Boolean m_fNested;
    }