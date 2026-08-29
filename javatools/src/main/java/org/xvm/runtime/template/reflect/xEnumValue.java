package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xEnum;


/**
 * Native EnumValue implementation.
 */
public class xEnumValue
        extends xClass {
    public xEnumValue(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        markNativeProperty("enumeration");
        markNativeProperty("value");

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        switch (sPropName) {
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
    protected int getPropertyEnumeration(Frame frame, ClassHandle hClass, int iReturn) {
        TypeConstant   typeEnumValue  = getClassType(hClass);
        ClassConstant  idEnumValue    = (ClassConstant) typeEnumValue.getDefiningConstant();
        ClassStructure clzEnumValue   = idEnumValue.getComponent();
        ClassStructure clzEnumeration = clzEnumValue.getSuper();

        return frame.assignDeferredValue(iReturn,
                frame.getConstHandle(clzEnumeration.getIdentityConstant()));
    }

    /**
     * Implements property: BaseType value
     */
    protected int getPropertyValue(Frame frame, ClassHandle hClass, int iReturn) {
        TypeConstant   typeEnumValue  = getClassType(hClass);
        ClassConstant  idEnumValue    = (ClassConstant) typeEnumValue.getDefiningConstant();
        ClassStructure clzEnumValue   = idEnumValue.getComponent();
        ClassStructure clzEnumeration = clzEnumValue.getSuper();
        xEnum          template       = (xEnum) frame.f_context.f_container.
                                            getTemplate(clzEnumeration.getIdentityConstant());

        return frame.assignDeferredValue(iReturn,
                template.ensureEnumByName(frame, idEnumValue.getName()));
    }
}
