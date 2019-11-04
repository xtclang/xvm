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

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTMethod.MethodHandle;

import org.xvm.runtime.template.collections.xArray;

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
    public boolean isGenericHandle()
        {
        return false;
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
        return new TypeHandle(INSTANCE.ensureClass(type.getType()));
        }

    /**
     * Inner class: TypeHandle. This is a handle to a native type.
     */
    public static class TypeHandle
        extends ObjectHandle
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
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        ArrayHandle hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
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
        ConstantPool                        pool       = frame.poolContext();
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
            {
            PropertyInfo propinfo = entry.getValue();
            if (!propinfo.isConstant())
                {
                continue;
                }

            TypeConstant typeReferent = propinfo.getType();
            TypeConstant typeImpl     = pool.ensurePropertyClassTypeConstant(typeTarget, entry.getKey());
            TypeConstant typeProperty = pool.ensureParameterizedTypeConstant(pool.typeProperty(),
                typeTarget, typeReferent, typeImpl);
            ObjectHandle hProperty    = xRTProperty.INSTANCE.makeHandle(typeProperty);

            listProps.add(hProperty);
            }

        ArrayHandle hArray = ensurePropertyArrayTemplate().createArrayHandle(
                ensurePropertyArray(typeTarget), listProps.toArray(new ObjectHandle[0]));
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
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle hParent = null;
            if (f_fParent)
                {
                hParent = ahArg[0];
                System.arraycopy(ahArg, 1, ahArg, 0, ahArg.length-1);
                }

            return f_clzTarget.getTemplate().construct(
                    frame, f_constructor, f_clzTarget, hParent, ahArg, iReturn);
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
        return frame.assignValue(iReturn, xBoolean.makeHandle(hType.getDataType().isImmutabilitySpecified()));
        }

    /**
     * Implements property: form.get()
     */
    public int getPropertyForm(Frame frame, TypeHandle hType, int iReturn)
        {
        ObjectHandle hForm = makeFormHandle(frame, hType.getDataType());
        return frame.assignValue(iReturn, hForm);
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
            MethodInfo info = entry.getValue();
            if (info.isFunction())
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
            MethodInfo info = entry.getValue();
            if (!info.isFunction() && !info.isConstructor())
                {
                listHandles.add(xRTMethod.makeHandle(typeTarget, entry.getKey()));
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
            PropertyInfo propinfo     = entry.getValue();
            if (propinfo.isConstant())
                {
                continue;
                }
            TypeConstant typeReferent = propinfo.getType();
            TypeConstant typeImpl     = pool.ensurePropertyClassTypeConstant(typeTarget, entry.getKey());
            TypeConstant typeProperty = pool.ensureParameterizedTypeConstant(pool.typeProperty(),
                                                typeTarget, typeReferent, typeImpl);
            ObjectHandle hProperty    = xRTProperty.INSTANCE.makeHandle(typeProperty);

            listProps.add(hProperty);
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
        else if (typeTarget.getFormat() == Constant.Format.TurtleType)
            {
            throw new UnsupportedOperationException("TODO GG"); // TODO GG (TypeSequenceTypeConstant) typeTarget
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
        return type.isAccessSpecified()
                ? frame.assignValues(aiReturn, xBoolean.TRUE, makeAccessHandle(frame, type.getAccess()))
                : frame.assignValues(aiReturn, xBoolean.FALSE, null);
        }

    /**
     * Implementation for: {@code conditional Annotation annotated()}.
     */
    public int invokeAnnotated(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hAnnotation = null; // TODO
        return hAnnotation == null
                ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
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
            return frame.assignValues(aiReturn, xBoolean.FALSE, null);
            }
        }

    /**
     * Implementation for: {@code conditional Class fromClass()}.
     */
    public int invokeFromClass(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hClass = null; // TODO
        return hClass == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hClass);
        }

    /**
     * Implementation for: {@code conditional Property fromProperty()}.
     */
    public int invokeFromProperty(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hProp = null; // TODO
        return hProp == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hProp);
        }

    /**
     * Implementation for: {@code conditional Type!<> modifying()}.
     */
    public int invokeModifying(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant type  = hType.getDataType();
        return type.isModifyingType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE, type.getUnderlyingType().getTypeHandle())
                : frame.assignValues(aiReturn, xBoolean.FALSE, null);
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
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, xString.makeHandle(sName));
        }

    /**
     * Implementation for: {@code conditional Type!<>[] parameterized()}.
     */
    public int invokeParameterized(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        // type.isParamsSpecified() frame.assignValues(aiReturn, xBoolean.TRUE, null) // TODO type.getParamTypesArray())
        ObjectHandle hParams = null; // TODO
        return hParams == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hParams);
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
        return type.isModifyingType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        type.getUnderlyingType().getTypeHandle(),
                        type.getUnderlyingType2().getTypeHandle())
                : frame.assignValues(aiReturn, xBoolean.FALSE, null, null);
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
        ClassComposition clz = f_templates.resolveClass(typePropertyArray);
        return clz;
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
        ClassComposition clz = f_templates.resolveClass(typeMethodArray);
        return clz;
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
    public ObjectHandle makeAccessHandle(Frame frame, Constants.Access access)
        {
        xEnum      enumAccess = (xEnum) f_templates.getTemplate("Type.Access");
        EnumHandle hEnum;
        switch (access)
            {
            case PUBLIC:
                hEnum = enumAccess.getEnumByName("Public");
                break;

            case PROTECTED:
                hEnum = enumAccess.getEnumByName("Protected");
                break;

            case PRIVATE:
                hEnum = enumAccess.getEnumByName("Private");
                break;

            case STRUCT:
                hEnum = enumAccess.getEnumByName("Struct");
                break;

            default:
                throw new IllegalStateException("unknown access value: " + access);
            }

        return Utils.ensureInitializedEnum(frame, hEnum);
        }

    /**
     * Given a TypeConstant, determine the Ecstasy "Form" value for the type.
     *
     * @param frame  the current frame
     * @param type   a TypeConstant used at runtime
     *
     * @return the handle to the appropriate Ecstasy {@code Type.Form} enum value
     */
    public ObjectHandle makeFormHandle(Frame frame, TypeConstant type)
        {
        xEnum      enumForm = (xEnum) f_templates.getTemplate("Type.Form");
        EnumHandle hEnum;

        switch (type.getFormat())
            {
            case TerminalType:
                if (type.isSingleDefiningConstant())
                    {
                    switch (type.getDefiningConstant().getFormat())
                        {
                        case NativeClass:
                            hEnum = enumForm.getEnumByName("Pure");
                            break;
                        case Module:
                        case Package:
                        case Class:
                        case ThisClass:
                        case ParentClass:
                        case ChildClass:
                            hEnum = enumForm.getEnumByName("Class");
                            break;
                        case Property:
                            hEnum = enumForm.getEnumByName("FormalProperty");
                            break;
                        case TypeParameter:
                            hEnum = enumForm.getEnumByName("FormalParameter");
                            break;
                        case FormalTypeChild:
                            hEnum = enumForm.getEnumByName("FormalChild");
                            break;
                        default:
                            throw new IllegalStateException("unsupported format: " +
                                    type.getDefiningConstant().getFormat());
                        }
                    }
                else
                    {
                    hEnum = enumForm.getEnumByName("Typedef");
                    }
                break;

            case ImmutableType:
                hEnum = enumForm.getEnumByName("Immutable");
                break;
            case AccessType:
                hEnum = enumForm.getEnumByName("Access");
                break;
            case AnnotatedType:
                hEnum = enumForm.getEnumByName("Annotated");
                break;
            case ParameterizedType:
                hEnum = enumForm.getEnumByName("Parameterized");
                break;
            case TurtleType:
                hEnum = enumForm.getEnumByName("Sequence");
                break;
            case VirtualChildType:
                hEnum = enumForm.getEnumByName("Child");
                break;
            case AnonymousClassType:
                hEnum = enumForm.getEnumByName("Class");
                break;
            case PropertyClassType:
                hEnum = enumForm.getEnumByName("Property");
                break;
            case UnionType:
                hEnum = enumForm.getEnumByName("Union");
                break;
            case IntersectionType:
                hEnum = enumForm.getEnumByName("Intersection");
                break;
            case DifferenceType:
                hEnum = enumForm.getEnumByName("Difference");
                break;
            case RecursiveType:
                hEnum = enumForm.getEnumByName("Typedef");
                break;

            case UnresolvedType:
            default:
                throw new IllegalStateException("unsupported type: " + type);
            }
        return Utils.ensureInitializedEnum(frame, hEnum);
        }
    }
