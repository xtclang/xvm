package org.xvm.runtime;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.TypedefStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.Const;
import org.xvm.runtime.template.Enum;
import org.xvm.runtime.template.Enum.EnumHandle;
import org.xvm.runtime.template.Function.FullyBoundHandle;
import org.xvm.runtime.template.Ref.RefHandle;
import org.xvm.runtime.template.Service;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xObject;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.util.ListMap;


/**
 * ClassTemplate represents a run-time class.
 *
 * @author gg 2017.02.23
 */
public abstract class ClassTemplate
    {
    public final TypeSet f_types;
    public final ClassStructure f_struct;

    public final String f_sName; // globally known ClassTemplate name (e.g. Boolean or annotations.AtomicRef)

    public final TypeComposition f_clazzCanonical; // public non-parameterized class
    public final ListMap<String, Type> f_mapGenericFormal; // formal types

    public final ClassTemplate f_templateSuper;
    public final ClassTemplate f_templateCategory; // a native category

    public final boolean f_fService; // is this a service

    /**
     * An empty ListMap; should never be mutated.
     */
    public static final ListMap EMPTY = new ListMap(0);

    // ----- caches ------

    // cache of TypeCompositions
    protected Map<List<Type>, TypeComposition> m_mapCompositions = new HashMap<>();

    // cache of relationships
    protected enum Relation {EXTENDS, IMPLEMENTS, INCOMPATIBLE}
    protected Map<ClassTemplate, Relation> m_mapRelations = new HashMap<>();

    // construct the template
    public ClassTemplate(TypeSet types, ClassStructure structClass)
        {
        f_types = types;
        f_struct = structClass;
        f_sName = structClass.getIdentityConstant().getPathString();

        Map<StringConstant, TypeConstant> mapParams = structClass.getTypeParams();
        int cParams = mapParams.size();

        ListMap<String, Type> mapFormal;
        if (cParams == 0)
            {
            mapFormal = EMPTY;
            }
        else
            {
            mapFormal = new ListMap<>(cParams);

            // we know that mapParams is a ListMap as well;
            // prepare it with "Object" types to be resolved by createCanonicalClass
            for (StringConstant constName : mapParams.keySet())
                {
                mapFormal.put(constName.getValue(), xObject.TYPE);
                }
            }
        f_mapGenericFormal = mapFormal;

        // calculate the parents (inheritance and "native")
        ClassStructure structSuper = null;
        ClassTemplate templateSuper = null;
        ClassTemplate templateCategory = null;
        boolean fService = false;

        TypeConstant constSuper = Adapter.getContribution(structClass, ClassStructure.Composition.Extends);
        if (constSuper == null)
            {
            if (!f_sName.equals("Object"))
                {
                templateSuper = xObject.INSTANCE;
                }
            }
        else
            {
            structSuper = (ClassStructure) ((IdentityConstant) constSuper.getDefiningConstant()).getComponent();
            templateSuper = f_types.getTemplate(structSuper.getIdentityConstant());
            fService = templateSuper.isService();
            }

        if (structSuper == null || structClass.getFormat() != structSuper.getFormat())
            {
            switch (structClass.getFormat())
                {
                case SERVICE:
                    templateCategory = Service.INSTANCE;
                    fService = true;
                    break;

                case CONST:
                    templateCategory = Const.INSTANCE;
                    break;

                case ENUM:
                    templateCategory = Enum.INSTANCE;
                    break;
                }
            }

        f_templateSuper = templateSuper;
        f_templateCategory = templateCategory;
        f_clazzCanonical = createCanonicalClass();
        f_fService = fService;
        }

    /**
     * Create the canonical class for this template.
     *
     * Note that the actual parameters of the canonical class could exist even if there are no
     * formal parameters. For example, the canonical class for String has an "ElementType" of Char
     * since String implements Sequence<Char>.
     */
    protected TypeComposition createCanonicalClass()
        {
        ListMap<String, Type> mapFormal = f_mapGenericFormal;

        if (!mapFormal.isEmpty())
            {
            // resolve generic constraints
            for (Map.Entry<StringConstant, TypeConstant> entry :
                    f_struct.getTypeParams().entrySet())
                {
                String sName = entry.getKey().getValue();
                TypeConstant constType = entry.getValue();

                mapFormal.put(sName, f_types.resolveType(constType, mapFormal));
                }
            }

        return new TypeComposition(this, addContributingTypes(mapFormal), true);
        }

    /**
     * Produce a TypeComposition for this template using the actual types for formal parameters.
     *
     * The passed-in actual parameters are only used to resolve the formal parameters at this
     * template level. However, there could be additional actual generic parameters for this
     * class that come from the contributions (extends, implements, incorporates).
     */
    public TypeComposition ensureClass(Map<String, Type> mapActual)
        {
        if (mapActual.isEmpty())
            {
            return f_clazzCanonical;
            }

        // sort the parameters by name and use the list of sorted (by formal name) types as a key
        Map<String, Type> mapSorted = mapActual.size() > 1 ?
                new TreeMap<>(mapActual) : mapActual;
        List<Type> key = new ArrayList<>(mapSorted.values());

        return m_mapCompositions.computeIfAbsent(key,
                (x) -> new TypeComposition(this, addContributingTypes(mapActual), false));
        }

    /**
     * Add resolved formal types that are specified by the contributions
     * (extends, implements, incorporates) to the passed-in map of the parameter types.
     */
    protected Map<String, Type> addContributingTypes(Map<String, Type> mapParams)
        {
        Map<String, Type> mapAllParams = null;

        for (Component.Contribution contribution : f_struct.getContributionsAsList())
            {
            switch (contribution.getComposition())
                {
                case Incorporates:
                    // TODO: how to detect a conditional incorporation?
                case Implements:
                case Extends:
                    TypeConstant constContribution = contribution.getClassConstant();
                    List<TypeConstant> listParams = constContribution.getParamTypes();
                    if (!listParams.isEmpty())
                        {
                        if (mapAllParams == null)
                            {
                            mapAllParams = new HashMap<>(mapParams);
                            }
                        ClassTemplate templateContribution = f_types.getTemplate(
                            (IdentityConstant) constContribution.getDefiningConstant());
                        templateContribution.resolveFormalParameters(listParams, mapParams, mapAllParams);
                        }
                    break;
                }
            }
        return mapAllParams == null ? mapParams : mapAllParams;
        }

    public boolean isRootObject()
        {
        return f_templateSuper == null && f_struct.getFormat() == Component.Format.CLASS;
        }

    /**
     * Determine if this template consumes a formal type with the specified name for the specified
     * access policy.
     */
    public boolean consumesFormalType(String sName, Access access)
        {
        for (Component child : f_struct.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (method.isAccessible(access) &&
                        method.consumesFormalType(sName, f_types))
                        {
                        return true;
                        }
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) child;
                TypeConstant constType = property.getType();

                MethodStructure methodGet = Adapter.getGetter(property);
                if (methodGet != null && methodGet.isAccessible(access) &&
                    constType.consumesFormalType(sName, f_types, Access.PUBLIC))
                    {
                    return true;
                    }

                MethodStructure methodSet = Adapter.getGetter(property);
                if (methodSet != null && methodSet.isAccessible(access) &&
                    constType.producesFormalType(sName, f_types, Access.PUBLIC))
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    /**
     * Determine if this template produces a formal type with the specified name for the
     * specified access policy.
     */
    public boolean producesFormalType(String sName, Access access)
        {
        for (Component child : f_struct.children())
            {
            if (child instanceof MultiMethodStructure)
                {
                for (MethodStructure method : ((MultiMethodStructure) child).methods())
                    {
                    if (method.isAccessible(access) &&
                        method.producesFormalType(sName, f_types))
                        {
                        return true;
                        }
                    }
                }
            else if (child instanceof PropertyStructure)
                {
                PropertyStructure property = (PropertyStructure) child;
                TypeConstant constType = property.getType();

                MethodStructure methodGet = Adapter.getGetter(property);
                if (methodGet != null && methodGet.isAccessible(access) &&
                    constType.producesFormalType(sName, f_types, Access.PUBLIC))
                    {
                    return true;
                    }

                MethodStructure methodSet = Adapter.getGetter(property);
                if (methodSet != null && methodSet.isAccessible(access) &&
                    constType.consumesFormalType(sName, f_types, Access.PUBLIC))
                    {
                    return true;
                    }
                }
            }
        return false;
        }

    // does this template extend that?
    public boolean extends_(ClassTemplate that)
        {
        if (this == that)
            {
            return true;
            }

        Relation relation = m_mapRelations.get(that);
        if (relation != null)
            {
            return relation == Relation.EXTENDS;
            }

        ClassTemplate templateSuper = f_templateSuper;
        while (templateSuper != null)
            {
            m_mapRelations.put(templateSuper, Relation.EXTENDS);

            // there is just one template instance per name
            if (templateSuper == that)
                {
                return true;
                }
            templateSuper = templateSuper.f_templateSuper;
            }

        m_mapRelations.put(that, Relation.INCOMPATIBLE);
        return false;
        }

    public ClassTemplate getSuper()
        {
        return f_templateSuper;
        }

    public boolean isService()
        {
        return f_fService;
        }

    public boolean isInterface()
        {
        return f_struct.getFormat() == Component.Format.INTERFACE;
        }

    public boolean isConst()
        {
        switch (f_struct.getFormat())
            {
            case PACKAGE:
            case CONST:
            case ENUM:
            case ENUMVALUE:
            case MODULE:
                return true;

            default:
                return false;
            }
        }

    // should we generate fields for this class
    public boolean isStateful()
        {
        switch (f_struct.getFormat())
            {
            case CLASS:
            case CONST:
            case MIXIN:
            case SERVICE:
                return true;

            default:
                return false;
            }
        }

    public boolean isSingleton()
        {
        return f_struct.isStatic();
        }

    public PropertyStructure getProperty(String sPropName)
        {
        return (PropertyStructure) f_struct.getChild(sPropName);
        }

    public PropertyStructure ensureProperty(String sPropName)
        {
        PropertyStructure property = getProperty(sPropName);
        if (property != null)
            {
            return property;
            }

        PropertyStructure propSuper = null;
        for (ClassTemplate templateSuper : f_clazzCanonical.getCallChain())
            {
            propSuper = templateSuper.getProperty(sPropName);
            if (propSuper != null)
                {
                break;
                }
            }

        if (propSuper == null)
            {
            throw new IllegalArgumentException("Property is not defined " + f_sName + "#" + sPropName);
            }

        return f_struct.createProperty(false,
            propSuper.getAccess(), propSuper.getType(), sPropName);
        }

    public TypeConstant getTypeConstant()
        {
        return f_struct.getIdentityConstant().asTypeConstant();
        }

    // get a method declared at this template level
    public MethodStructure getDeclaredMethod(String sName, TypeConstant[] atParam, TypeConstant[] atReturn)
        {
        MultiMethodStructure mms = (MultiMethodStructure) f_struct.getChild(sName);
        if (mms != null)
            {
            nextMethod:
            for (MethodStructure method : mms.methods())
                {
                MethodConstant constMethod = method.getIdentityConstant();

                TypeConstant[] atParamTest = constMethod.getRawParams();
                TypeConstant[] atReturnTest = constMethod.getRawReturns();

                if (atParam != null) // temporary work around; TODO: remove
                    {
                    int cParams = atParamTest.length;
                    if (cParams != atParam.length)
                        {
                        continue;
                        }

                    for (int i = 0; i < cParams; i++)
                        {
                        if (!compareTypes(atParamTest[i], atParam[i]))
                            {
                            continue nextMethod;
                            }
                        }
                    }

                if (atReturn != null) // temporary work around; TODO: remove
                    {
                    int cReturns = atReturnTest.length;
                    if (cReturns != atReturn.length)
                        {
                        continue;
                        }

                    for (int i = 0; i < cReturns; i++)
                        {
                        if (!compareTypes(atReturnTest[i], atReturn[i]))
                            {
                            continue nextMethod;
                            }
                        }
                    }
                return method;
                }
            }

        return null;
        }

    // compare the specified types
    private static boolean compareTypes(TypeConstant tTest, TypeConstant tParam)
        {
        while (tTest.isSingleDefiningConstant()
                && tTest.getDefiningConstant().getFormat() == Format.Typedef)
            {
            tTest = ((TypedefStructure)
                    ((IdentityConstant) tTest.getDefiningConstant()).getComponent()).getType();
            }

        // compensate for "function"
        if (tTest.getValueString().contains("Function") &&
            tParam.getValueString().contains("Function"))
            {
            return true;
            }

        return tTest.equals(tParam);
        }

    // find one of the "equals" or "compare" functions definition
    // @param atRet is one of xBoolean.TYPES or xOrdered.TYPES
    public MethodStructure findCompareFunction(String sName, TypeConstant[] atRet)
        {
        ClassTemplate template = this;
        TypeConstant typeThis = getTypeConstant();
        TypeConstant[] atArg = new TypeConstant[] {typeThis, typeThis};
        do
            {
            MethodStructure method = getDeclaredMethod(sName, atArg, atRet);
            if  (method != null && method.isStatic())
                {
                return method;
                }
            template = template.f_templateSuper;
            }
        while (template != null);
        return null;
        }

    // produce a TypeComposition for this template by resolving the generic types
    // NOTE: this method is overridden by the Tuple's implementation
    public TypeComposition resolveClass(TypeConstant constClassType, Map<String, Type> mapActual)
        {
        assert ((IdentityConstant) constClassType.getDefiningConstant()).getPathString().equals(f_sName);

        List<TypeConstant> listParams = constClassType.getParamTypes();
        if (listParams.isEmpty())
            {
            return f_clazzCanonical;
            }

        Map<String, Type> mapParams = new HashMap<>(listParams.size());
        resolveFormalParameters(listParams, mapActual, mapParams);

        return ensureClass(mapParams);
        }

    protected void resolveFormalParameters(List<TypeConstant> listParams,
            Map<String, Type> mapActual, Map<String, Type> mapParams)
        {
        int cParams = f_mapGenericFormal.size();

        assert cParams == listParams.size();

        int ix = 0;
        for (String sParamName : f_mapGenericFormal.keySet())
            {
            if (!mapParams.containsKey(sParamName))
                {
                mapParams.put(sParamName, f_types.resolveType(listParams.get(ix++), mapActual));
                }
            }
        }

    @Override
    public int hashCode()
        {
        return f_sName.hashCode();
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
        return f_struct.toString();
        }

    // ---- OpCode support: construction and initialization -----

    /**
     * Initialize properties, methods and functions declared at the "top" layer.
     *
     * TODO: remove; should be called from constructors
     */
    public void initDeclared()
        {
        }

    // create a RefHandle for the specified class
    // sName is an optional ref name
    // TODO: consider moving this method up to xRef
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // assign (Int i = 5;)
    // @return an immutable handle or null if this type doesn't take that constant
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        return null;
        }

    // @return true if a constant always results into the same ObjectHandle
    public boolean isConstantCacheable(Constant constant)
        {
        return true;
        }

    // return a handle with this:struct access
    protected ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        assert f_struct.getTypeParamsAsList().isEmpty();
        assert f_struct.getFormat() == Component.Format.CLASS ||
               f_struct.getFormat() == Component.Format.CONST;

        return new GenericHandle(clazz, clazz.ensureStructType());
        }

    // invoke the default constructors, then the specified constructor,
    // then finalizers; change this:struct handle to this:public
    // return one of the Op.R_ values
    public int construct(Frame frame, MethodStructure constructor,
                         TypeComposition clazz, ObjectHandle[] ahVar, int iReturn)
        {
        ObjectHandle hStruct = createStruct(frame, clazz); // this:struct

        // assume that we have C1 extending C0 with default constructors (DC),
        // regular constructors (RC), and finalizers (F);
        // the call sequence should be:
        //
        //  ("new" op-code) => DC0 -> DC1 -> RC1 => RC0 -> F0 -> F1 -> "assign" (continuation)
        //
        // -> indicates a call via continuation
        // => indicates a call via Construct op-cod
        //
        // we need to create the call chain in the revers order;
        // the very last frame should also assign the resulting new object

        // this:private -> this:public
        Frame.Continuation contAssign =
                frameCaller -> frameCaller.assignValue(iReturn,
                    hStruct.f_clazz.ensureAccess(hStruct, Access.PUBLIC));

        Frame frameRC1 = frame.f_context.createFrame1(frame, constructor, hStruct, ahVar, Frame.RET_UNUSED);

        Frame frameDC0 = clazz.callDefaultConstructors(frame, hStruct, ahVar,
                frameCaller -> frameCaller.call(frameRC1));

        // we need a non-null anchor (see Frame#chainFinalizer)
        frameRC1.m_hfnFinally = Utils.makeFinalizer(constructor, hStruct, ahVar); // hF1

        frameRC1.setContinuation(frameCaller ->
            {
            if (isConstructImmutable())
                {
                hStruct.makeImmutable();
                }

            FullyBoundHandle hF = frameRC1.m_hfnFinally;

            // this:struct -> this:private
            return hF.callChain(frameCaller, Access.PRIVATE, contAssign);
            });

        return frame.call(frameDC0 == null ? frameRC1 : frameDC0);
        }

    protected boolean isConstructImmutable()
        {
        return this instanceof Const;
        }

    // ----- OpCode support ------

    // invoke with a zero or one return value
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    // invoke with more than one return value
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        return frame.invokeN(chain, 0, hTarget, ahVar, aiReturn);
        }

    // invokeNative property "get" operation
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Unknown property getter: (" + f_sName + ")." + property.getName());
        }

    // invokeNative property "set" operation
    public int invokeNativeSet(Frame frame, ObjectHandle hTarget, PropertyStructure property, ObjectHandle hValue)
        {
        throw new IllegalStateException("Unknown property setter: (" + f_sName + ")." + property.getName());
        }

    // invokeNative with exactly one argument and zero or one return value
    // place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // invokeNative with zero or more than one arguments and zero or one return values
    // return one of the Op.R_ values
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (ahArg.length)
            {
            case 0:
                if (method.getName().equals("to"))
                    {
                    // how to differentiate; check the method's return type?
                    return buildStringValue(frame, hTarget, iReturn);
                    }
            }

        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // invokeNative with zero or more arguments and more than one return values
    // return one of the Op.R_ values
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        throw new IllegalStateException("Unknown method: (" + f_sName + ")." + method);
        }

    // Add operation; place the result into the specified frame register
    // return one of the Op.R_ values
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Neg operation
    // return one of the Op.R_ values
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Next operation (Sequential)
    // return either R_NEXT or R_EXCEPTION
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // Prev operation (Sequential)
    // return either R_NEXT or R_EXCEPTION
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        throw new IllegalStateException("Invalid op for " + f_sName);
        }

    // ---- OpCode support: register or property operations -----


    // increment the property value and place the result into the specified frame register
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int invokePreInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        switch (getPropertyValue(frame, hTarget, sPropName, Frame.RET_LOCAL))
            {
            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            case Op.R_NEXT:
                return new Utils.PreInc(hTarget, sPropName, iReturn).proceed(frame);

            case Op.R_CALL:
                frame.m_frameNext.setContinuation(new Utils.PreInc(hTarget, sPropName, iReturn));
                return Op.R_CALL;

            default:
                throw new IllegalStateException();
            }
        }

    // place the property value into the specified frame register and increment it
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int invokePostInc(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        switch (getPropertyValue(frame, hTarget, sPropName, Frame.RET_LOCAL))
            {
            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            case Op.R_NEXT:
                return new Utils.PostInc(hTarget, sPropName, iReturn).proceed(frame);

            case Op.R_CALL:
                frame.m_frameNext.setContinuation(new Utils.PostInc(hTarget, sPropName, iReturn));
                return Op.R_CALL;

            default:
                throw new IllegalStateException();
            }
        }

    // ----- OpCode support: property operations -----

    // get a property value into the specified register
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, int iReturn)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        CallChain.PropertyCallChain chain = hTarget.f_clazz.getPropertyGetterChain(sPropName);
        if (chain.isNative())
            {
            return invokeNativeGet(frame, chain.getProperty(), hTarget, iReturn);
            }

        MethodStructure method = hTarget.isStruct() ? null : chain.getTop();
        if (method == null)
            {
            return getFieldValue(frame, hTarget, chain.getProperty(), iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];

        return frame.invoke1(chain, 0, hTarget, ahVar, iReturn);
        }

    public int getFieldValue(Frame frame, ObjectHandle hTarget,
                             PropertyStructure property, int iReturn)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        String sName = property.getName();

        if (isGenericType(sName))
            {
            Type type = hTarget.f_clazz.getActualType(sName);

            return frame.assignValue(iReturn, type.getHandle());
            }

        GenericHandle hThis = (GenericHandle) hTarget;
        ObjectHandle hValue = hThis.m_mapFields.get(sName);

        if (hValue == null)
            {
            String sErr;
            if (isInjectable(property))
                {
                TypeComposition clz = hThis.f_clazz.resolveClass(property.getType());

                hValue = frame.f_context.f_container.getInjectable(sName, clz);
                if (hValue != null)
                    {
                    hThis.m_mapFields.put(sName, hValue);
                    return frame.assignValue(iReturn, hValue);
                    }
                sErr = "Unknown injectable property ";
                }
            else
                {
                sErr = hThis.m_mapFields.containsKey(sName) ?
                        "Un-initialized property \"" : "Invalid property \"";
                }

            return frame.raiseException(xException.makeHandle(sErr + sName + '"'));
            }

        if (isRef(property))
            {
            try
                {
                hValue = ((RefHandle) hValue).get();
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }

        return frame.assignValue(iReturn, hValue);
        }

    // set a property value
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, String sPropName, ObjectHandle hValue)
        {
        if (sPropName == null)
            {
            throw new IllegalStateException(f_sName);
            }

        CallChain.PropertyCallChain chain = hTarget.f_clazz.getPropertySetterChain(sPropName);
        PropertyStructure property = chain.getProperty();

        ExceptionHandle hException = null;
        if (!hTarget.isMutable())
            {
            hException = xException.makeHandle("Immutable object: " + hTarget);
            }
        else if (isCalculated(property))
            {
            hException = xException.makeHandle("Read-only property: " + property.getName());
            }

        if (hException == null)
            {
            if (isNativeSetter(property))
                {
                return invokeNativeSet(frame, hTarget, property, hValue);
                }

            MethodStructure method = hTarget.isStruct() ? null : Adapter.getSetter(property);
            if (method == null)
                {
                hException = setFieldValue(hTarget, property, hValue);
                }
            else
                {
                ObjectHandle[] ahVar = new ObjectHandle[method.getMaxVars()];
                ahVar[1] = hValue;

                return frame.call1(method, hTarget, ahVar, Frame.RET_UNUSED);
                }
            }

        return hException == null ? Op.R_NEXT : frame.raiseException(hException);
        }

    public ExceptionHandle setFieldValue(ObjectHandle hTarget,
                                         PropertyStructure property, ObjectHandle hValue)
        {
        if (property == null)
            {
            throw new IllegalStateException(f_sName);
            }

        GenericHandle hThis = (GenericHandle) hTarget;

        assert hThis.m_mapFields.containsKey(property.getName());

        if (isRef(property))
            {
            return ((RefHandle) hThis.m_mapFields.get(property.getName())).set(hValue);
            }

        hThis.m_mapFields.put(property.getName(), hValue);
        return null;
        }

    // ----- support for equality and comparison ------

    // compare for equality two object handles that both belong to the specified class
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 == hValue2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }

        // if there is an "equals" function, we need to call it
        MethodStructure functionEquals = findCompareFunction("equals", xBoolean.TYPES);
        if (functionEquals != null)
            {
            return frame.call1(functionEquals, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        // only Const classes have an automatic implementation;
        // for everyone else it's either a native method or a ref equality
        return frame.assignValue(iReturn, xBoolean.FALSE);
        }

    // compare for order two object handles that both belong to the specified class
    // return R_NEXT, R_CALL or R_EXCEPTION
    public int callCompare(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        // if there is an "compare" function, we need to call it
        MethodStructure functionCompare = findCompareFunction("compare", xOrdered.TYPES);
        if (functionCompare != null)
            {
            return frame.call1(functionCompare, null,
                    new ObjectHandle[]{hValue1, hValue2}, iReturn);
            }

        // only Const and Enum classes have automatic implementations
        switch (f_struct.getFormat())
            {
            case ENUMVALUE:
                EnumHandle hV1 = (EnumHandle) hValue1;
                EnumHandle hV2 = (EnumHandle) hValue2;

                return frame.assignValue(iReturn,
                        xOrdered.makeHandle(hV1.getValue() - hV2.getValue()));

            default:
                throw new IllegalStateException(
                        "No implementation for \"compare()\" function at " + f_sName);
            }
        }

    // build the String representation of the target handle
    // returns R_NEXT, R_CALL or R_EXCEPTION
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xString.makeHandle(hTarget.toString()));
        }

    // ----- Op-code support: array operations -----

    // get a handle to an array for the specified class
    // returns R_NEXT or R_EXCEPTION
    public int createArrayStruct(Frame frame, TypeComposition clzArray,
                                 long cCapacity, int iReturn)
        {
        if (cCapacity < 0 || cCapacity > Integer.MAX_VALUE)
            {
            return frame.raiseException(
                    xException.makeHandle("Invalid array size: " + cCapacity));
            }

        return frame.assignValue(iReturn, xArray.makeHandle(clzArray, cCapacity));
        }

    // ----- to be replaced when the structure support is added

    protected boolean isInjectable(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fInjectable;
        }

    protected boolean isAtomic(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fAtomic;
        }

    protected boolean isCalculated(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fCalculated;
        }

    protected boolean isNativeGetter(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fNativeGetter;
        }

    protected boolean isNativeSetter(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_fNativeSetter;
        }

    protected boolean isRef(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info != null && info.m_templateRef != null;
        }

    protected ClassTemplate getRefTemplate(PropertyStructure property)
        {
        PropertyInfo info = property.getInfo();
        return info == null ? null : info.m_templateRef;
        }

    protected boolean isGenericType(String sProperty)
        {
        return f_mapGenericFormal.containsKey(sProperty);
        }

    // =========== TEMPORARY ========

    public void markNativeMethod(String sName, String[] asParamType)
        {
        markNativeMethod(sName, asParamType, VOID);
        }

    public void markNativeMethod(String sName, String[] asParamType, String[] asRetType)
        {
        ensureMethodStructure(sName, asParamType, asRetType).setNative(true);
        }

    public MethodStructure ensureMethodStructure(String sName, String[] asParam)
        {
        return ensureMethodStructure(sName, asParam, VOID);
        }

    public MethodStructure ensureMethodStructure(String sName, String[] asParam, String[] asRet)
        {
        MethodStructure method = f_types.f_adapter.getMethod(this, sName, asParam, asRet);
        if (method == null)
            {
            MethodStructure methodSuper = null;
            for (ClassTemplate templateSuper : f_clazzCanonical.getCallChain())
                {
                methodSuper = f_types.f_adapter.getMethod(templateSuper, sName, asParam, asRet);
                if (methodSuper != null)
                    {
                    break;
                    }
                }

            if (methodSuper == null)
                {
                throw new IllegalArgumentException("Method is not defined: " + f_sName + '#' + sName);
                }

            method = f_struct.createMethod(false,
                    methodSuper.getAccess(),
                    methodSuper.getReturns().toArray(new Parameter[methodSuper.getReturnCount()]),
                    sName,
                    methodSuper.getParams().toArray(new Parameter[methodSuper.getParamCount()]));
            }
        return method;
        }

    public void markInjectable(String sPropName)
        {
        ensurePropertyInfo(sPropName).m_fInjectable = true;
        }

    public void markCalculated(String sPropName)
        {
        ensurePropertyInfo(sPropName).m_fCalculated = true;
        }

    public void markAtomicRef(String sPropName)
        {
        ensurePropertyInfo(sPropName).markAtomic();
        }

    // mark the property getter as native
    // Note: this also makes the property "calculated" (no storage)
    public void markNativeGetter(String sPropName)
        {
        MethodStructure methodGet = Adapter.getGetter(ensureProperty(sPropName));
        if (methodGet == null)
            {
            ensurePropertyInfo(sPropName).m_fNativeGetter = true;
            }
        else
            {
            methodGet.setNative(true);
            }
        ensurePropertyInfo(sPropName).m_fCalculated = true;
        }

    // mark the property setter as native
    // Note: this also makes the property "calculated" (no storage)
    public void markNativeSetter(String sPropName)
        {
        MethodStructure methodSet = Adapter.getSetter(ensureProperty(sPropName));
        if (methodSet == null)
            {
            ensurePropertyInfo(sPropName).m_fNativeSetter = true;
            }
        else
            {
            methodSet.setNative(true);
            }
        ensurePropertyInfo(sPropName).m_fCalculated = false;
        }

    public MethodStructure ensureGetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);

        return Adapter.getGetter(prop);
        }

    public MethodStructure ensureSetter(String sPropName)
        {
        PropertyStructure prop = getProperty(sPropName);

        return Adapter.getSetter(prop);
        }

    public PropertyInfo ensurePropertyInfo(String sPropName)
        {
        PropertyStructure property = ensureProperty(sPropName);

        PropertyInfo info = property.getInfo();
        if (info == null)
            {
            property.setInfo(info = new PropertyInfo(f_types, property));
            }
        return info;
        }

    public static class PropertyInfo
        {
        protected final TypeSet           f_types;
        protected final PropertyStructure f_property;
        protected       boolean           m_fAtomic;
        protected       boolean           m_fInjectable;
        protected       boolean           m_fCalculated;
        protected       boolean           m_fNativeGetter;
        protected       boolean           m_fNativeSetter;
        protected       ClassTemplate     m_templateRef;

        public PropertyInfo(TypeSet types, PropertyStructure property)
            {
            f_types = types;
            f_property = property;
            }

        protected void markAtomic()
            {
            m_fAtomic = true;

            TypeConstant constType = f_property.getType();
            if (constType.isEcstasy("Int"))
                {
                markAsRef("annotations.AtomicIntNumber");
                }
            else
                {
                markAsRef("annotations.AtomicRef");
                }
            }

        public void markAsRef(String sRefClassName)
            {
            m_templateRef = f_types.getTemplate(sRefClassName);
            }
        }


    public static String[] VOID   = new String[0];
    public static String[] OBJECT = new String[] {"Object"};
    public static String[] INT    = new String[] {"Int64"};
    public static String[] STRING = new String[] {"String"};
    }
