package org.xvm.asm.constants;


import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;


/**
 * An IdentityConstant identifies a Module, Package, Class, Property, MultiMethod, or Method.
 *
 * @author cp 2017.05.18
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
    public  List<IdentityConstant> getIdentityConstantPath()
        {
        List<IdentityConstant> list = getParentConstant().getIdentityConstantPath();
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
     * @return the Component structure that is identified by this IdentityConstant
     */
    public Component getComponent()
        {
        return getParentConstant().getComponent().getChild(this);
        }

    // ----- constant methods ----------------------------------------------------------------------

    @Override
    protected void registerConstants(ConstantPool pool)
        {
        super.registerConstants(pool);
        }

    @Override
    protected abstract void assemble(DataOutput out) throws IOException;

    @Override
    protected Object getLocator()
        {
        return super.getLocator();
        }

    @Override
    protected abstract int compareDetails(Constant that);
    }
