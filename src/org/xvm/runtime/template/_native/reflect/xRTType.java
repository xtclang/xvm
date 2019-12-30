package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.ChildInfo;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MapConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTMethod.MethodHandle;
import org.xvm.runtime.template._native.reflect.xRTProperty.PropertyHandle;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.util.ListMap;


/**
 * Native Type implementation.
 */
public class xRTType
        extends xConst
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
    public void initDeclared()
        {
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
        markNativeProperty("underlyingTypes");

        markNativeMethod("accessSpecified", null, null);
        markNativeMethod("annotated", null, null);
        markNativeMethod("contained", null, null);
        markNativeMethod("fromClass", null, null);
        markNativeMethod("fromProperty", null, null);
        markNativeMethod("modifying", null, null);
        markNativeMethod("named", null, null);
        markNativeMethod("purify", null, null);
        markNativeMethod("parameterized", null, null);
        markNativeMethod("relational", null, null);

        // TODO ops: add x3, or, and, sub x3

        getCanonicalType().invalidateTypeInfo();
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
            frame.pushStack(typeData.getTypeHandle());
            return Op.R_NEXT;
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
            String       sName      = idProp.getName();
            TypeConstant typeValue  = typeTarget.resolveGenericType(sName);

            return typeValue == null
                ? frame.raiseException("Unknown formal type: " + sName)
                : frame.assignValue(iReturn, typeValue.getTypeHandle());
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

            case "sub":
                return invokeSub(frame, hType, hArg, iReturn);

            case "and":
                return invokeAnd(frame, hType, hArg, iReturn);

            case "or":
                return invokeOr(frame, hType, hArg, iReturn);

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
        // TODO
        throw new UnsupportedOperationException();
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }


    // ----- TypeHandle support --------------------------------------------------------------------

    /**
     * Obtain a {@link TypeHandle} for the specified type.
     *
     * @param type  the {@link TypeConstant} to obtain a {@link TypeHandle} for
     *
     * @return the resulting {@link TypeHandle}
     */
    public static TypeHandle makeHandle(TypeConstant type)
        {
        return makeHandle(type, null);
        }

    /**
     * Obtain a {@link TypeHandle} for the specified type.
     *
     * @param type       the {@link TypeConstant} to obtain a {@link TypeHandle} for
     * @param typeOuter  the {@link TypeConstant} for the parent of the virtual child, or null
     *
     * @return the resulting {@link TypeHandle}
     */
    public static TypeHandle makeHandle(TypeConstant type, TypeConstant typeOuter)
        {
        ClassComposition clzType = INSTANCE.ensureClass(type.getType());
        // TODO - implement outer type

        // unfortunately, "makeHandle" is called from places where we cannot easily invoke the
        // default initializer, so we need to do it by hand
        TypeHandle    hType  = new TypeHandle(clzType);
        GenericHandle hMulti = (GenericHandle) hType.getField("multimethods");
        hMulti.setField("$outer",     hType);
        hMulti.setField("calculate",  xNullable.NULL);
        hMulti.setField("assignable", xBoolean.FALSE);
        return hType;
        }

    /**
     * Inner class: TypeHandle. This is a handle to a native type.
     */
    public static class TypeHandle
        extends ObjectHandle.GenericHandle
        {
        protected TypeHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        public TypeConstant getDataType()
            {
            return getType().getParamType(0);
            }

        public TypeConstant getOuterType()
            {
            return getType().getParamType(1);
            }
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: childTypes.get()
     */
    public int getPropertyChildTypes(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                      typeTarget  = hType.getDataType();
        TypeInfo                          infoTarget  = typeTarget.ensureTypeInfo();
        boolean                           fDeepParams = typeTarget.isParameterizedDeep();
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
        ObjectHandle hResult     = frame.getConstHandle(constResult);
        return frame.assignValue(iReturn, hResult);
        }

    /**
     * Implements property: constants.get()
     */
    public int getPropertyConstants(Frame frame, TypeHandle hType, int iReturn)
        {
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

        ArrayHandle hArray = ensurePropertyArrayTemplate().createArrayHandle(
                ensureConstantArray(), listProps.toArray(new ObjectHandle[0]));
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: constructors.get()
     */
    public int getPropertyConstructors(Frame frame, TypeHandle hType, int iReturn)
        {
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

        ArrayHandle hArray = ensureFunctionArrayTemplate().createArrayHandle(
                ensureConstructorArray(typeTarget, typeParent), ahFunctions);
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

            int iResult = f_clzTarget.getTemplate().construct(
                    frame, f_constructor, f_clzTarget, hParent, ahArg, Op.A_STACK);
            switch (iResult)
                {
                case Op.R_NEXT:
                    return frame.assignTuple(iReturn, frame.popStack());

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignTuple(iReturn, frame.popStack()));
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
                makeFormHandle(frame, hType.getDataType()), iReturn);
        }

    /**
     * Implements property: functions.get()
     */
    public int getPropertyFunctions(Frame frame, TypeHandle hType, int iReturn)
        {
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
        ArrayHandle      hArray      = ensureFunctionArrayTemplate().createArrayHandle(
                ensureFunctionArray(), ahFunctions);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: methods.get()
     */
    public int getPropertyMethods(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                    typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo> mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<MethodHandle>         listHandles = new ArrayList<>(mapMethods.size());
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodConstant idMethod = entry.getKey();
            MethodInfo     info     = entry.getValue();
            if (!info.isFunction() && !info.isConstructor() && idMethod.getNestedDepth() == 2)
                {
                listHandles.add(xRTMethod.makeHandle(typeTarget, idMethod));
                }
            }
        MethodHandle[] ahMethods = listHandles.toArray(new MethodHandle[0]);
        ArrayHandle    hArray    = ensureMethodArrayTemplate().createArrayHandle(
                ensureMethodArray(typeTarget), ahMethods);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertyProperties(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                        typeTarget = hType.getDataType();
        TypeInfo                            infoTarget = typeTarget.ensureTypeInfo();
        Map<PropertyConstant, PropertyInfo> mapProps   = infoTarget.getProperties();
        ArrayList<ObjectHandle>             listProps  = new ArrayList<>(mapProps.size());
        ConstantPool                        pool       = frame.poolContext();
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
            {
            PropertyConstant idProp   = entry.getKey();
            PropertyInfo     infoProp = entry.getValue();
            if (!infoProp.isConstant() && idProp.getNestedDepth() == 1)
                {
                TypeConstant  typeProperty = entry.getKey().getValueType(typeTarget);
                PropertyHandle hProperty   = xRTProperty.INSTANCE.makeHandle(typeProperty);

                listProps.add(hProperty);
                }
            }
        ArrayHandle hArray = ensurePropertyArrayTemplate().createArrayHandle(
                ensurePropertyArray(typeTarget), listProps.toArray(new ObjectHandle[0]));
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
     * Implements property: underlyingTypes.get()
     */
    public int getPropertyUnderlyingTypes(Frame frame, TypeHandle hType, int iReturn)
        {
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
            ahTypes[i] = aUnderlying[i].getTypeHandle();
            }

        ArrayHandle hArray = ensureTypeArrayTemplate().createArrayHandle(ensureTypeArray(), ahTypes);
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

            if (Op.isDeferred(hEnum))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hEnum};
                Frame.Continuation stepNext = frameCaller ->
                    frameCaller.assignValues(aiReturn, xBoolean.TRUE, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return frame.assignValues(aiReturn, xBoolean.TRUE, hEnum);
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Annotation annotated()}.
     */
    public int invokeAnnotated(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hAnnotation = null; // TODO
        return hAnnotation == null
                ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                : frame.assignValues(aiReturn, xBoolean.TRUE, hAnnotation);
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
            TypeHandle hParent = typeTarget.getParentType().getTypeHandle();
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
        TypeConstant typeTarget = hType.getDataType();
        if (typeTarget.isSingleUnderlyingClass(true))
            {
            IdentityConstant idClz  = typeTarget.getSingleUnderlyingClass(true);
            ObjectHandle     hClass = frame.getConstHandle(idClz);

            if (Op.isDeferred(hClass))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hClass};
                Frame.Continuation stepNext = frameCaller ->
                    frame.assignValues(aiReturn, xBoolean.TRUE, ahValue[0]);

                return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
                }

            return frame.assignValues(aiReturn, xBoolean.TRUE, hClass);
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Property fromProperty()}.
     */
    public int invokeFromProperty(Frame frame, TypeHandle hType, int[] aiReturn)
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

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional Type!<> modifying()}.
     */
    public int invokeModifying(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant type  = hType.getDataType();
        return type.isModifyingType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE, type.getUnderlyingType().getTypeHandle())
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
            if (id.getFormat() == Constant.Format.Class)
                {
                sName = ((ClassConstant) id).getPathString();
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
            ahTypes[i] = makeHandle(atypes[i]);
            }

        ArrayHandle hArray = ensureTypeArrayTemplate().createArrayHandle(ensureTypeArray(), ahTypes);
        return frame.assignValues(aiReturn, xBoolean.TRUE, hArray);
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
        TypeConstant type = hType.getDataType();
        return type.isRelationalType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        type.getUnderlyingType().getTypeHandle(),
                        type.getUnderlyingType2().getTypeHandle())
                : frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }


    // ----- Template caching -----------------------------------------------------------------------

    /**
     * @return the ClassTemplate for an Array of Type
     */
    public xArray ensureTypeArrayTemplate()
        {
        xArray template = TYPE_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType());
            TYPE_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typeTypeArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassTemplate for an Array of Property
     */
    public xArray ensurePropertyArrayTemplate()
        {
        xArray template = PROPERTY_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typePropertyArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeProperty());
            PROPERTY_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typePropertyArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassTemplate for an Array of Method
     */
    public xArray ensureMethodArrayTemplate()
        {
        xArray template = METHOD_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeMethodArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeMethod());
            METHOD_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typeMethodArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassTemplate for an Array of Function
     */
    public xArray ensureFunctionArrayTemplate()
        {
        xArray template = FUNCTION_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeFunctionArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeFunction());
            FUNCTION_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typeFunctionArray));
            assert template != null;
            }
        return template;
        }

    private static xArray TYPE_ARRAY_TEMPLATE;
    private static xArray PROPERTY_ARRAY_TEMPLATE;
    private static xArray METHOD_ARRAY_TEMPLATE;
    private static xArray FUNCTION_ARRAY_TEMPLATE;


    // ----- ClassComposition caching and helpers --------------------------------------------------

    /**
     * @return the ClassComposition for an Array of Type
     */
    public ClassComposition ensureTypeArray()
        {
        ClassComposition clz = TYPE_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType());
            TYPE_ARRAY = clz = f_templates.resolveClass(typeTypeArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of Property
     */
    public ClassComposition ensurePropertyArray(TypeConstant typeTarget)
        {
        assert typeTarget != null;
        ConstantPool pool = ConstantPool.getCurrentPool();
        TypeConstant typePropertyArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                pool.ensureParameterizedTypeConstant(pool.typeProperty(), typeTarget));
        return f_templates.resolveClass(typePropertyArray);
        }

    /**
     * @return the ClassComposition for an Array of Property constants
     */
    public ClassComposition ensureConstantArray()
        {
        ClassComposition clz = CONSTANT_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeConstantArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeProperty());
            CONSTANT_ARRAY = clz = f_templates.resolveClass(typeConstantArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of Method
     */
    public ClassComposition ensureMethodArray(TypeConstant typeTarget)
        {
        assert typeTarget != null;
        ConstantPool pool = ConstantPool.getCurrentPool();
        TypeConstant typeMethodArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                pool.ensureParameterizedTypeConstant(pool.typeMethod(), typeTarget));
        return f_templates.resolveClass(typeMethodArray);
        }

    /**
     * @return the ClassComposition for an Array of Function
     */
    public ClassComposition ensureFunctionArray()
        {
        ClassComposition clz = FUNCTION_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeFunctionArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeFunction());
            FUNCTION_ARRAY = clz = f_templates.resolveClass(typeFunctionArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the TypeConstant for a Constructor
     */
    public TypeConstant ensureConstructorType(TypeConstant typeTarget, TypeConstant typeParent)
        {
        assert typeTarget != null;
        ConstantPool pool = ConstantPool.getCurrentPool();
        TypeConstant typeParams  = typeParent == null
                ? pool.ensureParameterizedTypeConstant(pool.typeTuple(), TypeConstant.NO_TYPES)
                : pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeParent);
        TypeConstant typeReturns = pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeTarget);
        return pool.ensureParameterizedTypeConstant(pool.typeFunction(), typeParams, typeReturns);
        }

    /**
     * @return the ClassComposition for an Array of Constructor
     */
    public ClassComposition ensureConstructorArray(TypeConstant typeTarget, TypeConstant typeParent)
        {
        ConstantPool pool = ConstantPool.getCurrentPool();

        assert typeTarget != null;

        TypeConstant typeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                ensureConstructorType(typeTarget, typeParent));
        return f_templates.resolveClass(typeArray);
        }

    private static ClassComposition TYPE_ARRAY;
    private static ClassComposition CONSTANT_ARRAY;
    private static ClassComposition FUNCTION_ARRAY;


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
     * @param type   a TypeConstant used at runtime
     *
     * @return the handle to the appropriate Ecstasy {@code Type.Form} enum value
     */
    protected static EnumHandle makeFormHandle(Frame frame, TypeConstant type)
        {
        xEnum enumForm = (xEnum) INSTANCE.f_templates.getTemplate("Type.Form");

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
                return enumForm.getEnumByName("Parameterized");

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
    }
