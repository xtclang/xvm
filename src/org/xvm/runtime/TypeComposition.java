package org.xvm.runtime;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.Ref;
import org.xvm.runtime.template.Ref.RefHandle;
import org.xvm.runtime.template.xOrdered;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String>).
 *
 * NOTE: methods that rely on the associated ClassTemplate must be overridden by
 *       the extended classes (UnionComposition, InterComposition and ConstComposition)
 *
 * @author gg 2017.02.23
 */
public class TypeComposition
    {
    public final ClassTemplate f_template;

    public final Map<String, Type> f_mapGenericActual; // corresponding to the template's GenericTypeName
    private final boolean f_fCanonical;

    private TypeComposition m_clzSuper;
    private Type m_typePublic;
    private Type m_typeProtected;
    private Type m_typePrivate;
    private Type m_typeStruct;

    // cached "declared" call chain
    private List<ClassTemplate> m_listDeclaredChain;

    // cached "default" call chain
    private List<ClassTemplate> m_listDefaultChain;

    // cached "full" call chain
    private List<ClassTemplate> m_listCallChain;

    // cached method call chain (the top-most method first)
    private Map<SignatureConstant, CallChain> m_mapMethods = new HashMap<>();

    // cached property getter call chain (the top-most method first)
    private Map<String, CallChain.PropertyCallChain> m_mapGetters = new HashMap<>();

    // cached property setter call chain (the top-most method first)
    private Map<String, CallChain.PropertyCallChain> m_mapSetters = new HashMap<>();

    // cached map of fields (values are always nulls)
    private Map<String, ObjectHandle> m_mapFields;

    public TypeComposition(ClassTemplate template, Map<String, Type> mapParamsActual, boolean fCanonical)
        {
        f_template = template;
        f_mapGenericActual = mapParamsActual;
        f_fCanonical = fCanonical;
        }

    public TypeComposition getSuper()
        {
        if (m_clzSuper != null)
            {
            return m_clzSuper;
            }

        ClassTemplate templateSuper = f_template.getSuper();
        if (templateSuper != null)
            {
            Map<StringConstant, TypeConstant> mapFormalTypes =
                    templateSuper.f_struct.getTypeParams();

            if (mapFormalTypes.isEmpty())
                {
                return templateSuper.f_clazzCanonical;
                }

            Map<String, Type> mapParams = new HashMap<>();
            for (Map.Entry<StringConstant, TypeConstant> entryFormal : mapFormalTypes.entrySet())
                {
                String sParamName = entryFormal.getKey().getValue();
                if (templateSuper.f_mapGenericFormal.containsKey(sParamName))
                    {
                    mapParams.put(sParamName, f_mapGenericActual.get(sParamName));
                    }
                }

            return m_clzSuper = templateSuper.ensureClass(mapParams);
            }

        return null;
        }

    public Type getActualParamType(String sName)
        {
        return f_mapGenericActual.get(sName);
        }

    public boolean isRoot()
        {
        return this == xObject.CLASS;
        }

    public List<ClassTemplate> getCallChain()
        {
        if (m_listCallChain != null)
            {
            return m_listCallChain;
            }

        List<ClassTemplate> listDeclared = collectDeclaredCallChain(true);

        TypeComposition clzSuper = getSuper();
        if (clzSuper == null)
            {
            // this is "Object"; it has no contribution
            return m_listCallChain = listDeclared;
            }

        List<ClassTemplate> listDefault = collectDefaultCallChain();
        if (listDefault.isEmpty())
            {
            return m_listCallChain = listDeclared;
            }

        List<ClassTemplate> listMerge = new LinkedList<>(listDeclared);
        addNoDupes(listDefault, listMerge, new HashSet<>(listDeclared));
        return m_listCallChain = listMerge;
        }

    // There are two parts on the call chain:
    //  1. The "declared" chain that consists of:
    //   1.1 declared methods on the encapsulating mixins and traits (recursively)
    //   1.2 methods implemented by the class
    //   1.3 declared methods on the incorporated mixins, traits and delegates (recursively)
    //   1.4 if the class belongs to a "built-in category" (e.g. Enum, Service, Const)
    //       declared methods for the category itself
    //   1.5 followed by the "declared" chain on the super class,
    //       unless the super is the root Object and "this" class is a mixin or a trait
    //
    // 2. The "default" chain that consists of:
    //   2.1 default methods on the interfaces that are declared by encapsulating
    //          mixins and traits (recursively)
    //   2.2 default methods on the interfaces implemented by the class (recursively)
    //   2.3 default methods on the interfaces that are declared by mixins, traits and delegates (recursively)
    //   2.4 followed by the "default" chain on the super class
    //
    //  @param fTop  true if this composition is the "top of the chain"
    protected List<ClassTemplate> collectDeclaredCallChain(boolean fTop)
        {
        if (m_listDeclaredChain != null)
            {
            return m_listDeclaredChain;
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper == null)
            {
            // this is "Object"; it has no contribution
            return m_listDeclaredChain = Collections.singletonList(f_template);
            }

        ClassStructure struct = f_template.f_struct;
        List<ClassTemplate> list = m_listDeclaredChain = new LinkedList<>();
        Set<ClassTemplate> set = new HashSet<>(); // to avoid duplicates

        // TODO: 1.1

        // 1.2
        list.add(f_template);

        Component.Format format = struct.getFormat();
        if (fTop && format == Component.Format.MIXIN)
            {
            // native mix-in (e.g. FutureRef)
            TypeConstant constInto =
                    Adapter.getContribution(struct, Component.Composition.Into);

            assert constInto != null;

            TypeComposition clzInto = f_template.f_types.resolveClass(constInto, f_mapGenericActual);

            addNoDupes(clzInto.collectDeclaredCallChain(false), list, set);
            }

        // 1.3
        for (Component.Contribution contribution : struct.getContributionsAsList())
            {
            switch (contribution.getComposition())
                {
                case Incorporates:
                    // TODO: how to detect a conditional incorporation?
                    TypeComposition clzContribution = resolveClass(contribution.getTypeConstant());
                    addNoDupes(clzContribution.collectDeclaredCallChain(false), list, set);
                    break;

                case Delegates:
                    // TODO:
                    break;
                }
            }

        // 1.4
        ClassTemplate templateCategory = f_template.f_templateCategory;
        if (templateCategory != null)
            {
            // all categories are non-generic
            list.add(templateCategory);
            }

        // 1.5
        if (!clzSuper.isRoot() ||
                !(format == Component.Format.MIXIN ||
                  format == Component.Format.TRAIT))
            {
            addNoDupes(clzSuper.collectDeclaredCallChain(false), list, set);
            }
        return list;
        }

    protected List<ClassTemplate> collectDefaultCallChain()
        {
        if (m_listDefaultChain != null)
            {
            return m_listDefaultChain;
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper == null)
            {
            // this is "Object"; it has no contribution
            return Collections.emptyList();
            }

        ClassStructure struct = f_template.f_struct;
        List<ClassTemplate> list = m_listDefaultChain = new LinkedList<>();
        Set<ClassTemplate> set = new HashSet<>(); // to avoid duplicates

        // TODO: 2.1

        // 2.2
        if (f_template.f_struct.getFormat() == Component.Format.INTERFACE)
            {
            list.add(f_template);
            set.add(f_template);
            }

        for (Component.Contribution contribution : struct.getContributionsAsList())
            {
            switch (contribution.getComposition())
                {
                case Incorporates:
                    // TODO: how to detect a conditional incorporation?
                case Delegates:
                    TypeComposition clzContribution = resolveClass(contribution.getTypeConstant());
                    addNoDupes(clzContribution.collectDefaultCallChain(), list, set);
                    break;
                }
            }

        // 2.3
        for (Component.Contribution contribution : struct.getContributionsAsList())
            {
            switch (contribution.getComposition())
                {
                case Implements:
                    TypeComposition clzContribution = resolveClass(contribution.getTypeConstant());
                    addNoDupes(clzContribution.collectDefaultCallChain(), list, set);
                    break;
                }
            }

        // 2.4
        addNoDupes(clzSuper.collectDefaultCallChain(), list, set);
        return list;
        }

    private void addNoDupes(List<ClassTemplate> listFrom, List<ClassTemplate> listTo,
                            Set<ClassTemplate> setDupes)
        {
        for (ClassTemplate template : listFrom)
            {
            if (setDupes.add(template))
                {
                listTo.add(template);
                }
            }
        }

    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.f_clazz == this;

        Type typeCurrent = handle.m_type;
        Type typeTarget;

        switch (access)
            {
            case PUBLIC:
                typeTarget = ensurePublicType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case PROTECTED:
                typeTarget = ensureProtectedType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case PRIVATE:
                typeTarget = ensurePrivateType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case STRUCT:
                typeTarget = ensureStructType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            default:
                throw new IllegalStateException();
            }

        handle = handle.cloneHandle();
        handle.m_type = typeTarget;
        return handle;
        }

    public synchronized Type ensurePublicType()
        {
        Type type = m_typePublic;
        if (type == null)
            {
            m_typePublic = type = f_template.f_types.createType(this, Access.PUBLIC);
            }
        return type;
        }
    public synchronized Type ensureProtectedType()
        {
        Type type = m_typeProtected;
        if (type == null)
            {
            m_typeProtected = type = f_template.f_types.createType(this, Access.PROTECTED);
            }
        return type;
        }

    public synchronized Type ensurePrivateType()
        {
        Type type = m_typePrivate;
        if (type == null)
            {
            m_typePrivate = type = f_template.f_types.createType(this, Access.PRIVATE);
            }
        return type;
        }

    public synchronized Type ensureStructType()
        {
        Type type = m_typeStruct;
        if (type == null)
            {
            m_typeStruct = type = f_template.f_types.createType(this, Access.STRUCT);
            }
        return type;
        }

    public boolean isStruct(Type type)
        {
        return type == m_typeStruct;
        }

    // is this public interface of this class assignable to the specified class
    public boolean isA(TypeComposition that)
        {
        // check the most common (and cheap) case
        if (f_mapGenericActual.isEmpty() &&
                this.f_template.extends_(that.f_template))
            {
            return true;
            }

        // go the long way
        return this.ensurePublicType().isA(that.ensurePublicType());
        }

    // given a TypeConstant, return a corresponding TypeComposition within this class's context
    // or Object.class if the type cannot be resolved;
    //
    // for example, List<KeyType> in the context of Map<String, Int> will resolve in List<String>
    //
    // Note: this impl is almost identical to TypeSet.resolveParameterType()
    //       but returns TypeComposition rather than Type and is more tolerant
    public TypeComposition resolveClass(TypeConstant constType)
        {
        return f_template.f_types.resolveClass(constType, f_mapGenericActual);
        }

    // retrieve the actual type for the specified formal parameter name
    public Type getActualType(String sFormalName)
        {
        Type type = f_mapGenericActual.get(sFormalName);
        if (type == null)
            {
            // TODO: check the super class?

            throw new IllegalArgumentException(
                    "Invalid formal name: " + sFormalName + " for " + this);
            }
        return type;
        }

    // create a sequence of frames to be called in the inverse order (the base super first)
    public Frame callDefaultConstructors(Frame frame, ObjectHandle hStruct, ObjectHandle[] ahVar,
                                         Frame.Continuation continuation)
        {
        CallChain chain = getMethodCallChain(Utils.SIG_DEFAULT, Access.PUBLIC);

        int cMethods = chain.getDepth();
        if (cMethods == 0)
            {
            return null;
            }

        Frame frameBase = frame.f_context.createFrame1(frame, chain.getMethod(cMethods - 1),
            hStruct, ahVar, Frame.RET_UNUSED);

        if (cMethods > 1)
            {
            int[] holder = new int[]{cMethods - 1};
            Frame.Continuation cont = new Frame.Continuation()
                {
                public int proceed(Frame frameCaller)
                    {
                    int i = --holder[0];
                    if(i > 0)
                        {
                        frameCaller.call1(chain.getMethod(i), hStruct, ahVar, Frame.RET_UNUSED);
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;
                        }
                    else
                        {
                        return continuation.proceed(frameCaller);
                        }
                    }
                };
            frameBase.setContinuation(cont);
            }
        else
            {
            frameBase.setContinuation(continuation);
            }
        return frameBase;
        }

    // retrieve the call chain for the specified method
    public CallChain getMethodCallChain(SignatureConstant constSignature, Access access)
        {
        return m_mapMethods.computeIfAbsent(constSignature,
            sig -> collectMethodCallChain(sig, access));
        }

    // find a matching method and add to the list
    protected CallChain collectMethodCallChain(SignatureConstant constSignature, Access access)
        {
        List<MethodStructure> list = new LinkedList<>();

        nextInChain:
        for (ClassTemplate template : getCallChain())
            {
            MultiMethodStructure mms = (MultiMethodStructure)
                template.f_struct.getChild(constSignature.getName());
            if (mms != null)
                {
                for (MethodStructure method : mms.methods())
                    {
                    if (method.getAccess().compareTo(access) <= 0 &&
                        method.isCallableFor(constSignature, this))
                        {
                        list.add(method);

                        if (!method.isSuperCalled())
                            {
                            break nextInChain;
                            }
                        // no need to check other methods; it would be an error anyway...
                        break;
                        }

                    if (false) // debug only
                        {
                        System.out.println("non-match: \nprovided: " + constSignature +
                                "\n found: " + method.getIdentityConstant().getSignature());
                        }
                    }
                }
            }
        return new CallChain(list);
        }

    public CallChain.PropertyCallChain getPropertyGetterChain(String sProperty)
        {
        return m_mapGetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, true));
        }

    public CallChain.PropertyCallChain getPropertySetterChain(String sProperty)
        {
        return m_mapSetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, false));
        }

    protected CallChain.PropertyCallChain collectPropertyCallChain(String sPropName, boolean fGetter)
        {
        PropertyStructure propertyBase = null;
        List<MethodStructure> list = new LinkedList<>();

        for (ClassTemplate template : getCallChain())
            {
            PropertyStructure property = template.getProperty(sPropName);
            if (property != null)
                {
                MultiMethodStructure mms = (MultiMethodStructure) property.getChild(
                        fGetter ? "get" : "set");
                if (mms != null)
                    {
                    // TODO: compare the signature; see ClassTemplate#getDeclaredMethod
                    MethodStructure method = mms.methods().get(0);

                    list.add(method);

                    if (!method.isSuperCalled())
                        {
                        break;
                        }
                    }

                if (template.isStateful())
                    {
                    propertyBase = property;
                    }
                }
            }

        if (propertyBase == null)
            {
            throw new IllegalStateException("Class " + this + " missing property " + sPropName);
            }

        return new CallChain.PropertyCallChain(list, propertyBase, fGetter);
        }

    // retrieve the property structure for the specified property
    // for any of the structures in the inheritance tree
    public PropertyStructure getProperty(String sPropName)
        {
        PropertyStructure property = f_template.getProperty(sPropName);
        if (property != null)
            {
            return property;
            }

        ClassTemplate templateCategory = f_template.f_templateCategory;
        if (templateCategory != null)
            {
            property = templateCategory.getProperty(sPropName);
            if (property != null)
                {
                return property;
                }
            }

        // TODO: check the interfaces, traits and mix-ins

        TypeComposition clzSuper = getSuper();
        return clzSuper == null ? null : clzSuper.getProperty(sPropName);
        }

    // return the set of field names
    public Set<String> getFieldNames()
        {
        return m_mapFields.keySet();
        }

    // create unassigned (with a null value) entries for all fields
    protected void createFields(Map<String, ObjectHandle> mapFields)
        {
        Map mapCached = m_mapFields;
        if (mapCached != null)
            {
            mapFields.putAll(mapCached);
            return;
            }

        m_mapFields = mapCached = new HashMap<>();

        for (ClassTemplate template : collectDeclaredCallChain(true))
            {
            for (Component child : template.f_struct.children())
                {
                if (child instanceof PropertyStructure)
                    {
                    PropertyStructure prop = (PropertyStructure) child;

                    RefHandle hRef = null;
                    if (template.isRef(prop))
                        {
                        Ref referent = (Ref) template.getRefTemplate(prop);

                        hRef = referent.createRefHandle(referent.f_clazzCanonical, null);
                        }

                    if (template.isCalculated(prop))
                        {
                        // compensate for the lack of "isDeclaredAtThisLevel" API
                        mapCached.remove(prop.getName());
                        }
                    else
                        {
                        mapCached.put(prop.getName(), hRef);
                        }
                    }
                }
            }

        mapFields.putAll(mapCached);
        }

    /**
     * Determine if this class consumes a formal type with the specified name.
     */
    public boolean consumesFormalType(String sName, Access access)
        {
        for (ClassTemplate template : getCallChain())
            {
            if (!template.consumesFormalType(sName, access))
                {
                return false;
                }
            }
        return true;
        }

    /**
     * Determine if this class produces a formal type with the specified name.
     */
    public boolean producesFormalType(String sName, Access access)
        {
        for (ClassTemplate template : getCallChain())
            {
            if (!template.producesFormalType(sName, access))
                {
                return false;
                }
            }
        return true;
        }

    // ---- support for op-codes that require class specific information -----

    // compare for equality (==) two object handles that both belong to this class
    // return R_NEXT or R_EXCEPTION
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            frame.assignValue(iReturn, xBoolean.TRUE);
            return Op.R_NEXT;
            }
        return f_template.callEquals(frame, this, hValue1, hValue2, iReturn);
        }

    // compare for order (<=>) two object handles that both belong to this class
    // return R_NEXT or R_EXCEPTION
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            frame.assignValue(iReturn, xOrdered.EQUAL);
            return Op.R_NEXT;
            }
        return f_template.callCompare(frame, this, hValue1, hValue2, iReturn);
        }

    @Override
    public int hashCode()
        {
        return f_template.f_sName.hashCode();
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
        return f_template.f_struct.getIdentityConstant().getPathString() +
                Utils.formatArray(f_mapGenericActual.values().toArray(), "<", ">", ", ");
        }
    }
