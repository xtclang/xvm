package org.xvm.runtime;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.Annotation;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.IdentityConstant.NestedIdentity;
import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.TransientId;

import org.xvm.runtime.template.annotations.xLazy.LazyHandle;

import org.xvm.runtime.template.reflect.xRef.RefHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Lazy;
import org.xvm.util.ListMap;


/**
 * TypeComposition represents a fully resolved class (e.g. {@code ArrayList<String>} or
 * {@code @Interval Range<Date>}).
 */
public final class ClassComposition
        implements TypeComposition {
    /**
     * Construct the ClassComposition for a given "inception" type.
     *
     * The guarantees for the inception type are:
     *  - it has to be a class (TypeConstant.isClass())
     *  - it cannot be abstract
     *  - the only modifying types that are allowed are AnnotatedTypeConstant(s) and
     *    ParameterizedTypeConstant(s)
     */
    public ClassComposition(Container container, ClassTemplate template, TypeConstant typeInception) {
        assert typeInception.isSingleDefiningConstant();
        assert typeInception.getAccess() == Access.PUBLIC;

        ConstantPool pool = container.getConstantPool();

        f_clzInception    = this;
        f_container       = container;
        f_template        = template;
        f_typeInception   = pool.ensureAccessTypeConstant(typeInception, Access.PRIVATE);
        // Access views are regular runtime operations. Prewarm this composition's canonical
        // private/protected/struct type constants while the composition is created, then keep the
        // actual view compositions lazy as before. This avoids a later protected-view request
        // growing an already runtime-published pool.
        f_typeProtected   = pool.ensureAccessTypeConstant(typeInception, Access.PROTECTED);
        f_typeStructure   = pool.ensureAccessTypeConstant(typeInception, Access.STRUCT);
        f_typeRevealed    = typeInception;
        f_fStruct         = typeInception.getAccess() == Access.STRUCT;
        f_mapCompositions = new ConcurrentHashMap<>();
        f_mapProxies      = new ConcurrentHashMap<>();
        f_mapProperties   = new ConcurrentHashMap<>();
        f_mapMethods      = new ConcurrentHashMap<>();
        f_mapGetters      = new ConcurrentHashMap<>();
        f_mapSetters      = new ConcurrentHashMap<>();
        f_fieldLayout     = Lazy.ofOwner(ClassComposition::buildFieldLayout);
        f_fieldNames      = Lazy.ofOwner(ClassComposition::buildFieldNameArray);
        f_methodInit      = Lazy.ofOwner(ClassComposition::buildAutoInitializer);
    }

    /**
     * Construct a ClassComposition clone for the specified revealed type.
     */
    private ClassComposition(ClassComposition clzInception, TypeConstant typeRevealed) {
        f_clzInception    = clzInception;
        f_container       = clzInception.f_container;
        f_template        = clzInception.f_template;
        f_typeInception   = clzInception.f_typeInception;
        f_typeProtected   = clzInception.f_typeProtected;
        f_typeStructure   = clzInception.f_typeStructure;
        f_typeRevealed    = typeRevealed;
        f_fStruct         = typeRevealed.getAccess() == Access.STRUCT;
        f_mapCompositions = f_clzInception.f_mapCompositions;
        f_mapProxies      = f_clzInception.f_mapProxies;
        f_mapProperties   = f_clzInception.f_mapProperties;
        f_mapMethods      = f_clzInception.f_mapMethods;
        f_mapGetters      = f_clzInception.f_mapGetters;
        f_mapSetters      = f_clzInception.f_mapSetters;
        f_fieldLayout     = f_clzInception.f_fieldLayout;
        f_fieldNames      = f_clzInception.f_fieldNames;
        f_methodInit      = f_clzInception.f_methodInit;
    }

    /**
     * @return a ProxyComposition for the specified proxying type
     */
    public ProxyComposition ensureProxyComposition(TypeConstant typeProxy) {
        return f_mapProxies.computeIfAbsent(typeProxy,
                (type) -> new ProxyComposition(this, register(type)));
    }

    /**
     * @return a CanonicalizedTypeComposition for the specified type
     */
    public CanonicalizedTypeComposition ensureCanonicalizedComposition(TypeConstant typeActual) {
        assert typeActual.isShared(getContainer().getConstantPool());
        return (CanonicalizedTypeComposition) f_mapCompositions.computeIfAbsent(typeActual,
                (type) -> new CanonicalizedTypeComposition(this, register(type)));
    }

    /**
     * @return a PropertyComposition for the specified property
     */
    public PropertyComposition ensurePropertyComposition(PropertyInfo infoProp) {
        return f_mapProperties.computeIfAbsent(infoProp.getIdentity(),
                (idProp) -> new PropertyComposition(f_clzInception, infoProp));
    }


    // ----- TypeComposition interface -------------------------------------------------------------


    @Override
    public Container getContainer() {
        return f_container;
    }

    @Override
    public OpSupport getSupport() {
        return f_template;
    }

    @Override
    public ClassTemplate getTemplate() {
        return f_template;
    }

    @Override
    public TypeConstant getType() {
        return f_typeRevealed;
    }

    @Override
    public TypeConstant getInceptionType() {
        return f_typeInception;
    }

    @Override
    public TypeConstant getBaseType() {
        return getType().removeAccess();
    }

    @Override
    public TypeComposition maskAs(TypeConstant type) {
        return type.equals(f_typeRevealed) ? this :
               f_typeRevealed.isA(type)
                   ? f_mapCompositions.computeIfAbsent(type,
                        typeR -> new ClassComposition(this, register(typeR)))
                   : null;
    }

    @Override
    public TypeComposition revealAs(TypeConstant type) {
        return type.equals(f_typeRevealed) ? this :
               f_typeStructure.isA(type)   ? ensureAccess(Access.STRUCT) :
               f_typeInception.isA(type)
                   ? f_mapCompositions.computeIfAbsent(type,
                            typeR -> new ClassComposition(f_clzInception, register(typeR)))
                   : null;
    }

    @Override
    public ObjectHandle ensureOrigin(ObjectHandle handle) {
        assert handle.getComposition() == this;

        // retain the access modifier of the revealed type on the origin
        return isInception()
            ? handle
            : handle.cloneAs(f_clzInception).ensureAccess(handle.getType().getAccess());
    }

    @Override
    public ObjectHandle ensureAccess(ObjectHandle handle, Access access) {
        assert handle.getComposition() == this;

        return access == f_typeRevealed.getAccess()
            ? handle
            : handle.cloneAs(ensureAccess(access));
    }

    @Override
    public TypeComposition ensureAccess(Access access) {
        TypeConstant typeCurrent = f_typeRevealed;

        Access accessCurrent = typeCurrent.getAccess();
        if (accessCurrent == access) {
            return this;
        }

        if (typeCurrent instanceof AccessTypeConstant) {
            // strip the access
            typeCurrent = typeCurrent.getUnderlyingType();
        }

        ConstantPool pool = getContainer().getConstantPool();
        TypeConstant typeTarget;
        switch (access) {
        case PUBLIC:
            typeTarget = typeCurrent;
            if (typeTarget.equals(f_clzInception.f_typeRevealed)) {
                return f_clzInception;
            }
            break;

        case PROTECTED:
            typeTarget = typeCurrent.equals(f_clzInception.f_typeRevealed)
                    ? f_clzInception.f_typeProtected
                    : pool.ensureAccessTypeConstant(typeCurrent, Access.PROTECTED);
            break;

        case PRIVATE:
            typeTarget = typeCurrent.equals(f_clzInception.f_typeRevealed)
                    ? f_clzInception.f_typeInception
                    : pool.ensureAccessTypeConstant(typeCurrent, Access.PRIVATE);
            break;

        case STRUCT:
            typeTarget = typeCurrent.equals(f_clzInception.f_typeRevealed)
                    ? f_clzInception.f_typeStructure
                    : pool.ensureAccessTypeConstant(typeCurrent, Access.STRUCT);
            break;

        default:
            throw new IllegalStateException();
        }

        return f_mapCompositions.computeIfAbsent(
            typeTarget, typeR -> new ClassComposition(f_clzInception, register(typeR)));
    }

    @Override
    public boolean isStruct() {
        return f_fStruct;
    }

    @Override
    public MethodStructure ensureAutoInitializer() {
        if (!isInception()) {
            return f_clzInception.ensureAutoInitializer();
        }

        var layout = fieldLayout();
        if (layout.fields().isEmpty()) {
            return null;
        }

        /*
         * The synthetic structure initializer is owner-pool metadata, not decoded class identity.
         * All access views share the inception composition's field layout, so publish one completed
         * initializer from that owner through a final Lazy.Owner cell instead of a mutable field.
         */
        MethodStructure method = f_methodInit.get(this);
        return method.isAbstract() ? null : method;
    }

    @Override
    public boolean isInjected(PropertyConstant idProp) {
        PropertyInfo infoProp = f_typeInception.ensureTypeInfo().findProperty(idProp, true);
        return infoProp != null && infoProp.isInjected();
    }

    @Override
    public boolean isAtomic(PropertyConstant idProp) {
        // we assume that all native properties are atomic by default; it's up to the native
        // property implementation to re-route it to the corresponding context if necessary
        PropertyInfo infoProp = f_typeInception.ensureTypeInfo().findProperty(idProp, true);
        return infoProp != null && (infoProp.isAtomic() || infoProp.isNative());
    }

    @Override
    public CallChain getMethodCallChain(Object nidMethod) {
        CallChain chain = f_mapMethods.get(nidMethod);
        return chain == null
                ? ensureMethodChain(nidMethod)
                : chain;
    }

    /**
     * Compute the invocation {@link CallChain} for a given method.
     *
     * @param nidMethod the method nid
     *
     * @return the {@link CallChain}
     */
    private CallChain ensureMethodChain(Object nidMethod) {
        ConstantPool pool   = getConstantPool();
        // runtime-lazy chain resolution registers shared ids into this pool and drives lazy
        // TypeInfo/chain building - the legitimate post-publication metadata writer
        try (var _ = pool.openRuntimeSynthesisWindow("method call chain")) {
            boolean      fCache = true;
            if (nidMethod instanceof SignatureConstant sig) {
                if (sig.getConstantPool() != pool) {
                    if (sig.isShared(pool)) {
                        nidMethod = pool.register(sig);
                    } else {
                        // what else can we do here?
                        fCache = false;
                        System.err.println("WARNING: Foreign method chain for " + sig); // TODO: remove
                    }
                }
            } else {
                NestedIdentity   idNested = (NestedIdentity) nidMethod;
                IdentityConstant idParent = idNested.getIdentityConstant();
                if (idParent.getConstantPool() != pool) {
                    if (idParent.isShared(pool)) {
                        idParent  = pool.register(idParent);
                        nidMethod = idParent.appendNestedIdentity(pool, idNested);
                    } else {
                        fCache = false;
                        System.err.println("WARNING: Foreign nested method " + idNested +
                                           " for " + idParent); // TODO: remove
                    }
                }
            }
            return fCache
                    ? f_mapMethods.computeIfAbsent(nidMethod, this::computeMethodChain)
                    : computeMethodChain(nidMethod);
        }
    }

    private CallChain computeMethodChain(Object nidMethod) {
        TypeInfo info = isStruct()
                ? f_typeStructure.ensureTypeInfo()
                : f_typeInception.ensureTypeInfo();
        return new CallChain(info.getOptimizedMethodChain(nidMethod));
    }

    @Override
    public CallChain getPropertyGetterChain(PropertyConstant idProp) {
        CallChain chain = f_mapGetters.get(idProp);
        return chain == null
                ? ensureGetterChain(idProp)
                : chain == NIL_CHAIN ? null : chain;
    }

    /**
     * Compute the "getter" {@link CallChain} for a given property.
     *
     * @param idProp the property id
     *
     * @return the {@link CallChain}
     */
    private CallChain ensureGetterChain(PropertyConstant idProp) {
        ConstantPool pool    = getConstantPool();
        // see ensureMethodChain for the synthesis-window rationale
        try (var _ = pool.openRuntimeSynthesisWindow("getter call chain")) {
            boolean      fShared = true;
            if (idProp.getConstantPool() != pool) {
                if (idProp.getConstantPool() != pool) {
                    if (idProp.isShared(pool)) {
                        idProp = pool.register(idProp);
                    } else {
                        // most likely this happens due to duck-typed properties; we may consider
                        // caching chains by name (the PropertyInfo is most likely cached already)
                        fShared = false;
                    }
                }
            }

            CallChain chain = fShared
                    ? f_mapGetters.computeIfAbsent(idProp, this::computeGetterChain)
                    : computeGetterChain(idProp);
            return chain == NIL_CHAIN ? null : chain;
        }
    }

    private CallChain computeGetterChain(PropertyConstant id) {
        MethodBody[] aBody = f_typeInception.ensureTypeInfo().getOptimizedGetChain(id);
        return aBody == null
                ? NIL_CHAIN
                : CallChain.createPropertyCallChain(aBody);
    }

    @Override
    public CallChain getPropertySetterChain(PropertyConstant idProp) {
        CallChain chain = f_mapSetters.get(idProp);
        return chain == null
                ? ensurePropertySetterChain(idProp)
                : chain == NIL_CHAIN ? null : chain;
    }

    /**
     * Compute the "setter" {@link CallChain} for a given property.
     *
     * @param idProp the property id
     *
     * @return the {@link CallChain}
     */
    private CallChain ensurePropertySetterChain(PropertyConstant idProp) {
        ConstantPool pool    = getConstantPool();
        // see ensureMethodChain for the synthesis-window rationale
        try (var _ = pool.openRuntimeSynthesisWindow("setter call chain")) {
            boolean      fShared = true;
            if (idProp.getConstantPool() != pool) {
                if (idProp.isShared(pool)) {
                    idProp = pool.register(idProp);
                } else {
                    // see the comment in ensureGetterChain()
                    fShared = false;
                }
            }
            CallChain chain = fShared
                    ? f_mapSetters.computeIfAbsent(idProp, this::computeSetterChain)
                    : computeSetterChain(idProp);
            return chain == NIL_CHAIN ? null : chain;
        }
    }

    private CallChain computeSetterChain(PropertyConstant id) {
        MethodBody[] aBody = f_typeInception.ensureTypeInfo().getOptimizedSetChain(id);
        return aBody == null
                ? NIL_CHAIN
                : CallChain.createPropertyCallChain(aBody);
    }

    @Override
    public Map<Object, FieldInfo> getFieldLayout() {
        return fieldLayout().fields();
    }

    @Override
    public StringHandle[] getFieldNameArray() {
        if (!isInception()) {
            return f_clzInception.getFieldNameArray();
        }

        return f_fieldNames.get(this);
    }

    private MethodStructure buildAutoInitializer() {
        var pool = getContainer().getConstantPool();
        return f_template.getStructure().createInitializer(pool, f_typeStructure,
                fieldLayout().fields());
    }

    private StringHandle[] buildFieldNameArray() {
        /*
         * These StringHandles carry this composition's container. The old plain lazy write could
         * publish a partially filled owner-bearing array, and access-view clones could race separate
         * duplicate arrays. Keep the API's cached array behavior but publish exactly one
         * inception-owned array safely.
         */
        var layout   = fieldLayout();
        var ashNames = new StringHandle[layout.regularFieldCount()];

        int i = 0;
        for (Map.Entry<Object, FieldInfo> entry : layout.fields().entrySet()) {
            Object    enid  = entry.getKey();
            FieldInfo field = entry.getValue();

            if (!(enid instanceof NestedIdentity) && field.isRegular()) {
                ashNames[i++] = xString.makeHandle(getContainer(), field.getName());
            }
        }
        assert i == layout.regularFieldCount();

        return ashNames;
    }

    @Override
    public ObjectHandle[] getFieldValueArray(Frame frame, GenericHandle hValue) {
        var                    layout    = fieldLayout();
        Map<Object, FieldInfo> mapLayout = layout.fields();
        if (mapLayout.isEmpty()) {
            return Utils.OBJECTS_NONE;
        }

        ObjectHandle[] ahFields = new ObjectHandle[layout.regularFieldCount()];

        int i = 0;
        for (Map.Entry<Object, FieldInfo> entry : mapLayout.entrySet()) {
            Object    enid  = entry.getKey();
            FieldInfo field = entry.getValue();

            if (!(enid instanceof NestedIdentity) && field.isRegular()) {
                ahFields[i++] = hValue.getField(frame, field);
            }
        }
        assert i == layout.regularFieldCount();
        return ahFields;
    }

    @Override
    public ObjectHandle[] initializeStructure() {
        var layout    = fieldLayout();
        var mapFields = layout.fields();
        int cSize     = mapFields.size();

        if (cSize == 0) {
            return Utils.OBJECTS_NONE;
        }

        ObjectHandle[] aFields = new ObjectHandle[cSize];
        if (layout.hasSpecial()) {
            for (FieldInfo field : mapFields.values()) {
                if (field.isTransient()) {
                    aFields[field.getIndex()] = new TransientId();
                } else if (field.isInflated()) {
                    aFields[field.getIndex()] = field.createRefHandle(null);
                }
            }
        }

        return aFields;
    }

    @Override
    public FieldInfo getFieldInfo(Object id) {
        if (id instanceof PropertyConstant idProp &&
                idProp.getComponent().getAccess() != Access.PRIVATE) {
            id = idProp.getNestedIdentity();
        }
        return fieldLayout().fields().get(id);
    }

    @Override
    public boolean hasOuter() {
        return fieldLayout().hasOuter();
    }

    @Override
    public boolean makeStructureImmutable(ObjectHandle[] ahField) {
        for (FieldInfo field : fieldLayout().fields().values()) {
            ObjectHandle hValue = ahField[field.getIndex()];

            if (hValue != null && hValue.isMutable() && !hValue.isService() &&
                    (!(hValue instanceof LazyHandle hLazy) || hLazy.isAssigned()) &&
                    !hValue.makeImmutable()) {
                return false;
            }
        }

        return true;
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Ensure the constant we use as a key to any of the caches belongs to this Container's
     * ConstantPool or any of its ancestors.
     */
    private <T extends Constant> T register(T type) {
        Container    container = getContainer();
        ConstantPool poolThis  = container.getConstantPool();
        ConstantPool poolThat  = type.getConstantPool();

        if (poolThat == poolThis) {
            return type;
        }

        while (true) {
            container = container.f_parent;
            if (container == null) {
                break;
            }
            if (poolThat == container.getConstantPool()) {
                return type;
            }
        }
        return poolThis.register(type);
    }

    /**
     * @return true iff this TypeComposition represents an inception class
     */
    public boolean isInception() {
        return this == f_clzInception;
    }

    /**
     * Create a map of fields that serves as a prototype for all instances of this class.
     */
    public void ensureFieldLayout(Container container) {
        if (container != getContainer()) {
            throw new IllegalArgumentException("field layout owner mismatch");
        }
        fieldLayout();
    }

    private FieldLayout buildFieldLayout() {
        if (!f_template.isGenericHandle()) {
            return FieldLayout.EMPTY;
        }

        TypeConstant typePublic = f_typeInception.getUnderlyingType();
        if (typePublic instanceof PropertyClassTypeConstant) {
            return FieldLayout.EMPTY;
        }

        Container    container  = getContainer();
        ConstantPool pool       = container.getConstantPool();
        TypeConstant typeStruct = pool.ensureAccessTypeConstant(typePublic, Access.STRUCT);
        TypeInfo     infoStruct = typeStruct.ensureTypeInfo();

        Map<Object, FieldInfo> mapFields = new ListMap<>();
        int     cRegular  = 0;
        int     nIndex    = 0;
        boolean fHasOuter = false;
        boolean fSpecial  = false;

        // create storage for implicit fields
        for (String sField : f_template.getImplicitFields()) {
            if (sField.equals(GenericHandle.OUTER)) {
                fHasOuter = true;
            }
            mapFields.put(sField,
                    new FieldInfo(sField, nIndex++, pool.typeObject(), null,
                        /*synthetic*/ true, false, false, false));
        }

        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoStruct.sortedProperties()) {
            PropertyConstant idProp   = entry.getKey();
            PropertyInfo     infoProp = entry.getValue();
            boolean          fField   = infoProp.hasField();

            if (fField && !idProp.isTopLevel()) {
                IdentityConstant idParent = idProp.getParentConstant();
                switch (idParent.getFormat()) {
                case Property:
                    if (!infoStruct.getClassChain().
                            containsKey(infoProp.getIdentity().getClassIdentity())) {
                        // the property is defined by the underlying type; currently those
                        // nested properties are stored in the corresponding Ref "box"
                        continue;
                    }
                    break;

                case Method:
                    break;
                }
            }

            TypeComposition clzRef = null;
            if (infoProp.isRefAnnotated()) { // this doesn't include injected properties
                clzRef = infoProp.isCustomLogic()
                        ? ensurePropertyComposition(infoProp)
                        : container.resolveClass(infoProp.getBaseRefType());

                if (clzRef != null && !infoProp.isNative()) {
                    AnyConstructor:
                    for (Annotation anno : infoProp.getRefAnnotations()) {
                        TypeInfo infoAnno = anno.getAnnotationType().ensureTypeInfo();
                        int      cArgs    = anno.getParams().length;

                        Set<MethodConstant> setConstrId = infoAnno.findMethods("construct", cArgs,
                                TypeInfo.MethodKind.Constructor);
                        for (MethodConstant idConstruct : setConstrId) {
                            MethodStructure method = infoAnno.getMethodById(idConstruct, true).
                                getTopmostMethodStructure(infoAnno);

                            if (!method.isSynthetic() || !method.isNoOp()) {
                                // this will serve as a flag to call annotation constructors
                                // (see ClassTemplate.createPropertyRef() method)
                                clzRef = clzRef.ensureAccess(Access.STRUCT);
                                break AnyConstructor;
                            }
                        }
                    }
                }
            }

            if (fField) {
                boolean fTransient = infoProp.isTransient();
                boolean fPrivate   = infoProp.getRefAccess() == Access.PRIVATE;

                Object enid = fPrivate ? idProp : idProp.getNestedIdentity();

                assert fPrivate
                        ? idProp.getComponent() != null
                        : infoStruct.findPropertyByNid(enid) != null;
                assert !mapFields.containsKey(enid);

                TypeConstant type = infoProp.getType();
                if (type.containsFormalType(true)) {
                    type = type.resolveConstraints();
                }

                FieldInfo field = new FieldInfo(enid, nIndex++, type,
                        clzRef, infoProp.isInjected(), fTransient,
                        infoProp.isImplicitlyAssigned(),
                        infoProp.isLazy());
                mapFields.put(enid, field);

                fSpecial |= fTransient | clzRef != null;

                if (!(enid instanceof NestedIdentity) && field.isRegular()) {
                    cRegular++;
                }
            } else {
                // lazy constants are handled by the runtime
                assert clzRef == null || (infoProp.isLazy() && infoProp.isConstant());
            }
        }

        Map<Object, FieldInfo> mapFrozen = mapFields.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(mapFields.size() > 8
                    ? new LinkedHashMap<>(mapFields)
                    : mapFields);

        return new FieldLayout(mapFrozen, cRegular, fHasOuter, fSpecial);
    }

    private FieldLayout fieldLayout() {
        return f_fieldLayout.get(f_clzInception);
    }

    /**
     * @return the compile-time type for a given property name or identity (never nested identity)
     */
    public TypeConstant getFieldType(Object nid) {
        TypeConstant type     = getInceptionType();
        TypeInfo     infoType = type.ensureTypeInfo();
        PropertyInfo infoProp = nid instanceof PropertyConstant idProp
                ? infoType.findProperty(idProp, true)
                : infoType.findProperty((String) nid);
        return infoProp == null ? null : infoProp.inferImmutable(type);
    }

    /**
     * @return the compile-time PropertyInfo for a given property
     */
    public PropertyInfo getPropertyInfo(PropertyConstant idProp) {
        return getInceptionType().ensureTypeInfo().findProperty(idProp, true);
    }

    @Override
    public int hashCode() {
        return f_typeRevealed.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        // type compositions are singletons
        return this == obj;
    }

    @Override
    public String toString() {
        return f_typeRevealed.getValueString();
    }

    /**
     * Information regarding a field of this {@link ClassComposition}.
     */
    public static class FieldInfo {
        /**
         * Construct a {@link FieldInfo}.
         *
         * @param enid        the field's name, property id or nested identity
         * @param nIndex      the field's storage index
         * @param type        the field's type
         * @param clzRef      (optional) the TypeComposition for inflated fields
         * @param fSynthetic  true iff the field is synthetic (e.g. implicit or injected)
         */
        protected FieldInfo(Object enid, int nIndex, TypeConstant type, TypeComposition clzRef,
                            boolean fSynthetic, boolean fTransient, boolean fUnassigned, boolean fLazy) {
            f_enid        = enid;
            f_nIndex      = nIndex;
            f_type        = type;
            f_clzRef      = clzRef;
            f_fSynthetic  = fSynthetic;
            f_fTransient  = fTransient;
            f_fUnassigned = fUnassigned;
            f_fLazy       = fLazy;
        }

        /**
         * @return the field's name
         */
        public String getName() {
            return f_enid instanceof PropertyConstant idProp
                    ? idProp.getPathString()
                    : f_enid.toString();
        }

        /**
         * @return the field's index
         */
        public int getIndex() {
            return f_nIndex;
        }

        /**
         * @return the field's type
         */
        public TypeConstant getType() {
           return f_type;
       }

        /**
         * @return true iff the field is inflated
         */
        public boolean isInflated() {
            return f_clzRef != null;
        }

        /**
         * @return true iff the field is synthetic
         */
        public boolean isSynthetic() {
            return f_fSynthetic;
        }

        /**
         * @return true iff the field is transient
         */
        public boolean isTransient() {
            return f_fTransient;
        }

        /**
         * @return true iff the field is allowed to be unassigned
         */
        public boolean isUnassigned() {
            return f_fUnassigned;
        }

        /**
         * @return true iff the field is Lazy annotated
         */
        public boolean isLazy() {
            return f_fLazy;
        }

        /**
         * @return true if this field is allowed to stay unassigned
         */
        public boolean isAllowedUnassigned() {
            return isSynthetic() || isTransient() || isUnassigned();
        }

        /**
         * @return true if this field is regular field (used by Const auto-generated methods)
         */
        public boolean isRegular() {
            return !isSynthetic() && !isTransient() && !isUnassigned() && !isLazy();
        }

        /**
         * @return a new RefHandle for this field
         */
        public RefHandle createRefHandle(Frame frame) {
            return ((VarSupport) f_clzRef.getSupport()).createRefHandle(frame, f_clzRef, getName());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append(getName())
              .append('@')
              .append(getIndex());

            if (isSynthetic()) {
                sb.append(" synthetic");
            }
            if (isTransient()) {
                sb.append(" transient");
            }
            if (isInflated()) {
                sb.append(" inflated");
            }

            return sb.toString();
        }

        // ----- fields ----------------------------------------------------------------------------

        private final Object          f_enid; // String | PropertyConstant | NestedIdentity
        private final int             f_nIndex;
        private final TypeConstant    f_type;
        private final TypeComposition f_clzRef;
        private final boolean         f_fSynthetic;
        private final boolean         f_fTransient;
        private final boolean         f_fUnassigned;
        private final boolean         f_fLazy;

        public Constant        constInit;
        public MethodStructure methodInit;
    }

    private record FieldLayout(Map<Object, FieldInfo> fields, int regularFieldCount,
                               boolean hasOuter, boolean hasSpecial) {
        private static final FieldLayout EMPTY =
                new FieldLayout(Collections.emptyMap(), 0, false, false);
    }


    // ----- data fields ---------------------------------------------------------------------------

    private final Container f_container;

    /**
     * The {@link ClassTemplate} for the defining class of the inception type. Note, that the
     * defining class could be {@link org.xvm.asm.constants.NativeRebaseConstant native}.
     */
    private final ClassTemplate f_template;

    /**
     * The inception TypeComposition.
     */
    private final ClassComposition f_clzInception;

    /**
     * The inception type - the maximum of what this type composition could be revealed as.
     *
     * Note: the access of the inception type is always Access.PRIVATE.
     */
    private final TypeConstant f_typeInception;

    /**
     * The protected access type for the inception type.
     */
    private final TypeConstant f_typeProtected;

    /**
     * The structure type for the inception type.
     */
    private final TypeConstant f_typeStructure;

    /**
     * The type that is revealed by the ObjectHandle that refer to this composition.
     */
    private final TypeConstant f_typeRevealed;

    /**
     * {@code true} if {@link #f_typeRevealed} is a struct
     */
    private final boolean f_fStruct;

    /**
     * A cache of derivative TypeCompositions keyed by the "revealed type".
     * <p/>
     * We assume that there will never be two instantiate-able classes with the same inception type,
     * but different revealed type. The ClassComposition may hide (or mask) its original identity
     * via the {@link #maskAs(TypeConstant)} operation and later reveal it back.
     * <p/>
     * Most of the time the revealed type is identical to the inception type so this map is going
     * to be empty. One exception is the native types (e.g. Ref, Service), for which the inception
     * type is defined by a {@link NativeRebaseConstant} class constant and the revealed type refers
     * to the corresponding natural interface.
     * */
    private final Map<TypeConstant, TypeComposition> f_mapCompositions;

    /**
     * A cache of derivative ProxyCompositions keyed by the "proxy type".
     */
    private final Map<TypeConstant, ProxyComposition> f_mapProxies;

    /**
     * A cache of derivative PropertyCompositions keyed by the property id.
     */
    private final Map<PropertyConstant, PropertyComposition> f_mapProperties;

    // cached method call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapMethods;

    // cached property getter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapGetters;

    // cached property setter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapSetters;

    /**
     * Inception-owned immutable field layout. Field layout is a group invariant; keep the field map,
     * regular-field count, and flags in one final lazy holder so access views cannot copy a stale
     * partial layout.
     */
    private final Lazy.Owner<ClassComposition, FieldLayout> f_fieldLayout;

    /**
     * Cached field-name handles for native Stringable methods.
     */
    private final Lazy.Owner<ClassComposition, StringHandle[]> f_fieldNames;

    /**
     * Cached auto-generated structure initializer.
     */
    private final Lazy.Owner<ClassComposition, MethodStructure> f_methodInit;

    /**
     * Marker for a cached null {@link CallChain}.
     */
    private static final CallChain NIL_CHAIN = new CallChain(MethodBody.NO_BODIES);
}
