package org.xvm.runtime;

import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xRef;
import org.xvm.runtime.template.xRef.RefHandle;
import org.xvm.runtime.template.xString.StringHandle;

/**
 * PropertyComposition represents a "custom" property class.
 */
public class PropertyComposition
        implements TypeComposition
    {
    /**
     * Construct the PropertyComposition for a given property of the specified parent.
     *
     * @param clzParent  the parent's ClassComposition
     * @param infoProp   the property name
     */
    protected PropertyComposition(ClassComposition clzParent, PropertyInfo infoProp)
        {
        f_clzParent = clzParent;
        f_infoProp  = infoProp;

        TypeConstant typeParent = clzParent.getInceptionType();
        TypeInfo     infoParent = typeParent.ensureTypeInfo();
        TypeConstant typeRef    = infoProp.getBaseRefType();

        f_clzRef     = clzParent.getRegistry().resolveClass(typeRef);
        f_infoParent = infoParent;
        f_mapMethods = new ConcurrentHashMap<>();
        f_mapGetters = new ConcurrentHashMap<>();
        f_mapSetters = new ConcurrentHashMap<>();
        }


    // ----- TypeComposition interface -------------------------------------------------------------

    @Override
    public OpSupport getSupport()
        {
        return f_clzRef.getSupport();
        }

    @Override
    public ClassTemplate getTemplate()
        {
        return f_clzRef.getTemplate();
        }

    @Override
    public TypeConstant getType()
        {
        TypeConstant typeParent = f_clzParent.getInceptionType();
        return typeParent.getConstantPool().ensurePropertyClassTypeConstant(
                typeParent, f_infoProp.getIdentity());
        }

    @Override
    public TypeComposition maskAs(TypeConstant type)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeComposition revealAs(TypeConstant type, Container container)
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
        assert handle.getComposition() == this;

        return access == f_clzParent.getType().getAccess()
            ? handle
            : handle.cloneAs(ensureAccess(access));
        }

    @Override
    public TypeComposition ensureAccess(Access access)
        {
        ClassComposition clzParent = f_clzParent.ensureAccess(access);
        if (clzParent == f_clzParent)
            {
            return this;
            }
        return new PropertyComposition(clzParent, f_infoProp);
        }

    @Override
    public boolean isStruct()
        {
        return f_clzParent.isStruct();
        }

    @Override
    public boolean isService()
        {
        return false;
        }

    @Override
    public boolean isConst()
        {
        return false;
        }

    @Override
    public MethodStructure ensureAutoInitializer()
        {
        return f_clzRef.ensureAutoInitializer();
        }

    @Override
    public Map<Object, ObjectHandle> createFields()
        {
        return f_clzRef.createFields();
        }

    @Override
    public boolean isInflated(PropertyConstant idProp)
        {
        return f_clzRef.isInflated(idProp);
        }

    @Override
    public boolean isInjected(PropertyConstant idProp)
        {
        return false;
        }

    @Override
    public boolean isAtomic(PropertyConstant idProp)
        {
        return false;
        }

    @Override
    public CallChain getMethodCallChain(SignatureConstant signature)
        {
        return f_mapMethods.computeIfAbsent(signature,
            sig ->
                {
                MethodConstant idNested = (MethodConstant) f_infoProp.getIdentity().
                    appendNestedIdentity(ConstantPool.getCurrentPool(), sig);

                MethodInfo info = f_infoParent.getMethodBySignature(idNested.getSignature());
                return info == null
                    ? f_clzRef.getMethodCallChain(sig)
                    : new CallChain(info.ensureOptimizedMethodChain(f_infoParent));
                });
        }

    @Override
    public CallChain getPropertyGetterChain(Object nidProp)
        {
        return f_mapGetters.computeIfAbsent(nidProp,
            nid ->
                {
                // see if there's a nested property first; default to the base otherwise
                PropertyConstant idNested = (PropertyConstant) f_infoProp.getIdentity().
                    appendNestedIdentity(ConstantPool.getCurrentPool(), nid);

                PropertyInfo infoProp = f_infoParent.findProperty(idNested);
                return infoProp == null
                    ? f_clzRef.getPropertyGetterChain(nid)
                    : new CallChain(infoProp.ensureOptimizedGetChain(f_infoParent));
                });
        }

    @Override
    public CallChain getPropertySetterChain(Object nidProp)
        {
        return f_mapSetters.computeIfAbsent(nidProp,
            nid ->
                {
                PropertyConstant idNested = (PropertyConstant) f_infoProp.getIdentity().
                    appendNestedIdentity(ConstantPool.getCurrentPool(), nid);

                PropertyInfo infoProp = f_infoParent.findProperty(idNested);
                return infoProp == null
                    ? f_clzRef.getPropertySetterChain(nid)
                    : new CallChain(infoProp.ensureOptimizedSetChain(f_infoParent));
                });
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        assert f_infoProp.getIdentity().equals(idProp);

        return ((xRef) getTemplate()).get(frame, (RefHandle) hTarget, iReturn);
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue)
        {
        assert f_infoProp.getIdentity().equals(idProp);

        return ((xRef) getTemplate()).set(frame, (RefHandle) hTarget, hValue);
        }

    @Override
    public Set<String> getFieldNames()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public StringHandle[] getFieldNameArray()
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public ObjectHandle[] getFieldValueArray(GenericHandle hValue)
        {
        return new ObjectHandle[0];
        }

    @Override
    public String toString()
        {
        return "PropertyComposition: " + f_clzParent + "." + f_infoProp.getIdentity().getValueString();
        }

    // ----- data fields

    private final ClassComposition f_clzParent;

    private final TypeInfo         f_infoParent;
    private final ClassComposition f_clzRef;
    private final PropertyInfo     f_infoProp;

    // cached method call chain (the top-most method first)
    private final Map<SignatureConstant, CallChain> f_mapMethods;

    // cached property getter call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapGetters;

    // cached property setter call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapSetters;
    }
