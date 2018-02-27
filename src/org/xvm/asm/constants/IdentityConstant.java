package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;

import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


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
                .append(getName());
        }

    /**
     * @return the number of identity segments that need to be traversed to find a class in the
     *         IdentityConstant's path
     */
    public int depthFromClass()
        {
        IdentityConstant constId = this;
        int              cDepth  = 0;
        while (true)
            {
            switch (constId.getFormat())
                {
                case Typedef:
                case Property:
                case MultiMethod:
                case Method:
                    ++cDepth;
                    break;

                default:
                    return cDepth;
                }

            constId = constId.getParentConstant();
            }
        }

    /**
     * @return the first class encountered when traversing the IdentityConstant's path (which could
     *         be this IdentityConstant if this is the identity of class)
     */
    public IdentityConstant getClassIdentity()
        {
        IdentityConstant constId = this;
        while (true)
            {
            switch (constId.getFormat())
                {
                case Typedef:
                case Property:
                case MultiMethod:
                case Method:
                    break;

                case Module:
                case Package:
                case Class:
                    return constId;

                default:
                    throw new IllegalStateException();
                }

            constId = constId.getParentConstant();
            }
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
        return getParentConstant().getComponent().getChild(this);
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
                return getConstantPool().ensureTerminalTypeConstant(this);

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
