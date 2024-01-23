package org.xvm.runtime;


import java.util.Map;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * An abstract base for delegating TypeCompositions.
 */
public abstract class DelegatingComposition
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
    public Container getContainer()
        {
        return f_clzOrigin.getContainer();
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
    public MethodStructure ensureAutoInitializer()
        {
        return f_clzOrigin.ensureAutoInitializer();
        }

    @Override
    public boolean isInjected(PropertyConstant idProp)
        {
        return f_clzOrigin.isInjected(idProp);
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
    public Map<Object, FieldInfo> getFieldLayout()
        {
        return f_clzOrigin.getFieldLayout();
        }

    @Override
    public StringHandle[] getFieldNameArray()
        {
        return f_clzOrigin.getFieldNameArray();
        }

    @Override
    public ObjectHandle[] getFieldValueArray(Frame frame, GenericHandle hValue)
        {
        return f_clzOrigin.getFieldValueArray(frame, hValue);
        }

    @Override
    public ObjectHandle[] initializeStructure()
        {
        return f_clzOrigin.initializeStructure();
        }

    @Override
    public FieldInfo getFieldInfo(Object id)
        {
        return f_clzOrigin.getFieldInfo(id);
        }

    @Override
    public boolean makeStructureImmutable(ObjectHandle[] ahField)
        {
        return f_clzOrigin.makeStructureImmutable(ahField);
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