package org.xvm.runtime;


import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;

import org.xvm.util.ListMap;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String> or
 * @Range Interval<Date>).
 */
public class ClassComposition
        implements TypeComposition
    {
    /**
     * Construct the ClassComposition for a given "inception" type and a "revealed" type.
     *
     * The guarantees for the inception type are:
     *  - it has to be a class (TypeConstant.isClass())
     *  - it cannot be abstract
     *  - the only modifying types that are allowed are AnnotatedTypeConstant(s) and
     *    ParameterizedTypeConstant(s)
     *
     * @param support        the OpSupport implementation for the inception type
     * @param typeInception  the "origin type"
     * @param typeRevealed   the type to reveal an ObjectHandle reference to this class as
     */
    protected ClassComposition(OpSupport support, TypeConstant typeInception, TypeConstant typeRevealed)
        {
        assert typeInception.isSingleDefiningConstant();
        assert typeInception.getAccess() == Access.PUBLIC;

        ClassTemplate template = support.getTemplate(typeInception);
        if (support instanceof ClassTemplate)
            {
            support = template;
            }

        ConstantPool pool = typeInception.getConstantPool();

        f_clzInception = this;
        f_support = support;
        f_template = template;
        f_typeInception = pool.ensureAccessTypeConstant(typeInception, Access.PRIVATE);
        f_typeStructure = pool.ensureAccessTypeConstant(typeInception, Access.STRUCT);
        f_typeRevealed = typeRevealed;
        f_mapCompositions = new ConcurrentHashMap<>();
        f_mapMethods = new ConcurrentHashMap<>();
        f_mapGetters = new ConcurrentHashMap<>();
        f_mapSetters = new ConcurrentHashMap<>();
        f_mapFields  = f_template.isGenericHandle() ? createFieldLayout() : null;
        }

    /**
     * Construct a ClassComposition clone for the specified revealed type.
     */
    private ClassComposition(ClassComposition clzInception, TypeConstant typeRevealed)
        {
        f_clzInception = clzInception;
        f_support = clzInception.f_support;
        f_template = clzInception.f_template;
        f_typeInception = clzInception.f_typeInception;
        f_typeStructure = clzInception.f_typeStructure;
        f_typeRevealed = typeRevealed;
        f_mapCompositions = f_clzInception.f_mapCompositions;
        f_mapMethods = f_clzInception.f_mapMethods;
        f_mapGetters = f_clzInception.f_mapGetters;
        f_mapSetters = f_clzInception.f_mapSetters;
        f_mapFields = f_clzInception.f_mapFields;
        }

    /**
     * @return true iff this class represents an instance inner class
     */
    public boolean isInstanceChild()
        {
        return f_template.f_struct.isInstanceChild();
        }

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
    public ClassComposition maskAs(TypeConstant type)
        {
        if (type.equals(f_typeRevealed))
            {
            return this;
            }

        if (!f_typeRevealed.isA(type))
            {
            throw new IllegalArgumentException("Type " + f_typeRevealed + " cannot be widened to " + type);
            }

        return f_mapCompositions.computeIfAbsent(type, typeR -> new ClassComposition(this, typeR));
        }

    @Override
    public ClassComposition revealAs(TypeConstant type, Container container)
        {
        // TODO: this is only allowed within the container that created the original TypeComposition

        if (type.equals(f_typeRevealed))
            {
            return this;
            }

        if (!f_typeInception.isA(type))
            {
            throw new IllegalArgumentException("Type " + f_typeInception + " cannot be revealed as " + type);
            }

        return f_mapCompositions.computeIfAbsent(type, typeR -> new ClassComposition(this, typeR));
        }

    @Override
    public ObjectHandle ensureOrigin(ObjectHandle handle)
        {
        assert handle.getComposition() == this;

        return isInception() ? handle : handle.cloneAs(f_clzInception);
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
    public boolean isService()
        {
        TypeConstant type = f_typeInception;
        return type.getSingleUnderlyingClass(false).getComponent().getFormat() == Format.SERVICE;
        }

    @Override
    public boolean isConst()
        {
        TypeConstant type = f_typeInception;
        return ((ClassStructure) type.getSingleUnderlyingClass(false).getComponent()).isConst();
        }

    @Override
    public MethodStructure ensureAutoInitializer()
        {
        if (f_mapFields == null)
            {
            return null;
            }

        MethodStructure method = m_methodInit;
        if (method == null)
            {
            m_methodInit = method = f_template.f_struct.createInitializer(f_typeStructure, f_mapFields);
            }
        return method.isAbstract() ? null : method;
        }

    @Override
    public boolean isInflated(Object nid)
        {
        return f_mapFields != null && f_mapFields.get(nid) != null;
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
    public CallChain getMethodCallChain(SignatureConstant signature)
        {
        // we only cache the PUBLIC access chains; all others are cached at the op-code level
        return f_mapMethods.computeIfAbsent(signature,
            sig ->
                {
                TypeInfo info = isStruct()
                        ? f_typeStructure.ensureTypeInfo()
                        : f_typeInception.ensureTypeInfo();
                return new CallChain(info.getOptimizedMethodChain(sig));
                }
            );
        }

    @Override
    public CallChain getPropertyGetterChain(Object nidProp)
        {
        return f_mapGetters.computeIfAbsent(nidProp,
            nid ->
                {
                TypeInfo     info = f_typeInception.ensureTypeInfo();
                PropertyInfo prop = info.findPropertyByNid(nid);

                return new CallChain(info.getOptimizedGetChain(prop.getIdentity()));
                });
        }

    @Override
    public CallChain getPropertySetterChain(Object nidProp)
        {
        return f_mapSetters.computeIfAbsent(nidProp,
            nid ->
                {
                TypeInfo     info = f_typeInception.ensureTypeInfo();
                PropertyInfo prop = info.findPropertyByNid(nid);

                return new CallChain(info.getOptimizedSetChain(prop.getIdentity()));
                });
        }

    @Override
    public Set<String> getFieldNames()
        {
        Set<String> setNames = m_setNames;
        if (setNames == null)
            {
            Map<Object, TypeComposition> mapFields = f_mapFields;
            if (mapFields == null)
                {
                setNames = Collections.EMPTY_SET;
                }
            else
                {
                setNames = new HashSet<>(mapFields.size());
                for (Object nid : mapFields.keySet())
                    {
                    // disregard nested (private) properties
                    if (nid instanceof String)
                        {
                        setNames.add((String) nid);
                        }
                    }
                }
            m_setNames = setNames;
            }

        return setNames;
        }

    @Override
    public StringHandle[] getFieldNameArray()
        {
        StringHandle[] ashNames = m_ashFieldNames;
        if (ashNames == null)
            {
            Set<String> setNames = getFieldNames();

            ashNames = new StringHandle[setNames.size()];

            int i = 0;
            for (String sName : setNames)
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
        Set<String> setNames = getFieldNames();
        if (setNames.isEmpty())
            {
            return Utils.OBJECTS_NONE;
            }

        ObjectHandle[] ahFields = new ObjectHandle[setNames.size()];

        int i = 0;
        for (String sName : setNames)
            {
            ahFields[i++] = hValue.getField(sName);
            }

        return ahFields;
        }

    @Override
    public Map<Object, ObjectHandle> initializeStructure()
        {
        Map<Object, TypeComposition> mapCached = f_mapFields;
        if (mapCached == null)
            {
            return null;
            }

        Map<Object, ObjectHandle> mapFields = new ListMap<>();
        for (Map.Entry<Object, TypeComposition> entry : f_mapFields.entrySet())
            {
            Object          nidProp = entry.getKey();
            TypeComposition clzRef  = entry.getValue();
            ObjectHandle    hValue  = null;
            if (clzRef != null)
                {
                hValue = ((VarSupport) clzRef.getSupport()).createRefHandle(clzRef, nidProp.toString());
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
    protected TypeConstant getInceptionType()
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
     *
     * @return a prototype map
     */
    private Map<Object, TypeComposition> createFieldLayout()
        {
        ConstantPool pool = f_typeInception.getConstantPool();

        TypeConstant typePublic = f_typeInception.getUnderlyingType();
        if (typePublic instanceof PropertyClassTypeConstant)
            {
            return null;
            }

        TypeConstant typeStruct = pool.ensureAccessTypeConstant(typePublic, Access.STRUCT);
        TypeInfo     infoStruct = typeStruct.ensureTypeInfo();

        Map<Object, TypeComposition> mapFields = new ListMap<>();
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : infoStruct.getProperties().entrySet())
            {
            PropertyConstant idProp   = entry.getKey();
            PropertyInfo     infoProp = entry.getValue();

            if (infoProp.hasField())
                {
                TypeComposition clzRef = null;
                if (infoProp.isRefAnnotated())
                    {
                    if (infoProp.isCustomLogic())
                        {
                        clzRef = new PropertyComposition(this, infoProp);
                        }
                    else
                        {
                        clzRef = f_template.f_templates.resolveClass(infoProp.getBaseRefType());
                        }
                    }

                mapFields.put(idProp.getNestedIdentity(), clzRef);
                }
            else
                {
                // TODO: what if the prop is annotated
                assert !infoProp.isRefAnnotated();
                }
            }
        return mapFields.isEmpty() ? null : mapFields;
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
    private final Map<Object, TypeComposition> f_mapFields;

    /**
     * A cache of derivative TypeCompositions keyed by the "revealed type".
     */
    private final Map<TypeConstant, ClassComposition> f_mapCompositions;

    // cached method call chain (the top-most method first)
    private final Map<SignatureConstant, CallChain> f_mapMethods;

    // cached property getter call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapGetters;

    // cached property setter call chain by nid (the top-most method first)
    private final Map<Object, CallChain> f_mapSetters;

    // cached set of field names
    private Set<String> m_setNames;

    // cached array of field name handles
    private StringHandle[] m_ashFieldNames;

    // cached auto-generated structure initializer
    private MethodStructure m_methodInit;
    }
