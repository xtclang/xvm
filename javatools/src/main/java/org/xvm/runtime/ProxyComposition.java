package org.xvm.runtime;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.Proxy;


/**
 * ProxyComposition represents a Proxy object.
 */
public class ProxyComposition
        extends DelegatingComposition
    {
    /**
     * Construct the ProxyComposition for a given "inception" composition and a "proxy" type.
     */
    public ProxyComposition(ClassComposition clzOrigin, TypeConstant typeProxy)
        {
        super(clzOrigin);

        f_typeProxy = typeProxy;
        }

    /**
     * @return the original ("inception") composition
     */
    public ClassComposition getOrigin()
        {
        return (ClassComposition) f_clzOrigin;
        }

    /**
     * @return true iff this class represents an instance inner class
     */
    public boolean isInstanceChild()
        {
        return false;
        }

    @Override
    public OpSupport getSupport()
        {
        return Proxy.INSTANCE;
        }

    @Override
    public ClassTemplate getTemplate()
        {
        return Proxy.INSTANCE;
        }

    @Override
    public TypeConstant getType()
        {
        return f_typeProxy;
        }

    @Override
    public TypeConstant getBaseType()
        {
        return f_typeProxy;
        }

    @Override
    public ProxyComposition maskAs(TypeConstant type)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeComposition revealAs(TypeConstant type)
        {
        return f_clzOrigin.revealAs(type);
        }

    @Override
    public ObjectHandle ensureOrigin(ObjectHandle handle)
        {
        return handle;
        }

    @Override
    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public ProxyComposition ensureAccess(Access access)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public boolean isStruct()
        {
        return false;
        }

    @Override
    public boolean isConst()
        {
        return true;
        }

    @Override
    public MethodStructure ensureAutoInitializer(ConstantPool pool)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public ObjectHandle[] initializeStructure()
        {
        return Utils.OBJECTS_NONE;
        }

    @Override
    public String toString()
        {
        return "Proxy: " + f_clzOrigin.toString() + " as " + f_typeProxy.getValueString();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The revealed (proxying) type.
     */
    private final TypeConstant f_typeProxy;
    }
