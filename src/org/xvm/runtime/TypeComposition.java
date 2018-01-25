package org.xvm.runtime;


import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain.PropertyCallChain;

import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xRef.RefHandle;


/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String> or
 * @Range Interval<Date>).
 */
public class TypeComposition
    {
    /**
     * The underlying {@link OpSupport} for the inception type.
     */
    private final OpSupport f_support;

    /**
     * The {@link ClassTemplate} for the defining class (or Tuple) of the inception type.
     */
    private final ClassTemplate f_template;

    /**
     * The actual type - the maximum of what this type composition could be revealed as.
     */
    private final TypeConstant f_typeInception;

    /**
     * The type that is revealed by the ObjectHandle that refer to this composition.
     */
    private final TypeConstant f_typeRevealed;

    // super composition
    private TypeComposition m_clzSuper;

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

    /**
     * Construct the TypeComposition for a given "inception" type and a "revealed" type.
     *
     * The guarantees for the inception type are:
     *  - it has to be a class (TypeConstant.isClass())
     *  - it cannot be abstract
     *  - the only modifying types that are allowed are AnnotatedTypeConstant(s) and
     *    a ParameterizedTypeConstant
     *
     * @param support        the OpSupport implementation for the inception type
     * @param typeInception  the "origin type"
     * @param typeRevealed   the type to reveal an ObjectHandle reference to this class as
     */
    protected TypeComposition(OpSupport support, TypeConstant typeInception, TypeConstant typeRevealed)
        {
        assert typeInception.isSingleDefiningConstant();

        f_support = support;
        f_template = support.getTemplate();
        f_typeInception = typeInception;
        f_typeRevealed = typeRevealed;
        }

    /**
     * @return the OpSupport for the inception type of this TypeComposition
     */
    public OpSupport getSupport()
        {
        return f_support;
        }
    /**
     * @return the template for the defining class (can be Tuple) for the inception type
     */
    public ClassTemplate getTemplate()
        {
        return f_template;
        }

    /**
     * @return the current (revealed) type of this TypeComposition
     */
    public TypeConstant getType()
        {
        return f_typeRevealed;
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
            TypeConstant typeActual = f_typeInception;
            if (typeActual.isParamsSpecified())
                {
                List<TypeConstant> listActual =
                    contribExtends.transformActualTypes(structClz, typeActual.getParamTypes());

                TypeConstant typeSuper = templateSuper.f_struct.resolveType(listActual);

                // alternatively, we can do
                //     template.ensureClass(f_typeInception, typeSuper);
                return m_clzSuper = templateSuper.ensureClass(typeSuper);
                }
            }

        return m_clzSuper = templateSuper.ensureCanonicalClass();
        }

    /**
     * Retrieve a TypeComposition that widens the current type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     */
    public TypeComposition maskAs(TypeConstant type)
        {
        if (type.equals(f_typeRevealed))
            {
            return this;
            }

        if (!f_typeRevealed.isA(type))
            {
            throw new IllegalArgumentException("Type " + f_typeRevealed + " cannot be widened to " + type);
            }

        return f_template.ensureClass(f_typeInception, type);
        }

    /**
     * Retrieve a TypeComposition that widens the actual type to the specified type.
     *
     * Note that the underlying ClassTemplate doesn't change.
     */
    public TypeComposition revealAs(TypeConstant type, Container container)
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

        return f_template.ensureClass(f_typeInception, type);
        }

    public ObjectHandle ensureOrigin(ObjectHandle handle)
        {
        assert handle.getComposition() == this;

        TypeConstant typeInception = f_typeInception;

        // struct is not "revealable"
        return typeInception == f_typeRevealed|| isStruct()
            ? handle : handle.cloneAs(f_template.ensureClass(typeInception, typeInception));
        }

    public ObjectHandle ensureAccess(ObjectHandle handle, Access access)
        {
        assert handle.getComposition() == this;

        TypeConstant typeCurrent = f_typeRevealed;
        TypeConstant typeTarget;

        ConstantPool pool = typeCurrent.getConstantPool();
        switch (access)
            {
            case PUBLIC:
                typeTarget = f_typeInception;
                break;

            case PROTECTED:
                typeTarget = pool.ensureAccessTypeConstant(f_typeInception, Access.PROTECTED);
                break;

            case PRIVATE:
                typeTarget = pool.ensureAccessTypeConstant(f_typeInception, Access.PRIVATE);
                break;

            case STRUCT:
                typeTarget = pool.ensureAccessTypeConstant(f_typeInception, Access.STRUCT);
                break;

            default:
                throw new IllegalStateException();
            }

        return typeCurrent.equals(typeTarget) ?
            handle : handle.cloneAs(f_template.ensureClass(f_typeInception, typeTarget));
        }

    public boolean isStruct()
        {
        return f_typeRevealed.getAccess() == Access.STRUCT;
        }

    /**
     * @return true iff the revealed type has a formal type parameter with the specified name
     */
    public boolean isGenericType(String sName)
        {
        return f_typeRevealed.isGenericType(sName);
        }

    /**
     * @return true iff the inception type represents a service
     */
    public boolean isService()
        {
        TypeConstant type = f_typeInception;
        return type.getSingleUnderlyingClass().getComponent().getFormat() == Format.SERVICE;
        }

    /**
     * @return true iff the inception type represents a const
     */
    public boolean isConst()
        {
        TypeConstant type = f_typeInception;
        return ((ClassStructure) type.getSingleUnderlyingClass().getComponent()).isConst();
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
        return f_typeRevealed.getGenericParamType(sName);
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

        List<TypeComposition> listMerge = new ArrayList<>(listDeclared);
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
        List<TypeComposition> list = new ArrayList<>();
        Set<TypeComposition> set = new HashSet<>(); // to avoid duplicates

        // TODO: 1.1

        // 1.2
        list.add(this);

        TypeConstant typeActual = f_typeInception;
        Component.Format format = structThis.getFormat();
        if (fTop && format == Component.Format.MIXIN)
            {
            // native mix-in (e.g. FutureVar)
            Contribution contribInto = structThis.findContribution(Composition.Into);

            assert contribInto != null;

            TypeConstant typeInto = contribInto.resolveGenerics(typeActual);

            TypeComposition clzInto = f_template.f_templates.resolveClass(typeInto);

            addNoDupes(clzInto.collectDeclaredCallChain(true), list, set);
            }

        // 1.3
        for (Contribution contrib : structThis.getContributionsAsList())
            {
            switch (contrib.getComposition())
                {
                case Incorporates:
                    TypeConstant typeInto = contrib.resolveGenerics(typeActual);
                    if (typeInto != null)
                        {
                        TypeComposition clzContribution = f_template.f_templates.resolveClass(typeInto);
                        addNoDupes(clzContribution.collectDeclaredCallChain(false), list, set);
                        }
                    break;

                case Delegates:
                    // TODO:
                    break;
                }
            }

        // 1.4
        ClassTemplate templateCategory = f_template.getTemplateCategory();
        if (templateCategory != xObject.INSTANCE)
            {
            // all categories are non-generic
            list.add(templateCategory.ensureCanonicalClass());
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
        TypeConstant typeActual = f_typeInception;
        List<TypeComposition> list = new ArrayList<>();
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
                    TypeConstant typeInto = contrib.resolveGenerics(typeActual);
                    if (typeInto != null)
                        {
                        TypeComposition clzContribution = f_template.f_templates.resolveClass(typeInto);
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

    // is the revealed type of this class assignable to the revealed type of the specified class
    public boolean isA(TypeComposition that)
        {
        return this.f_typeRevealed.isA(that.f_typeRevealed);
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
        // TODO: this will be replaced whit the TypeInfo
        // we only cache the PUBLIC access chains; all others are only cached at the op-code level
        return access == Access.PUBLIC
            ? m_mapMethods.computeIfAbsent(constSignature, sig -> collectMethodCallChain(sig, access))
            : collectMethodCallChain(constSignature, access);
        }

    // find a matching method and add to the list
    protected CallChain collectMethodCallChain(SignatureConstant constSignature, Access access)
        {
        List<MethodStructure> list = new ArrayList<>();

        TypeConstant typeActual = f_typeInception;
        if (typeActual.isParamsSpecified())
            {
            constSignature = constSignature.resolveGenericTypes(typeActual);
            }

        nextInChain:
        for (TypeComposition clz : getCallChain())
            {
            MultiMethodStructure mms = (MultiMethodStructure)
                clz.getTemplate().f_struct.getChild(constSignature.getName());
            if (mms != null)
                {
                for (MethodStructure method : mms.methods())
                    {
                    if (method.getAccess().compareTo(access) <= 0 &&
                        method.isSubstitutableFor(constSignature, typeActual))
                        {
                        list.add(method);

                        if (!method.usesSuper())
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
        // TODO: this will be replaced whit the TypeInfo
        return m_mapGetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, true));
        }

    public PropertyCallChain getPropertySetterChain(String sProperty)
        {
        // TODO: this will be replaced whit the TypeInfo
        return m_mapSetters.computeIfAbsent(sProperty, sPropName ->
                collectPropertyCallChain(sPropName, false));
        }

    protected PropertyCallChain collectPropertyCallChain(String sPropName, boolean fGetter)
        {
        PropertyStructure propertyBase = null;
        List<MethodStructure> list = new ArrayList<>();

        for (TypeComposition clz : getCallChain())
            {
            ClassTemplate template = clz.getTemplate();
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

        ClassTemplate templateCategory = f_template.getTemplateCategory();
        if (templateCategory != xObject.INSTANCE)
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
            ClassTemplate template = clz.getTemplate();

            for (Component child : template.f_struct.children())
                {
                if (child instanceof PropertyStructure)
                    {
                    PropertyStructure prop = (PropertyStructure) child;

                    RefHandle hRef = null;
                    if (template.isRef(prop))
                        {
                        TypeComposition clzRef = template.getRefClass(prop);

                        hRef = clzRef.getTemplate().createRefHandle(clzRef, prop.getName());
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
    }
