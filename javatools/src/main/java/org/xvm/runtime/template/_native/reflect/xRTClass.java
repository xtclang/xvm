package org.xvm.runtime.template._native.reflect;


import java.util.Iterator;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.DecoratedClassConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native Class implementation.
 */
public class xRTClass
        extends xConst
    {
    public static xRTClass INSTANCE;

    public xRTClass(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("composition");
        markNativeProperty("implicitName");
        markNativeProperty("name");
        markNativeProperty("path");
        markNativeProperty("virtualChild");

        markNativeMethod("allocate"              , null, null);
        markNativeMethod("derivesFrom"           , null, null);
        markNativeMethod("extends"               , null, null);
        markNativeMethod("getFormalNamesAndTypes", null, null);
        markNativeMethod("implements"            , null, null);
        markNativeMethod("incorporates"          , null, null);
        markNativeMethod("isSingleton"           , null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public void registerNativeTemplates()
        {
        ClassStructure clzEnum = f_templates.getClassStructure("Enumeration");
        new xRTEnumeration(f_templates, clzEnum, true).initNative();
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return type.isA(pool().typeEnumeration())
                ? xRTEnumeration.INSTANCE
                : this;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ClassConstant || constant instanceof DecoratedClassConstant)
            {
            IdentityConstant idClz    = (IdentityConstant) constant;
            TypeConstant     typeClz  = idClz.getValueType(null).
                resolveGenerics(frame.poolContext(), frame.getGenericsResolver());
            ClassComposition clz      = ensureClass(typeClz);
            ClassTemplate    template = clz.getTemplate();

            MethodStructure constructor = getStructure().findMethod("construct", 0);
            ObjectHandle[]  ahVar       = new ObjectHandle[constructor.getMaxVars()];

            return template.construct(frame, constructor, clz, null, ahVar, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public ObjectHandle createStruct(Frame frame, ClassComposition clazz)
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

            case "composition":
                return getPropertyComposition(frame, hTarget, iReturn);

            case "implicitName":
                return getPropertyImplicitName(frame, hTarget, iReturn);

            case "name":
                return getPropertyName(frame, hTarget, iReturn);

            case "path":
                return getPropertyPath(frame, hTarget, iReturn);

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

            case "getFormalNamesAndTypes":
                return invokeGetFormalNamesAndTypes(frame, hTarget, aiReturn);

            case "isSingleton":
                return invokeIsSingleton(frame, hTarget, aiReturn);

            // TODO CP
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(getClassType(hValue1).equals(getClassType(hValue2))));
        }

    @Override
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn,
            xOrdered.makeHandle(getClassType(hValue1).compareTo(getClassType(hValue2))));
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
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
        TypeConstant typeTarget = getClassType(hTarget);
        ObjectHandle hResult    = null; // TODO CP
        return frame.assignValue(iReturn, hResult);
        }

    /**
     * Implements property: implicitName.get()
     */
    public int getPropertyImplicitName(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TypeConstant     typeTarget = getClassType(hTarget);
        IdentityConstant idClz      = typeTarget.getSingleUnderlyingClass(true);
        ModuleConstant   idModule   = idClz.getModuleConstant();
        if (idModule.isEcstasyModule())
            {
            String sAlias = pool().getImplicitImportName("ecstasy." + idClz.getPathString());
            if (sAlias != null)
                {
                return frame.assignValue(iReturn, xString.makeHandle(sAlias));
                }
            }
        return frame.assignValue(iReturn, xNullable.NULL);
        }

    /**
     * Implements property: name.get()
     */
    public int getPropertyName(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TypeConstant     typeTarget = getClassType(hTarget);
        IdentityConstant idClz      = typeTarget.getSingleUnderlyingClass(true);
        String           sName      = idClz.getName();
        return frame.assignValue(iReturn, xString.makeHandle(sName));
        }

    /**
     * Implements property: path.get()
     */
    public int getPropertyPath(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        TypeConstant     typeTarget = getClassType(hTarget);
        IdentityConstant idClz      = typeTarget.getSingleUnderlyingClass(true);
        String           sPath      = idClz.getModuleConstant().getName() + ':' + idClz.getPathString();
        return frame.assignValue(iReturn, xString.makeHandle(sPath));
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

        ClassComposition clz      = f_templates.resolveClass(typePublic);
        ObjectHandle     hStruct  = template.createStruct(frame, clz);
        MethodStructure  methodAI = clz.ensureAutoInitializer();
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
     * Implementation for: {@code (String[], Type[]) getFormalNamesAndTypes()}.
     */
    public int invokeGetFormalNamesAndTypes(Frame frame, ObjectHandle hTarget, int[] aiReturn)
        {
        TypeConstant   typeClz = getClassType(hTarget);
        ClassStructure clz     = (ClassStructure) typeClz.getSingleUnderlyingClass(true).getComponent();
        boolean        fTuple  = typeClz.isTuple();

        int cParams = fTuple
                ? typeClz.getParamsCount()
                : clz.getTypeParamCount();
        if (cParams == 0)
            {
            return frame.assignValues(aiReturn, xString.ensureEmptyArray(), xRTType.ensureEmptyTypeArray());
            }

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

        return frame.assignValues(aiReturn,
                xString.ensureArrayTemplate().createArrayHandle(xString.ensureArrayComposition(), ahNames),
                xRTType.ensureArrayTemplate().createArrayHandle(xRTType.ensureTypeArrayComposition(), ahTypes));
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


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * ClassHandle is a trivial extension of GenericHandle that supports native equality
     * (used by JumpVal ops).
     */
    protected static class ClassHandle
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
        }


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ClassTemplate for an Array of Class
     */
    public static xArray ensureArrayTemplate()
        {
        xArray template = ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeClassArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeClass());
            ARRAY_TEMPLATE = template = ((xArray) INSTANCE.f_templates.getTemplate(typeClassArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an Array of Class
     */
    public static ClassComposition ensureArrayComposition()
        {
        ClassComposition clz = ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeClassArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    pool.typeClass());
            ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeClassArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of Class
     */
    public static ArrayHandle ensureEmptyArray()
        {
        if (ARRAY_EMPTY == null)
            {
            ARRAY_EMPTY = ensureArrayTemplate().createArrayHandle(
                ensureArrayComposition(), Utils.OBJECTS_NONE);
            }
        return ARRAY_EMPTY;
        }


    // ----- data members --------------------------------------------------------------------------

    private static xArray           ARRAY_TEMPLATE;
    private static ClassComposition ARRAY_CLZCOMP;
    private static ArrayHandle      ARRAY_EMPTY;
    }
