package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

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
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xMethod;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xFunction;


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
        markNativeProperty("multimethods");
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

            TypeConstant typeData = typeTarget.getParamTypesArray()[0].
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
        TypeHandle hThis = (TypeHandle) hTarget;

        switch (sPropName)
            {
            case "childTypes":
                return getChildTypesProperty(frame, hThis, iReturn);

            case "constants":
                return getConstantsProperty(frame, hThis, iReturn);

            case "constructors":
                return getConstructorsProperty(frame, hThis, iReturn);

            case "explicitlyImmutable":
                return frame.assignValue(iReturn, xBoolean.makeHandle(
                        hThis.getDataType().isImmutabilitySpecified()));

            case "form":
                return getFormProperty(frame, hThis, iReturn);

            case "functions":
                return getFunctionsProperty(frame, hThis, iReturn);

            case "methods":
                return getMethodsProperty(frame, hThis, iReturn);

            case "multimethods":
                return getMultimethodsProperty(frame, hThis, iReturn);

            case "properties":
                return getPropertiesProperty(frame, hThis, iReturn);

            case "recursive":
                return frame.assignValue(iReturn, xBoolean.makeHandle(
                        hThis.getDataType().containsRecursiveType()));

            case "underlyingTypes":
                return getUnderlyingTypesProperty(frame, hThis, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "purify":
                {
                // TODO GG
                break;
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        if (ahArg[0] instanceof TypeHandle)
            {
            TypeHandle   hType = (TypeHandle) ahArg[0];
            TypeConstant type  = hType.getDataType();
            switch (method.getName())
                {
                case "accessSpecified":
                    {
                    return type.isAccessSpecified()
                                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                                        makeAccessHandle(type.getAccess()))
                                : frame.assignValues(aiReturn, xBoolean.FALSE, null);
                    }

                case "annotated":
                    return calcAnnotated(frame, hType, aiReturn);

                case "contained":
                    return calcContained(frame, hType, aiReturn);

                case "fromClass":
                    return calcFromClass(frame, hType, aiReturn);

                case "fromProperty":
                    return calcFromProperty(frame, hType, aiReturn);

                case "modifying":
                    {
                    return type.isModifyingType()
                            ? frame.assignValues(aiReturn, xBoolean.TRUE,
                                    makeHandle(type.getUnderlyingType()))
                            : frame.assignValues(aiReturn, xBoolean.FALSE, null);
                    }

                case "named":
                    return calcNamed(frame, hType, aiReturn);

                case "parameterized":
                    return calcParameterized(frame, hType, aiReturn);

                case "relational":
                    {
                    return type.isModifyingType()
                            ? frame.assignValues(aiReturn, xBoolean.TRUE,
                                    makeHandle(type.getUnderlyingType()),
                                    makeHandle(type.getUnderlyingType2()))
                            : frame.assignValues(aiReturn, xBoolean.FALSE, null, null);
                    }
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
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
        ConstantPool pool = ConstantPool.getCurrentPool();
        return new TypeHandle(INSTANCE.ensureParameterizedClass(pool, type));
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
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: childTypes.get()
     */
    public int getChildTypesProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        ObjectHandle.ArrayHandle hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: constants.get()
     */
    public int getConstantsProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        ObjectHandle.ArrayHandle hArray = null; // TODO - ask GG if these are in TypeInfo.getProperties()
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: constructors.get()
     */
    public int getConstructorsProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                        typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo>     mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<xFunction.FunctionHandle> listHandles = new ArrayList<>();
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodInfo info = entry.getValue();
            if (info.isConstructor())
                {
                // TODO we need to create a factory "T function(a,b,c,...)" that does a "return new T(a,b,c,...)"
                // listHandles.add(xFunction.makeHandle(info.getHead().getMethodStructure()));
                }
            }
        xFunction.FunctionHandle[] ahFunctions = listHandles.toArray(new xFunction.FunctionHandle[0]);
        ObjectHandle.ArrayHandle   hArray      = ensureMethodArrayTemplate().createArrayHandle(
                ensureMethodArray(typeTarget), ahFunctions);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: form.get()
     */
    public int getFormProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        ObjectHandle hForm = makeFormHandle(hType.getDataType());
        return frame.assignValue(iReturn, hForm);
        }

    /**
     * Implements property: functions.get()
     */
    public int getFunctionsProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                        typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo>     mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<xFunction.FunctionHandle> listHandles = new ArrayList<>(mapMethods.size());
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodInfo info = entry.getValue();
            if (info.isFunction())
                {
                listHandles.add(xFunction.makeHandle(info.getHead().getMethodStructure()));
                }
            }
        xFunction.FunctionHandle[] ahFunctions = listHandles.toArray(new xFunction.FunctionHandle[0]);
        ObjectHandle.ArrayHandle   hArray      = ensureMethodArrayTemplate().createArrayHandle(
                ensureMethodArray(typeTarget), ahFunctions);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: methods.get()
     */
    public int getMethodsProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                    typeTarget  = hType.getDataType();
        Map<MethodConstant, MethodInfo> mapMethods  = typeTarget.ensureTypeInfo().getMethods();
        ArrayList<xMethod.MethodHandle> listHandles = new ArrayList<>(mapMethods.size());
        for (Map.Entry<MethodConstant, MethodInfo> entry : mapMethods.entrySet())
            {
            MethodInfo info = entry.getValue();
            if (!info.isFunction() && !info.isConstructor())
                {
                listHandles.add(xMethod.makeHandle(typeTarget, entry.getKey()));
                }
            }
        xMethod.MethodHandle[]   ahMethods = listHandles.toArray(new xMethod.MethodHandle[0]);
        ObjectHandle.ArrayHandle hArray    = ensureMethodArrayTemplate().createArrayHandle(
                ensureMethodArray(typeTarget), ahMethods);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: multimethods.get()
     */
    public int getMultimethodsProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        TypeInfo     infoTarget = typeTarget.ensureTypeInfo();

        ObjectHandle.ArrayHandle hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertiesProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                        typeTarget = hType.getDataType();
        TypeInfo                            infoTarget = typeTarget.ensureTypeInfo();
        Map<PropertyConstant, PropertyInfo> mapProps   = infoTarget.getProperties();
        ObjectHandle[]                      ahProps    = new ObjectHandle[mapProps.size()];
        ConstantPool                        pool       = frame.poolContext();
        int                                 cProps     = 0;
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : mapProps.entrySet())
            {
            PropertyInfo     propinfo     = entry.getValue();
            TypeConstant     typeReferent = propinfo.getType();
            TypeConstant     typeImpl     = pool.ensurePropertyClassTypeConstant(typeTarget, entry.getKey());
            TypeConstant     typeProperty = pool.ensureParameterizedTypeConstant(pool.typeProperty(),
                                                    typeTarget, typeReferent, typeImpl);
            ClassComposition clzProperty = f_templates.resolveClass(typeProperty);
            ObjectHandle     hProperty   = xRTProperty.INSTANCE.makeHandle(clzProperty);

            ahProps[cProps++] = hProperty;
            }
        ObjectHandle.ArrayHandle hArray = ensurePropertyArrayTemplate().createArrayHandle(
                ensurePropertyArray(typeTarget), ahProps);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: underlyingTypes.get()
     */
    public int getUnderlyingTypesProperty(Frame frame, TypeHandle hType, int iReturn)
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
            ahTypes[i] = makeHandle(aUnderlying[i]);
            }

        ObjectHandle.ArrayHandle hArray = ensureTypeArrayTemplate().createArrayHandle(ensureTypeArray(), ahTypes);
        return frame.assignValue(iReturn, hArray);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Annotation annotated()}.
     */
    public int calcAnnotated(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hAnnotation = null; // TODO
        return hAnnotation == null
                ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
                : frame.assignValues(aiReturn, xBoolean.TRUE, hAnnotation);
        }

    /**
     * Implementation for: {@code conditional Type!<> contained()}.
     */
    public int calcContained(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        TypeConstant typeTarget = hType.getDataType();
        if (typeTarget.isVirtualChild() || typeTarget.isAnonymousClass()) // REVIEW GG
            {
            TypeHandle hParent = makeHandle(typeTarget.getParentType());
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
    public int calcFromClass(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hClass = null; // TODO
        return hClass == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hClass);
        }

    /**
     * Implementation for: {@code conditional Property fromProperty()}.
     */
    public int calcFromProperty(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hProp = null; // TODO
        return hProp == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hProp);
        }

    /**
     * Implementation for: {@code conditional String named()}.
     */
    public int calcNamed(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        ObjectHandle hName = null; // TODO
        return hName == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hName);
        }

    /**
     * Implementation for: {@code conditional Type!<>[] parameterized()}.
     */
    public int calcParameterized(Frame frame, TypeHandle hType, int[] aiReturn)
        {
        // type.isParamsSpecified() frame.assignValues(aiReturn, xBoolean.TRUE, null) // TODO type.getParamTypesArray())
        ObjectHandle hParams = null; // TODO
        return hParams == null
            ? frame.assignValues(aiReturn, xBoolean.FALSE, null)
            : frame.assignValues(aiReturn, xBoolean.TRUE, hParams);
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

    private xArray TYPE_ARRAY_TEMPLATE;
    private xArray PROPERTY_ARRAY_TEMPLATE;
    private xArray METHOD_ARRAY_TEMPLATE;
    private xArray FUNCTION_ARRAY_TEMPLATE;


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
        ConstantPool pool = INSTANCE.pool();
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
        ConstantPool pool = INSTANCE.pool();
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
     * @return the ClassComposition for an Array of Constructor
     */
    public ClassComposition ensureConstructorArray(TypeConstant typeTarget)
        {
        assert typeTarget != null;
        ConstantPool pool = INSTANCE.pool();
        TypeConstant typeConstructorArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                pool.ensureParameterizedTypeConstant(pool.typeFunction(), pool.typeTuple(),
                pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeTarget)));
        ClassComposition clz = f_templates.resolveClass(typeConstructorArray);
        return clz;
        }

    private ClassComposition TYPE_ARRAY;
    private ClassComposition CONSTANT_ARRAY;
    private ClassComposition FUNCTION_ARRAY;


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given an Access value, determine the corresponding Ecstasy "Access" value.
     *
     * @param access  an Access value
     *
     * @return the handle to the appropriate Ecstasy {@code Type.Access} enum value
     */
    public xEnum.EnumHandle makeAccessHandle(Constants.Access access)
        {
        xEnum enumAccess = (xEnum) f_templates.getTemplate("Type.Access");
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
     * @param type  a TypeConstant used at runtime
     *
     * @return the handle to the appropriate Ecstasy {@code Type.Form} enum value
     */
    public xEnum.EnumHandle makeFormHandle(TypeConstant type)
        {
        xEnum enumForm = (xEnum) f_templates.getTemplate("Type.Form");

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
                        }
                    }
                else
                    {
                    return enumForm.getEnumByName("Typedef");
                    }
                break;

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
            }

        throw new IllegalStateException("unsupported type: " + type);
        }
    }
