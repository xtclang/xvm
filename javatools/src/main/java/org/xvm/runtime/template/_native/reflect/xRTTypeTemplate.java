package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.HandleConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.GenericArrayDelegate;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate.ComponentTemplateHandle;


/**
 * Native TypeTemplate implementation.
 */
public class xRTTypeTemplate
        extends xConst
    {
    public static xRTTypeTemplate INSTANCE;

    public xRTTypeTemplate(Container container, ClassStructure structure, boolean fInstance)
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

        TEMPLATE_ARRAY_TYPE = pool.ensureArrayType(
                                pool.ensureEcstasyTypeConstant("reflect.TypeTemplate"));

        CREATE_COMPOSITION_METHOD = f_struct.findMethod("createComposition", 2);

        markNativeProperty("desc");
        markNativeProperty("explicitlyImmutable");
        markNativeProperty("form");
        markNativeProperty("name");
        markNativeProperty("recursive");
        markNativeProperty("underlyingTypes");

        markNativeMethod("accessSpecified",   null, null);
        markNativeMethod("annotated",         null, null);
        markNativeMethod("contained",         null, null);
        markNativeMethod("fromClass",         null, null);
        markNativeMethod("fromProperty",      null, null);
        markNativeMethod("isA",               null, null);
        markNativeMethod("modifying",         null, null);
        markNativeMethod("relational",        null, null);
        markNativeMethod("parameterized",     null, null);
        markNativeMethod("purify",            null, null);
        markNativeMethod("parameterize",      null, null);
        markNativeMethod("annotate",          null, null);
        markNativeMethod("resolveFormalType", null, null);

        invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TypeTemplateHandle hType = (TypeTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "desc":
                return getPropertyDesc(frame, hType, iReturn);

            case "explicitlyImmutable":
                return getPropertyExplicitlyImmutable(frame, hType, iReturn);

            case "form":
                return getPropertyForm(frame, hType, iReturn);

            case "name":
                return getPropertyName(frame, hType, iReturn);

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
        TypeTemplateHandle hType = (TypeTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "isA":
                return invokeIsA(frame, hType, (TypeTemplateHandle) hArg, iReturn);

            case "parameterize":
                return invokeParameterize(frame, hType, (ArrayHandle) hArg, iReturn);

            case "annotate":
                return invokeAnnotate(frame, hType, (GenericHandle) hArg, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        TypeTemplateHandle hType = (TypeTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "purify":
                return invokePurify(frame, hType, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        TypeTemplateHandle hType = (TypeTemplateHandle) hTarget;
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

            case "parameterized":
                return invokeParameterized(frame, hType, aiReturn);

            case "resolveFormalType":
                return invokeResolveFormalType(frame, hType, (StringHandle) ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- TypeTemplateHandle support ------------------------------------------------------------

    /**
     * Obtain a {@link TypeTemplateHandle} for the specified type.
     *
     * @param container  the container for the handle
     * @param type       the {@link TypeConstant} to obtain a {@link TypeTemplateHandle} for
     *
     * @return the resulting {@link TypeTemplateHandle}
     */
    public static TypeTemplateHandle makeHandle(Container container, TypeConstant type)
        {
        ConstantPool    pool = INSTANCE.pool();
        TypeComposition clz  = INSTANCE.ensureClass(container, INSTANCE.getCanonicalType(),
                pool.ensureEcstasyTypeConstant("reflect.TypeTemplate"));
        return new TypeTemplateHandle(clz, type);
        }

    /**
     * Inner class: TypeTemplateHandle. This is a handle to a native TypeConstant.
     */
    public static class TypeTemplateHandle
            extends GenericHandle
        {
        protected TypeTemplateHandle(TypeComposition clz, TypeConstant type)
            {
            super(clz);

            f_type     = type;
            m_fMutable = false;
            }

        public TypeConstant getDataType()
            {
            return f_type;
            }

        @Override
        public String toString()
            {
            return super.toString() + " " + f_type;
            }

        private final TypeConstant f_type;
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: childTypes.get()
     */
    public int getPropertyDesc(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        TypeConstant type  = hType.getDataType();
        String       sDesc = type.getValueString();
        return frame.assignValue(iReturn, xString.makeHandle(sDesc));
        }

    /**
     * Implements property: explicitlyImmutable.get()
     */
    public int getPropertyExplicitlyImmutable(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        TypeConstant type = hType.getDataType();
        return frame.assignValue(iReturn, xBoolean.makeHandle(type.isImmutabilitySpecified()));
        }

    /**
     * Implements property: form.get()
     */
    public int getPropertyForm(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        TypeConstant type  = hType.getDataType();
        EnumHandle   hForm = makeFormHandle(frame, type);
        return Utils.assignInitializedEnum(frame, hForm, iReturn);
        }

    /**
     * Implements property: name.get()
     */
    public int getPropertyName(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        String       sName = null;
        TypeConstant type  = hType.getDataType();
        if (type.isSingleDefiningConstant() &&
                type.getDefiningConstant() instanceof ClassConstant idClz)
            {
            sName = type.isVirtualChild()
                    ? idClz.getName()
                    : idClz.getPathString();
            }

        return frame.assignValue(iReturn, sName == null ? xNullable.NULL : xString.makeHandle(sName));
        }

    /**
     * Implements property: recursive.get()
     */
    public int getPropertyRecursive(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        TypeConstant type   = hType.getDataType();
        boolean      fRecur = type.containsRecursiveType();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fRecur));
        }

    /**
     * Implements property: underlyingTypes.get()
     */
    public int getPropertyUnderlyingTypes(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        TypeConstant   typeTarget  = hType.getDataType();
        TypeConstant[] aUnderlying = TypeConstant.NO_TYPES;
        if (typeTarget.isModifyingType())
            {
            aUnderlying = new TypeConstant[] {typeTarget.getUnderlyingType()};
            }
        else if (typeTarget.isRelationalType())
            {
            aUnderlying = new TypeConstant[]
                            {typeTarget.getUnderlyingType(), typeTarget.getUnderlyingType2()};
            }
        else if (typeTarget.isFormalTypeSequence())
            {
            aUnderlying = new TypeConstant[] {typeTarget}; // turtle type
            }

        TypeTemplateHandle[] ahTypes = new TypeTemplateHandle[aUnderlying.length];
        for (int i = 0, c = ahTypes.length; i < c; ++i)
            {
            ahTypes[i] = makeHandle(frame.f_context.f_container, aUnderlying[i]);
            }

        ObjectHandle hArray = xArray.createImmutableArray(
                                ensureArrayClassComposition(frame.f_context.f_container), ahTypes);
        return frame.assignValue(iReturn, hArray);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Access accessSpecified()}.
     */
    public int invokeAccessSpecified(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
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
     * Implementation for: {@code conditional Annotation annotated()}.
     */
    public int invokeAnnotated(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();

        if (type.isAnnotated())
            {
            Annotation       annotation = type.getAnnotations()[0];
            IdentityConstant idClass    = (IdentityConstant) annotation.getAnnotationClass();
            Constant[]       aconstArg  = annotation.getParams();
            int              cArgs      = aconstArg.length;

            ComponentTemplateHandle hClass = xRTComponentTemplate.makeComponentHandle(
                    frame.f_context.f_container, idClass.getComponent());

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
                    ahArg[i] = xRTType.makeArgumentHandle(frame, aconstArg[i]);
                    }
                }

            if (Op.anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller ->
                    {
                    frameCaller.assignValue(aiReturn[0], xBoolean.TRUE);
                    return Utils.constructAnnotationTemplate(frame, hClass, ahArg, aiReturn[1]);
                    };
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
                }

            frame.assignValue(aiReturn[0], xBoolean.TRUE);
            return Utils.constructAnnotationTemplate(frame, hClass, ahArg, aiReturn[1]);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional TypeTemplate contained()}.
     */
    public int invokeContained(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant typeTarget = hType.getDataType();

        // REVIEW GG + CP: include PropertyClassTypeConstant?
        if (typeTarget.isVirtualChild() || typeTarget.isAnonymousClass())
            {
            TypeTemplateHandle hParent =
                    makeHandle(frame.f_context.f_container, typeTarget.getParentType());
            return frame.assignValues(aiReturn, xBoolean.TRUE, hParent);
            }
        else
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }
        }

    /**
     * Implementation for: {@code conditional Composition fromClass()}.
     */
    public int invokeFromClass(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        if (!type.isExplicitClassIdentity(true))
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        IdentityConstant idClz = type.getSingleUnderlyingClass(true);
        ClassStructure   clz   = (ClassStructure) idClz.getComponent();
        if (clz == null)
            {
            return frame.raiseException(
                    xException.invalidType(frame, "Unknown type " + type.getValueString()));
            }

        ComponentTemplateHandle hClass =
                xRTComponentTemplate.makeComponentHandle(frame.f_context.f_container, clz);

        return type.isAnnotated()
                ? new CreateAnnotationComposition(hClass, type.getAnnotations(), aiReturn).doNext(frame)
                : frame.assignValues(aiReturn, xBoolean.TRUE, hClass);
        }

    /**
     * Implementation for: {@code conditional PropertyTemplate fromProperty()}.
     */
    public int invokeFromProperty(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        if (type.isSingleDefiningConstant())
            {
            Constant constDef = type.getDefiningConstant();
            if (constDef instanceof PropertyConstant idProp)
                {
                TypeConstant  typeTarget = idProp.getClassIdentity().getType();
                TypeInfo      infoTarget = typeTarget.ensureTypeInfo();
                PropertyInfo  infoProp   = infoTarget.findProperty(idProp, true);
                ObjectHandle  hProperty  = xRTProperty.makeHandle(frame, typeTarget, infoProp);

                return Op.isDeferred(hProperty)
                    ? hProperty.proceed(frame, frameCaller ->
                        frameCaller.assignValues(aiReturn, xBoolean.TRUE, frameCaller.popStack()))
                    : frame.assignValues(aiReturn, xBoolean.TRUE, hProperty);
                }
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code Boolean isA(TypeTemplate that)}.
     */
    public int invokeIsA(Frame frame, TypeTemplateHandle hType, TypeTemplateHandle hThat, int iReturn)
        {
        TypeConstant typeThis = hType.getDataType();
        TypeConstant typeThat = hThat.getDataType();
        return frame.assignValue(iReturn, xBoolean.makeHandle(typeThis.isA(typeThat)));
        }

    /**
     * Implementation for: {@code conditional TypeTemplate modifying()}.
     */
    public int invokeModifying(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        return type.isModifyingType()
                ? frame.assignValues(aiReturn, xBoolean.TRUE,
                        makeHandle(frame.f_context.f_container, type.getUnderlyingType()))
                : frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional (TypeTemplate, TypeTemplate) relational()}.
     */
    public int invokeRelational(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant type      = hType.getDataType();
        Container    container = frame.f_context.f_container;
        return type.isRelationalType()
            ? frame.assignValues(aiReturn, xBoolean.TRUE,
                    makeHandle(container, type.getUnderlyingType()),
                    makeHandle(container, type.getUnderlyingType2()))
            : frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional TypeTemplate[] parameterized()}.
     */
    public int invokeParameterized(Frame frame, TypeTemplateHandle hType, int[] aiReturn)
        {
        TypeConstant type = hType.getDataType();
        if (!type.isParamsSpecified())
            {
            return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        TypeConstant[]       atypes  = type.getParamTypesArray();
        int                  cTypes  = atypes.length;
        TypeTemplateHandle[] ahTypes = new TypeTemplateHandle[cTypes];
        for (int i = 0; i < cTypes; ++i)
            {
            ahTypes[i] = makeHandle(frame.f_context.f_container, atypes[i]);
            }

        ObjectHandle hArray = xArray.createImmutableArray(
                ensureArrayClassComposition(frame.f_context.f_container), ahTypes);
        return frame.assignValues(aiReturn, xBoolean.TRUE, hArray);
        }

    /**
     * Implementation for: {@code TypeTemplate purify()}.
     */
    public int invokePurify(Frame frame, TypeTemplateHandle hType, int iReturn)
        {
        return frame.assignValue(iReturn, hType); // TODO GG - implement Pure type constant etc.
        }

    /**
     * Implementation for: {@code TypeTemplate! parameterize(TypeTemplate![] paramTypes = [])}.
     */
    public int invokeParameterize(Frame frame, TypeTemplateHandle hType, ArrayHandle hArray, int iReturn)
        {
        TypeConstant typeThis = hType.getDataType();

        if (typeThis.isParamsSpecified())
            {
            return frame.raiseException(xException.invalidType(frame,
                    "Already parameterized: " + typeThis.getValueString()));
            }

        int            cTypes = (int) hArray.m_hDelegate.m_cSize;
        TypeConstant[] aTypes = new TypeConstant[cTypes];
        for (int i = 0; i < cTypes; i++)
            {
            int iResult = hArray.getTemplate().extractArrayValue(frame, hArray, i, Op.A_STACK);
            if (iResult != Op.R_NEXT)
                {
                return frame.raiseException(
                        xException.invalidType(frame, "Invalid type array argument"));
                }
            aTypes[i] = ((TypeTemplateHandle) frame.popStack()).getDataType();

            // TODO: validate constraints
            }
        return frame.assignValue(iReturn,
                makeHandle(frame.f_context.f_container,
                frame.poolContext().ensureParameterizedTypeConstant(typeThis, aTypes)));
        }

    /**
     * Implementation for: {@code TypeTemplate! annotate(AnnotationTemplate annotation)}.
     */
    public int invokeAnnotate(Frame frame, TypeTemplateHandle hType, GenericHandle hAnno, int iReturn)
        {
        TypeConstant            typeThis  = hType.getDataType();
        ComponentTemplateHandle hTemplate = (ComponentTemplateHandle) hAnno.getField(frame, "template");
        ClassStructure          clzMixin  = (ClassStructure) hTemplate.getComponent();
        IdentityConstant        idMixin   = clzMixin.getIdentityConstant();
        TypeConstant            typeInto  = clzMixin.getTypeInto();

        if (clzMixin.getFormat() == Component.Format.MIXIN)
            {
            ConstantPool pool = frame.poolContext();

            // note, that the annotation could be into the Class itself
            if (typeThis.isA(typeInto) ||
                    pool.ensureParameterizedTypeConstant(pool.typeClass(), typeThis).isA(typeInto))
                {
                ArrayHandle          haArgs     = (ArrayHandle) hAnno.getField(frame, "arguments");
                GenericArrayDelegate haDelegate = (GenericArrayDelegate) haArgs.m_hDelegate;
                int                  cArgs      = (int) haDelegate.m_cSize;
                Constant[]           aconst;

                if (cArgs > 0)
                    {
                    aconst = new Constant[cArgs];

                    for (int i = 0; i < cArgs; i++)
                        {
                        GenericHandle hArg   = (GenericHandle) haDelegate.get(i);
                        ObjectHandle  hValue = hArg.getField(frame, "value");

                        aconst[i] = new HandleConstant(hValue);
                        }
                    }
                else
                    {
                    aconst = Constant.NO_CONSTS;
                    }

                AnnotatedTypeConstant typeAnno =
                        pool.ensureAnnotatedTypeConstant(idMixin, aconst, typeThis);
                return frame.assignValue(iReturn, makeHandle(frame.f_context.f_container, typeAnno));
                }
            }
        return frame.raiseException("Invalid annotation: " + idMixin.getValueString());
        }

    /**
     * Implementation for: {@code conditional TypeTemplate resolveFormalType(String)}
     */
    public int invokeResolveFormalType(Frame frame, TypeTemplateHandle hType,
                                       StringHandle hName, int[] aiReturn)
        {
        TypeConstant type  = hType.getDataType();
        TypeConstant typeR = type.resolveGenericType(hName.getStringValue());

        return typeR == null
                ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                : frame.assignValues(aiReturn, xBoolean.TRUE,
                    makeHandle(frame.f_context.f_container, typeR));
        }


    // ----- TypeComposition caching ---------------------------------------------------------------

    /**
     * @return the TypeComposition for an Array of TypeTemplate
     */
    public static TypeComposition ensureArrayClassComposition(Container container)
        {
        return container.ensureClassComposition(TEMPLATE_ARRAY_TYPE, xArray.INSTANCE);
        }

    private static TypeConstant TEMPLATE_ARRAY_TYPE;


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given an Access value, determine the corresponding Ecstasy "Access" value.
     *
     * @param frame   the current frame
     * @param access  an Access value
     *
     * @return the handle to the appropriate Ecstasy {@code Access} enum value
     */
    public EnumHandle makeAccessHandle(Frame frame, Constants.Access access)
        {
        xEnum enumAccess = (xEnum) f_container.getTemplate("reflect.Access");
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
     * @return the handle to the appropriate Ecstasy {@code TypeTemplate.Form} enum value
     */
    protected EnumHandle makeFormHandle(Frame frame, TypeConstant type)
        {
        xEnum enumForm = (xEnum) f_container.getTemplate("reflect.TypeTemplate.Form");

        switch (type.getFormat())
            {
            case ParameterizedType:
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

    /**
     * Helper class for collecting the annotation composition.
     */
    public static class CreateAnnotationComposition
            implements Frame.Continuation
        {
        public CreateAnnotationComposition(ComponentTemplateHandle hClass, Annotation[] aAnno,
                                           int[] aiReturn)
            {
            this.hClass   = hClass;
            this.aAnno    = aAnno;
            this.ahAnno   = new ObjectHandle[aAnno.length];
            this.aiReturn = aiReturn;
            stageNext     = Stage.ArgValue;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            switch (stageNext)
                {
                case ArgValue:
                    // the resolved args are in the ahValue array
                    stageNext = Stage.Argument;
                    break;

                case Argument:
                    assert iArg >= 0;
                    ahAnnoArg[iArg] = frameCaller.popStack();
                    break;

                case Template:
                    assert iAnno >= 0;
                    ahAnno[iAnno] = frameCaller.popStack();
                    stageNext = Stage.ArgValue;
                    break;
                }

            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            switch (stageNext)
                {
                case ArgValue:
                    {
                    // start working on the next AnnotationTemplate
                    if (++iAnno == aAnno.length)
                        {
                        // all done
                        break;
                        }
                    assert ahAnnoArg == null && ahValue == null;

                    Annotation anno  = aAnno[iAnno];
                    Constant[] aArg  = anno.getParams();
                    int        cArgs = aArg.length;

                    if (cArgs > 0)
                        {
                        ClassConstant  idAnno  = (ClassConstant) anno.getAnnotationClass();
                        ClassStructure clzAnno = (ClassStructure) idAnno.getComponent();

                        constructor = clzAnno.findMethod("construct", cArgs);
                        if (constructor == null)
                            {
                            return frameCaller.raiseException("missing annotation constructor " +
                                    idAnno.getValueString() + " with " + clzAnno + " parameters");
                            }

                        ahValue   = new ObjectHandle[cArgs];
                        ahAnnoArg = new ObjectHandle[cArgs];
                        for (int i = 0; i < cArgs; i++)
                            {
                            ahValue[i] = frameCaller.getConstHandle(aArg[i]);
                            }
                        if (Op.anyDeferred(ahValue))
                            {
                            return new Utils.GetArguments(ahValue, this).doNext(frameCaller);
                            }
                        }
                    else
                        {
                        ahValue   = Utils.OBJECTS_NONE;
                        ahAnnoArg = Utils.OBJECTS_NONE;
                        }

                    stageNext = Stage.Argument;
                    // break through
                    }

                case Argument:
                    {
                    assert ahValue != null && ahAnnoArg != null;

                    int cArgs = ahAnnoArg.length;

                    CreateArgs:
                    if (cArgs > 0)
                        {
                        if (++iArg == cArgs)
                            {
                            break CreateArgs;
                            }

                        ObjectHandle hValue = ahValue[iArg];
                        if (hValue.isMutable())
                            {
                            return frameCaller.raiseException("argument is not a const");
                            }
                        Parameter param   = constructor.getParam(iArg);
                        int       iResult = Utils.constructArgument(frameCaller,
                                                param.getType().freeze(), hValue, param.getName());
                        if (iResult == Op.R_CALL)
                            {
                            frameCaller.m_frameNext.addContinuation(this);
                            }
                        else
                            {
                            assert iResult == Op.R_EXCEPTION;
                            }
                        return iResult;
                        }
                    else
                        {
                        ahAnnoArg = Utils.OBJECTS_NONE;
                        }

                    iArg      = -1;
                    ahValue   = null;
                    stageNext = Stage.Template;
                    // break through
                    }

                case Template:
                    {
                    assert ahAnnoArg != null;

                    Annotation     anno    = aAnno[iAnno];
                    ClassConstant  idAnno  = (ClassConstant) anno.getAnnotationClass();
                    ClassStructure clzAnno = (ClassStructure) idAnno.getComponent();

                    ComponentTemplateHandle hAnnoClass = xRTComponentTemplate.
                            makeComponentHandle(frameCaller.f_context.f_container, clzAnno);

                    int iResult = Utils.constructAnnotationTemplate(
                                    frameCaller, hAnnoClass, ahAnnoArg, Op.A_STACK);
                    if (iResult == Op.R_CALL)
                        {
                        frameCaller.m_frameNext.addContinuation(this);

                        ahAnnoArg = null;
                        }
                    else
                        {
                        assert iResult == Op.R_EXCEPTION;
                        }
                    return iResult;
                    }
                }

            TypeComposition clzArray = xRTClassTemplate.
                    ensureAnnotationTemplateArrayComposition(frameCaller.f_context.f_container);

            ObjectHandle[] ahVar = new ObjectHandle[CREATE_COMPOSITION_METHOD.getMaxVars()];
            ahVar[0] = hClass;
            ahVar[1] = xArray.createImmutableArray(clzArray, ahAnno);

            return frameCaller.callN(CREATE_COMPOSITION_METHOD, null, ahVar, aiReturn);
            }

        enum Stage {ArgValue, Argument, Template}
        private Stage stageNext;

        private final ComponentTemplateHandle hClass;
        private final Annotation[]            aAnno;
        private final int[]                   aiReturn;
        private final ObjectHandle[]          ahAnno;

        private int                           iAnno = -1;
        private int                           iArg  = -1;
        private ObjectHandle[]                ahAnnoArg;
        private ObjectHandle[]                ahValue;
        private MethodStructure               constructor;
        }

    private static MethodStructure CREATE_COMPOSITION_METHOD;
    }