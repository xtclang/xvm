package org.xvm.runtime;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.ListMap;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String> or
 * @Interval Range<Date>).
 */
public class ClassComposition
        implements TypeComposition
    {
    /**
     * Construct the ClassComposition for a given "inception" type.
     *
     * The guarantees for the inception type are:
     *  - it has to be a class (TypeConstant.isClass())
     *  - it cannot be abstract
     *  - the only modifying types that are allowed are AnnotatedTypeConstant(s) and
     *    ParameterizedTypeConstant(s)
     *
     * @param support the OpSupport implementation for the inception type
     */
    public ClassComposition(OpSupport support, TypeConstant typeInception)
        {
        assert typeInception.isSingleDefiningConstant();
        assert typeInception.getAccess() == Access.PUBLIC;

        ConstantPool  pool     = typeInception.getConstantPool();
        ClassTemplate template = support.getTemplate(typeInception);

        if (support instanceof ClassTemplate)
            {
            support = template;
            }

        f_clzInception    = this;
        f_support         = support;
        f_template        = template;
        f_typeInception   = pool.ensureAccessTypeConstant(typeInception, Access.PRIVATE);
        f_typeStructure   = pool.ensureAccessTypeConstant(typeInception, Access.STRUCT);
        f_typeRevealed    = typeInception;
        f_mapCompositions = new ConcurrentHashMap<>();
        f_mapProxies      = new ConcurrentHashMap<>();
        f_mapMethods      = new ConcurrentHashMap<>();
        f_mapGetters      = new ConcurrentHashMap<>();
        f_mapSetters      = new ConcurrentHashMap<>();
        }

    /**
     * Construct a ClassComposition clone for the specified revealed type.
     */
    private ClassComposition(ClassComposition clzInception, TypeConstant typeRevealed)
        {
        f_clzInception    = clzInception;
        f_support         = clzInception.f_support;
        f_template        = clzInception.f_template;
        f_typeInception   = clzInception.f_typeInception;
        f_typeStructure   = clzInception.f_typeStructure;
        f_typeRevealed    = typeRevealed;
        f_mapCompositions = f_clzInception.f_mapCompositions;
        f_mapProxies      = f_clzInception.f_mapProxies;
        f_mapMethods      = f_clzInception.f_mapMethods;
        f_mapGetters      = f_clzInception.f_mapGetters;
        f_mapSetters      = f_clzInception.f_mapSetters;

        m_mapFields       = f_clzInception.m_mapFields;
        m_methodInit      = f_clzInception.m_methodInit;
        }

    /**
     * @return true iff this class represents an instance inner class
     */
    public boolean isInstanceChild()
        {
        return f_template.getStructure().isInstanceChild();
        }

    /**
     * @return a ProxyComposition for the specified proxying type
     */
    public ProxyComposition ensureProxyComposition(TypeConstant typeProxy)
        {
        return f_mapProxies.computeIfAbsent(typeProxy, (type) -> new ProxyComposition(this, type));
        }


    // ----- TypeComposition interface -------------------------------------------------------------

    @Override
    public OpSupport getSupport()
        {
        return f_support;
        }

    @Override
    public ClassTemplate getTemplate()
        {
        return f_template;
        }

    @Override
    public TypeConstant getType()
        {
        return f_typeRevealed;
        }

    @Override
    public TypeConstant getBaseType()
        {
        return getType().removeAccess();
        }

    @Override
    public ClassComposition maskAs(TypeConstant type)
        {
        return type.equals(f_typeRevealed) ? this :
               f_typeRevealed.isA(type)
                   ? f_mapCompositions.computeIfAbsent(type, typeR -> new ClassComposition(this, typeR))
                   : null;
        }

    @Override
    public ClassComposition revealAs(TypeConstant type)
        {
        return type.equals(f_typeRevealed)  ? this :
               type.equals(f_typeInception) ? f_clzInception :
               type.equals(f_typeStructure) ? ensureAccess(Access.STRUCT) :
               f_typeInception.isA(type)
                   ? f_mapCompositions.computeIfAbsent(type,
                            typeR -> new ClassComposition(f_clzInception, typeR))
                   : null;
        }

    @Override
    public ObjectHandle ensureOrigin(ObjectHandle handle)
        {
        assert handle.getComposition() == this;

        // retain the access modifier of the revealed type on the origin
        return isInception()
            ? handle
            : handle.cloneAs(f_clzInception).ensureAccess(handle.getType().getAccess());
        }

    @Override
    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.getComposition() == this;

        return access == f_typeRevealed.getAccess()
            ? handle
            : handle.cloneAs(ensureAccess(access));
        }

    @Override
    public ClassComposition ensureAccess(Access access)
        {
        TypeConstant typeCurrent = f_typeRevealed;

        Access accessCurrent = typeCurrent.getAccess();
        if (accessCurrent == access)
            {
            return this;
            }

        if (typeCurrent instanceof AccessTypeConstant)
            {
            // strip the access
            typeCurrent = typeCurrent.getUnderlyingType();
            }

        ConstantPool pool = typeCurrent.getConstantPool();
        TypeConstant typeTarget;
        switch (access)
            {
            case PUBLIC:
                typeTarget = typeCurrent;
                if (typeTarget.equals(f_clzInception.f_typeRevealed))
                    {
                    return f_clzInception;
                    }
                break;

            case PROTECTED:
                typeTarget = pool.ensureAccessTypeConstant(typeCurrent, Access.PROTECTED);
                break;

            case PRIVATE:
                typeTarget = pool.ensureAccessTypeConstant(typeCurrent, Access.PRIVATE);
                break;

            case STRUCT:
                typeTarget = pool.ensureAccessTypeConstant(typeCurrent, Access.STRUCT);
                break;

            default:
                throw new IllegalStateException();
            }

        return f_mapCompositions.computeIfAbsent(
            typeTarget, typeR -> new ClassComposition(f_clzInception, typeR));
        }

    @Override
    public boolean isStruct()
        {
        return f_typeRevealed.getAccess() == Access.STRUCT;
        }

    @Override
    public boolean isConst()
        {
        return ((ClassStructure) f_typeInception.
                getSingleUnderlyingClass(false).getComponent()).isConst();
        }

    @Override
    public MethodStructure ensureAutoInitializer()
        {
        if (m_mapFields.isEmpty())
            {
            return null;
            }

        MethodStructure method = m_methodInit;
        if (method == null)
            {
            m_methodInit = method =
                f_template.getStructure().createInitializer(f_typeStructure, m_mapFields);
            }
        return method.isAbstract() ? null : method;
        }

    @Override
    public boolean isInflated(Object nid)
        {
        return m_mapFields.get(nid) != null;
        }

    @Override
    public boolean isLazy(Object nid)
        {
        if (isInflated(nid))
            {
            ConstantPool    pool = f_typeInception.getConstantPool();
            TypeComposition clz  = m_mapFields.get(nid);

            return clz instanceof PropertyComposition && ((PropertyComposition) clz).isLazy()
                || clz.getType().containsAnnotation(pool.clzLazy());
            }
        return false;
        }

    @Override
    public boolean isAllowedUnassigned(Object nid)
        {
        return f_typeStructure.ensureTypeInfo().findPropertyByNid(nid).isSimpleUnassigned();
        }

    @Override
    public boolean isInjected(PropertyConstant idProp)
        {
        return f_typeInception.ensureTypeInfo().findProperty(idProp).isInjected();
        }

    @Override
    public boolean isAtomic(PropertyConstant idProp)
        {
        return f_typeInception.ensureTypeInfo().findProperty(idProp).isAtomic();
        }

    @Override
    public CallChain getMethodCallChain(Object nidMethod)
        {
        return f_mapMethods.computeIfAbsent(nidMethod,
            nid ->
                {
                TypeInfo info = isStruct()
                        ? f_typeStructure.ensureTypeInfo()
                        : f_typeInception.ensureTypeInfo();
                return new CallChain(info.getOptimizedMethodChain(nid));
                }
            );
        }

    @Override
    public CallChain getPropertyGetterChain(PropertyConstant idProp)
        {
        return f_mapGetters.computeIfAbsent(idProp,
            id ->
                {
                TypeInfo     info = f_typeInception.ensureTypeInfo();
                PropertyInfo prop = info.findProperty(id);

                return prop == null
                        ? null
                        : new CallChain(info.getOptimizedGetChain(prop.getIdentity()));
                });
        }

    @Override
    public CallChain getPropertySetterChain(PropertyConstant idProp)
        {
        return f_mapSetters.computeIfAbsent(idProp,
            id ->
                {
                TypeInfo     info = f_typeInception.ensureTypeInfo();
                PropertyInfo prop = info.findProperty(id);

                return prop == null
                        ? null
                        : new CallChain(info.getOptimizedSetChain(prop.getIdentity()));
                });
        }

    @Override
    public List<String> getFieldNames()
        {
        List<String> listNames = m_listNames;
        if (listNames == null)
            {
            Map<Object, TypeComposition> mapFields = m_mapFields;
            if (mapFields.isEmpty())
                {
                listNames = Collections.EMPTY_LIST;
                }
            else
                {
                listNames = new ArrayList<>(mapFields.size());
                for (Object nid : mapFields.keySet())
                    {
                    // disregard nested (private) and synthetic properties
                    if (nid instanceof String)
                        {
                        String sName = (String) nid;
                        if (sName.charAt(0) != '$' && !isAllowedUnassigned(sName) && !isLazy(sName))
                            {
                            listNames.add(sName);
                            }
                        }
                    }
                }
            m_listNames = listNames;
            }

        return listNames;
        }

    @Override
    public StringHandle[] getFieldNameArray()
        {
        StringHandle[] ashNames = m_ashFieldNames;
        if (ashNames == null)
            {
            List<String> listNames = getFieldNames();

            ashNames = new StringHandle[listNames.size()];

            int i = 0;
            for (String sName : listNames)
                {
                ashNames[i++] = xString.makeHandle(sName);
                }
            m_ashFieldNames = ashNames;
            }
        return ashNames;
        }

    @Override
    public ObjectHandle[] getFieldValueArray(GenericHandle hValue)
        {
        List<String> listNames = getFieldNames();
        if (listNames.isEmpty())
            {
            return Utils.OBJECTS_NONE;
            }

        ObjectHandle[] ahFields = new ObjectHandle[listNames.size()];

        int i = 0;
        for (String sName : listNames)
            {
            ahFields[i++] = hValue.getField(sName);
            }

        return ahFields;
        }

    @Override
    public Map<Object, ObjectHandle> initializeStructure()
        {
        Map<Object, TypeComposition> mapCached = m_mapFields;
        if (mapCached.isEmpty())
            {
            return null;
            }

        Map<Object, ObjectHandle> mapFields = new ListMap<>();
        for (Map.Entry<Object, TypeComposition> entry : mapCached.entrySet())
            {
            Object          nidProp = entry.getKey();
            TypeComposition clzRef  = entry.getValue();
            ObjectHandle    hValue  = null;
            if (clzRef != null)
                {
                hValue = ((VarSupport) clzRef.getSupport()).createRefHandle(null, clzRef, nidProp.toString());
                }
            mapFields.put(nidProp, hValue);
            }
        return mapFields;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the template registry
     */
    protected TemplateRegistry getRegistry()
        {
        return f_template.f_templates;
        }

    /**
     * @return the inception type (private access)
     */
    public TypeConstant getInceptionType()
        {
        return f_typeInception;
        }

    /**
     * @return true iff this TypeComposition represents an inception class
     */
    protected boolean isInception()
        {
        return this == f_clzInception;
        }

    /**
     * Create a map of fields that serves as a prototype for all instances of this class.
     */
    public synchronized void ensureFieldLayout()
        {
        if (m_mapFields != null)
            {
            return;
            }

        if (!f_template.isGenericHandle())
            {
            m_mapFields = Collections.EMPTY_MAP;
            return;
            }

        TypeConstant typePublic = f_typeInception.getUnderlyingType();
        if (typePublic instanceof PropertyClassTypeConstant)
            {
            m_mapFields = Collections.EMPTY_MAP;
            return;
            }

        ConstantPool pool       = typePublic.getConstantPool();
        TypeConstant typeStruct = pool.ensureAccessTypeConstant(typePublic, Access.STRUCT);
        TypeInfo     infoStruct = typeStruct.ensureTypeInfo();

        Map<Object, TypeComposition> mapFields = new ListMap<>();

        Map.Entry<PropertyConstant, PropertyInfo>[] aEntry =
                infoStruct.getProperties().entrySet().toArray(new Map.Entry[0]);

        if (aEntry.length > 1)
            {
            Arrays.sort(aEntry, RANKER);
            }

        for (Map.Entry<PropertyConstant, PropertyInfo> entry : aEntry)
            {
            PropertyConstant idProp   = entry.getKey();
            PropertyInfo     infoProp = entry.getValue();
            boolean          fField   = infoProp.hasField();

            if (fField && idProp.getNestedDepth() > 1)
                {
                IdentityConstant idParent = idProp.getParentConstant();
                switch (idParent.getFormat())
                    {
                    case Property:
                        if (!infoProp.getIdentity().getParentConstant().equals(idParent))
                            {
                            // the property is defined by the underlying type; currently those
                            // nested properties are stored in the corresponding Refs "box"
                            // REVIEW: consider having this helper at the PropertyInfo
                            continue;
                            }
                        break;

                    case Method:
                        break;
                    }
                }

            TypeComposition clzRef = null;
            if (infoProp.isRefAnnotated())
                {
                if (infoProp.isCustomLogic())
                    {
                    clzRef = new PropertyComposition(this, infoProp);
                    }
                else if (!infoProp.isSimpleUnassigned())
                    {
                    clzRef = f_template.f_templates.resolveClass(infoProp.getBaseRefType());
                    }

                if (clzRef != null)
                    {
                    AnyConstructor:
                    for (Annotation anno : infoProp.getRefAnnotations())
                        {
                        TypeInfo infoAnno = anno.getAnnotationType().ensureTypeInfo();
                        int      cArgs    = anno.getParams().length;

                        Set<MethodConstant> setConstrId = infoAnno.findMethods("construct", cArgs,
                                TypeInfo.MethodKind.Constructor);
                        for (MethodConstant idConstruct : setConstrId)
                            {
                            MethodStructure method = infoAnno.getMethodById(idConstruct).
                                getTopmostMethodStructure(infoAnno);

                            if (!method.isSynthetic() || !method.ensureCode().isNoOp())
                                {
                                // this will serve as a flag to call annotation constructors
                                // (see ClassTemplate.createPropertyRef() method)
                                clzRef = clzRef.ensureAccess(Access.STRUCT);
                                break AnyConstructor;
                                }
                            }
                        }
                    }
                }

            if (fField || clzRef != null)
                {
                mapFields.put(idProp.getNestedIdentity(), clzRef);
                }
            }

        m_mapFields = mapFields.isEmpty() ? Collections.EMPTY_MAP : mapFields;
        }

    /**
     * @return the compile-time type for a given property name
     */
    public TypeConstant getFieldType(String sProp)
        {
        PropertyInfo info = getInceptionType().ensureTypeInfo().findProperty(sProp);
        return info == null ? null : info.getType();
        }

    @Override
    public int hashCode()
        {
        return f_typeRevealed.hashCode();
        }

    @Override
    public boolean equals(Object obj)
        {
        // type compositions are singletons
        return this == obj;
        }

    @Override
    public String toString()
        {
        return f_typeRevealed.getValueString();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * The underlying {@link OpSupport} for the inception type.
     */
    private final OpSupport f_support;

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
     * The structure type for the inception type.
     */
    private final TypeConstant f_typeStructure;

    /**
     * The type that is revealed by the ObjectHandle that refer to this composition.
     */
    private final TypeConstant f_typeRevealed;

    /**
     * Template for class fields (values are either nulls or TypeComposition for refs).
     */
    private Map<Object, TypeComposition> m_mapFields;

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
    private final Map<TypeConstant, ClassComposition> f_mapCompositions;

    /**
     * A cache of derivative ProxyCompositions keyed by the "proxy type".
     */
    private final Map<TypeConstant, ProxyComposition> f_mapProxies;

    // cached method call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapMethods;

    // cached property getter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapGetters;

    // cached property setter call chain by property id (the top-most method first)
    private final Map<PropertyConstant, CallChain> f_mapSetters;

    // cached list of field names
    private List<String> m_listNames;

    // cached array of field name handles
    private StringHandle[] m_ashFieldNames;

    // cached auto-generated structure initializer
    private MethodStructure m_methodInit;

    /**
     * Rank comparator for new Map.Entry<PropertyConstant, PropertyInfo> objects.
     */
    public static Comparator<Map.Entry<PropertyConstant, PropertyInfo>> RANKER =
        Comparator.comparingInt(e -> e.getValue().getRank());
    }
