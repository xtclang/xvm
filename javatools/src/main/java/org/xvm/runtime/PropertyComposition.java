package org.xvm.runtime;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassComposition.FieldInfo;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.reflect.xRef;
import org.xvm.runtime.template.reflect.xRef.RefHandle;
import org.xvm.runtime.template.reflect.xVar;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Lazy;


/**
 * PropertyComposition represents a "custom" property class.
 */
public final class PropertyComposition
        implements TypeComposition {
    /**
     * Construct the PropertyComposition for a given property of the specified parent.
     *
     * @param clzParent  the parent's TypeComposition
     * @param infoProp   the property info
     */
    public PropertyComposition(ClassComposition clzParent, PropertyInfo infoProp) {
        assert !clzParent.isStruct();

        Container     container   = clzParent.getContainer();
        TypeConstant  typeRef     = infoProp.getBaseRefType();
        ClassTemplate templateRef = container.getTemplate(typeRef);

        f_clzParent  = clzParent;
        f_infoProp   = infoProp;
        f_clzRef     = templateRef.ensureClass(container, typeRef);
        f_mapMethods = new ConcurrentHashMap<>();
        f_mapGetters = new ConcurrentHashMap<>();
        f_mapSetters = new ConcurrentHashMap<>();

        f_clzInception = this;
        f_fStruct      = false;
    }

    /**
     * Construct a PropertyComposition clone that represents a "structure" view.
     */
    private PropertyComposition(PropertyComposition clzInception) {
        f_clzParent    = clzInception.f_clzParent;
        f_infoProp     = clzInception.f_infoProp;
        f_clzRef       = clzInception.f_clzRef;
        f_mapMethods   = clzInception.f_mapMethods;
        f_mapGetters   = clzInception.f_mapGetters;
        f_mapSetters   = clzInception.f_mapSetters;
        f_clzInception = clzInception;
        f_fStruct      = true;
    }


    // ----- TypeComposition interface -------------------------------------------------------------

    @Override
    public Container getContainer() {
        return f_clzParent.getContainer();
    }

    @Override
    public OpSupport getSupport() {
        return f_clzRef.getSupport();
    }

    @Override
    public ClassTemplate getTemplate() {
        return f_clzRef.getTemplate();
    }

    @Override
    public TypeConstant getType() {
        return getInceptionType();
    }

    @Override
    public TypeConstant getInceptionType() {
        TypeConstant typeParent = f_clzParent.getInceptionType();
        return typeParent.getConstantPool().ensurePropertyClassTypeConstant(
                typeParent, f_infoProp.getIdentity());
    }

    @Override
    public TypeConstant getBaseType() {
        return f_infoProp.getBaseRefType();
    }

    @Override
    public TypeComposition maskAs(TypeConstant type) {
        throw new UnsupportedOperationException("a property composition cannot be masked as " + type);
    }

    @Override
    public TypeComposition revealAs(TypeConstant type) {
        throw new UnsupportedOperationException("a property composition cannot be revealed as " + type);
    }

    @Override
    public ObjectHandle ensureOrigin(ObjectHandle handle) {
        return handle;
    }

    @Override
    public ObjectHandle ensureAccess(ObjectHandle handle, Access access) {
        assert handle.getComposition() == this;

        return access == Access.STRUCT ^ isStruct()
                ? handle.cloneAs(ensureAccess(access))
                : handle;
    }

    @Override
    public PropertyComposition ensureAccess(Access access) {
        if (access == Access.STRUCT) {
            return isStruct()
                    ? this
                    : f_structView.get(this);
        }

        // for any other access return the inception composition
        return f_clzInception;
    }

    @Override
    public boolean isStruct() {
        return f_fStruct;
    }

    @Override
    public boolean isConst() {
        return false;
    }

    @Override
    public boolean isInstanceChild() {
        return false;
    }

    @Override
    public MethodStructure ensureAutoInitializer() {
        return f_clzRef.ensureAutoInitializer();
    }

    @Override
    public ObjectHandle[] initializeStructure() {
        return f_clzRef.initializeStructure();
    }

    @Override
    public FieldInfo getFieldInfo(Object id) {
        return f_clzRef.getFieldInfo(id);
    }

    @Override
    public boolean makeStructureImmutable(ObjectHandle[] ahField) {
        return f_clzRef.makeStructureImmutable(ahField);
    }

    @Override
    public boolean hasOuter() {
        return f_clzRef.hasOuter();
    }

    @Override
    public boolean isInjected(PropertyConstant idProp) {
        return false;
    }

    @Override
    public boolean isAtomic(PropertyConstant idProp) {
        return false;
    }

    @Override
    public CallChain getMethodCallChain(Object nidMethod) {
        ConstantPool pool = getConstantPool();
        if (nidMethod instanceof SignatureConstant sig) {
            if (sig.getConstantPool() != pool) {
                nidMethod = pool.register(sig);
            }
        } else {
            NestedIdentity   idNested = (NestedIdentity) nidMethod;
            IdentityConstant idParent = idNested.getIdentityConstant();
            if (idParent.getConstantPool() != pool) {
                idParent  = pool.register(idParent);
                // The old nested identity already names a complete method path. After registering
                // that method identity in this owner pool, use the registered identity's own
                // relative key; appending the old key again creates paths such as
                // value#setValue#value#setValue under parallel containers.
                nidMethod = idParent.getNestedIdentity();
            }
        }
        return f_mapMethods.computeIfAbsent(nidMethod,
            nid -> {
                PropertyConstant idBase   = f_infoProp.getIdentity();
                MethodConstant   idNested = (MethodConstant)
                    (nid instanceof NestedIdentity nested &&
                        nested.getIdentityConstant().getNestedDepth() > idBase.getNestedDepth()
                            ? nested.getIdentityConstant()
                            : idBase.appendNestedIdentity(idBase.getConstantPool(), nid));

                TypeInfo   infoParent = getParentInfo();
                MethodInfo info       = infoParent.getMethodByNestedId(idNested.getNestedIdentity(), true);
                return info == null
                        ? f_clzRef.getMethodCallChain(nid)
                        : new CallChain(info.ensureOptimizedMethodChain(infoParent));
            });
    }

    @Override
    public CallChain getPropertyGetterChain(PropertyConstant idProp) {
        ConstantPool pool = getConstantPool();
        if (idProp.getConstantPool() != pool) {
            idProp = pool.register(idProp);
        }
        return f_mapGetters.computeIfAbsent(idProp,
            id -> {
                // see if there's a nested property first; default to the base otherwise
                PropertyConstant idBase   = f_infoProp.getIdentity();
                PropertyConstant idNested = id.getNestedDepth() > idBase.getNestedDepth()
                        ? id
                        : (PropertyConstant) idBase.appendNestedIdentity(
                                idBase.getConstantPool(), id.getNestedIdentity());

                MethodBody[] chain = getParentInfo().getOptimizedGetChain(idNested);
                return chain == null
                        ? f_clzRef.getPropertyGetterChain(id)
                        : CallChain.createPropertyCallChain(chain);
            });
    }

    @Override
    public CallChain getPropertySetterChain(PropertyConstant idProp) {
        ConstantPool pool = getConstantPool();
        if (idProp.getConstantPool() != pool) {
            idProp = pool.register(idProp);
        }
        return f_mapSetters.computeIfAbsent(idProp,
            id -> {
                PropertyConstant idBase   = f_infoProp.getIdentity();
                PropertyConstant idNested = id.getNestedDepth() > idBase.getNestedDepth()
                        ? id
                        : (PropertyConstant) idBase.appendNestedIdentity(
                                idBase.getConstantPool(), id.getNestedIdentity());

                MethodBody[] chain = getParentInfo().getOptimizedSetChain(idNested);
                return chain == null
                        ? f_clzRef.getPropertySetterChain(id)
                        : new CallChain(chain);
            });
    }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn) {
        RefHandle hRef = (RefHandle) hTarget;

        if (idProp.isTopLevel()) {
            xRef template = (xRef) getTemplate();
            return f_infoProp.containsBody(idProp)
                    ? template.getNativeReferent(frame, hRef, iReturn)
                    : template.getFieldValue(frame, hRef, idProp, iReturn);
        }
        return f_clzRef.getTemplate().getFieldValue(frame, hRef.getReferentHolder(), idProp, iReturn);
    }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, ObjectHandle hValue) {
        RefHandle hRef = (RefHandle) hTarget;

        if (idProp.isTopLevel()) {
            xVar template = (xVar) getTemplate();
            return f_infoProp.containsBody(idProp)
                    ? template.setNativeReferent(frame, hRef, hValue)
                    : template.setFieldValue(frame, hRef, idProp, hValue);
        }
        return f_clzRef.getTemplate().setFieldValue(frame, hRef.getReferentHolder(), idProp, hValue);
    }

    @Override
    public Map<Object, FieldInfo> getFieldLayout() {
        // strictly speaking, the list should include the non-top-level fields that are kept in
        // the parent's structure, but those are NestedIdentities, not Strings
        return f_clzRef.getFieldLayout();
    }

    @Override
    public StringHandle[] getFieldNameArray() {
        return Utils.STRINGS_NONE;
    }

    @Override
    public ObjectHandle[] getFieldValueArray(Frame frame, GenericHandle hValue) {
        return Utils.OBJECTS_NONE;
    }

    @Override
    public String toString() {
        return f_clzParent + "." + f_infoProp.getName() + (isStruct() ? ":struct" : "");
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return true if the custom property this class represents is Lazy annotated.
     */
    public boolean isLazy() {
        return f_infoProp.isLazy();
    }

    /**
     * @return the PropertyInfo for this property composition
     */
    public PropertyInfo getPropertyInfo() {
        return f_infoProp;
    }

    /**
     * @return the ClassComposition for this property composition
     */
    public TypeComposition getPropertyClass() {
        return f_clzRef;
    }

    /**
     * @return the ClassComposition for the parent
     */
    public ClassComposition getParentComposition() {
        return f_clzParent;
    }

    /**
     * @return the TypeInfo for the parent
     */
    private TypeInfo getParentInfo() {
        return f_clzParent.getInceptionType().ensureTypeInfo();
    }

    private static PropertyComposition createStructView(PropertyComposition owner) {
        /*
         * The struct view is a runtime composition object for this property owner. It shares all
         * call-chain caches with the inception composition, so Lazy.Bound publishes one completed
         * view without a constructor-captured `this` supplier or a mutable cache field.
         */
        return new PropertyComposition(owner);
    }


    // ----- data fields ---------------------------------------------------------------------------

    private final ClassComposition f_clzParent;
    private final TypeComposition  f_clzRef;
    private final PropertyInfo     f_infoProp;

    // cached method call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapMethods;

    // cached property getter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapGetters;

    // cached property setter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapSetters;

    private final PropertyComposition f_clzInception;

    private final boolean f_fStruct;

    private final Lazy.Bound<PropertyComposition, PropertyComposition> f_structView =
            Lazy.ofBound(PropertyComposition::createStructView);
}
