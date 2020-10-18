package org.xvm.runtime;


import java.util.List;
import java.util.Map;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.InterfaceProxy;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * ProxyComposition represents a proxy interface.
 */
public class ProxyComposition
        implements TypeComposition
    {
    /**
     * Construct the ProxyComposition for a given "inception" type and a "proxy" type.
     */
    public ProxyComposition(ClassComposition clzOrigin, TypeConstant typeProxy)
        {
        assert typeProxy.isInterfaceType();

        f_clzOrigin = clzOrigin;
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
    public boolean isInflated(Object nid)
        {
        return f_clzOrigin.isInflated(nid);
        }

    @Override
    public boolean isLazy(Object nid)
        {
        return f_clzOrigin.isLazy(nid);
        }

    @Override
    public boolean isAllowedUnassigned(Object nid)
        {
        return f_clzOrigin.isAllowedUnassigned(nid);
        }

    @Override
    public boolean isInjected(PropertyConstant idProp)
        {
        return f_clzOrigin.isInflated(idProp);
        }

    @Override
    public boolean isAtomic(PropertyConstant idProp)
        {
        return f_clzOrigin.isAtomic(idProp);
        }

    @Override
    public CallChain getMethodCallChain(Object nidMethod)
        {
        return f_clzOrigin.getMethodCallChain(nidMethod);
        }

    @Override
    public CallChain getPropertyGetterChain(PropertyConstant idProp)
        {
        return f_clzOrigin.getPropertyGetterChain(idProp);
        }

    @Override
    public CallChain getPropertySetterChain(PropertyConstant idProp)
        {
        return f_clzOrigin.getPropertySetterChain(idProp);
        }

    @Override
    public List<String> getFieldNames()
        {
        return f_clzOrigin.getFieldNames();
        }

    @Override
    public StringHandle[] getFieldNameArray()
        {
        return f_clzOrigin.getFieldNameArray();
        }

    @Override
    public ObjectHandle[] getFieldValueArray(GenericHandle hValue)
        {
        return f_clzOrigin.getFieldValueArray(hValue);
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
     * The original ClassComposition.
     */
    private final ClassComposition f_clzOrigin;

    /**
     * The revealed (proxying) type.
     */
    private final TypeConstant f_typeProxy;
    }
