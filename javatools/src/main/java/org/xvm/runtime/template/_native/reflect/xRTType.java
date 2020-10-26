package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.Map;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.ChildInfo;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PackageConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.PseudoConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.DeferredArrayHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template._native.reflect.xRTClass.ClassHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTMethod.MethodHandle;
import org.xvm.runtime.template._native.reflect.xRTProperty.PropertyHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.GenericArrayHandle;
import org.xvm.runtime.template.collections.xTuple;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.ListMap;


/**
 * Native Type implementation.
 */
public class xRTType
        extends    xConst
        implements IndexSupport // for turtle types
    {
    public static xRTType INSTANCE;

    public xRTType(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ANNOTATION_TEMPLATE  = f_templates.getTemplate("reflect.Annotation");
        ANNOTATION_CONSTRUCT = ANNOTATION_TEMPLATE.getStructure().findMethod("construct", 2);

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

        markNativeMethod("accessSpecified", null, null);
        markNativeMethod("annotate"       , null, null);
        markNativeMethod("annotated"      , null, null);
        markNativeMethod("contained"      , null, null);
        markNativeMethod("fromClass"      , null, null);
        markNativeMethod("fromProperty"   , null, null);
        markNativeMethod("modifying"      , null, null);
        markNativeMethod("named"          , null, null);
        markNativeMethod("parameterize"   , null, null);
        markNativeMethod("parameterized"  , null, null);
        markNativeMethod("purify"         , null, null);
        markNativeMethod("relational"     , null, null);

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

        ClassConstant   idType     = pool().clzType();
        TypeConstant    typeType   = idType.getType();
        ClassStructure  structType = (ClassStructure) idType.getComponent();

        structType.findMethod("hashCode", 2).markNative();
        structType.findMethod("equals"  , 3).markNative();
        structType.findMethod("compare" , 3).markNative();

        typeType.invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
            pool.typeType(), pool.typeObject(), pool.typeObject());
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof TypeConstant)
            {
            ConstantPool pool = frame.poolContext();

            TypeConstant typeTarget = (TypeConstant) constant;
            assert typeTarget.isA(pool.typeType());

            TypeConstant typeData = typeTarget.getParamType(0).
                    resolveGenerics(pool, frame.getGenericsResolver());
            return frame.pushStack(typeData.normalizeParameters().ensureTypeHandle(pool));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        TypeHandle hThis = (TypeHandle) hTarget;

        if (idProp instanceof FormalTypeChildConstant)
            {
            TypeConstant typeTarget = hThis.getDataType();
            TypeConstant typeValue  = typeTarget.resolveFormalType(idProp);

            return typeValue == null
                ? frame.raiseException(xException.invalidType(frame,
                    "Unknown formal type: " + idProp.getName()))
                : frame.assignValue(iReturn, typeValue.ensureTypeHandle(frame.poolContext()));
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

            case "named":
                return invokeNamed(frame, hType, aiReturn);

            case "parameterized":
                return invokeParameterized(frame, hType, aiReturn);

            case "relational":
                return invokeRelational(frame, hType, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // hArg may be a Type, a Method, or a Property
        if (hArg instanceof TypeHandle)
            {
            ConstantPool pool     = frame.poolContext();
            TypeConstant typeThis = ((TypeHandle) hTarget).getDataType();
            TypeConstant typeThat = ((TypeHandle) hArg   ).getDataType();
            if (!typeThis.isShared(pool) || !typeThat.isShared(pool))
                {
                return frame.raiseException(xException.invalidType(frame,
                    "No common TypeSystem for (" + typeThis + " + " + typeThat + ")"));
                }
            TypeConstant typeResult = pool.ensureUnionTypeConstant(typeThis, typeThat);
            return frame.assignValue(iReturn, typeResult.ensureTypeHandle(pool));
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
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // hArg may be a Type, a Method, or a Property
        if (hArg instanceof TypeHandle)
            {
            ConstantPool pool     = frame.poolContext();
            TypeConstant typeThis = ((TypeHandle) hTarget).getDataType();
            TypeConstant typeThat = ((TypeHandle) hArg   ).getDataType();
            if (typeThis.isShared(pool) && typeThat.isShared(pool))
                {
                TypeConstant typeResult = pool.ensureDifferenceTypeConstant(typeThis, typeThat);
                return frame.assignValue(iReturn, typeResult.ensureTypeHandle(frame.poolContext()));
                }
            return frame.raiseException(xException.invalidType(frame,
                "No common TypeSystem for (" + typeThis + " - " + typeThat + ")"));
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
        // hArg is a Type
        if (hArg instanceof TypeHandle)
            {
            ConstantPool pool     = frame.poolContext();
            TypeConstant typeThis = ((TypeHandle) hTarget).getDataType();
            TypeConstant typeThat = ((TypeHandle) hArg   ).getDataType();
            if (typeThis.isShared(pool) && typeThat.isShared(pool))
                {
                // TODO
                // TypeConstant typeResult = pool.ensure???TypeConstant(typeThis, typeThat);
                // return frame.assignValue(iReturn, typeResult.getTypeHandle());
                throw new UnsupportedOperationException();
                }
            return frame.raiseException(xException.invalidType(frame,
                "No common TypeSystem for (" + typeThis + " & " + typeThat + ")"));
            }

        return super.invokeOr(frame, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // hArg is a Type
        if (hArg instanceof TypeHandle)
            {
            ConstantPool pool     = frame.poolContext();
            TypeConstant typeThis = ((TypeHandle) hTarget).getDataType();
            TypeConstant typeThat = ((TypeHandle) hArg   ).getDataType();
            if (typeThis.isShared(pool) && typeThat.isShared(pool))
                {
                TypeConstant typeResult = pool.ensureIntersectionTypeConstant(typeThis, typeThat);
                return frame.assignValue(iReturn, typeResult.ensureTypeHandle(frame.poolContext()));
                }
            return frame.raiseException(xException.invalidType(frame,
                "No common TypeSystem for (" + typeThis + " | " + typeThat + ")"));
            }

        return super.invokeOr(frame, hTarget, hArg, iReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(
            (((TypeHandle) hValue1).getDataType()).equals(((TypeHandle) hValue2).getDataType())));
        }

    @Override
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xOrdered.makeHandle(
            (((TypeHandle) hValue1).getDataType()).compareTo(((TypeHandle) hValue2).getDataType())));
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
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
            ? frame.assignValue(iReturn, type.getParamType(nIndex).ensureTypeHandle(frame.poolContext()))
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
        throw xException.unsupportedOperation(frame).getException();
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: childTypes.get()
     */
    public int getPropertyChildTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return Utils.constructListMap(frame, ensureListMapComposition(),
                    xString.ensureEmptyArray(), ensureEmptyTypeArray(), iReturn);
            }

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

        TypeInfo                          infoTarget  = typeTarget.ensureTypeInfo();
        ConstantPool                      poolCtx     = frame.poolContext();
        Map<String, ChildInfo>            mapInfos    = infoTarget.getChildInfosByName();
        Map<StringConstant, TypeConstant> mapResult   = new ListMap<>();
        for (String sName : mapInfos.keySet())
            {
            TypeConstant typeChild = infoTarget.calculateChildType(poolCtx, sName);
            mapResult.put(poolCtx.ensureStringConstant(sName), typeChild.getType());
            }
        TypeConstant typeResult  = poolCtx.ensureImmutableTypeConstant(
                poolCtx.ensureParameterizedTypeConstant(poolCtx.typeMap(),
                        poolCtx.typeString(), poolCtx.typeType()));
        MapConstant  constResult = poolCtx.ensureMapConstant(typeResult, mapResult);

        return frame.assignDeferredValue(iReturn, frame.getConstHandle(constResult));
        }

    /**
     * Implements property: constants.get()
     */
    public int getPropertyConstants(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return frame.assignValue(iReturn, xRTProperty.ensureEmptyArray());
            }

        TypeConstant                        typeTarget = hType.getDataType();
        TypeInfo                            infoTarget = typeTarget.ensureTypeInfo();
        Map<PropertyConstant, PropertyInfo> mapProps   = infoTarget.getProperties();
        ArrayList<ObjectHandle>             listProps  = new ArrayList<>(mapProps.size());
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
            {
            PropertyInfo infoProp = entry.getValue();
            if (!infoProp.isConstant())
                {
                continue;
                }

            TypeConstant typeProperty = entry.getKey().getValueType(typeTarget);
            ObjectHandle hProperty    = xRTProperty.INSTANCE.makeHandle(typeProperty);

            listProps.add(hProperty);
            }

        ArrayHandle hArray = xRTProperty.ensureArrayTemplate().createArrayHandle(
                xRTProperty.ensureArrayComposition(), listProps.toArray(Utils.OBJECTS_NONE));
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: constructors.get()
     */
    public int getPropertyConstructors(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return frame.assignValue(iReturn, xRTFunction.ensureEmptyArray());
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
        // TODO verify that pure type is not newable
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        // each of the generated constructor functions (not the "construct" functions) for a virtual
        // child will require a parent reference to be passed as the first argument
        TypeConstant typeParent = null;
        if (infoTarget.isVirtualChild())
            {
            typeParent = hType.getOuterType();
            assert typeParent != null;
            assert !typeParent.equals(pool().typeObject());
            }

        FunctionHandle[] ahFunctions;
        if (infoTarget.isNewable())
            {
            ConstantPool     pool       = frame.poolContext();
            TypeConstant     typeStruct = pool.ensureAccessTypeConstant(typeTarget, Constants.Access.STRUCT);
            ClassComposition clzTarget  = f_templates.resolveClass(typeTarget);

            ArrayList<FunctionHandle> listHandles   = new ArrayList<>();
            boolean                   fStructConstr = false;
            for (MethodConstant idConstr : infoTarget.findMethods("construct", -1, TypeInfo.MethodKind.Constructor))
                {
                MethodInfo      infoMethod  = infoTarget.getMethodById(idConstr);
                MethodStructure constructor = infoMethod.getTopmostMethodStructure(infoTarget);
                Parameter[]     aParams     = constructor.getParamArray();

                TypeConstant[] atypeParams = infoMethod.getSignature().getRawParams();
                if (atypeParams.length == 1 && atypeParams[0].equals(typeStruct))
                    {
                    fStructConstr = true;
                    }

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

                TypeConstant typeConstr = pool.buildFunctionType(atypeParams, typeTarget);
                listHandles.add(
                        new ConstructorHandle(clzTarget, typeConstr, constructor, aParams, typeParent != null));
                }

            if (!fStructConstr)
                {
                // add a struct constructor (e.g. for deserialization)
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

                TypeConstant typeConstr = pool.buildFunctionType(atypeParams, typeTarget);
                listHandles.add(
                        new ConstructorHandle(clzTarget, typeConstr, null, aParams, typeParent != null));
                }

            ahFunctions = listHandles.toArray(new FunctionHandle[0]);
            }
        else
            {
            ahFunctions = new FunctionHandle[0];
            }

        ArrayHandle hArray = xRTFunction.ensureArrayTemplate().createArrayHandle(
                xRTFunction.ensureConstructorArray(typeTarget, typeParent), ahFunctions);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * FunctionHandle that represents a constructor function.
     */
    public static class ConstructorHandle
            extends FunctionHandle
        {
        public ConstructorHandle(ClassComposition clzTarget, TypeConstant typeConstruct,
                                 MethodStructure constructor, Parameter[] aParams, boolean fParent)
            {
            super(typeConstruct, constructor);

            f_clzTarget   = clzTarget;
            f_constructor = constructor;
            f_aParams     = aParams;
            f_fParent     = fParent;
            m_fMutable    = false;
            }

        @Override
        public int callT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle hParent = null;
            if (f_fParent)
                {
                hParent = ahArg[0];
                System.arraycopy(ahArg, 1, ahArg, 0, ahArg.length-1);
                }

            ClassComposition clzTarget   = f_clzTarget;
            ClassTemplate    template    = clzTarget.getTemplate();
            MethodStructure  constructor = f_constructor;
            ConstantPool     pool        = frame.poolContext();
            TypeConstant     typeTuple   = pool.ensureParameterizedTypeConstant(
                                            pool.typeTuple(), clzTarget.getType());
            ClassComposition clzTuple    = xTuple.INSTANCE.ensureClass(typeTuple);

            int iResult = constructor == null
                ? template.proceedConstruction(frame, null, false, ahArg[0], ahArg, Op.A_STACK)
                : template.construct(frame, constructor, clzTarget, hParent, ahArg, Op.A_STACK);
            switch (iResult)
                {
                case Op.R_NEXT:
                    return frame.assignValue(iReturn,
                        xTuple.makeImmutableHandle(clzTuple, frame.popStack()));

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn,
                            xTuple.makeImmutableHandle(clzTuple, frameCaller.popStack())));
                    // fall through
                default:
                    return iResult;
                }
            }

        @Override
        protected ObjectHandle[] prepareVars(ObjectHandle[] ahArg)
            {
            throw new IllegalStateException();
            }

        @Override
        public String getName()
            {
            return "construct";
            }

        @Override
        public int getParamCount()
            {
            return f_aParams.length;
            }

        @Override
        public Parameter getParam(int iArg)
            {
            return f_aParams[iArg];
            }

        @Override
        public int getReturnCount()
            {
            return 1;
            }

        @Override
        public Parameter getReturn(int iArg)
            {
            assert iArg == 0;
            TypeConstant typeTarget = f_clzTarget.getType();
            return new Parameter(typeTarget.getConstantPool(), typeTarget, null, null, true, 0, false);
            }

        @Override
        public TypeConstant getReturnType(int iArg)
            {
            assert iArg == 0;
            return f_clzTarget.getType();
            }

        @Override
        public int getVarCount()
            {
            int cVars = super.getVarCount();
            return Math.max(cVars, f_aParams.length);
            }

        final private ClassComposition f_clzTarget;
        final private MethodStructure  f_constructor;
        final protected Parameter[]    f_aParams;
        final private boolean          f_fParent;
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
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return frame.assignValue(iReturn, xRTFunction.ensureEmptyArray());
            }

        TypeConstant                    typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo> mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<FunctionHandle>       listHandles = new ArrayList<>(mapMethods.size());
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodConstant id   = entry.getKey();
            MethodInfo     info = entry.getValue();
            if (info.isFunction() && id.getNestedDepth() == 2)
                {
                listHandles.add(xRTFunction.makeHandle(info.getHead().getMethodStructure()));
                }
            }
        FunctionHandle[] ahFunctions = listHandles.toArray(new FunctionHandle[0]);
        ArrayHandle      hArray      = xRTFunction.ensureArrayTemplate().createArrayHandle(
                xRTFunction.ensureArrayComposition(), ahFunctions);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: methods.get()
     */
    public int getPropertyMethods(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return frame.assignValue(iReturn, xRTMethod.ensureEmptyArray());
            }

        TypeConstant                    typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo> mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<ObjectHandle>         listHandles = new ArrayList<>(mapMethods.size());
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodConstant idMethod = entry.getKey();
            MethodInfo     info     = entry.getValue();
            if (!info.isCapped() && !info.isFunction() && !info.isConstructor()
                    && idMethod.getNestedDepth() == 2)
                {
                listHandles.add(xRTMethod.makeHandle(frame, typeTarget, info.getIdentity()));
                }
            }

        ClassComposition clzArray  = xRTMethod.ensureArrayComposition(typeTarget);
        ObjectHandle[]   ahMethods = listHandles.toArray(Utils.OBJECTS_NONE);
        if (Op.anyDeferred(ahMethods))
            {
            ObjectHandle hDeferred = new DeferredArrayHandle(clzArray, ahMethods);
            return hDeferred.proceed(frame,
                frameCaller -> frameCaller.assignValue(iReturn, frameCaller.popStack()));
            }
        return frame.assignValue(iReturn, xArray.INSTANCE.createArrayHandle(clzArray, ahMethods));
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertyProperties(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return frame.assignValue(iReturn, xRTProperty.ensureEmptyArray());
            }

        TypeConstant                        typeTarget = hType.getDataType();
        TypeInfo                            infoTarget = typeTarget.ensureTypeInfo();
        Map<PropertyConstant, PropertyInfo> mapProps   = infoTarget.getProperties();
        ArrayList<ObjectHandle>             listProps  = new ArrayList<>(mapProps.size());
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
            {
            PropertyConstant idProp   = entry.getKey();
            PropertyInfo     infoProp = entry.getValue();
            if (!infoProp.isConstant() && idProp.getNestedDepth() == 1)
                {
                TypeConstant  typeProperty = idProp.getValueType(typeTarget);
                PropertyHandle hProperty   = xRTProperty.INSTANCE.makeHandle(typeProperty);

                listProps.add(hProperty);
                }
            }
        ArrayHandle hArray = xRTProperty.ensureArrayTemplate().createArrayHandle(
                xRTProperty.ensureArrayComposition(typeTarget), listProps.toArray(Utils.OBJECTS_NONE));
        return frame.assignValue(iReturn, hArray);
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
        return frame.assignValue(iReturn, xRTTypeTemplate.makeHandle(hType.getDataType()));
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
        // TODO GG - the code here is providing the "current context" type system, which is wrong
        return frame.f_context.f_container.ensureTypeSystemHandle(frame, iReturn);
        }

    /**
     * Implements property: underlyingTypes.get()
     */
    public int getPropertyUnderlyingTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            return frame.assignValue(iReturn, ensureEmptyTypeArray());
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
            ahTypes[i] = aUnderlying[i].ensureTypeHandle(frame.poolContext());
            }

        ArrayHandle hArray = ensureArrayTemplate().
                createArrayHandle(ensureTypeArrayComposition(), ahTypes);
        return frame.assignValue(iReturn, hArray);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Access accessSpecified()}.
     */
    public int invokeAccessSpecified(Frame frame, TypeHandle hType, int[] aiReturn)
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
    public int invokeAnnotate(Frame frame, TypeHandle hType, ObjectHandle hArg, int iReturn)
        {
        ConstantPool  pool     = frame.poolContext();
        TypeConstant  typeThis = hType.getDataType();
        GenericHandle hAnno    = (GenericHandle) hArg;
        ClassHandle   hClass   = (ClassHandle) hAnno.getField("mixinClass");
        ArrayHandle   hArgs    = (ArrayHandle) hAnno.getField("arguments");

        if (hArgs.m_cSize > 0)
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
            return frame.assignValue(iReturn, typeResult.ensureTypeHandle(pool));
            }
        return frame.raiseException(xException.invalidType(frame,
            "No common TypeSystem for (" + typeThis + " - " + typeAnno + ")"));
        }

    /**
     * Implementation for: {@code conditional Annotation annotated()}.
     */
    public int invokeAnnotated(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        if (hType.isForeign())
            {
            // TODO GG: ask the type's container to answer
            frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        TypeConstant typeThis = hType.getDataType();
        if (typeThis.isAnnotated())
            {
            while (!(typeThis instanceof AnnotatedTypeConstant))
                {
                assert typeThis.isModifyingType();
                typeThis = typeThis.getUnderlyingType();
                }

            Annotation       annotation = ((AnnotatedTypeConstant) typeThis).getAnnotation();
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
                    ahArg[i] = frame.getConstHandle(aconstArg[i]);
                    }
                }

            return Op.isDeferred(hClass)
                    ? hClass.proceed(frame, frameCaller ->
                        resolveInvokeAnnotatedArgs(frameCaller, frameCaller.popStack(), ahArg, aiReturn))
                    : resolveInvokeAnnotatedArgs(frame, hClass, ahArg, aiReturn);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    private int resolveInvokeAnnotatedArgs(Frame frame, ObjectHandle hClass,
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

    private int completeInvokeAnnotated(Frame frame, ObjectHandle hClass,
                                        ObjectHandle[] ahArg, int[] aiReturn)
        {
        ClassTemplate   templateAnno  = ANNOTATION_TEMPLATE;
        MethodStructure constructAnno = ANNOTATION_CONSTRUCT;

        ArrayHandle hArgs = ahArg.length == 0
            ? ensureEmptyArgumentArray()
            : ensureArrayTemplate().createArrayHandle(ensureArgumentArrayComposition(), ahArg);

        ObjectHandle[] ahVar = new ObjectHandle[constructAnno.getMaxVars()];
        ahVar[0] = hClass;
        ahVar[1] = hArgs;

        frame.assignValue(aiReturn[0], xBoolean.TRUE);
        return templateAnno.construct(frame, constructAnno,
                templateAnno.getCanonicalClass(), null, ahVar, aiReturn[1]);
        }

    /**
     * Implementation for: {@code conditional Type!<> contained()}.
     */
    public int invokeContained(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        // REVIEW CP: include PropertyClassTypeConstant?
        if (typeTarget.isVirtualChild() || typeTarget.isAnonymousClass())
            {
            TypeHandle hParent = typeTarget.getParentType().ensureTypeHandle(frame.poolContext());
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
    public int invokeFromClass(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        if (!hType.isForeign())
            {
            TypeConstant typeTarget = hType.getDataType();
            if (typeTarget.isExplicitClassIdentity(true))
                {
                typeTarget = typeTarget.removeAccess().removeImmutable();

                IdentityConstant idClz = frame.poolContext().ensureClassConstant(typeTarget);

                return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(idClz));
                }
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Property fromProperty()}.
     */
    public int invokeFromProperty(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        if (!hType.isForeign())
            {
            TypeConstant type = hType.getDataType();
            if (type.isSingleDefiningConstant())
                {
                Constant constDef = type.getDefiningConstant();
                if (constDef instanceof PropertyConstant)
                    {
                    TypeConstant   typeProperty = ((PropertyConstant) constDef).getValueType(null);
                    PropertyHandle hProperty    = xRTProperty.INSTANCE.makeHandle(typeProperty);

                    return frame.assignValues(aiReturn, xBoolean.TRUE, hProperty);
                    }
                }
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Type!<> modifying()}.
     */
    public int invokeModifying(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        return type.isModifyingType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        type.getUnderlyingType().ensureTypeHandle(frame.poolContext()))
                : frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional String named()}.
     */
    public int invokeNamed(Frame frame, TypeHandle hType, int[] aiReturn)
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

        return sName == null
            ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
            : frame.assignValues(aiReturn, xBoolean.TRUE, xString.makeHandle(sName));
        }

    /**
     * Implementation for: {@code conditional Type!<>[] parameterized()}.
     */
    public int invokeParameterized(Frame frame, TypeHandle hType, int[] aiReturn)
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
            ahTypes[i] = atypes[i].normalizeParameters().ensureTypeHandle(frame.poolContext());
            }

        ArrayHandle hArray = ensureArrayTemplate().
                createArrayHandle(ensureTypeArrayComposition(), ahTypes);
        return frame.assignValues(aiReturn, xBoolean.TRUE, hArray);
        }

    /**
     * Implementation for: {@code Type!<> parameterize(Type!<>... paramTypes)}.
     */
    public int invokeParameterize(Frame frame, TypeHandle hType, ObjectHandle hArg, int iReturn)
        {
        if (hType.isForeign())
            {
            return frame.raiseException(xException.invalidType(frame,
                "Pure type " + hType.getDataType().getValueString()));
            }

        ObjectHandle[] ahFormalTypes;
        int            cFormalTypes;
        if (hArg instanceof GenericArrayHandle)
            {
            GenericArrayHandle hArray = (GenericArrayHandle) hArg;
            ahFormalTypes = hArray.m_ahValue;
            cFormalTypes  = hArray.m_cSize;
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

        TypeConstant   typeThis     = hType.getDataType();
        TypeConstant[] atypeParams  = new TypeConstant[cFormalTypes];
        for (int i = 0; i < cFormalTypes; ++i)
            {
            atypeParams[i] = ((TypeHandle) ahFormalTypes[i]).getDataType();
            }

        ConstantPool pool = frame.poolContext();
        TypeConstant typeResult;
        try
            {
            typeResult = typeThis.adoptParameters(pool, atypeParams);
            }
        catch (RuntimeException e)
            {
            return frame.raiseException(xException.invalidType(frame, e.getMessage()));
            }
        return frame.assignValue(iReturn, typeResult.ensureTypeHandle(pool));
        }

    /**
     * Implementation for: {@code conditional Type!<> purify()}.
     */
    public int invokePurify(Frame frame, TypeHandle hType, int iReturn)
        {
        return frame.assignValue(iReturn, hType); // TODO GG - implement Pure type constant etc.
        }

    /**
     * Implementation for: {@code conditional (Type!<>, Type!<>) relational()}.
     */
    public int invokeRelational(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ConstantPool pool = frame.poolContext();
        TypeConstant type = hType.getDataType();
        return type.isRelationalType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        type.getUnderlyingType().ensureTypeHandle(pool),
                        type.getUnderlyingType2().ensureTypeHandle(pool))
                : frame.assignValue(aiReturn[0], xBoolean.FALSE);
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
    public static EnumHandle makeAccessHandle(Frame frame, Constants.Access access)
        {
        xEnum enumAccess = (xEnum) INSTANCE.f_templates.getTemplate("reflect.Access");
        switch (access)
            {
            case PUBLIC:
                return enumAccess.getEnumByName("Public");

            case PROTECTED:
                return enumAccess.getEnumByName("Protected");

            case PRIVATE:
                return enumAccess.getEnumByName("Private");

            case STRUCT:
                return enumAccess.getEnumByName("Struct");

            default:
                throw new IllegalStateException("unknown access value: " + access);
            }
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
        xEnum enumForm = (xEnum) INSTANCE.f_templates.getTemplate("reflect.Type.Form");

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
                    switch (type.getDefiningConstant().getFormat())
                        {
                        case NativeClass:
                            return enumForm.getEnumByName("Pure");

                        case Module:
                        case Package:
                        case Class:
                        case ThisClass:
                        case ParentClass:
                        case ChildClass:
                            return enumForm.getEnumByName("Class");

                        case Property:
                            return enumForm.getEnumByName("FormalProperty");

                        case TypeParameter:
                            return enumForm.getEnumByName("FormalParameter");

                        case FormalTypeChild:
                            return enumForm.getEnumByName("FormalChild");

                        default:
                            throw new IllegalStateException("unsupported format: " +
                                    type.getDefiningConstant().getFormat());
                        }
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

            case ParameterizedType:
                // the underlying type will be "Class" or "Child"
                return makeFormHandle(frame, type.getUnderlyingType());

            case TurtleType:
                return enumForm.getEnumByName("Sequence");

            case VirtualChildType:
                return enumForm.getEnumByName("Child");

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


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ClassTemplate for an Array of Type
     */
    public static xArray ensureArrayTemplate()
        {
        xArray template = ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType());
            ARRAY_TEMPLATE = template = ((xArray) INSTANCE.f_templates.getTemplate(typeTypeArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an Array of Type
     */
    public static ClassComposition ensureTypeArrayComposition()
        {
        ClassComposition clz = TYPE_ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType());
            TYPE_ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeTypeArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of Arguments
     */
    public static ClassComposition ensureArgumentArrayComposition()
        {
        ClassComposition clz = ARGUMENT_ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeArg      = pool.ensureEcstasyTypeConstant("reflect.Argument");
            TypeConstant typeArgArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), typeArg);
            ARGUMENT_ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeArgArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of Type
     */
    public static ArrayHandle ensureEmptyTypeArray()
        {
        if (TYPE_ARRAY_EMPTY == null)
            {
            TYPE_ARRAY_EMPTY = ensureArrayTemplate().createArrayHandle(
                ensureTypeArrayComposition(), Utils.OBJECTS_NONE);
            }
        return TYPE_ARRAY_EMPTY;
        }

    /**
     * @return the handle for an empty Array of Arguments
     */
    public static ArrayHandle ensureEmptyArgumentArray()
        {
        if (ARGUMENT_ARRAY_EMPTY == null)
            {
            ARGUMENT_ARRAY_EMPTY = ensureArrayTemplate().createArrayHandle(
                ensureArgumentArrayComposition(), Utils.OBJECTS_NONE);
            }
        return ARGUMENT_ARRAY_EMPTY;
        }

    /**
     * @return the ClassComposition for ListMap<String, Type>
     */
    private static ClassComposition ensureListMapComposition()
        {
        ClassComposition clz = LISTMAP_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeList = pool.ensureEcstasyTypeConstant("collections.ListMap");
            typeList = pool.ensureParameterizedTypeConstant(typeList, pool.typeString(), pool.typeString());
            LISTMAP_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeList);
            assert clz != null;
            }
        return clz;
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
    public static TypeHandle makeHandle(TypeConstant type, boolean fShared)
        {
        // unfortunately, "makeHandle" is called from places where we cannot easily invoke the
        // default initializer, so we need to do it by hand
        TypeHandle hType = fShared
            ? new TypeHandle(INSTANCE.ensureClass(type.getType()), null)
            : new TypeHandle(INSTANCE.getCanonicalClass(), type.getType());
        GenericHandle hMulti = (GenericHandle) hType.getField("multimethods");
        hMulti.setField(GenericHandle.OUTER, hType);
        hMulti.setField("calculate",  xNullable.NULL);
        hMulti.setField("assignable", xBoolean.FALSE);
        return hType;
        }

    /**
     * Inner class: TypeHandle. This is a handle to a native type.
     */
    public static class TypeHandle
            extends GenericHandle
        {
        protected TypeHandle(ClassComposition clazz, TypeConstant typeForeign)
            {
            super(clazz);

            f_typeForeign = typeForeign;
            m_fMutable    = false;
            }

        @Override
        public TypeConstant getType()
            {
            return f_typeForeign == null
                ? super.getType()
                : f_typeForeign;
            }

        public boolean isForeign()
            {
            return f_typeForeign != null;
            }

        public TypeConstant getDataType()
            {
            return getType().getParamType(0);
            }

        public TypeConstant getOuterType()
            {
            return getType().getParamType(1);
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            return that instanceof TypeHandle
                    ? this.getDataType().compareTo(((TypeHandle) that).getDataType())
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
            return obj instanceof TypeHandle &&
                    this.getDataType().equals(((TypeHandle) obj).getDataType());
            }

        private final TypeConstant f_typeForeign;
        }


    // ----- data members --------------------------------------------------------------------------

    private static xArray           ARRAY_TEMPLATE;
    private static ClassComposition TYPE_ARRAY_CLZCOMP;
    private static ArrayHandle      TYPE_ARRAY_EMPTY;
    private static ClassComposition LISTMAP_CLZCOMP;


    private static ClassTemplate    ANNOTATION_TEMPLATE;
    private static MethodStructure  ANNOTATION_CONSTRUCT;
    private static ClassComposition ARGUMENT_ARRAY_CLZCOMP;
    private static ArrayHandle      ARGUMENT_ARRAY_EMPTY;
    }
