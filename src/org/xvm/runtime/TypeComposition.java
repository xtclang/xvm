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
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain.PropertyCallChain;

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
 */
public class TypeComposition
        implements TypeConstant.GenericTypeResolver
    {
    public final ClassTemplate f_template;

    public final TypeConstant f_typeActual;

    private TypeComposition m_clzSuper;
    private TypeConstant m_typePublic;
    private TypeConstant m_typeProtected;
    private TypeConstant m_typePrivate;
    private TypeConstant m_typeStruct;

    // cached "declared" call chain
    private List<TypeComposition> m_listDeclaredChain;

    // cached "default" call chain
    private List<TypeComposition> m_listDefaultChain;

    // cached "full" call chain
    private List<TypeComposition> m_listCallChain;

    // cached method call chain (the top-most method first)
    private Map<SignatureConstant, CallChain> m_mapMethods = new HashMap<>();

    // cached property getter call chain (the top-most method first)
    private Map<String, PropertyCallChain> m_mapGetters = new HashMap<>();

    // cached property setter call chain (the top-most method first)
    private Map<String, PropertyCallChain> m_mapSetters = new HashMap<>();

    // cached map of fields (values are always nulls)
    private Map<String, ObjectHandle> m_mapFields;

    public TypeComposition(ClassTemplate template, TypeConstant typeActual)
        {
        if (typeActual.isParamsSpecified())
            {
            ClassStructure struct = template.f_struct;
            assert typeActual.getParamTypesArray().length ==
                        struct.getTypeParams().size() ||
                   struct.getIdentityConstant().equals(
                       struct.getConstantPool().clzTuple());
            }

        switch (typeActual.getAccess())
            {
            case STRUCT:
                m_typeStruct = typeActual;
                break;

            case PUBLIC:
                m_typePublic = typeActual;
                break;

            case PROTECTED:
                m_typeProtected = typeActual;
                break;

            case PRIVATE:
                m_typePrivate = typeActual;
                break;
            }

        f_template = template;
        f_typeActual = typeActual;
        }

    protected TypeComposition getSuper()
        {
        if (m_clzSuper != null)
            {
            return m_clzSuper;
            }

        ClassTemplate templateSuper = f_template.getSuper();
        if (templateSuper == null)
            {
            return null;
            }

        ClassStructure structClz      = f_template.f_struct;
        Contribution   contribExtends = structClz.findContribution(Composition.Extends);
        if (contribExtends != null)
            {
            TypeConstant typeActual = f_typeActual;
            if (typeActual.isParamsSpecified())
                {
                List<TypeConstant> listActual =
                    contribExtends.transformActualTypes(structClz, typeActual.getParamTypes());

                TypeConstant typeSuper = templateSuper.f_struct.resolveType(listActual);

                return m_clzSuper = templateSuper.ensureClass(typeSuper);
                }
            }

        return m_clzSuper = templateSuper.f_clazzCanonical;
        }

    /**
     * Find the type for the specified formal parameter. Note that the formal name could be declared
     * by some contributions, rather than this class itself.
     *
     * @param sName  the formal parameter name
     *
     * @return the corresponding actual type
     */
    public TypeConstant getActualParamType(String sName)
        {
        TypeConstant type = f_template.f_struct.
            getActualParamType(sName, f_typeActual.getParamTypes());

        if (type == null)
            {
            throw new IllegalArgumentException(
                "Invalid formal name: " + sName + " for " + this);
            }
        return type;
        }

    @Override
    public TypeConstant resolveGenericType(PropertyConstant constProperty)
        {
        return getActualParamType(constProperty.getName());
        }

    public boolean isRoot()
        {
        return this == xObject.CLASS;
        }

    public List<TypeComposition> getCallChain()
        {
        if (m_listCallChain != null)
            {
            return m_listCallChain;
            }

        List<TypeComposition> listDeclared = collectDeclaredCallChain(true);

        TypeComposition clzSuper = getSuper();
        if (clzSuper == null)
            {
            // this is "Object"; it has no contribution
            return m_listCallChain = listDeclared;
            }

        List<TypeComposition> listDefault = collectDefaultCallChain();
        if (listDefault.isEmpty())
            {
            return m_listCallChain = listDeclared;
            }

        List<TypeComposition> listMerge = new LinkedList<>(listDeclared);
        addNoDupes(listDefault, listMerge, new HashSet<>(listDeclared));
        return m_listCallChain = listMerge;
        }

    // There are two parts on the call chain:
    //  1. The "declared" chain that consists of:
    //   1.1 declared methods on the encapsulating mixins (recursively)
    //   1.2 methods implemented by the class
    //   1.3 declared methods on the incorporated mixins and delegates (recursively)
    //   1.4 if the class belongs to a "built-in category" (e.g. Enum, Service, Const)
    //       declared methods for the category itself
    //   1.5 followed by the "declared" chain on the super class,
    //       unless the super is the root Object and "this" class is a mixin
    //
    // 2. The "default" chain that consists of:
    //   2.1 default methods on the interfaces that are declared by encapsulating
    //       mixins (recursively)
    //   2.2 default methods on the interfaces implemented by the class, or that are declared by
    //       mixins and delegates (recursively)
    //   2.3 followed by the "default" chain on the super class
    //
    //  @param fTop  true if this composition is the "top of the chain"
    protected List<TypeComposition> collectDeclaredCallChain(boolean fTop)
        {
        if (m_listDeclaredChain != null)
            {
            return m_listDeclaredChain;
            }

        TypeComposition clzSuper = getSuper();
        if (clzSuper == null)
            {
            // this is "Object"; it has no contribution
            return m_listDeclaredChain = Collections.singletonList(this);
            }

        ClassStructure structThis = f_template.f_struct;
        List<TypeComposition> list = new LinkedList<>();
        Set<TypeComposition> set = new HashSet<>(); // to avoid duplicates

        // TODO: 1.1

        // 1.2
        list.add(this);

        Component.Format format = structThis.getFormat();
        if (fTop && format == Component.Format.MIXIN)
            {
            // native mix-in (e.g. FutureVar)
            Contribution contribInto = structThis.findContribution(Composition.Into);

            assert contribInto != null;

            TypeConstant typeInto = contribInto.resolveGenerics(this);

            TypeComposition clzInto = f_template.f_types.resolveClass(typeInto);

            addNoDupes(clzInto.collectDeclaredCallChain(false), list, set);
            }

        // 1.3
        for (Contribution contrib : structThis.getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Incorporates:
                    TypeConstant typeInto = contrib.resolveGenerics(this);
                    if (typeInto != null)
                        {
                        TypeComposition clzContribution = resolveClass(typeInto);
                        addNoDupes(clzContribution.collectDeclaredCallChain(false), list, set);
                        }
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
            list.add(templateCategory.f_clazzCanonical);
            }

        // 1.5
        if (!clzSuper.isRoot() || format != Component.Format.MIXIN)
            {
            addNoDupes(clzSuper.collectDeclaredCallChain(false), list, set);
            }

        return m_listDeclaredChain = list;
        }

    protected List<TypeComposition> collectDefaultCallChain()
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
        List<TypeComposition> list = new LinkedList<>();
        Set<TypeComposition> set = new HashSet<>(); // to avoid duplicates

        // TODO: 2.1

        // 2.2
        if (f_template.f_struct.getFormat() == Component.Format.INTERFACE)
            {
            list.add(this);
            set.add(this);
            }

        for (Contribution contrib : struct.getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Incorporates:
                case Implements:
                case Delegates:
                    TypeConstant typeInto = contrib.resolveGenerics(this);
                    if (typeInto != null)
                        {
                        TypeComposition clzContribution = resolveClass(typeInto);
                        addNoDupes(clzContribution.collectDefaultCallChain(), list, set);
                        }
                    break;
                }
            }

        // 2.3
        addNoDupes(clzSuper.collectDefaultCallChain(), list, set);
        return m_listDefaultChain = list;
        }

    private void addNoDupes(List<TypeComposition> listFrom, List<TypeComposition> listTo,
                            Set<TypeComposition> setDupes)
        {
        for (TypeComposition clz : listFrom)
            {
            if (setDupes.add(clz))
                {
                listTo.add(clz);
                }
            }
        }

    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.f_clazz == this;

        TypeConstant typeCurrent = handle.m_type;
        TypeConstant typeTarget;

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

    public synchronized TypeConstant ensurePublicType()
        {
        TypeConstant type = m_typePublic;
        if (type == null)
            {
            m_typePublic = type = f_typeActual.modifyAccess(Access.PUBLIC);
            }
        return type;
        }
    public synchronized TypeConstant ensureProtectedType()
        {
        TypeConstant type = m_typeProtected;
        if (type == null)
            {
            m_typeProtected = type = f_typeActual.modifyAccess(Access.PROTECTED);
            }
        return type;
        }

    public synchronized TypeConstant ensurePrivateType()
        {
        TypeConstant type = m_typePrivate;
        if (type == null)
            {
            m_typePrivate = type = f_typeActual.modifyAccess(Access.PRIVATE);
            }
        return type;
        }

    public synchronized TypeConstant ensureStructType()
        {
        TypeConstant type = m_typeStruct;
        if (type == null)
            {
            m_typeStruct = type = f_typeActual.modifyAccess(Access.STRUCT);
            }
        return type;
        }

    public boolean isStruct(TypeConstant type)
        {
        return type == m_typeStruct;
        }

    // is the public interface of this class assignable to the specified class
    public boolean isA(TypeComposition that)
        {
        return this.ensurePublicType().isA(that.ensurePublicType());
        }

    // given a TypeConstant, return a corresponding TypeComposition within this class's context
    // or Object.class if the type cannot be resolved;
    //
    // for example, List<KeyType> in the context of Map<String, Int> will resolve in List<String>
    //
    // Note: this impl is almost identical to TypeSet.resolveParameterType()
    //       but returns TypeComposition rather than Type and is more tolerant
    public TypeComposition resolveClass(TypeConstant type)
        {
        return f_template.f_types.resolveClass(type.resolveGenerics(this));
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

        Frame frameBase = frame.createFrame1(chain.getMethod(cMethods - 1),
            hStruct, ahVar, Frame.RET_UNUSED);

        if (cMethods > 1)
            {
            frameBase.setContinuation(new Frame.Continuation()
                {
                private int index = cMethods - 2;

                public int proceed(Frame frameCaller)
                    {
                    int i = index--;
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
                });
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
        // we only cache the PUBLIC access chains; all others are only cached at the op-code level
        return access == Access.PUBLIC
            ? m_mapMethods.computeIfAbsent(constSignature, sig -> collectMethodCallChain(sig, access))
            : collectMethodCallChain(constSignature, access);
        }

    // find a matching method and add to the list
    protected CallChain collectMethodCallChain(SignatureConstant constSignature, Access access)
        {
        List<MethodStructure> list = new LinkedList<>();

        if (f_typeActual.isParamsSpecified())
            {
            constSignature = constSignature.resolveGenericTypes(this);
            }

        nextInChain:
        for (TypeComposition clz : getCallChain())
            {
            MultiMethodStructure mms = (MultiMethodStructure)
                clz.f_template.f_struct.getChild(constSignature.getName());
            if (mms != null)
                {
                for (MethodStructure method : mms.methods())
                    {
                    if (method.getAccess().compareTo(access) <= 0 &&
                        method.isSubstitutableFor(constSignature, this))
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

    public PropertyCallChain getPropertyGetterChain(String sProperty)
        {
        return m_mapGetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, true));
        }

    public PropertyCallChain getPropertySetterChain(String sProperty)
        {
        return m_mapSetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, false));
        }

    protected PropertyCallChain collectPropertyCallChain(String sPropName, boolean fGetter)
        {
        PropertyStructure propertyBase = null;
        List<MethodStructure> list = new LinkedList<>();

        for (TypeComposition clz : getCallChain())
            {
            ClassTemplate template = clz.f_template;
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

        return new PropertyCallChain(list, propertyBase, fGetter);
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

        // TODO: check the interfaces and mix-ins

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

        for (TypeComposition clz : collectDeclaredCallChain(true))
            {
            ClassTemplate template = clz.f_template;

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

    // ---- support for op-codes that require class specific information -----

    // compare for equality (==) two object handles that both belong to this class
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int callEquals(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }
        return f_template.callEquals(frame, this, hValue1, hValue2, iReturn);
        }

    // compare for order (<=>) two object handles that both belong to this class
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int callCompare(Frame frame, ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xOrdered.EQUAL);
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
        return f_typeActual.getValueString();
        }
    }
