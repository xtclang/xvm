package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.ChildInfo;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.RecursiveTypeConstant;
import org.xvm.asm.constants.RegisterConstant;
import org.xvm.asm.constants.RelationalTypeConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ProxyComposition;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.Proxy;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template.reflect.xClass.ClassHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTMethod.MethodHandle;
import org.xvm.runtime.template._native.reflect.xRTProperty.PropertyHandle;


/**
 * Native RTType implementation.
 */
public class xRTType
        extends xConst
        implements IndexSupport // for turtle types
    {
    public static xRTType INSTANCE;

    public xRTType(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ConstantPool pool = f_container.getConstantPool();

        TYPE_ARRAY_TYPE  = pool.ensureArrayType(pool.typeType());
        EMPTY_TYPE_ARRAY = pool.ensureArrayConstant(TYPE_ARRAY_TYPE, Constant.NO_CONSTS);
        PROP_CALCULATE   = (PropertyConstant) pool.clzLazy().getComponent().getChild("calculate").getIdentityConstant();
        PROP_HASHER      = (PropertyConstant) f_struct.getChild("hasher").getIdentityConstant();

        markNativeProperty("childTypes");
        markNativeProperty("constants");
        markNativeProperty("constructors");
        markNativeProperty("explicitlyImmutable");
        markNativeProperty("form");
        markNativeProperty("functions");
        markNativeProperty("methods");
        markNativeProperty("properties");
        markNativeProperty("recursive");
        markNativeProperty("template");
        markNativeProperty("typeSystem");
        markNativeProperty("underlyingTypes");

        markNativeMethod("accessSpecified"  , null, null);
        markNativeMethod("annotate"         , null, null);
        markNativeMethod("annotated"        , null, null);
        markNativeMethod("contained"        , null, null);
        markNativeMethod("fromClass"        , null, null);
        markNativeMethod("fromProperty"     , null, null);
        markNativeMethod("modifying"        , null, null);
        markNativeMethod("relational"       , null, null);
        markNativeMethod("named"            , null, null);
        markNativeMethod("parameterize"     , null, null);
        markNativeMethod("parameterized"    , null, null);
        markNativeMethod("purify"           , null, null);
        markNativeMethod("resolveFormalType", null, null);
        markNativeMethod("structConstructor", null, null);

        markNativeMethod("dump", null, null); // temporary

        final String[] PARAM_TYPE    = new String[] {"reflect.Type!<>"};
        final String[] PARAM_METHODS = new String[] {"collections.Array<reflect.Method>"};
        final String[] PARAM_PROPS   = new String[] {"collections.Array<reflect.Property>"};

        markNativeMethod("add", PARAM_TYPE   , null);
        markNativeMethod("add", PARAM_METHODS, null);
        markNativeMethod("add", PARAM_PROPS  , null);
        markNativeMethod("sub", PARAM_TYPE   , null);
        markNativeMethod("sub", PARAM_METHODS, null);
        markNativeMethod("sub", PARAM_PROPS  , null);
        markNativeMethod("and", PARAM_TYPE   , null);
        markNativeMethod("or" , PARAM_TYPE   , null);

        ClassStructure structType = (ClassStructure) pool().clzType().getComponent();

        structType.findMethod("equals",   3).markNative();
        structType.findMethod("compare",  3).markNative();
        structType.findMethod("hashCode", 2).markNative();

        // while the natural "isA()" implementation is almost correct, we need to deal with
        // "foreign" types, which requires a native implementation
        structType.findMethod("isA"    , 1).markNative();

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
            pool.typeType(), pool.typeObject(), pool.typeObject());
        }

    @Override
    public TypeComposition ensureClass(Container container, TypeConstant typeActual)
        {
        return typeActual.equals(getCanonicalType())
            ? getCanonicalClass(container)
            : getCanonicalClass(container).ensureCanonicalizedComposition(typeActual);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof TypeConstant typeTarget)
            {
            ConstantPool pool = frame.poolContext();

            assert typeTarget.isTypeOfType();

            TypeConstant typeData = typeTarget.getParamType(0);

            typeData = typeData.resolveGenerics(pool,
                    frame.getGenericsResolver(typeData.containsDynamicType()));
            return frame.pushStack(typeData.normalizeParameters().ensureTypeHandle(frame.f_context.f_container));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int createProxyHandle(Frame frame, ServiceContext ctxTarget, ObjectHandle hTarget,
                                 TypeConstant typeProxy)
        {
        // a proxy for a non-shareable TypeHandle is a "foreign" handle
        return frame.assignValue(Op.A_STACK,
                makeForeignHandle(((TypeHandle) hTarget).getUnsafeDataType()));
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        TypeHandle hType = (TypeHandle) hTarget;

        if (idProp instanceof FormalTypeChildConstant)
            {
            TypeConstant typeTarget = hType.getDataType();
            TypeConstant typeValue  =
                "OuterType".equals(idProp.getName()) && typeTarget.isVirtualChild()
                    ? typeTarget.getParentType()
                    : typeTarget.resolveFormalType(idProp);

            return typeValue == null
                ? frame.raiseException(xException.invalidType(frame,
                    "Unknown formal type: " + idProp.getName()))
                : frame.assignValue(iReturn, typeValue.ensureTypeHandle(frame.f_context.f_container));
            }

        if ("DataType".equals(idProp.getName()))
            {
            TypeConstant typeResult = hType.getUnsafeDataType();
            return frame.assignValue(iReturn, typeResult.ensureTypeHandle(frame.f_context.f_container));
            }
        return super.getPropertyValue(frame, hTarget, idProp, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TypeHandle hType = (TypeHandle) hTarget;
        switch (sPropName)
            {
            case "childTypes":
                return getPropertyChildTypes(frame, hType, iReturn);

            case "constants":
                return getPropertyConstants(frame, hType, iReturn);

            case "constructors":
                return getPropertyConstructors(frame, hType, iReturn);

            case "explicitlyImmutable":
                return getPropertyExplicitlyImmutable(frame, hType, iReturn);

            case "form":
                return getPropertyForm(frame, hType, iReturn);

            case "functions":
                return getPropertyFunctions(frame, hType, iReturn);

            case "methods":
                return getPropertyMethods(frame, hType, iReturn);

            case "properties":
                return getPropertyProperties(frame, hType, iReturn);

            case "recursive":
                return getPropertyRecursive(frame, hType, iReturn);

            case "template":
                return getPropertyTemplate(frame, hType, iReturn);

            case "typeSystem":
                return getPropertyTypeSystem(frame, hType, iReturn);

            case "underlyingTypes":
                return getPropertyUnderlyingTypes(frame, hType, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        TypeHandle hType = (TypeHandle) hTarget;
        switch (method.getName())
            {
            case "isA":
                return invokeIsA(frame, hType, (TypeHandle) hArg, iReturn);

            case "add":
                return invokeAdd(frame, hType, hArg, iReturn);

            case "annotate":
                return invokeAnnotate(frame, hType, hArg, iReturn);

            case "sub":
                return invokeSub(frame, hType, hArg, iReturn);

            case "and":
                return invokeAnd(frame, hType, hArg, iReturn);

            case "or":
                return invokeOr(frame, hType, hArg, iReturn);

            case "parameterize":
                return invokeParameterize(frame, hType, hArg, iReturn);

            case "purify":
                return invokePurify(frame, hType, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        TypeHandle hType = (TypeHandle) hTarget;
        switch (method.getName())
            {
            case "dump":
                return frame.assignValue(iReturn,
                    xString.makeHandle(hType.getUnsafeDataType().ensureTypeInfo().toString(true)));
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        TypeHandle hType = (TypeHandle) hTarget;
        switch (method.getName())
            {
            case "accessSpecified":
                return invokeAccessSpecified(frame, hType, aiReturn);

            case "annotated":
                return invokeAnnotated(frame, hType, aiReturn);

            case "contained":
                return invokeContained(frame, hType, aiReturn);

            case "fromClass":
                return invokeFromClass(frame, hType, aiReturn);

            case "fromProperty":
                return invokeFromProperty(frame, hType, aiReturn);

            case "modifying":
                return invokeModifying(frame, hType, aiReturn);

            case "relational":
                return invokeRelational(frame, hType, aiReturn);

            case "named":
                return invokeNamed(frame, hType, aiReturn);

            case "parameterized":
                return invokeParameterized(frame, hType, aiReturn);

            case "resolveFormalType":
                return invokeResolveFormalType(frame, hType, (StringHandle) ahArg[0], aiReturn);

            case "structConstructor":
                return invokeStructConstructor(frame, hType, ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        TypeHandle hTypeThis = (TypeHandle) hTarget;

        // hArg may be a Type, a Method, or a Property
        if (hArg instanceof TypeHandle hTypeThat)
            {
            if (hTypeThis.getUnsafeDataType().isImmutableOnly())
                {
                return ensureImmutable(frame, hTypeThat, iReturn);
                }
            if (hTypeThat.getUnsafeDataType().isImmutableOnly())
                {
                return ensureImmutable(frame, hTypeThis, iReturn);
                }
            return makeRelationalType(frame, hTypeThis, hTypeThat,
                    ConstantPool::ensureIntersectionTypeConstant, iReturn);
            }
        else if (hArg instanceof MethodHandle)
            {
            // TODO
            throw new UnsupportedOperationException();
            }
        else if (hArg instanceof PropertyHandle)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        return super.invokeAdd(frame, hTarget, hArg, iReturn);
        }

    private int ensureImmutable(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant type = hType.getUnsafeDataType();
        return type.isImmutabilitySpecified()
                ? frame.assignValue(iReturn, hType)
                : frame.assignValue(iReturn,
                    type.freeze().ensureTypeHandle(frame.f_context.f_container));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        TypeHandle hTypeThis = (TypeHandle) hTarget;

        // hArg may be a Type, a Method, or a Property
        if (hArg instanceof TypeHandle hTypeThat)
            {
            return makeRelationalType(frame, hTypeThis, hTypeThat,
                    ConstantPool::ensureDifferenceTypeConstant, iReturn);
            }
        else if (hArg instanceof MethodHandle)
            {
            // TODO
            throw new UnsupportedOperationException();
            }
        else if (hArg instanceof PropertyHandle)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        return super.invokeAdd(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
//        TypeHandle hTypeThis = (TypeHandle) hTarget;
//
//        // hArg is a Type
//        if (hArg instanceof TypeHandle hTypeThat)
//            {
//            return makeRelationalType(frame, hTypeThis, hTypeThat,
//                    ConstantPool::ensure???TypeConstant, iReturn);
//            }

        return super.invokeOr(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        TypeHandle hTypeThis = (TypeHandle) hTarget;

        // hArg is a Type
        if (hArg instanceof TypeHandle hTypeThat)
            {
            return makeRelationalType(frame, hTypeThis, hTypeThat,
                    ConstantPool::ensureUnionTypeConstant, iReturn);
            }

        return super.invokeOr(frame, hTarget, hArg, iReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(
            (((TypeHandle) hValue1).getDataType()).equals(((TypeHandle) hValue2).getDataType())));
        }

    @Override
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xOrdered.makeHandle(
            (((TypeHandle) hValue1).getDataType()).compareTo(((TypeHandle) hValue2).getDataType())));
        }

    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn,
            xInt64.makeHandle(((TypeHandle) hTarget).getDataType().hashCode()));
        }


    // ----- IndexSupport (turtle types only) ------------------------------------------------------

    @Override
    public long size(ObjectHandle hTarget)
        {
        TypeConstant type = ((TypeHandle) hTarget).getDataType();
        return type.getParamsCount();
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        TypeConstant type   = ((TypeHandle) hTarget).getDataType();
        int          nIndex = (int) lIndex;

        return nIndex >= 0 && nIndex < type.getParamsCount()
            ? frame.assignValue(iReturn, type.getParamType(nIndex).ensureTypeHandle(frame.f_context.f_container))
            : frame.raiseException(xException.outOfBounds(frame, lIndex, type.getParamsCount()));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        return frame.raiseException(xException.immutableObject(frame));
        }

    @Override
    public TypeConstant getElementType(Frame frame, ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        throw xException.unsupported(frame).getException();
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: childTypes.get()
     */
    public int getPropertyChildTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            return getForeignChildTypes(frame, hType, iReturn);
            }

        Container        container  = frame.f_context.f_container;
        TypeComposition  clzListMap = container.resolveClass(ensureListMapType(container));
        ObjectHandle[][] aah        = collectChildTypes(frame, hType);
        StringHandle[]   ahName     = (StringHandle[]) aah[0];
        TypeHandle[]     ahType     = (TypeHandle[])   aah[1];

        return Utils.constructListMap(frame, clzListMap,
                xArray.makeStringArrayHandle(ahName),
                xArray.createImmutableArray(ensureTypeArrayComposition(container), ahType),
                iReturn);
        }

    private ObjectHandle[][] collectChildTypes(Frame frame, TypeHandle hType)
        {
        // bridge from one module to another if necessary
        TypeConstant typeTarget = hType.getDataType();
        if (typeTarget.isSingleUnderlyingClass(false))
            {
            IdentityConstant id = typeTarget.getSingleUnderlyingClass(false);
            if (id instanceof PackageConstant)
                {
                PackageStructure pkg = (PackageStructure) id.getComponent();
                if (pkg.isModuleImport())
                    {
                    typeTarget = pkg.getImportedModule().getIdentityConstant().getType();
                    }
                }
            }

        TypeInfo                infoTarget = typeTarget.ensureTypeInfo();
        ConstantPool            poolCtx    = frame.poolContext();
        Container               container  = frame.f_context.f_container;
        Map<String, ChildInfo>  mapInfos   = infoTarget.getChildInfosByName();
        int                     cInfos     = mapInfos.size();
        StringHandle[]          ahName     = new StringHandle[cInfos];
        TypeHandle[]            ahType     = new TypeHandle[cInfos];
        int                     ix         = 0;
        for (String sName : mapInfos.keySet())
            {
            ahName[ix  ] = xString.makeHandle(sName);
            ahType[ix++] = infoTarget.calculateChildType(poolCtx, sName).ensureTypeHandle(container);
            }

        return new ObjectHandle[][]{ahName, ahType};
        }

    private int getForeignChildTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    Container        container  = frame.f_context.f_container;
                    TypeHandle       hType      = typeForeign.ensureTypeHandle(container);
                    ObjectHandle[][] aah        = collectChildTypes(frame, hType);
                    TypeComposition  clzListMap = container.resolveClass(ensureListMapType(container));
                    StringHandle[]   ahName     = (StringHandle[]) aah[0];
                    TypeHandle[]     ahType     = (TypeHandle[])   aah[1];

                    for (int i = 0, c = ahType.length; i < c; i++)
                        {
                        ahType[i] = xRTType.makeHandle(null, ahType[i].getDataType(), false);
                        }

                    return Utils.constructListMap(frame, clzListMap,
                            xArray.makeStringArrayHandle(ahName),
                            xArray.createImmutableArray(ensureTypeArrayComposition(container), ahType),
                            0);
                    }

                public String toString()
                    {
                    return "getForeignChildTypes";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty map
        return Utils.constructListMap(frame, container.resolveClass(ensureListMapType(container)),
                xString.ensureEmptyArray(), ensureEmptyTypeArray(container), iReturn);
        }

    /**
     * Implements property: constants.get()
     */
    public int getPropertyConstants(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            return getForeignConstants(frame, hType, iReturn);
            }

        TypeConstant                        typeTarget = hType.getDataType();
        TypeInfo                            infoTarget = typeTarget.ensureTypeInfo();
        Map<PropertyConstant, PropertyInfo> mapProps   = infoTarget.getProperties();

        ArrayList<PropertyInfo> listInfo = new ArrayList<>(mapProps.size());
        for (PropertyInfo infoProp : mapProps.values())
            {
            if (infoProp.isConstant())
                {
                listInfo.add(infoProp);
                }
            }

        return makePropertyArray(frame, typeTarget, listInfo, iReturn);
        }

    private int getForeignConstants(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    switch (getPropertyConstants(frame, hType, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return createProxyArray(frame, (ArrayHandle) frame.popStack(),
                                    xRTFunction.INSTANCE.getCanonicalClass(), 0);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                createProxyArray(frameCaller, (ArrayHandle) frameCaller.popStack(),
                                    xRTFunction.INSTANCE.getCanonicalClass(), 0));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;
                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "getForeignConstants";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty array
        return frame.assignValue(iReturn, xRTProperty.ensureEmptyArray(container));
        }

    /**
     * Implements property: constructors.get()
     */
    public int getPropertyConstructors(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            return getForeignConstructors(frame, hType, iReturn);
            }

        // the actual construction process uses a "construct" function as a structural initializer
        // and an optional "finally" method as a post-object-instantiation (i.e. first time that
        // "this" object exists) method. reflection hides this complicated process, and instead
        // pretends that each constructor is a factory function that returns an instance of the
        // target type. since each constructor has its own unique sequence of parameter types, the
        // exact type of a resulting array of these factory functions is not expressible, so instead
        // we use "Array<Function<<>, <TargetType>>>", i.e. an array of functions that have zero or
        // more parameters and return the TargetType

        // to have constructors, the type must be a class, it must not be abstract, it must not be a
        // singleton, and all three of these conditions are checked by TypeInfo.isNewable().
        // additionally,
        // TODO GG it must be part of the type system of the current container (which means that the
        //         type is a class of a module that is loaded in this container, or shared with this
        //         container from its parent container, or loaded in a container that is nested
        //         within this container)
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        // each of the generated constructor functions (not the "construct" functions) for a virtual
        // child will require a parent reference to be passed as the first argument
        TypeConstant typeParent = null;
        if (infoTarget.isVirtualChildClass())
            {
            typeParent = hType.getOuterType();
            assert typeParent != null;
            assert !typeParent.equals(pool().typeObject());
            }

        ObjectHandle[] ahFunctions;
        if (infoTarget.isNewable(false, ErrorListener.BLACKHOLE))
            {
            ConstantPool            pool        = frame.poolContext();
            TypeComposition         clzTarget   = typeTarget.ensureClass(frame);
            ArrayList<ObjectHandle> listHandles = new ArrayList<>();
            for (MethodConstant idConstr :
                    infoTarget.findMethods("construct", -1, TypeInfo.MethodKind.Constructor))
                {
                MethodInfo      infoMethod  = infoTarget.getMethodById(idConstr, true);
                MethodStructure constructor = infoMethod.getTopmostMethodStructure(infoTarget);
                Parameter[]     aParams     = constructor.getParamArray();
                TypeConstant[]  atypeParams = infoMethod.getSignature().getRawParams();

                // each constructor function will be of a certain type, which differs only in the
                // additional parameters that each constructor has; for a virtual child, all of the
                // parameters are shifted to the right by one to prepend a "parent" parameter
                if (typeParent != null)
                    {
                    int cParams = atypeParams.length;
                    assert cParams == aParams.length;

                    // add the required parent reference as a parameter type
                    TypeConstant[] atypeNew = new TypeConstant[cParams + 1];
                    atypeNew[0] = typeParent;
                    System.arraycopy(atypeParams, 0, atypeNew, 1, cParams);

                    // add the required parent reference as a parameter
                    Parameter[] aParamsNew = new Parameter[cParams + 1];
                    aParamsNew[0] = new Parameter(pool, typeParent, "0", null, false, 0, false);
                    for (int i = 0; i < cParams; ++i)
                        {
                        Parameter param = aParams[i];
                        assert !param.isTypeParameter();
                        aParamsNew[i+1] = new Parameter(pool, param.getType(), param.getName(),
                                param.getDefaultValue(), false, i+1, false);
                        }

                    atypeParams = atypeNew;
                    aParams     = aParamsNew;
                    }

                TypeConstant typeConstructor = pool.buildFunctionType(atypeParams, typeTarget);

                listHandles.add(xRTFunction.makeConstructorHandle(frame, constructor,
                            typeConstructor, clzTarget, aParams, typeParent != null));
                }
            ahFunctions = listHandles.toArray(Utils.OBJECTS_NONE);
            }
        else
            {
            ahFunctions = Utils.OBJECTS_NONE;
            }

        TypeComposition clzArray = xRTFunction.ensureConstructorArray(frame, typeTarget, typeParent);
        if (Op.anyDeferred(ahFunctions))
            {
            ObjectHandle hDeferred = new DeferredArrayHandle(clzArray, ahFunctions);
            return hDeferred.proceed(frame,
                frameCaller -> frameCaller.assignValue(iReturn, frameCaller.popStack()));
            }

        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahFunctions));
        }

    private int getForeignConstructors(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    switch (getPropertyConstructors(frame, hType, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return createProxyArray(frame, (ArrayHandle) frame.popStack(),
                                    xRTFunction.INSTANCE.getCanonicalClass(), 0);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                createProxyArray(frameCaller, (ArrayHandle) frameCaller.popStack(),
                                    xRTFunction.INSTANCE.getCanonicalClass(), 0));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;
                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "getForeignConstructors";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty array
        return frame.assignValue(iReturn, xRTFunction.ensureEmptyArray(container));
        }

    /**
     * Implements property: explicitlyImmutable.get()
     */
    public int getPropertyExplicitlyImmutable(Frame frame, TypeHandle hType, int iReturn)
        {
        return frame.assignValue(iReturn,
                xBoolean.makeHandle(hType.getDataType().isImmutabilitySpecified()));
        }

    /**
     * Implements property: form.get()
     */
    public int getPropertyForm(Frame frame, TypeHandle hType, int iReturn)
        {
        return Utils.assignInitializedEnum(frame,
                makeFormHandle(frame, hType.isForeign() ? null : hType.getDataType()), iReturn);
        }

    /**
     * Implements property: functions.get()
     */
    public int getPropertyFunctions(Frame frame, TypeHandle hType, int iReturn)
        {
        Container container = frame.f_context.f_container;
        if (hType.isForeign())
            {
            return getForeignFunctions(frame, hType, iReturn);
            }

        TypeConstant                    typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo> mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<ObjectHandle>         listHandles = new ArrayList<>(mapMethods.size());
        boolean                         fDeferred   = false;
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodConstant id   = entry.getKey();
            MethodInfo     info = entry.getValue();
            if (info.isFunction() && id.isTopLevel())
                {
                ObjectHandle hFn = xRTFunction.makeHandle(frame, info.getHead().getMethodStructure());
                fDeferred |= Op.isDeferred(hFn);
                listHandles.add(hFn);
                }
            }

        TypeComposition clzArray    = xRTFunction.ensureArrayComposition(container);
        ObjectHandle[]  ahFunctions = listHandles.toArray(Utils.OBJECTS_NONE);
        if (fDeferred)
            {
            ObjectHandle hDeferred = new DeferredArrayHandle(clzArray, ahFunctions);
            return hDeferred.proceed(frame,
                frameCaller -> frameCaller.assignValue(iReturn, frameCaller.popStack()));
            }

        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahFunctions));
        }

    private int getForeignFunctions(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    switch (getPropertyFunctions(frame, hType, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return createProxyArray(frame, (ArrayHandle) frame.popStack(),
                                    xRTFunction.INSTANCE.getCanonicalClass(), 0);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                createProxyArray(frameCaller, (ArrayHandle) frameCaller.popStack(),
                                    xRTFunction.INSTANCE.getCanonicalClass(), 0));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;
                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "getForeignFunctions";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty array
        return frame.assignValue(iReturn, xRTFunction.ensureEmptyArray(container));
        }

    /**
     * Implements property: methods.get()
     */
    public int getPropertyMethods(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            return getForeignMethods(frame, hType, iReturn);
            }

        TypeConstant            typeTarget  = hType.getDataType();
        var                     aMethods    = typeTarget.ensureTypeInfo().sortedMethods();
        ArrayList<ObjectHandle> listHandles = new ArrayList<>(aMethods.length);

        for (Map.Entry<MethodConstant, MethodInfo> entry : aMethods)
            {
            MethodConstant idMethod = entry.getKey();
            MethodInfo     info     = entry.getValue();
            if (!info.isCapped() && !info.isFunction() && !info.isConstructor()
                    && idMethod.isTopLevel())
                {
                listHandles.add(xRTMethod.makeHandle(frame, typeTarget, info.getIdentity()));
                }
            }

        TypeComposition clzArray  = xRTMethod.ensureArrayComposition(frame, typeTarget);
        ObjectHandle[]  ahMethods = listHandles.toArray(Utils.OBJECTS_NONE);
        if (Op.anyDeferred(ahMethods))
            {
            ObjectHandle hDeferred = new DeferredArrayHandle(clzArray, ahMethods);
            return hDeferred.proceed(frame,
                frameCaller -> frameCaller.assignValue(iReturn, frameCaller.popStack()));
            }
        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahMethods));
        }

    private int getForeignMethods(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    switch (getPropertyMethods(frame, hType, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return createProxyArray(frame, (ArrayHandle) frame.popStack(),
                                    xRTMethod.INSTANCE.getCanonicalClass(), 0);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                createProxyArray(frameCaller, (ArrayHandle) frameCaller.popStack(),
                                    xRTMethod.INSTANCE.getCanonicalClass(), 0));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;
                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "getForeignMethods";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty array
        return frame.assignValue(iReturn, xRTMethod.ensureEmptyArray(container));
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertyProperties(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            return getForeignProperties(frame, hType, iReturn);
            }

        TypeConstant            typeTarget = hType.getDataType();
        var                     aProps     = typeTarget.ensureTypeInfo().sortedProperties();
        ArrayList<PropertyInfo> listProps  = new ArrayList<>(aProps.length);

        for (Map.Entry<PropertyConstant, PropertyInfo> entry : aProps)
            {
            PropertyConstant idProp   = entry.getKey();
            PropertyInfo     infoProp = entry.getValue();
            if (!infoProp.isConstant() && idProp.isTopLevel())
                {
                listProps.add(infoProp);
                }
            }

        return makePropertyArray(frame, typeTarget, listProps, iReturn);
        }

    private int getForeignProperties(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    switch (getPropertyProperties(frame, hType, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return createProxyArray(frame, (ArrayHandle) frame.popStack(),
                                    xRTProperty.INSTANCE.getCanonicalClass(), 0);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                createProxyArray(frameCaller, (ArrayHandle) frameCaller.popStack(),
                                    xRTProperty.INSTANCE.getCanonicalClass(), 0));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;
                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "getForeignProperties";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty array
        return frame.assignValue(iReturn, xRTProperty.ensureEmptyArray(container));
        }

    /**
     * Implements property: recursive.get()
     */
    public int getPropertyRecursive(Frame frame, TypeHandle hType, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(hType.getDataType().containsRecursiveType()));
        }

    /**
     * Implements property: template.get()
     */
    public int getPropertyTemplate(Frame frame, TypeHandle hType, int iReturn)
        {
        return frame.assignValue(iReturn,
                xRTTypeTemplate.makeHandle(frame.f_context.f_container, hType.getDataType()));
        }

    /**
     * Implements property: typeSystem.get()
     */
    public int getPropertyTypeSystem(Frame frame, TypeHandle hType, int iReturn)
        {
        // conceptually, a type comes from some "origin" type system; e.g. String comes from the
        // primordial (-1) type system, but it is not wrong to return a more specific type system
        // that includes that same type e.g. the "MyApp" type system that linked in String as part
        // of its type system
        if (hType.isForeign())
            {
            throw new UnsupportedOperationException("create a proxy");
            }
        else
            {
            return frame.f_context.f_container.ensureTypeSystemHandle(frame, iReturn);
            }
        }

    /**
     * Implements property: underlyingTypes.get()
     */
    public int getPropertyUnderlyingTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        Container container = frame.f_context.f_container;
        if (hType.isForeign())
            {
            return getForeignUnderlyingTypes(frame, hType, iReturn);
            }

        TypeConstant   typeTarget  = hType.getDataType();
        TypeConstant[] aUnderlying = TypeConstant.NO_TYPES;
        if (typeTarget.isModifyingType())
            {
            aUnderlying = new TypeConstant[] {typeTarget.getUnderlyingType()};
            }
        else if (typeTarget.isRelationalType())
            {
            aUnderlying = new TypeConstant[] {typeTarget.getUnderlyingType(), typeTarget.getUnderlyingType2()};
            }
        else if (typeTarget.isFormalTypeSequence())
            {
            aUnderlying = new TypeConstant[] {typeTarget}; // turtle type
            }

        TypeHandle[] ahTypes = new TypeHandle[aUnderlying.length];
        for (int i = 0, c = ahTypes.length; i < c; ++i)
            {
            ahTypes[i] = aUnderlying[i].ensureTypeHandle(container);
            }

        ObjectHandle hArray = xArray.createImmutableArray(
                                ensureTypeArrayComposition(container), ahTypes);
        return frame.assignValue(iReturn, hArray);
        }

    private int getForeignUnderlyingTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    switch (getForeignUnderlyingTypes(frame, hType, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return createProxyArray(frame, (ArrayHandle) frame.popStack(),
                                    xRTProperty.INSTANCE.getCanonicalClass(), 0);

                        case Op.R_CALL:
                            frame.m_frameNext.addContinuation(frameCaller ->
                                createProxyArray(frameCaller, (ArrayHandle) frameCaller.popStack(),
                                    xRTProperty.INSTANCE.getCanonicalClass(), 0));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;
                        default:
                            throw new IllegalStateException();
                        }
                    }

                public String toString()
                    {
                    return "getForeignUnderlyingTypes";
                    }
                };

            return containerForeign.ensureServiceContext().sendOp1Request(frame, opCall, iReturn);
            }

        // cannot find the corresponding container; just return an empty array
        return frame.assignValue(iReturn, ensureEmptyTypeArray(container));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Access accessSpecified()}.
     */
    protected int invokeAccessSpecified(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        if (type.isAccessSpecified())
            {
            ObjectHandle hEnum = Utils.ensureInitializedEnum(frame,
                makeAccessHandle(frame, type.getAccess()));

            return frame.assignConditionalDeferredValue(aiReturn, hEnum);
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code Type!<> annotate(Annotation annotation)}.
     */
    protected int invokeAnnotate(Frame frame, TypeHandle hType, ObjectHandle hArg, int iReturn)
        {
        ConstantPool  pool     = frame.poolContext();
        TypeConstant  typeThis = hType.getDataType();
        GenericHandle hAnno    = (GenericHandle) hArg;
        ClassHandle   hClass   = (ClassHandle) hAnno.getField(frame, "mixinClass");
        ArrayHandle   hArgs    = (ArrayHandle) hAnno.getField(frame, "arguments");

        if (xArray.INSTANCE.size(hArgs) > 0)
            {
            // TODO args
            throw new UnsupportedOperationException();
            }

        TypeConstant typeAnno = hClass.getType().getParamType(0);
        if (typeThis.isShared(pool) && typeAnno.isShared(pool))
            {
            ClassConstant clzAnno = (ClassConstant) typeAnno.getDefiningConstant();
            Annotation    anno    = pool.ensureAnnotation(clzAnno);

            TypeConstant typeResult = pool.ensureAnnotatedTypeConstant(typeThis, anno);
            return frame.assignValue(iReturn, typeResult.ensureTypeHandle(frame.f_context.f_container));
            }
        return frame.raiseException(xException.invalidType(frame,
                "No common TypeSystem for (" + typeThis + " and " + typeAnno + ")"));
        }

    /**
     * Implementation for: {@code conditional Annotation annotated()}.
     */
    protected int invokeAnnotated(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        if (hType.isForeign())
            {
            return invokeForeignAnnotated(frame, hType, aiReturn);
            }

        TypeConstant typeThis = hType.getDataType();
        if (typeThis.isAnnotated())
            {
            Annotation       annotation = typeThis.getAnnotations()[0];
            IdentityConstant idClass    = (IdentityConstant) annotation.getAnnotationClass();
            Constant[]       aconstArg  = annotation.getParams();

            ObjectHandle   hClass = frame.getConstHandle(idClass);
            int            cArgs  = aconstArg.length;
            ObjectHandle[] ahArg;
            if (cArgs == 0)
                {
                ahArg = Utils.OBJECTS_NONE;
                }
            else
                {
                ahArg = new ObjectHandle[cArgs];
                for (int i = 0; i < cArgs; i++)
                    {
                    Constant constArg = aconstArg[i];
                    ahArg[i] = constArg instanceof RegisterConstant constReg
                        ? makeRegisterHandle(frame, constReg.getRegisterIndex())
                        : makeArgumentHandle(frame, constArg);
                    }
                }

            return Op.isDeferred(hClass)
                    ? hClass.proceed(frame, frameCaller ->
                        resolveInvokeAnnotatedArgs(
                            frameCaller, (ClassHandle) frameCaller.popStack(), ahArg, aiReturn))
                    : resolveInvokeAnnotatedArgs(frame, (ClassHandle) hClass, ahArg, aiReturn);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    private int invokeForeignAnnotated(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant typeForeign      = hType.getUnsafeDataType();
        Container    container        = frame.f_context.f_container;
        Container    containerForeign = container.f_runtime.findContainer(typeForeign.getConstantPool());
        if (containerForeign != null)
            {
            Op opCall = new Op()
                {
                public int process(Frame frame, int iPC)
                    {
                    TypeHandle hType = typeForeign.ensureTypeHandle(frame.f_context.f_container);

                    return invokeAnnotated(frame, hType, new int[] {0, 1});
                    }
                public String toString()
                    {
                    return "invokeForeignAnnotated";
                    }
                };

            return containerForeign.ensureServiceContext().sendOpNRequest(frame, opCall, aiReturn);
            }

        // cannot find the corresponding container
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    private int resolveInvokeAnnotatedArgs(Frame frame, ClassHandle hClass,
                                           ObjectHandle[] ahArg, int[] aiReturn)
        {
        if (Op.anyDeferred(ahArg))
            {
            Frame.Continuation stepNext = frameCaller ->
                    completeInvokeAnnotated(frameCaller, hClass, ahArg, aiReturn);
            return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }
        return completeInvokeAnnotated(frame, hClass, ahArg, aiReturn);
        }

    private int completeInvokeAnnotated(Frame frame, ClassHandle hClass,
                                        ObjectHandle[] ahArg, int[] aiReturn)
        {
        frame.assignValue(aiReturn[0], xBoolean.TRUE);
        return Utils.constructAnnotation(frame, hClass, ahArg, aiReturn[1]);
        }

    /**
     * Implementation for: {@code conditional Type!<> contained()}.
     */
    protected int invokeContained(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        // REVIEW CP: include PropertyClassTypeConstant?
        if (typeTarget.isVirtualChild() || typeTarget.isAnonymousClass())
            {
            TypeHandle hParent = typeTarget.getParentType().ensureTypeHandle(frame.f_context.f_container);
            return frame.assignValues(aiReturn, xBoolean.TRUE, hParent);
            }
        else
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        }

    /**
     * Implementation for: {@code conditional Class fromClass()}.
     */
    protected int invokeFromClass(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        if (!hType.isForeign())
            {
            TypeConstant typeTarget = hType.getDataType();
            if (typeTarget.isExplicitClassIdentity(true))
                {
                typeTarget = typeTarget.removeAccess().removeImmutable().resolveAutoNarrowingBase();

                IdentityConstant idClz = frame.poolContext().ensureClassConstant(typeTarget);

                return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(idClz));
                }
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Property fromProperty()}.
     */
    protected int invokeFromProperty(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        if (!hType.isForeign())
            {
            TypeConstant type = hType.getDataType();
            if (type.isSingleDefiningConstant())
                {
                Constant constDef = type.getDefiningConstant();
                if (constDef instanceof PropertyConstant idProp)
                    {
                    TypeConstant typeParent = idProp.getParentConstant().getType();
                    PropertyInfo infoProp   = frame.poolContext().ensureAccessTypeConstant(
                            typeParent, Access.PRIVATE).ensureTypeInfo().findProperty(idProp, true);

                    if (infoProp != null)
                        {
                        ObjectHandle hProp = xRTProperty.makeHandle(frame, typeParent, infoProp);
                        return Op.isDeferred(hProp)
                            ? hProp.proceed(frame, frameCaller ->
                                frameCaller.assignValues(aiReturn, xBoolean.TRUE, frameCaller.popStack()))
                            : frame.assignValues(aiReturn, xBoolean.TRUE, hProp);
                        }
                    }
                }
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Type!<> modifying()}.
     */
    protected int invokeModifying(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        return type.isModifyingType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        type.getUnderlyingType().ensureTypeHandle(frame.f_context.f_container))
                : frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional String named()}.
     */
    protected int invokeNamed(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        String       sName = null;
        TypeConstant type  = hType.getDataType();

        if (type.isSingleDefiningConstant())
            {
            Constant id = type.getDefiningConstant();
            switch (id.getFormat())
                {
                case Module:
                case Package:
                case Class:
                case NativeClass:
                case Property:
                case TypeParameter:
                case FormalTypeChild:
                case Typedef:
                    sName = ((IdentityConstant) id).getName();
                    break;

                case ThisClass:
                case ParentClass:
                case ChildClass:
                    sName = ((PseudoConstant) id).getDeclarationLevelClass().getName();
                    break;
                }
            }
        else if (type instanceof RecursiveTypeConstant typeRecursive)
            {
            sName = typeRecursive.getTypedef().getName();
            }

        return sName == null
            ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
            : frame.assignValues(aiReturn, xBoolean.TRUE, xString.makeHandle(sName));
        }

    /**
     * Implementation for: {@code conditional Type!<>[] parameterized()}.
     */
    protected int invokeParameterized(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        if (!type.isParamsSpecified())
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        TypeConstant[] atypes  = type.getParamTypesArray();
        int            cTypes  = atypes.length;
        TypeHandle[]   ahTypes = new TypeHandle[cTypes];
        for (int i = 0; i < cTypes; ++i)
            {
            ahTypes[i] = atypes[i].normalizeParameters().ensureTypeHandle(frame.f_context.f_container);
            }

        ObjectHandle hArray = xArray.createImmutableArray(
                                ensureTypeArrayComposition(frame.f_context.f_container), ahTypes);
        return frame.assignValues(aiReturn, xBoolean.TRUE, hArray);
        }

    /**
     * Implementation for: {@code Type!<> parameterize(Type!<>... paramTypes)}.
     */
    protected int invokeParameterize(Frame frame, TypeHandle hType, ObjectHandle hArg, int iReturn)
        {
        if (hType.isForeign())
            {
            return frame.raiseException(xException.invalidType(frame,
                "Pure type " + hType.getDataType().getValueString()));
            }

        ObjectHandle[] ahFormalTypes;
        int            cFormalTypes;
        if (hArg instanceof ArrayHandle)
            {
            xArray template = (xArray) hArg.getTemplate();

            try
                {
                ahFormalTypes = template.toArray(frame, hArg);
                cFormalTypes  = (int) template.size(hArg);
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }
        else if (hArg == ObjectHandle.DEFAULT)
            {
            ahFormalTypes = Utils.OBJECTS_NONE;
            cFormalTypes  = 0;
            }
        else
            {
            // TODO GG return a continuation that turns the sequence into an array and calls this?
            throw new UnsupportedOperationException();
            }

        ConstantPool   pool         = frame.poolContext();
        TypeConstant   typeThis     = hType.getDataType();
        TypeConstant[] atypeParams  = new TypeConstant[cFormalTypes];
        for (int i = 0; i < cFormalTypes; ++i)
            {
            TypeHandle   hTypeParam = (TypeHandle) ahFormalTypes[i];
            TypeConstant typeParam  = hTypeParam.getUnsafeDataType();

            if (hTypeParam.isForeign())
                {
                pool = typeParam.getConstantPool();
                }

            atypeParams[i] = typeParam;
            }

        try
            {
            TypeConstant typeResult = typeThis.adoptParameters(pool, atypeParams);
            return frame.assignValue(iReturn, typeResult.ensureTypeHandle(frame.f_context.f_container));
            }
        catch (RuntimeException e)
            {
            // this is temporary; only correct for one type argument
            return frame.raiseException(xException.invalidType(frame,
                "No common TypeSystem for (" + typeThis + " and " + atypeParams[0] + ")"));
            }
        }

    /**
     * Implementation for: {@code conditional Type!<> purify()}.
     */
    protected int invokePurify(Frame frame, TypeHandle hType, int iReturn)
        {
        return frame.assignValue(iReturn, hType); // TODO GG - implement Pure type constant etc.
        }

    /**
     * Implementation for: {@code conditional (Type!<>, Type!<>) relational()}.
     */
    protected int invokeRelational(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        Container    container = frame.f_context.f_container;
        TypeConstant type      = hType.getDataType();
        return type.isRelationalType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        type.getUnderlyingType().ensureTypeHandle(container),
                        type.getUnderlyingType2().ensureTypeHandle(container))
                : frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code Boolean isA(Type! that)}.
     */
    protected int invokeIsA(Frame frame, TypeHandle hThis, TypeHandle hThat, int iReturn)
        {
        TypeConstant typeThis = hThis.getUnsafeDataType();
        TypeConstant typeThat = hThat.getUnsafeDataType();
        return frame.assignValue(iReturn, xBoolean.makeHandle(typeThis.isA(typeThat)));
        }

    /**
     * Implementation for: {@code conditional Type resolveFormalType(String)}
     */
    public int invokeResolveFormalType(Frame frame, TypeHandle hType, StringHandle hName, int[] aiReturn)
        {
        TypeConstant type  = hType.getUnsafeType().getParamType(0);
        TypeConstant typeR = type.resolveGenericType(hName.getStringValue());

        return typeR == null
            ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
            : frame.assignValues(aiReturn, xBoolean.TRUE, typeR.ensureTypeHandle(frame.f_context.f_container));
        }

    /**
     * Implementation for:
     *      {@code conditional function DataType(Struct) structConstructor(OuterType? outer = Null)}
     */
    public int invokeStructConstructor(Frame frame, TypeHandle hType, ObjectHandle hOuter, int[] aiReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        if (hOuter == ObjectHandle.DEFAULT)
            {
            hOuter = xNullable.NULL;
            }

        TypeConstant typeParent = null;
        if (infoTarget.isVirtualChildClass())
            {
            if (hOuter == xNullable.NULL)
                {
                return frame.raiseException(
                    xException.illegalArgument(frame, "Parent is not specified"));
                }
            typeParent = hType.getOuterType();
            assert typeParent != null;
            assert !typeParent.equals(pool().typeObject());
            }

        if (!infoTarget.isNewable(false, ErrorListener.BLACKHOLE))
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        ConstantPool    pool       = frame.poolContext();
        TypeConstant    typeStruct = pool.ensureAccessTypeConstant(typeTarget, Access.STRUCT);
        TypeComposition clzTarget  = typeTarget.ensureClass(frame);

        // create a synthetic constructor handle
        TypeConstant[] atypeParams;
        Parameter[]    aParams;
        if (typeParent == null)
            {
            atypeParams = new TypeConstant[] {typeStruct};
            aParams     = new Parameter[]
                            {
                            new Parameter(pool, typeStruct, "0", null, false, 0, false)
                            };
            }
        else
            {
            atypeParams = new TypeConstant[] {typeParent, typeStruct};
            aParams     = new Parameter[]
                            {
                            new Parameter(pool, typeParent, "0", null, false, 0, false),
                            new Parameter(pool, typeStruct, "1", null, false, 1, false)
                            };
            }

        TypeConstant   typeConstructor = pool.buildFunctionType(atypeParams, typeTarget);
        FunctionHandle hConstructor    = (FunctionHandle) xRTFunction.makeConstructorHandle(frame,
                null, typeConstructor, clzTarget, aParams, typeParent != null);
        if (typeParent != null)
            {
            hConstructor = hConstructor.bind(frame, 0, hOuter);
            }
        return frame.assignValues(aiReturn, xBoolean.TRUE, hConstructor);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given an Access value, determine the corresponding Ecstasy "Access" value.
     *
     * @param frame   the current frame
     * @param access  an Access value
     *
     * @return the handle to the appropriate Ecstasy {@code Type.Access} enum value
     */
    public static EnumHandle makeAccessHandle(Frame frame, Access access)
        {
        xEnum enumAccess = (xEnum) INSTANCE.f_container.getTemplate("reflect.Access");
        return switch (access)
            {
            case PUBLIC    -> enumAccess.getEnumByName("Public");
            case PROTECTED -> enumAccess.getEnumByName("Protected");
            case PRIVATE   -> enumAccess.getEnumByName("Private");
            case STRUCT    -> enumAccess.getEnumByName("Struct");
            };
        }

    /**
     * Given a TypeConstant, determine the Ecstasy "Form" value for the type.
     *
     * @param frame  the current frame
     * @param type   a TypeConstant used at runtime (null for a foreign type)
     *
     * @return the handle to the appropriate Ecstasy {@code Type.Form} enum value
     */
    protected static EnumHandle makeFormHandle(Frame frame, TypeConstant type)
        {
        xEnum enumForm = (xEnum) INSTANCE.f_container.getTemplate("reflect.Type.Form");

        if (type == null)
            {
            // this is an indicator of a foreign type
            return enumForm.getEnumByName("Pure");
            }

        switch (type.getFormat())
            {
            case TerminalType:
                if (type.isSingleDefiningConstant())
                    {
                    return switch (type.getDefiningConstant().getFormat())
                        {
                        case NativeClass ->
                            enumForm.getEnumByName("Pure");

                        case Module, Package, Class, ThisClass, ParentClass, ChildClass,
                             IsClass, IsConst, IsEnum, IsModule, IsPackage ->
                            enumForm.getEnumByName("Class");

                        case Property ->
                            enumForm.getEnumByName("FormalProperty");

                        case TypeParameter ->
                            enumForm.getEnumByName("FormalParameter");

                        case FormalTypeChild ->
                            enumForm.getEnumByName("FormalChild");

                        default ->
                            throw new IllegalStateException("unsupported format: " +
                                type.getDefiningConstant().getFormat());
                        };
                    }
                else
                    {
                    return enumForm.getEnumByName("Typedef");
                    }

            case ImmutableType:
                return enumForm.getEnumByName("Immutable");

            case AccessType:
                return enumForm.getEnumByName("Access");

            case AnnotatedType:
                return enumForm.getEnumByName("Annotated");

            case ServiceType:
                return enumForm.getEnumByName("Service");

            case ParameterizedType:
                // the underlying type will be "Class" or "Child"
                return makeFormHandle(frame, type.getUnderlyingType());

            case TurtleType:
                return enumForm.getEnumByName("Sequence");

            case VirtualChildType:
                return enumForm.getEnumByName("Child");

            case InnerChildType:
            case AnonymousClassType:
                return enumForm.getEnumByName("Class");

            case PropertyClassType:
                return enumForm.getEnumByName("Property");

            case UnionType:
                return enumForm.getEnumByName("Union");

            case IntersectionType:
                return enumForm.getEnumByName("Intersection");

            case DifferenceType:
                return enumForm.getEnumByName("Difference");

            case RecursiveType:
                return enumForm.getEnumByName("Typedef");

            case UnresolvedType:
            default:
                throw new IllegalStateException("unsupported type: " + type);
            }
        }

    private int makePropertyArray(Frame frame, TypeConstant typeTarget,
                                  ArrayList<PropertyInfo> listInfo, int iReturn)
        {
        if (listInfo.size() > 1)
            {
            listInfo.sort(Comparator.comparingInt(PropertyInfo::getRank));
            }

        ArrayList<ObjectHandle> listProps = new ArrayList<>(listInfo.size());
        for (PropertyInfo infoProp : listInfo)
            {
            listProps.add(xRTProperty.makeHandle(frame, typeTarget, infoProp));
            }

        ObjectHandle[]  ahProps  = listProps.toArray(Utils.OBJECTS_NONE);
        TypeComposition clzArray = xRTProperty.ensureArrayComposition(frame, typeTarget);

        if (Op.anyDeferred(ahProps))
            {
            ObjectHandle hDeferred = new DeferredArrayHandle(clzArray, ahProps);
            return hDeferred.proceed(frame,
                frameCaller -> frameCaller.assignValue(iReturn, frameCaller.popStack()));
            }

        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahProps));
        }

    public static ObjectHandle makeArgumentHandle(Frame frame, Constant constArg)
        {
        ObjectHandle hArg    = frame.getConstHandle(constArg);
        int          iResult = Op.isDeferred(hArg)
            ? hArg.proceed(frame, frameCaller ->
                    {
                    ObjectHandle hValue = frameCaller.popStack();
                    return Utils.constructArgument(frameCaller, hValue.getType(), hValue, null);
                    })
            : Utils.constructArgument(frame, hArg.getType(), hArg, null);

        switch (iResult)
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                return new DeferredCallHandle(frame.m_frameNext);

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    private ObjectHandle makeRegisterHandle(Frame frame, int nRegister)
        {
        TypeComposition clz  = REGISTER_CLZCOMP;
        MethodStructure ctor = REGISTER_CONSTRUCT;
        if (clz == null)
            {
            TypeConstant typeReg = pool().ensureEcstasyTypeConstant("reflect.Register");
            REGISTER_CLZCOMP = clz = typeReg.ensureClass(frame);
            REGISTER_CONSTRUCT = ctor = REGISTER_CLZCOMP.getTemplate().getStructure().findMethod("construct", 1);
            }

        ObjectHandle[] ahArg = new ObjectHandle[ctor.getMaxVars()];
        ahArg[0] = xInt64.makeHandle(nRegister);

        int iResult = clz.getTemplate().construct(frame, ctor, clz, null, ahArg, Op.A_STACK);
        switch (iResult)
            {
            case Op.R_NEXT:
                return frame.popStack();

            case Op.R_CALL:
                {
                DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                hDeferred.addContinuation(frameCaller ->
                     Utils.constructArgument(
                         frameCaller, REGISTER_CLZCOMP.getType(), frameCaller.popStack(), null));
                return hDeferred;
                }

            case Op.R_EXCEPTION:
                return new DeferredCallHandle(frame.m_hException);

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Convert an array or reflective objects into an array of proxies.
     */
    private int createProxyArray(Frame frame, ArrayHandle hArray, TypeComposition clzElement, int iReturn)
        {
        TypeConstant    typeElement = clzElement.getType();
        TypeComposition clzArray    = frame.f_context.f_container.resolveClass(
                frame.poolContext().ensureArrayType(typeElement));
        ProxyComposition clzProxy  = new ProxyComposition(clzElement, typeElement);

        int            cValues = (int) hArray.m_hDelegate.m_cSize;
        ObjectHandle[] ahValue = new ObjectHandle[cValues];
        for (int i = 0; i < cValues; i++)
            {
            switch (hArray.getTemplate().extractArrayValue(frame, hArray, i, Op.A_STACK))
                {
                case Op.R_NEXT:
                    ObjectHandle hElement = frame.popStack();
                    ahValue[i] = hElement instanceof TypeHandle hType
                            ? xRTType.makeForeignHandle(hType.getUnsafeType())
                            : Proxy.makeHandle(clzProxy, frame.f_context, hElement, false);
                    break;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahValue));
        }


    // ----- Composition and handle caching --------------------------------------------------------

    /**
     * @return the TypeComposition for an Array of Type
     */
    public static TypeComposition ensureTypeArrayComposition(Container container)
        {
        return container.ensureClassComposition(TYPE_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the handle for an empty Array of Type
     */
    public static ArrayHandle ensureEmptyTypeArray(Container container)
        {
        ArrayHandle haEmpty = (ArrayHandle) container.f_heap.getConstHandle(EMPTY_TYPE_ARRAY);
        if (haEmpty == null)
            {
            haEmpty = xArray.createImmutableArray(ensureTypeArrayComposition(container), Utils.OBJECTS_NONE);
            container.f_heap.saveConstHandle(EMPTY_TYPE_ARRAY, haEmpty);
            }
        return haEmpty;
        }

    /**
     * @return the TypeConstant for {@code immutable ListMap<String, Type>}
     */
    public static TypeConstant ensureListMapType(Container container)
        {
        TypeConstant type = LISTMAP_TYPE;
        if (type == null)
            {
            ConstantPool pool = container.getConstantPool();

            type = pool.ensureEcstasyTypeConstant("collections.ListMap");
            type = pool.ensureParameterizedTypeConstant(type, pool.typeString(), pool.typeType());
            LISTMAP_TYPE = type = pool.ensureImmutableTypeConstant(type);
            }
        return type;
        }

    // ----- TypeHandle support --------------------------------------------------------------------

    /**
     * Obtain a {@link TypeHandle} for the specified type.
     *
     * @param type     the {@link TypeConstant} to obtain a {@link TypeHandle} for
     * @param fShared  if false, the handle represents a "foreign" type external to the
     *                 type system that will use the handle
     *
     * @return the resulting {@link TypeHandle}
     */
    public static TypeHandle makeHandle(Container container, TypeConstant type, boolean fShared)
        {
        // unfortunately, "makeHandle" is called from places where we cannot easily invoke the
        // default initializer, so we need to do it by hand
        TypeHandle hType = fShared
            ? new TypeHandle(INSTANCE.ensureClass(container, type.getType()), null)
            : new TypeHandle(INSTANCE.getCanonicalClass(), type.getType());

        GenericHandle hMulti = (GenericHandle) hType.getField(null, "multimethods");
        hMulti.setField(null, GenericHandle.OUTER, hType);
        hMulti.setField(null, PROP_CALCULATE,  xNullable.NULL);

        GenericHandle hHasher = (GenericHandle) hType.getField(null, PROP_HASHER);
        hHasher.setField(null, GenericHandle.OUTER, hType);
        hHasher.setField(null, PROP_CALCULATE,  xNullable.NULL);

        GenericHandle hIter = (GenericHandle) hType.getField(null, "emptyIterator");
        hIter.setField(null, GenericHandle.OUTER, hType);
        hIter.setField(null, PROP_CALCULATE,  xNullable.NULL);

        return hType;
        }

    /**
     * @return a "foreign" {@link TypeHandle} that serves as a proxy handle for the specified type.
     */
    public static TypeHandle makeForeignHandle(TypeConstant type)
        {
        return makeHandle(null, type, false);
        }

    /**
     * Java function used by makeRelationalType() method below.
     */
    @FunctionalInterface
    interface RelationalOperation
        {
        TypeConstant makeRelational(ConstantPool pool, TypeConstant t1, TypeConstant t2);
        }

    /**
     * Helper method used by bi-operators.
     */
    private int makeRelationalType(Frame frame, TypeHandle hType1, TypeHandle hType2,
                                   RelationalOperation op, int iReturn)
        {
        TypeConstant type1 = hType1.getUnsafeDataType();
        TypeConstant type2 = hType2.getUnsafeDataType();

        ConstantPool pool;
        if (type1.isShared(type2.getConstantPool()))
            {
            pool = type2.getConstantPool();
            }
        else if (type2.isShared(type1.getConstantPool()))
            {
            pool = type1.getConstantPool();
            }
        else
            {
            return frame.raiseException(xException.invalidType(frame,
                "No common TypeSystem for (" + type1 + " and " + type2 + ")"));
            }

        TypeConstant typeResult = op.makeRelational(pool, type1, type2);
        if (typeResult instanceof RelationalTypeConstant typeRel)
            {
            typeResult = typeRel.simplify(pool);
            }
        return frame.assignValue(iReturn, typeResult.ensureTypeHandle(frame.f_context.f_container));
        }

    /**
     * Inner class: TypeHandle. This is a handle to a native type.
     */
    public static class TypeHandle
            extends GenericHandle
        {
        protected TypeHandle(TypeComposition clazz, TypeConstant typeForeign)
            {
            super(clazz);

            f_typeForeign = typeForeign;
            m_fMutable    = false;
            }

        @Override
        public ObjectHandle revealOrigin()
            {
            return this;
            }

        public TypeConstant getDataType()
            {
            return getType().getParamType(0);
            }

        public TypeConstant getOuterType()
            {
            return getType().getParamType(1);
            }

        /**
         * @return true iff this type handle represents a type from a "foreign" type system
         */
        public boolean isForeign()
            {
            return f_typeForeign != null;
            }

        @Override
        public TypeConstant getUnsafeType()
            {
            return f_typeForeign == null ? super.getType() : f_typeForeign;
            }

        /**
         * As a general rule, the returned TypeConstant could be used *only* for an "isA()"
         * evaluation.
         *
         * @return a TypeConstant that *may* belong to a "foreign" type system
         */
        public TypeConstant getUnsafeDataType()
            {
            return getUnsafeType().getParamType(0);
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle obj)
            {
            return obj instanceof TypeHandle that
                    ? this.getDataType().compareTo(that.getDataType())
                    : 1;
            }

        @Override
        public int hashCode()
            {
            return getDataType().hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof TypeHandle that &&
                    this.getDataType().equals(that.getDataType());
            }

        @Override
        public String toString()
            {
            return "(Type) " + getDataType().getValueString();
            }

        private final TypeConstant f_typeForeign;
        }


    // ----- data members --------------------------------------------------------------------------

    private static TypeConstant  TYPE_ARRAY_TYPE;
    private static ArrayConstant EMPTY_TYPE_ARRAY;
    private static TypeConstant  LISTMAP_TYPE;

    private static TypeComposition REGISTER_CLZCOMP;
    private static MethodStructure REGISTER_CONSTRUCT;

    private static PropertyConstant PROP_CALCULATE;
    private static PropertyConstant PROP_HASHER;
    }