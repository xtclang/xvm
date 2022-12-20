package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;


/**
 * Native Property implementation.
 */
public class xRTProperty
        extends xConst
    {
    public static xRTProperty INSTANCE;

    public xRTProperty(Container container, ClassStructure structure, boolean fInstance)
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
        ConstantPool pool = pool();

        EMPTY_PROPERTY_ARRAY = pool.ensureArrayConstant(
                pool.ensureArrayType(pool.ensureEcstasyTypeConstant("reflect.Property")),
                Constant.NO_CONSTS);

        markNativeProperty("abstract");
        markNativeProperty("annotations");
        markNativeProperty("atomic");
        markNativeProperty("formal");
        markNativeProperty("hasField");
        markNativeProperty("hasUnreachableSetter");
        markNativeProperty("injected");
        markNativeProperty("lazy");
        markNativeProperty("name");
        markNativeProperty("readOnly");

        markNativeMethod("get", null, null);
        markNativeMethod("isConstant", null, null);
        markNativeMethod("of", null, null);
        markNativeMethod("set", null, null);

        invalidateTypeInfo();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof PropertyClassTypeConstant constProp)
            {
            TypeConstant typeParent = constProp.getParentType();
            PropertyInfo infoProp   = constProp.getPropertyInfo();
            ObjectHandle hProperty  = makeHandle(frame, typeParent, infoProp);

            return Op.isDeferred(hProperty)
                ? hProperty.proceed(frame, Utils.NEXT)
                : frame.pushStack(hProperty);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        PropertyHandle hThis = (PropertyHandle) hTarget;

        switch (sPropName)
            {
            case "abstract":
                return getPropertyAbstract(frame, hThis, iReturn);

            case "annotations":
                return getPropertyAnnotations(frame, hThis, iReturn);

            case "atomic":
                return getPropertyAtomic(frame, hThis, iReturn);

            case "formal":
                return getPropertyFormal(frame, hThis, iReturn);

            case "hasField":
                return getPropertyHasField(frame, hThis, iReturn);

            case "hasUnreachableSetter":
                return getPropertyHasUnreachableSetter(frame, hThis, iReturn);

            case "injected":
                return getPropertyInjected(frame, hThis, iReturn);

            case "lazy":
                return getPropertyLazy(frame, hThis, iReturn);

            case "name":
                return getPropertyName(frame, hThis, iReturn);

            case "readOnly":
                return getPropertyReadOnly(frame, hThis, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "of":
                return invokeOf(frame, (PropertyHandle) hTarget, hArg, iReturn);

            case "get":
                return invokeGet(frame, (PropertyHandle) hTarget, hArg, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "set":
                return invokeSet(frame, (PropertyHandle) hTarget, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "isConstant":
                return invokeIsConstant(frame, (PropertyHandle) hTarget, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- PropertyHandle support ----------------------------------------------------------------

    /**
     * Obtain a handle for the specified property.
     *
     * @param frame       the current frame
     * @param typeTarget  (optional) the type of the property target
     * @param infoProp    the property info
     *
     * @return the resulting {@link PropertyHandle} or a {@link DeferredCallHandle}
     */
    public static ObjectHandle makeHandle(Frame frame, TypeConstant typeTarget, PropertyInfo infoProp)
        {
        Annotation[] aAnno    = infoProp.getPropertyAnnotations();
        TypeConstant typeProp = infoProp.getIdentity().getValueType(frame.poolContext(), typeTarget);

        if (aAnno != null && aAnno.length > 0)
            {
            typeProp = frame.poolContext().ensureAnnotatedTypeConstant(typeProp, aAnno);

            TypeComposition clzProp = INSTANCE.ensureClass(frame.f_context.f_container, typeProp);
            PropertyHandle  hStruct = new PropertyHandle(clzProp.ensureAccess(Access.STRUCT));

            switch (INSTANCE.proceedConstruction(
                    frame, null, true, hStruct, Utils.OBJECTS_NONE, Op.A_STACK))
                {
                case Op.R_NEXT:
                    {
                    ObjectHandle hM = frame.popStack();
                    hM.makeImmutable();
                    return hM;
                    }

                case Op.R_CALL:
                    DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                    hDeferred.addContinuation(frameCaller ->
                        {
                        frameCaller.peekStack().makeImmutable();
                        return Op.R_NEXT;
                        });
                    return hDeferred;

                case Op.R_EXCEPTION:
                    return new DeferredCallHandle(frame.m_hException);
                }
            }

        return new PropertyHandle(INSTANCE.ensureClass(frame.f_context.f_container, typeProp));
        }

    /**
     * Inner class: PropertyHandle. This is a handle to a native property.
     */
    public static class PropertyHandle
            extends GenericHandle
        {
        protected PropertyHandle(TypeComposition clzProp)
            {
            super(clzProp);

            m_fMutable = clzProp.isStruct();
            }

        public TypeConstant getTargetType()
            {
            return getType().getParamType(0);
            }

        public TypeConstant getReferentType()
            {
            return getType().getParamType(1);
            }

        public TypeConstant getImplementationType()
            {
            return getType().getParamType(2);
            }

        public PropertyConstant getPropertyConstant()
            {
            return ((PropertyClassTypeConstant) getImplementationType()).getProperty();
            }

        public PropertyInfo getPropertyInfo()
            {
            return ((PropertyClassTypeConstant) getImplementationType()).getPropertyInfo();
            }
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: abstract.get()
     */
    public int getPropertyAbstract(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hProp.getPropertyInfo().isAbstract()));
        }

    /**
     * Implements property: annotations.get()
     */
    public int getPropertyAnnotations(Frame frame, PropertyHandle hProp, int iReturn)
        {
        PropertyInfo prop  = hProp.getPropertyInfo();
        Annotation[] aAnno = prop.getPropertyAnnotations();

        return aAnno.length > 0
                ? new Utils.CreateAnnos(aAnno, iReturn).doNext(frame)
                : frame.assignValue(iReturn,
                    Utils.makeAnnoArrayHandle(frame.f_context.f_container, Utils.OBJECTS_NONE));
        }

    /**
     * Implements property: atomic.get()
     */
    public int getPropertyAtomic(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hProp.getPropertyInfo().isAtomic()));
        }

    /**
     * Implements property: formal.get()
     */
    public int getPropertyFormal(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hProp.getPropertyInfo().isFormalType()));
        }

    /**
     * Implements property: hasField.get()
     */
    public int getPropertyHasField(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hProp.getPropertyInfo().hasField()));
        }

    /**
     * Implements property: hasUnreachableSetter.get()
     */
    public int getPropertyHasUnreachableSetter(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hProp.getPropertyInfo().isSetterUnreachable()));
        }

    /**
     * Implements property: injected.get()
     */
    public int getPropertyInjected(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hProp.getPropertyInfo().isInjected()));
        }

    /**
     * Implements property: lazy.get()
     */
    public int getPropertyLazy(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(hProp.getPropertyInfo().isLazy()));
        }

    /**
     * Implements property: name.get()
     */
    public int getPropertyName(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, xString.makeHandle(hProp.getPropertyConstant().getName()));
        }

    /**
     * Implements property: readOnly.get()
     */
    public int getPropertyReadOnly(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(!hProp.getPropertyInfo().isVar()));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Referent isConstant()}.
     */
    public int invokeIsConstant(Frame frame, PropertyHandle hProp, int[] aiReturn)
        {
        PropertyInfo info = hProp.getPropertyInfo();
        if (info.isConstant())
            {
            PropertyConstant  idProp      = hProp.getPropertyConstant();
            SingletonConstant idSingleton = idProp.getConstantPool().ensureSingletonConstConstant(idProp);

            return frame.assignConditionalDeferredValue(aiReturn, frame.getConstHandle(idSingleton));
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code Implementation of(Target)}.
     */
    public int invokeOf(Frame frame, PropertyHandle hProp, ObjectHandle hTarget, int iReturn)
        {
        PropertyConstant idProp   = hProp.getPropertyConstant();
        PropertyInfo     infoProp = hProp.getPropertyInfo();
        Access           access   = hProp.getType().getParamType(0).getAccess();

        return hTarget.getTemplate().createPropertyRef(frame, hTarget.ensureAccess(access),
                idProp, !infoProp.isVar(), iReturn);
        }

    /**
     * Implementation for: {@code Referent get(Target)}.
     */
    public int invokeGet(Frame frame, PropertyHandle hProp, ObjectHandle hTarget, int iReturn)
        {
        PropertyConstant idProp = hProp.getPropertyConstant();

        return hTarget.getTemplate().getPropertyValue(frame, hTarget, idProp, iReturn);
        }

    /**
     * Implementation for: {@code void set(Target, Referent)}.
     */
    public int invokeSet(Frame frame, PropertyHandle hProp, ObjectHandle[] ahArg, int iReturn)
        {
        ObjectHandle     hTarget = ahArg[0];
        PropertyConstant idProp  = hProp.getPropertyConstant();
        ObjectHandle     hValue  = ahArg[1];

        return hTarget.getTemplate().setPropertyValue(frame, hTarget, idProp, hValue);
        }


    // ----- Composition and handle caching --------------------------------------------------------

    /**
     * @return the handle for an empty Array of Property
     */
    public static ArrayHandle ensureEmptyArray(Container container)
        {
        ArrayHandle haEmpty = (ArrayHandle) container.f_heap.getConstHandle(EMPTY_PROPERTY_ARRAY);
        if (haEmpty == null)
            {
            ConstantPool    pool = container.getConstantPool();
            TypeComposition clz  = container.resolveClass(pool.ensureArrayType(pool.typeProperty()));

            haEmpty = xArray.createImmutableArray(clz, Utils.OBJECTS_NONE);
            container.f_heap.saveConstHandle(EMPTY_PROPERTY_ARRAY, haEmpty);
            }
        return haEmpty;
        }

    /**
     * @return the TypeComposition for an Array of Property for a given target type
     */
    public static TypeComposition ensureArrayComposition(Frame frame, TypeConstant typeTarget)
        {
        assert typeTarget != null;

        ConstantPool pool              = frame.poolContext();
        TypeConstant typePropertyArray = pool.ensureArrayType(
                pool.ensureParameterizedTypeConstant(pool.typeProperty(), typeTarget));
        return frame.f_context.f_container.resolveClass(typePropertyArray);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ArrayConstant EMPTY_PROPERTY_ARRAY;
    }