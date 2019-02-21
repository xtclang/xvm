package org.xvm.runtime;

import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

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
     * The guarantees for the inception type are:
     *  - it has to be a class (TypeConstant.isClass())
     *  - it cannot be abstract
     *  @param clzParent  the parent's type composition
     * @param infoProp  the property name
     */
    protected PropertyComposition(ClassComposition clzParent, PropertyInfo infoProp)
        {
        f_clzParent = clzParent;
        f_infoProp  = infoProp;

        TypeConstant typeParent = clzParent.getInceptionType();
        TypeInfo     infoParent = typeParent.ensureTypeInfo();
        TypeConstant typeRef    = infoProp.getRefType();

        f_clzRef     = clzParent.getRegistry().resolveClass(typeRef);
        f_infoParent = infoParent;
        f_mapMethods = new ConcurrentHashMap<>();
        f_mapGetters = new ConcurrentHashMap<>();
        f_mapSetters = new ConcurrentHashMap<>();
        }

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
        return f_infoProp.getRefType();
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
    public ObjectHandle ensureAccess(ObjectHandle handle, Constants.Access access)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeComposition ensureAccess(Constants.Access access)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public boolean isStruct()
        {
        return false;
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
    public Map<String, ObjectHandle> createFields()
        {
        return f_clzRef.createFields();
        }

    @Override
    public boolean isRefAnnotated(String sProperty)
        {
        return f_clzRef.isRefAnnotated(sProperty);
        }

    @Override
    public boolean isInjected(String sProperty)
        {
        return false;
        }

    @Override
    public boolean isAtomic(String sProperty)
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
    public CallChain getPropertyGetterChain(String sProperty)
        {
        return f_mapGetters.computeIfAbsent(sProperty,
            sName ->
                {
                // see if there's a nested property first; default to the base otherwise
                PropertyConstant idNested = (PropertyConstant) f_infoProp.getIdentity().
                    appendNestedIdentity(ConstantPool.getCurrentPool(), sName);

                PropertyInfo infoProp = f_infoParent.findProperty(idNested);
                return infoProp == null
                    ? f_clzRef.getPropertyGetterChain(sName)
                    : new CallChain(infoProp.ensureOptimizedGetChain(f_infoParent));
                });
        }

    @Override
    public CallChain getPropertySetterChain(String sProperty)
        {
        return f_mapSetters.computeIfAbsent(sProperty,
            sName ->
                {
                PropertyConstant idNested = (PropertyConstant) f_infoProp.getIdentity().
                    appendNestedIdentity(ConstantPool.getCurrentPool(), sName);

                PropertyInfo infoProp = f_infoParent.findProperty(idNested);
                return infoProp == null
                    ? f_clzRef.getPropertySetterChain(sName)
                    : new CallChain(infoProp.ensureOptimizedSetChain(f_infoParent));
                });
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
    public ObjectHandle[] getFieldValueArray(ObjectHandle.GenericHandle hValue)
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

    // cached property getter call chain (the top-most method first)
    private final Map<String, CallChain> f_mapGetters;

    // cached property setter call chain (the top-most method first)
    private final Map<String, CallChain> f_mapSetters;
    }
