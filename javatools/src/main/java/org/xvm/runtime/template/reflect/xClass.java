package org.xvm.runtime.template.reflect;


import java.util.Iterator;

import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.DecoratedClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTComponentTemplate;
import org.xvm.runtime.template._native.reflect.xRTType;
import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native Class implementation.
 */
public class xClass
        extends xConst
    {
    public static xClass INSTANCE;

    public xClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("abstract");
        markNativeProperty("canonicalParams");
        markNativeProperty("composition");
        markNativeProperty("virtualChild");

        markNativeMethod("allocate"    , null, null);
        markNativeMethod("derivesFrom" , null, null);
        markNativeMethod("extends"     , null, null);
        markNativeMethod("implements"  , null, null);
        markNativeMethod("incorporates", null, null);
        markNativeMethod("isSingleton" , null, null);
        markNativeMethod("defaultValue", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeComposition ensureClass(TypeConstant typeActual)
        {
        return typeActual.equals(getCanonicalType()) || !isCanonicalStructure(typeActual)
            ? super.ensureClass(typeActual)
            : getCanonicalClass().ensureCanonicalizedComposition(typeActual);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ClassConstant || constant instanceof DecoratedClassConstant)
            {
            IdentityConstant idClz    = (IdentityConstant) constant;
            TypeConstant     typeClz  = idClz.getValueType(frame.poolContext(), null);

            typeClz = typeClz.resolveGenerics(frame.poolContext(),
                        frame.getGenericsResolver(typeClz.containsDynamicType(null)));

            ClassTemplate template;
            switch (idClz.getComponent().getFormat())
                {
                case ENUMVALUE:
                    template = xEnumValue.INSTANCE;
                    break;

                case ENUM:
                    template = xEnumeration.INSTANCE;
                    break;

                default:
                    template = this;
                    break;
                }

            TypeComposition clz = template.ensureClass(typeClz);

            // skip the natural constructor; it's a "make believe" code anyway
            return template.construct(frame, null, clz, null, Utils.OBJECTS_NONE, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        return new ClassHandle(clazz.ensureAccess(Constants.Access.STRUCT));
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "abstract":
                return getPropertyAbstract(frame, hTarget, iReturn);

            case "canonicalParams":
                return invokePropertyCanonicalParams(frame, (ClassHandle) hTarget, iReturn);

            case "composition":
                return getPropertyComposition(frame, hTarget, iReturn);

            case "virtualChild":
                return getPropertyVirtualChild(frame, hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "implements":
                return invokeImplements(frame, hTarget, hArg, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "allocate":
                return invokeAllocate(frame, hTarget, aiReturn);

            case "isSingleton":
                return invokeIsSingleton(frame, hTarget, aiReturn);

            case "defaultValue":
                return invokeDefaultValue(frame, hTarget, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(getClassType(hValue1).equals(getClassType(hValue2))));
        }

    @Override
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn,
            xOrdered.makeHandle(getClassType(hValue1).compareTo(getClassType(hValue2))));
        }

    @Override
    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, xInt64.makeHandle(getClassType(hTarget).hashCode()));
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: abstract.get()
     */
    public int getPropertyAbstract(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TypeConstant typeTarget = getClassType(hTarget);
        ObjectHandle hResult    = xBoolean.makeHandle(typeTarget.ensureTypeInfo().isAbstract());
        return frame.assignValue(iReturn, hResult);
        }

    /**
     * Implements property: composition.get()
     */
    public int getPropertyComposition(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        // TODO CP: can typeTarget be annotated?
        TypeConstant typeTarget  = getClassType(hTarget);
        Constant     constTarget = typeTarget.getDefiningConstant();

        if  (constTarget instanceof IdentityConstant)
            {
            Component component = ((IdentityConstant) constTarget).getComponent();
            return frame.assignValue(iReturn, xRTComponentTemplate.makeComponentHandle(component));
            }

        throw new IllegalStateException();
        }

    /**
     * Implements property: virtualChild.get()
     */
    public int getPropertyVirtualChild(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TypeConstant typeTarget = getClassType(hTarget);
        ObjectHandle hResult    = xBoolean.makeHandle(typeTarget.isVirtualChild());
        return frame.assignValue(iReturn, hResult);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional StructType allocate()}.
     */
    public int invokeAllocate(Frame frame, ObjectHandle hTarget, int[] aiReturn)
        {
        TypeConstant typeClz    = hTarget.getType();
        TypeConstant typePublic = typeClz.getParamType(0);

        if (typePublic.isImmutabilitySpecified())
            {
            typePublic = typePublic.getUnderlyingType();
            }
        if (typePublic.isAccessSpecified())
            {
            typePublic = typePublic.getUnderlyingType();
            }

        ClassTemplate template = f_templates.getTemplate(typePublic);

        switch (template.getStructure().getFormat())
            {
            case CLASS:
            case CONST:
            case MIXIN:
            case ENUMVALUE:
                break;

            default:
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

        TypeComposition clz      = typePublic.ensureClass(frame);
        ObjectHandle    hStruct  = template.createStruct(frame, clz);
        MethodStructure methodAI = clz.ensureAutoInitializer(frame.poolContext());
        if (methodAI != null)
            {
            switch (frame.call1(methodAI, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE))
                {
                case Op.R_NEXT:
                    break;

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValues(aiReturn, xBoolean.TRUE, hStruct));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        return frame.assignValues(aiReturn, xBoolean.TRUE, hStruct);
        }

    /**
     * Implementation for: {@code @Lazy ListMap<String, Type> canonicalParams.calc()}.
     */
    public int invokePropertyCanonicalParams(Frame frame, ClassHandle hClass, int iReturn)
        {
        ObjectHandle hMap = hClass.getField(frame, "canonicalParams");
        if (hMap == null)
            {
            TypeConstant   typeClz = getClassType(hClass);
            boolean        fTuple  = typeClz.isTuple();
            ClassStructure clz     = null;
            int            cParams = 0;

            if (typeClz.isSingleUnderlyingClass(true))
                {
                clz     = (ClassStructure) typeClz.getSingleUnderlyingClass(true).getComponent();
                cParams = fTuple
                        ? typeClz.getParamsCount()
                        : clz.getTypeParamCount();
                }

            int iResult;
            if (cParams == 0)
                {
                iResult = Utils.constructListMap(frame, xRTType.ensureListMapComposition(),
                        xString.ensureEmptyArray(), xRTType.ensureEmptyTypeArray(), Op.A_STACK);
                }
            else
                {
                StringHandle[] ahNames = new StringHandle[cParams];
                TypeHandle  [] ahTypes = new TypeHandle  [cParams];
                if (fTuple)
                    {
                    TypeConstant[] atypeParam = typeClz.getParamTypesArray();
                    for (int i = 0; i < cParams; ++i)
                        {
                        ahNames[i] = xString.makeHandle("ElementTypes[" + i + "]");
                        ahTypes[i] = atypeParam[i].ensureTypeHandle(frame.poolContext());
                        }
                    }
                else
                    {
                    Iterator<StringConstant> iterNames  = clz.getTypeParams().keySet().iterator();
                    TypeConstant[]           atypeParam = clz.normalizeParameters(INSTANCE.pool(), typeClz.getParamTypesArray());
                    for (int i = 0; i < cParams; ++i)
                        {
                        ahNames[i] = xString.makeHandle(iterNames.next().getValue());
                        ahTypes[i] = atypeParam[i].ensureTypeHandle(frame.poolContext());
                        }
                    }

                iResult = Utils.constructListMap(frame, xRTType.ensureListMapComposition(),
                        xArray.makeStringArrayHandle(ahNames),
                        xArray.createImmutableArray(xRTType.ensureTypeArrayComposition(), ahTypes),
                        Op.A_STACK);
                }
            switch (iResult)
                {
                case Op.R_NEXT:
                    hMap = frame.popStack();
                    hClass.setField(frame, "canonicalParams", hMap);
                    break;

                case Op.R_CALL:
                    Frame.Continuation stepNext = frameCaller ->
                        {
                        ObjectHandle hResult = frameCaller.popStack();
                        hClass.setField(frame, "canonicalParams", hResult);
                        return frameCaller.assignValue(iReturn, hResult);
                        };
                    frame.m_frameNext.addContinuation(stepNext);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        return frame.assignValue(iReturn, hMap);
        }

    /**
     * Implementation for: {@code conditional PublicType isSingleton()}.
     */
    public int invokeIsSingleton(Frame frame, ObjectHandle hTarget, int[] aiReturn)
        {
        TypeConstant typeClz = getClassType(hTarget);
        if (typeClz.ensureTypeInfo().isSingleton())
            {
            IdentityConstant idClz         = typeClz.getSingleUnderlyingClass(false);
            ConstantPool     pool          = frame.poolContext();
            Constant         constInstance = pool.ensureSingletonConstConstant(idClz);

            return frame.assignConditionalDeferredValue(aiReturn,
                    frame.getConstHandle(constInstance));
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional PublicType defaultValue()}.
     */
    public int invokeDefaultValue(Frame frame, ObjectHandle hTarget, int[] aiReturn)
        {
        TypeConstant typeClz      = getClassType(hTarget);
        Constant     constDefault = typeClz.getDefaultValue();
        if (constDefault != null)
            {
            return frame.assignConditionalDeferredValue(aiReturn,
                    frame.getConstHandle(constDefault));
            }
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code Boolean implements(Class clz)}.
     */
    public int invokeImplements(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        TypeConstant typeThis = getClassType(hTarget);
        TypeConstant typeThat = getClassType(hArg);
        // TODO GG: not quite right
        return frame.assignValue(iReturn, xBoolean.makeHandle(typeThis.isA(typeThat)));
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Return the type of the Class, reverse-engineered from the PublicType.
     *
     * @param hTarget  the handle for the instance of Class
     *
     * @return the TypeConstant for the Class
     */
    protected static TypeConstant getClassType(ObjectHandle hTarget)
        {
        return hTarget.getComposition().getType().getParamType(0);
        }

    /**
     * @return if the structure for the specified type is the same as the structure for the
     *         canonical Class type
     */
    private boolean isCanonicalStructure(TypeConstant type)
        {
        if (type.isAnnotated())
            {
            ConstantPool pool  = type.getConstantPool();
            Annotation[] aAnno = type.getAnnotations();
            for (Annotation anno : aAnno)
                {
                ClassConstant idAnno = (ClassConstant) anno.getAnnotationClass();
                if (idAnno.equals(pool.clzAbstract()) ||
                    idAnno.equals(pool.clzOverride()))
                    {
                    continue;
                    }
                return false;
                }
            }
        return true;
        }

    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * ClassHandle is a trivial extension of GenericHandle that supports native equality
     * (used by JumpVal ops).
     */
    public static class ClassHandle
            extends GenericHandle
        {
        public ClassHandle(TypeComposition clazz)
            {
            super(clazz);
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int hashCode()
            {
            return getType().getParamType(0).hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj instanceof ClassHandle)
                {
                ClassHandle that = (ClassHandle) obj;

                TypeConstant typeThis = this.getType().getParamType(0);
                TypeConstant typeThat = that.getType().getParamType(0);
                return typeThis.equals(typeThat);
                }
            return false;
            }

        @Override
        public String toString()
            {
            return "(Class) " + getType().getParamType(0);
            }
        }


    // ----- Composition and handle caching --------------------------------------------------------

    /**
     * @return the TypeComposition for an Array of Class
     */
    public static TypeComposition ensureArrayComposition()
        {
        TypeComposition clz = ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeClassArray = pool.ensureArrayType(pool.typeClass());
            ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeClassArray);
            assert clz != null;
            }
        return clz;
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition ARRAY_CLZCOMP;
    }
