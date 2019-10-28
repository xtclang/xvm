package org.xvm.runtime.template;


import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.FormalTypeChildConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.reflect.xMethod;


/**
 * Native Type implementation.
 */
public class xType
        extends ClassTemplate
    {
    public static xType INSTANCE;

    public xType(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
                break; // TODO

            case "constants":
                break; // TODO

            case "constructors":
                break; // TODO

            case "explicitlyImmutable":
                break; // TODO

            case "form":
                return getFormProperty(frame, hThis, iReturn);

            case "functions":
                break; // TODO

            case "methods":
                return getMethodsProperty(frame, hThis, iReturn);

            case "multimethods":
                break; // TODO

            case "properties":
                break; // TODO

            case "recursive":
                break; // TODO

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
                        ? frame.assignValues(aiReturn, xBoolean.TRUE, makeAccessHandle(type.getAccess()))
                        : frame.assignValues(aiReturn, xBoolean.FALSE, null);
                    }

                case "annotated":
                    {
                    return type instanceof AnnotatedTypeConstant
                        ? frame.assignValues(aiReturn, xBoolean.TRUE, null) // TODO reflect.Annotation requires xClassTemplate
                        : frame.assignValues(aiReturn, xBoolean.FALSE, null);
                    }

                case "contained":
                    {
                    break; // TODO
                    }

                case "fromClass":
                    {
                    break; // TODO
                    }

                case "fromProperty":
                    {
                    break; // TODO
                    }

                case "modifying":
                    {
                    break; // TODO
                    }

                case "named":
                    {
                    break; // TODO
                    }

                case "parameterized":
                    {
                    break; // TODO
                    }

                case "relational":
                    {
                    break; // TODO
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
     * Implements property: form.get()
     */
    public int getFormProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        ObjectHandle hForm = makeFormHandle(hType.getDataType());
        return frame.assignValue(iReturn, hForm);
        }

    /**
     * Implements property: methods.get()
     */
    public int getMethodsProperty(Frame frame, TypeHandle hType, int iReturn)
        {
        TypeConstant                    typeTarget = hType.getDataType();
        Map<MethodConstant, MethodInfo> mapMethods = typeTarget.ensureTypeInfo().getMethods();
        xMethod.MethodHandle[]          ahMethods  = new xMethod.MethodHandle[mapMethods.size()];

        int iMethod = 0;
        for (MethodConstant idMethod : mapMethods.keySet())
            {
            ahMethods[iMethod++] = xMethod.makeHandle(typeTarget, idMethod);
            }

        ObjectHandle.ArrayHandle hArray = ensureMethodArrayTemplate().createArrayHandle(ensureMethodArray(), ahMethods);
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


    // ----- type caching --------------------------------------------------------------------------

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
            TYPE_ARRAY_TEMPLATE = ((xArray) f_templates.getTemplate(typeTypeArray));
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassTemplate for an Array of Type
     */
    public xArray ensureTypeArrayTemplate()
        {
        xArray template = TYPE_ARRAY_TEMPLATE;
        if (template == null)
            {
            ensureTypeArray();
            template = TYPE_ARRAY_TEMPLATE;
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an Array of Method
     */
    public ClassComposition ensureMethodArray()
        {
        ClassComposition clz = METHOD_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeMethodArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeMethod());
            METHOD_ARRAY = clz = f_templates.resolveClass(typeMethodArray);
            METHOD_ARRAY_TEMPLATE = ((xArray) f_templates.getTemplate(typeMethodArray));
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassTemplate for an Array of Method
     */
    public xArray ensureMethodArrayTemplate()
        {
        xArray template = METHOD_ARRAY_TEMPLATE;
        if (template == null)
            {
            ensureMethodArray();
            template = METHOD_ARRAY_TEMPLATE;
            assert template != null;
            }
        return template;
        }

    private ClassComposition TYPE_ARRAY;
    private xArray           TYPE_ARRAY_TEMPLATE;
    private ClassComposition METHOD_ARRAY;
    private xArray           METHOD_ARRAY_TEMPLATE;


    // ----- helpers -------------------------------------------------------------------------------

    public xEnum.EnumHandle makeAccessHandle(Constants.Access access)
        {
        xEnum enumAccess = (xEnum) getChildTemplate("Access");
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

    public xEnum.EnumHandle makeFormHandle(TypeConstant type)
        {
        xEnum enumForm = (xEnum) getChildTemplate("Form");

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
