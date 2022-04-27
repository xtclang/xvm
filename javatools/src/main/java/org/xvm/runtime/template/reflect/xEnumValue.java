package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xEnum;


/**
 * Native EnumValue implementation.
 */
public class xEnumValue
        extends xClass
    {
    public static xEnumValue INSTANCE;

    public xEnumValue(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("enumeration");
        markNativeProperty("value");

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "enumeration":
                return getPropertyEnumeration(frame, (ClassHandle) hTarget, iReturn);

            case "value":
                return getPropertyValue(frame, (ClassHandle) hTarget, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    /**
     * Implements property: Enumeration<BaseType> enumeration
     */
    protected int getPropertyEnumeration(Frame frame, ClassHandle hClass, int iReturn)
        {
        TypeConstant   typeEnumValue  = getClassType(hClass);
        ClassConstant  idEnumValue    = (ClassConstant) typeEnumValue.getDefiningConstant();
        ClassStructure clzEnumValue   = (ClassStructure) idEnumValue.getComponent();
        ClassStructure clzEnumeration = clzEnumValue.getSuper();

        ObjectHandle hEnumeration = frame.getConstHandle(clzEnumeration.getIdentityConstant());

        return Op.isDeferred(hEnumeration)
            ? hEnumeration.proceed(frame, frameCaller ->
                frameCaller.assignValue(iReturn, frameCaller.popStack()))
            : frame.assignValue(iReturn, hEnumeration);
        }

    /**
     * Implements property: BaseType value
     */
    protected int getPropertyValue(Frame frame, ClassHandle hClass, int iReturn)
        {
        TypeConstant   typeEnumValue  = getClassType(hClass);
        ClassConstant  idEnumValue    = (ClassConstant) typeEnumValue.getDefiningConstant();
        ClassStructure clzEnumValue   = (ClassStructure) idEnumValue.getComponent();
        ClassStructure clzEnumeration = clzEnumValue.getSuper();
        xEnum          template       = (xEnum) frame.f_context.f_container.
                                            getTemplate(clzEnumeration.getIdentityConstant());

        ObjectHandle hValue = Utils.ensureInitializedEnum(frame,
                template.getEnumByName(idEnumValue.getName()));

        return Op.isDeferred(hValue)
            ? hValue.proceed(frame, frameCaller ->
                frameCaller.assignValue(iReturn, frameCaller.popStack()))
            : frame.assignValue(iReturn, hValue);
        }
    }