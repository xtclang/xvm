package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;

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
     * @return true iff this constant represents a component nested within a class, but not a class
     *         itself
     */
    public boolean isNested()
        {
        switch (getFormat())
            {
            case Typedef:
            case Property:
            case MultiMethod:
            case Method:
                return true;

            default:
                return false;
            }
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
     * @return an object that identifies this constant relative to the class within which it nests,
     *         or null if this constant refers to a class structure
     */
    public Object getNestedIdentity()
        {
        return isNested()
                ? new NestedIdentity()
                : null;
        }

    public Object resolveNestedIdentity(GenericTypeResolver resolver)
        {
        return isNested()
                ? new NestedIdentity(resolver)
                : null;
        }

    /**
     * Determine the nesting depth of a particular nested identity.
     *
     * @param nid  a nested identity
     *
     * @return the depth of the nested identity, in the same measure as {@link #getNestedDepth()}
     */
    public int getNestedDepth(Object nid)
        {
        return nid instanceof NestedIdentity
                ? ((NestedIdentity) nid).getIdentityConstant().getNestedDepth()
                : nid == null ? 0 : 1;
        }

    /**
     * Determine if two nested identities refer to members that are nested within the same
     * component container.
     * <p/>
     * Note: Makes some big assumptions, e.g. like that the two nids both refer to methods (since
     * depths for methods are +1 compared to properties).
     *
     * @param nid1  the first nested identity
     * @param nid2  the second nested identity
     *
     * @return true if the two nested identities refer to members within the same container
     */
    public static boolean isNestedSibling(Object nid1, Object nid2)
        {
        if (nid1 == null || nid2 == null)
            {
            return nid1 == nid2;
            }

        if (nid1 instanceof NestedIdentity)
            {
            if (nid2 instanceof NestedIdentity)
                {
                IdentityConstant id1 = ((NestedIdentity) nid1).getIdentityConstant();
                IdentityConstant id2 = ((NestedIdentity) nid2).getIdentityConstant();
                return id1.getNestedDepth() == id2.getNestedDepth()
                        && Handy.equals(id1.getParentConstant().getNestedIdentity(),
                                        id2.getParentConstant().getNestedIdentity());
                }
            else
                {
                // nid1 must be at depth 1 (since nid1 is at depth 1)
                return ((NestedIdentity) nid1).getIdentityConstant().getNestedDepth() == 1;
                }
            }
        else
            {
            if (nid2 instanceof NestedIdentity)
                {
                // nid2 must be at depth 1 (since nid1 is at depth 1)
                return ((NestedIdentity) nid2).getIdentityConstant().getNestedDepth() == 1;
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
     * @param nid
     *
     * @return
     */
    public IdentityConstant appendNestedIdentity(Object nid)
        {
        if (nid instanceof String)
            {
            return getConstantPool().ensurePropertyConstant(this, (String) nid);
            }
        else if (nid instanceof SignatureConstant)
            {
            return getConstantPool().ensureMethodConstant(this, (SignatureConstant) nid);
            }
        else if (nid instanceof NestedIdentity)
            {
            return ((NestedIdentity) nid).getIdentityConstant().ensureNestedIdentity(this);
            }
        else if (nid == null)
            {
            return this;
            }

        throw new IllegalArgumentException("illegal nid: " + nid);
        }

    protected IdentityConstant ensureNestedIdentity(IdentityConstant that)
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
            IdentityConstant id = IdentityConstant.this;
            return id.getPathString().substring(id.getClassIdentity().getPathString().length()+1);
            }

        @Override
        public int hashCode()
            {
            int n = 0;
            IdentityConstant id = IdentityConstant.this;
            while (id.isNested())
                {
                n ^= resolve(id.getPathElement()).hashCode();
                id = id.getNamespace();
                }
            return n;
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (!(obj instanceof NestedIdentity))
                {
                return false;
                }

            NestedIdentity   that   = (NestedIdentity) obj;
            IdentityConstant idThis = this.getIdentityConstant();
            IdentityConstant idThat = that.getIdentityConstant();
            while (idThis.isNested() && idThat.isNested())
                {
                if (!resolve(idThis.getPathElement()).equals(resolve(idThat.getPathElement())))
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
            // REVIEW: should we resolveAutoNarrowing()
            return m_resolver != null && element instanceof SignatureConstant
                    ? ((SignatureConstant) element).resolveGenericTypes(m_resolver)
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
     * @return the Component structure that is identified by this IdentityConstant
     */
    public Component getComponent()
        {
        Component parent = getParentConstant().getComponent();
        return parent == null ? null : parent.getChild(this);
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
     * @return true iff this IdentityConstant represents an auto-narrowing identity
     */
    public boolean isAutoNarrowing()
        {
        return false;
        }

    /**
     * @return the ClassConstant that would respresent a child class of the specified name
     */
    public ClassConstant ensureChild(String sName)
        {
        switch (getFormat())
            {
            case Module:
            case Package:
            case Class:
                return getConstantPool().ensureClassConstant(this, sName);

            default:
                throw new IllegalStateException("not a class type: " + this);
            }
        }

    /**
     * @return a TypeConstant for this class
     */
    public TypeConstant asTypeConstant()
        {
        switch (getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Typedef:
            case Property:
            case NativeClass:
                return getConstantPool().ensureTerminalTypeConstant(this);

            default:
                throw new IllegalStateException("not a class type: " + this);
            }
        }

    /**
     * @return the TypeInfo for the type implied by this identity, which must be a class identity
     *         (including module and package), or a property identity (to obtain a nested TypeInfo
     *         for the property, as if the property were a class itself)
     */
    public TypeInfo ensureTypeInfo(ErrorListener errs)
        {
        switch (getFormat())
            {
            case Module:
            case Package:
            case Class:
            case Typedef:
            case NativeClass:
                return asTypeConstant().ensureTypeInfo(errs);

            case Property:
                throw new UnsupportedOperationException("TODO: TypeInfo for property");

            default:
                throw new IllegalStateException("not a class type: " + this);
            }
        }


    // ----- constant methods ----------------------------------------------------------------------

    @Override
    public TypeConstant getType()
        {
        if (isClass())
            {
            // if a class name is specified in code, and it resolves to a class constant, then the type
            // of the expression that yields this constant is the Class type:
            //  Class<PublicType, ProtectedType, PrivateType, StructType>
            ConstantPool pool = getConstantPool();
            return pool.ensureParameterizedTypeConstant(pool.typeClass(),
                    pool.ensureClassTypeConstant(this, Access.PUBLIC),
                    pool.ensureClassTypeConstant(this, Access.PROTECTED),
                    pool.ensureClassTypeConstant(this, Access.PRIVATE),
                    pool.ensureClassTypeConstant(this, Access.STRUCT));
            }

        return super.getType();
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
    }
