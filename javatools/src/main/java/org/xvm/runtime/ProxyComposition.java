package org.xvm.runtime;


import java.util.Map;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.InterfaceProxy;


/**
 * ProxyComposition represents a proxy interface.
 */
public class ProxyComposition
        extends DelegatingComposition
    {
    /**
     * Construct the ProxyComposition for a given "inception" type and a "proxy" type.
     */
    public ProxyComposition(ClassComposition clzOrigin, TypeConstant typeProxy)
        {
        super(clzOrigin);

        assert typeProxy.isInterfaceType();

        f_typeProxy = typeProxy;
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
        return InterfaceProxy.INSTANCE;
        }

    @Override
    public ClassTemplate getTemplate()
        {
        return InterfaceProxy.INSTANCE;
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
    public ProxyComposition revealAs(TypeConstant type)
        {
        throw new UnsupportedOperationException();
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
    public MethodStructure ensureAutoInitializer()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public Map<Object, ObjectHandle> initializeStructure()
        {
        return null;
        }

    @Override
    public String toString()
        {
        return "Proxy: " + f_clzOrigin.toString();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The revealed (proxying) type.
     */
    private final TypeConstant f_typeProxy;
    }
