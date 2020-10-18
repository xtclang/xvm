package org.xvm.runtime;

import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString.StringHandle;


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
     * @param infoProp   the property info
     */
    public PropertyComposition(ClassComposition clzParent, PropertyInfo infoProp)
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

        assert !clzParent.isStruct();
        }

    /**
     * Construct a PropertyComposition clone that represents a "structure" view.
     */
    private PropertyComposition(PropertyComposition clzInception)
        {
        f_clzParent    = clzInception.f_clzParent;
        f_infoProp     = clzInception.f_infoProp;
        f_clzRef       = clzInception.f_clzRef;
        f_infoParent   = clzInception.f_infoParent;
        f_mapMethods   = clzInception.f_mapMethods;
        f_mapGetters   = clzInception.f_mapGetters;
        f_mapSetters   = clzInception.f_mapSetters;
        m_clzInception = clzInception;
        m_clzStruct    = this;
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
    public TypeConstant getBaseType()
        {
        return f_infoProp.getBaseRefType();
        }

    @Override
    public TypeComposition maskAs(TypeConstant type)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public TypeComposition revealAs(TypeConstant type)
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

        return access == Access.STRUCT ^ isStruct()
                ? handle.cloneAs(ensureAccess(access))
                : handle;
        }

    @Override
    public TypeComposition ensureAccess(Access access)
        {
        if (access == Access.STRUCT)
            {
            PropertyComposition clzStruct = m_clzStruct;
            if (clzStruct == null)
                {
                m_clzStruct = clzStruct = new PropertyComposition(this);
                }
            return clzStruct;
            }

        // for any other access return the inception composition
        return isStruct() ? m_clzInception : this;
        }

    @Override
    public boolean isStruct()
        {
        return m_clzStruct == this;
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
    public Map<Object, ObjectHandle> initializeStructure()
        {
        return f_clzRef.initializeStructure();
        }

    @Override
    public boolean isInflated(Object nid)
        {
        return f_clzRef.isInflated(nid);
        }

    @Override
    public boolean isLazy(Object nid)
        {
        return f_clzRef.isLazy(nid);
        }

    @Override
    public boolean isAllowedUnassigned(Object nid)
        {
        return f_clzRef.isAllowedUnassigned(nid);
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
    public CallChain getMethodCallChain(Object nidMethod)
        {
        return f_mapMethods.computeIfAbsent(nidMethod,
            nid ->
                {
                PropertyConstant idBase   = f_infoProp.getIdentity();
                MethodConstant   idNested = (MethodConstant) idBase.appendNestedIdentity(
                                                idBase.getConstantPool(), nid);

                MethodInfo info = f_infoParent.getMethodByNestedId(idNested.getNestedIdentity());
                return info == null
                    ? f_clzRef.getMethodCallChain(nid)
                    : new CallChain(info.ensureOptimizedMethodChain(f_infoParent));
                });
        }

    @Override
    public CallChain getPropertyGetterChain(PropertyConstant idProp)
        {
        return f_mapGetters.computeIfAbsent(idProp,
            id ->
                {
                // see if there's a nested property first; default to the base otherwise
                PropertyConstant idBase   = f_infoProp.getIdentity();
                PropertyConstant idNested = (PropertyConstant) idBase.appendNestedIdentity(
                                                idBase.getConstantPool(), id.getNestedIdentity());

                PropertyInfo infoProp = f_infoParent.findProperty(idNested);
                return infoProp == null
                    ? f_clzRef.getPropertyGetterChain(id)
                    : new CallChain(infoProp.ensureOptimizedGetChain(f_infoParent, idNested));
                });
        }

    @Override
    public CallChain getPropertySetterChain(PropertyConstant idProp)
        {
        return f_mapSetters.computeIfAbsent(idProp,
            id ->
                {
                PropertyConstant idBase   = f_infoProp.getIdentity();
                PropertyConstant idNested = (PropertyConstant) idBase.appendNestedIdentity(
                                                idBase.getConstantPool(), id.getNestedIdentity());

                PropertyInfo infoProp = f_infoParent.findProperty(idNested);
                return infoProp == null
                    ? f_clzRef.getPropertySetterChain(id)
                    : new CallChain(infoProp.ensureOptimizedSetChain(f_infoParent, idNested));
                });
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        if (idProp.getNestedDepth() == 1)
            {
            xRef template = (xRef) getTemplate();
            return f_infoProp.getIdentity().equals(idProp)
                    ? template.getReferent(frame, hRef, iReturn)
                    : template.getFieldValue(frame, hRef, idProp, iReturn);
            }
        return f_clzRef.getTemplate().getFieldValue(frame, hRef.getReferentHolder(), idProp, iReturn);
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue)
        {
        RefHandle hRef = (RefHandle) hTarget;

        if (idProp.getNestedDepth() == 1)
            {
            xRef template = (xRef) getTemplate();
            return f_infoProp.getIdentity().equals(idProp)
                    ? template.setReferent(frame, hRef, hValue)
                    : template.setFieldValue(frame, hRef, idProp, hValue);
            }
        return f_clzRef.getTemplate().setFieldValue(frame, hRef.getReferentHolder(), idProp, hValue);
        }

    @Override
    public List<String> getFieldNames()
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
        return Utils.OBJECTS_NONE;
        }

    @Override
    public String toString()
        {
        return "PropertyComposition: " + f_clzParent + "." + f_infoProp.getIdentity().getValueString();
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return true if the custom property this class represents is LazyVar annotated.
     */
    public boolean isLazy()
        {
        return f_infoProp.isLazy();
        }

    /**
     * @return the PropertyInfo for this property composition
     */
    public PropertyInfo getPropertyInfo()
        {
        return f_infoProp;
        }

    /**
     * @return the ClassComposition for this property composition
     */
    public ClassComposition getPropertyClass()
        {
        return f_clzRef;
        }

    /**
     * @return the ClassComposition for the parent
     */
    public ClassComposition getParentComposition()
        {
        return f_clzParent;
        }

    /**
     * @return the TypeInfo for the parent
     */
    public TypeInfo getParentInfo()
        {
        return f_infoParent;
        }


    // ----- data fields ---------------------------------------------------------------------------

    private final ClassComposition f_clzParent;

    private final TypeInfo         f_infoParent;
    private final ClassComposition f_clzRef;
    private final PropertyInfo     f_infoProp;

    // cached method call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapMethods;

    // cached property getter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapGetters;

    // cached property setter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapSetters;

    // cached PropertyComposition for the inception class
    private PropertyComposition m_clzInception;

    // cached PropertyComposition for the struct class
    private PropertyComposition m_clzStruct;
    }
