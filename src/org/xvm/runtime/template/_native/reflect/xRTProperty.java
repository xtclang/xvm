package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyClassTypeConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.template.xString;


/**
 * Native Property implementation.
 */
public class xRTProperty
        extends ClassTemplate
    {
    public static xRTProperty INSTANCE;

    public xRTProperty(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        if (constant instanceof PropertyConstant)
            {
            ConstantPool pool = frame.poolContext();

            PropertyConstant propTarget = (PropertyConstant) constant;

            // TODO
//            TypeConstant typeData = propTarget.getParamTypesArray()[0].
//                resolveGenerics(pool, frame.getGenericsResolver());
//            frame.pushStack(typeData.getTypeHandle());
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
                return getAbstractProperty(frame, hThis, iReturn);
            case "atomic":
                return getAtomicProperty(frame, hThis, iReturn);
            case "formal":
                return getFormalProperty(frame, hThis, iReturn);
            case "hasField":
                return getHasFieldProperty(frame, hThis, iReturn);
            case "hasUnreachableSetter":
                return getHasUnreachableSetterProperty(frame, hThis, iReturn);
            case "injected":
                return getInjectedProperty(frame, hThis, iReturn);
            case "lazy":
                return getLazyProperty(frame, hThis, iReturn);
            case "name":
                return getNameProperty(frame, hThis, iReturn);
            case "readOnly":
                return getReadOnlyProperty(frame, hThis, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- PropertyHandle support ----------------------------------------------------------------

    /**
     * Obtain a {@link PropertyHandle} for the specified property.
     *
     * @param clzProp  the {@link ClassComposition} to obtain a {@link PropertyHandle} for
     *
     * @return the resulting {@link PropertyHandle}
     */
    public static PropertyHandle makeHandle(ClassComposition clzProp)
        {
        return new PropertyHandle(clzProp);
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

        public TypeConstant getDataType()
            {
            return getType().getParamType(0);
            }

        public TypeConstant getTargetType()
            {
            return getDataType().getParamType(0);
            }

        public TypeConstant getReferentType()
            {
            return getDataType().getParamType(1);
            }

        public TypeConstant getImplementationType()
            {
            return getDataType().getParamType(2);
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
    public int getAbstractProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: atomic.get()
     */
    public int getAtomicProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: formal.get()
     */
    public int getFormalProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: hasField.get()
     */
    public int getHasFieldProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: hasUnreachableSetter.get()
     */
    public int getHasUnreachableSetterProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: injected.get()
     */
    public int getInjectedProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: lazy.get()
     */
    public int getLazyProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }

    /**
     * Implements property: name.get()
     */
    public int getNameProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, xString.makeHandle(hProp.getPropertyConstant().getName()));
        }

    /**
     * Implements property: readOnly.get()
     */
    public int getReadOnlyProperty(Frame frame, PropertyHandle hProp, int iReturn)
        {
        return frame.assignValue(iReturn, null); // TODO
        }
    }
