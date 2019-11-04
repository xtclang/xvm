package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SingletonConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xString;


/**
 * Native Property implementation.
 */
public class xRTProperty
        extends xConst
    {
    public static xRTProperty INSTANCE;

    public xRTProperty(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("abstract");
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

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        // TODO CP - identity mode for property name needs to emit a constant that provides enough
        //           info (e.g. a PropertyClassTypeConstant)

        // @deprecated compiler should no longer emit PropertyConstant for identity mode TODO remove
        if (constant instanceof PropertyConstant)
            {
            ConstantPool     pool         = constant.getConstantPool();  // note: purposeful
            PropertyConstant idProp       = (PropertyConstant) constant;
            TypeConstant     typeTarget   = idProp.getClassIdentity().getType();
            TypeInfo         infoTarget   = typeTarget.ensureTypeInfo();
            PropertyInfo     infoProp     = infoTarget.findProperty(idProp);
            TypeConstant     typeReferent = infoProp.getType();
            TypeConstant     typeImpl     = pool.ensurePropertyClassTypeConstant(typeTarget, idProp);
            TypeConstant     typeProperty = pool.ensureParameterizedTypeConstant(pool.typeProperty(),
                                                typeTarget, typeReferent, typeImpl);
            ObjectHandle     hProperty    = xRTProperty.INSTANCE.makeHandle(typeProperty);

            frame.pushStack(hProperty);
            return Op.R_NEXT;
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
     * Obtain a {@link PropertyHandle} for the specified property.
     *
     * @param typeProp  the type of the property to obtain a {@link PropertyHandle} for
     *
     * @return the resulting {@link PropertyHandle}
     */
    public PropertyHandle makeHandle(TypeConstant typeProp)
        {
        return new PropertyHandle(ensureClass(typeProp));
        }

    /**
     * Inner class: PropertyHandle. This is a handle to a native property.
     */
    public static class PropertyHandle
            extends ObjectHandle
        {
        protected PropertyHandle(ClassComposition clzProp)
            {
            super(clzProp);
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
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().isAbstract());
        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Implements property: atomic.get()
     */
    public int getPropertyAtomic(Frame frame, PropertyHandle hProp, int iReturn)
        {
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().isAtomic());
        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Implements property: formal.get()
     */
    public int getPropertyFormal(Frame frame, PropertyHandle hProp, int iReturn)
        {
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().isFormalType());
        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Implements property: hasField.get()
     */
    public int getPropertyHasField(Frame frame, PropertyHandle hProp, int iReturn)
        {
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().hasField());
        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Implements property: hasUnreachableSetter.get()
     */
    public int getPropertyHasUnreachableSetter(Frame frame, PropertyHandle hProp, int iReturn)
        {
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().isSetterUnreachable());
        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Implements property: injected.get()
     */
    public int getPropertyInjected(Frame frame, PropertyHandle hProp, int iReturn)
        {
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().isInjected());
        return frame.assignValue(iReturn, hValue);
        }

    /**
     * Implements property: lazy.get()
     */
    public int getPropertyLazy(Frame frame, PropertyHandle hProp, int iReturn)
        {
        ObjectHandle hValue = xBoolean.makeHandle(hProp.getPropertyInfo().isLazy());
        return frame.assignValue(iReturn, hValue);
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
        ObjectHandle hValue = xBoolean.makeHandle(!hProp.getPropertyInfo().isVar());
        return frame.assignValue(iReturn, hValue);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional Referent isConstant()}.
     */
    public int invokeIsConstant(Frame frame, PropertyHandle hProp, int[] aiReturn)
        {
        PropertyInfo info = hProp.getPropertyInfo();
        if (!info.isConstant())
            {
            return frame.assignValues(aiReturn, xBoolean.FALSE, null);
            }

        PropertyConstant  idProp      = hProp.getPropertyConstant();
        SingletonConstant idSingleton = idProp.getConstantPool().ensureSingletonConstConstant(idProp);
        ObjectHandle      hValue      = frame.getConstHandle(idSingleton);

        if (Op.isDeferred(hValue))
            {
            ObjectHandle[] ahTarget = new ObjectHandle[] {hValue};
            Frame.Continuation stepNext = frameCaller ->
                frame.assignValues(aiReturn, xBoolean.TRUE, ahTarget[0]);

            return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
            }

        return frame.assignValues(aiReturn, xBoolean.TRUE, hValue);
        }

    /**
     * Implementation for: {@code Implementation of(Target)}.
     */
    public int invokeOf(Frame frame, PropertyHandle hProp, ObjectHandle hArg, int iReturn)
        {
        PropertyConstant idProp   = hProp.getPropertyConstant();
        PropertyInfo     infoProp = hProp.getPropertyInfo();
        return hArg.getTemplate().createPropertyRef(frame, hArg, idProp, !infoProp.isVar(), iReturn);
        }

    /**
     * Implementation for: {@code Referent get(Target)}.
     */
    public int invokeGet(Frame frame, PropertyHandle hProp, ObjectHandle hArg, int iReturn)
        {
        ObjectHandle     hTarget = hArg;
        PropertyConstant idProp  = hProp.getPropertyConstant();
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
    }
