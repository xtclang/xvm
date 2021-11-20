package org.xvm.runtime;


import java.util.List;
import java.util.Set;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * An abstract base for delegating TypeCompositions.
 */
abstract public class DelegatingComposition
        implements TypeComposition
    {
    /**
     * Constructor.
     */
    protected DelegatingComposition(TypeComposition clzOrigin)
        {
        f_clzOrigin = clzOrigin;
        }


    // ----- TypeComposition interface -------------------------------------------------------------

    @Override
    public boolean isInstanceChild()
        {
        return f_clzOrigin.isInstanceChild();
        }

    @Override
    public OpSupport getSupport()
        {
        return f_clzOrigin.getSupport();
        }

    @Override
    public ClassTemplate getTemplate()
        {
        return f_clzOrigin.getTemplate();
        }

    @Override
    public MethodStructure ensureAutoInitializer(ConstantPool pool)
        {
        return f_clzOrigin.ensureAutoInitializer(pool);
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
    public ObjectHandle[] initializeStructure()
        {
        return f_clzOrigin.initializeStructure();
        }

    @Override
    public int getFieldPosition(Object nid)
        {
        return f_clzOrigin.getFieldPosition(nid);
        }

    @Override
    public int makeStructureImmutable(Frame frame, ObjectHandle[] ahField)
        {
        return f_clzOrigin.makeStructureImmutable(frame, ahField);
        }

    @Override
    public Set<Object> getFieldNids()
        {
        return f_clzOrigin.getFieldNids();
        }

    @Override
    public boolean hasOuter()
        {
        return f_clzOrigin.hasOuter();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The TypeComposition to delegate to.
     */
    protected final TypeComposition f_clzOrigin;
    }
