package org.xvm.proto;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.ClassTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UnresolvedTypeConstant;

import org.xvm.proto.template.xObject;
import org.xvm.proto.template.Ref;
import org.xvm.proto.template.Ref.RefHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private Map<MethodConstant, List<MethodStructure>> m_mapMethods = new HashMap<>();

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
                if (templateSuper.f_listGenericParams.contains(sParamName))
                    {
                    mapParams.put(sParamName, f_mapGenericActual.get(sParamName));
                    }
                }

            return m_clzSuper = templateSuper.ensureClass(mapParams);
            }

        return null;
        }

    public List<ClassTemplate> getCallChain()
        {
        if (m_listCallChain != null)
            {
            return m_listCallChain;
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper == null)
            {
            // this is "Object"; it has no contribution
            return m_listCallChain = collectDeclaredCallChain();
            }

        List<ClassTemplate> list = m_listCallChain = new LinkedList<>(collectDeclaredCallChain());

        addNoDupes(collectDefaultCallChain(), list, new HashSet<>(list));
        return list;
        }

    // There are two parts on the call chain:
    //  1. The "declared" chain that consists of:
    //   1.1 declared methods on the encapsulating mixins and traits (recursively)
    //   1.2 methods implemented by the class
    //   1.3 declared methods on the incorporated mixins, traits and delegates (recursively)
    //   1.4 if the class belongs to a "built-in category" (e.g. Enum, Service, Const)
    //       declared methods for the category itself
    //   1.5 followed by the "declared" chain on the super class
    //
    // 2. The "default" chain that consists of:
    //   2.1 default methods on the interfaces that are declared by encapsulating
    //          mixins and traits (recursively)
    //   2.2 default methods on the interfaces implemented by the class (recursively)
    //   2.3 default methods on the interfaces that are declared by mixins, traits and delegates (recursively)
    //   2.4 followed by the "default" chain on the super class
    protected List<ClassTemplate> collectDeclaredCallChain()
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

        // 1.3
        for (Component.Contribution contribution : struct.getContributionsAsList())
            {
            switch (contribution.getComposition())
                {
                case Incorporates:
                    // TODO: how to detect a conditional incorporation?
                case Delegates:
                    TypeComposition clzContribution = resolveClass(contribution.getClassConstant());
                    addNoDupes(clzContribution.collectDeclaredCallChain(), list, set);
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
        addNoDupes(clzSuper.collectDeclaredCallChain(), list, set);
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
                    TypeComposition clzContribution = resolveClass(contribution.getClassConstant());
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
                    TypeComposition clzContribution = resolveClass(contribution.getClassConstant());
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

    public ObjectHandle ensureAccess(ObjectHandle handle, Constants.Access access)
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
            m_typePublic = type = f_template.f_types.createType(this, Constants.Access.PUBLIC);
            }
        return type;
        }
    public synchronized Type ensureProtectedType()
        {
        Type type = m_typeProtected;
        if (type == null)
            {
            m_typeProtected = type = f_template.f_types.createType(this, Constants.Access.PROTECTED);
            }
        return type;
        }

    public synchronized Type ensurePrivateType()
        {
        Type type = m_typePrivate;
        if (type == null)
            {
            m_typePrivate = type = f_template.f_types.createType(this, Constants.Access.PRIVATE);
            }
        return type;
        }

    public synchronized Type ensureStructType()
        {
        Type type = m_typeStruct;
        if (type == null)
            {
            m_typeStruct = type = f_template.f_types.createType(this, Constants.Access.STRUCT);
            }
        return type;
        }

    public boolean isStruct(Type type)
        {
        return type == m_typeStruct;
        }

    // does this class extend that?
    public boolean extends_(TypeComposition that)
        {
        assert that.f_template.f_struct.getFormat() != Component.Format.INTERFACE;

        if (this.f_template.extends_(that.f_template))
            {
            // TODO: check the generic type relationship
            return true;
            }

        return false;
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
        if (constType instanceof UnresolvedTypeConstant)
            {
            constType = ((UnresolvedTypeConstant) constType).getResolvedConstant();
            }

        if (constType instanceof ClassTypeConstant)
            {
            ClassTypeConstant constClass = (ClassTypeConstant) constType;
            ClassTemplate template = f_template.f_types.getTemplate(constClass.getClassConstant());
            return template.resolveClass(constClass, f_mapGenericActual);
            }

        if (constType instanceof ParameterTypeConstant)
            {
            ParameterTypeConstant constParam = (ParameterTypeConstant) constType;
            Type type = f_mapGenericActual.get(constParam.getName());
            if (type != null && type.f_clazz != null)
                {
                return type.f_clazz;
                }
            }

        return xObject.CLASS;
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
        ClassTemplate template = f_template;

        // TODO: there must be a better way to create a MethodIdConst
        MethodStructure method = template.f_types.f_adapter.getMethod(template, "default",
                ClassTemplate.VOID, ClassTemplate.VOID);
        if (method == null)
            {
            return null;
            }

        MethodConstant constDefault = method.getIdentityConstant();
        List<MethodStructure> listMethods =
                collectMethodCallChain(constDefault, new ArrayList<>());

        int cMethods = listMethods.size();
        if (cMethods == 0)
            {
            return null;
            }

        Frame frameBase = frame.f_context.createFrame1(frame, listMethods.get(cMethods - 1),
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
                        frameCaller.call1(listMethods.get(i), hStruct, ahVar, Frame.RET_UNUSED);
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
    // TODO: replace MethodConstant with MethodIdConstant
    public List<MethodStructure> getMethodCallChain(MethodConstant constMethod)
        {
        return m_mapMethods.computeIfAbsent(constMethod,
                cm -> collectMethodCallChain(cm, new LinkedList<>()));
        }

    // resolve the super call chain for the specified method
    public MethodStructure resolveSuper(MethodStructure method)
        {
        MethodConstant constMethod = method.getIdentityConstant();

        List<MethodStructure> listMethods = m_mapMethods.computeIfAbsent(constMethod, cm ->
            {
            Component parent = method.getParent().getParent();
            return parent instanceof PropertyStructure ?
                    collectAccessorCallChain(parent.getName(), cm, new LinkedList<>()) :
                    collectMethodCallChain(cm, new LinkedList<>());
            });

        for (int i = 0, c = listMethods.size(); i < c - 1; i++)
            {
            if (listMethods.get(i).equals(method))
                {
                return listMethods.get(i + 1);
                }
            }
        return null;
        }

    // find a matching method and add to the list
    // TODO: replace MethodConstant with MethodIdConstant
    protected List<MethodStructure> collectMethodCallChain(
            MethodConstant constMethod, List<MethodStructure> list)
        {
        for (ClassTemplate template : getCallChain())
            {
            MethodStructure method = template.getDeclaredMethod(constMethod);
            if (method != null)
                {
                list.add(method);
                }
            }
        return list;
        }

    // find a matching property accessor and add to the list
    // TODO: replace MethodConstant with MethodIdConstant
    protected List<MethodStructure> collectAccessorCallChain(
            String sPropName, MethodConstant constMethod, List<MethodStructure> list)
        {
        for (ClassTemplate template : getCallChain())
            {
            PropertyStructure property = template.getProperty(sPropName);
            if (property != null)
                {
                MultiMethodStructure mms = (MultiMethodStructure) property.getChild(constMethod.getName());
                if (mms != null)
                    {
                    // TODO: compare the signature; see ClassTemplate#getDeclaredMethod
                    list.add((MethodStructure) mms.children().get(0));
                    }
                }
            }

        return list;
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

        ClassTemplate template = f_template;

        while (template != null)
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

                    if (!template.isCalculated(prop) || hRef != null)
                        {
                        mapCached.put(prop.getName(), hRef);
                        }
                    }
                }

            ClassTemplate templateCategory = template.f_templateCategory;
            if (templateCategory != null)
                {
                // the categories are not generic; no reason to resolve
                templateCategory.f_clazzCanonical.createFields(mapCached);
                }

            // TODO: process the mix-ins

            template = template.getSuper();
            }

        mapFields.putAll(mapCached);
        }

    // ---- support for op-codes that require class specific information -----

    // compare for equality (==) two object handles that both belong to this class
    // return R_NEXT or R_EXCEPTION
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return f_template.callEquals(frame, this, hValue1, hValue2, iReturn);
        }

    // compare for order (<=>) two object handles that both belong to this class
    // return R_NEXT or R_EXCEPTION
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
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
