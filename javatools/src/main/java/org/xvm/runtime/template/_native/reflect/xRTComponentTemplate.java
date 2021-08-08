package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;

import org.xvm.runtime.template.text.xString;


/**
 * Native ComponentTemplate (abstract base class) implementation.
 */
public class xRTComponentTemplate
        extends ClassTemplate
    {
    public static xRTComponentTemplate INSTANCE;

    public xRTComponentTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        if (this == INSTANCE)
            {
            ClassStructure struct = (ClassStructure) f_templates.getComponent(
                "_native.reflect.RTMultiMethodTemplate");
            registerNativeTemplate(new xRTComponentTemplate(f_templates, struct, false));
            }
        }

    @Override
    public void initNative()
        {
        markNativeProperty("access");
        markNativeProperty("doc");
        markNativeProperty("format");
        markNativeProperty("isAbstract");
        markNativeProperty("isStatic");
        markNativeProperty("name");
        markNativeProperty("parent");
        markNativeProperty("synthetic");

        markNativeMethod("children", null, null);
        markNativeMethod("toString", VOID, STRING);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "access":
                return getPropertyAccess(frame, hComponent, iReturn);

            case "doc":
                return getPropertyDoc(frame, hComponent, iReturn);

            case "format":
                return getPropertyFormat(frame, hComponent, iReturn);

            case "isAbstract":
                return getPropertyIsAbstract(frame, hComponent, iReturn);

            case "isStatic":
                return getPropertyIsStatic(frame, hComponent, iReturn);

            case "name":
                return getPropertyName(frame, hComponent, iReturn);

            case "parent":
                return getPropertyParent(frame, hComponent, iReturn);

            case "synthetic":
                return getPropertySynthetic(frame, hComponent, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "children":
                return invokeChildren(frame, hComponent, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ComponentTemplateHandle hTemplate1 = (ComponentTemplateHandle) hValue1;
        ComponentTemplateHandle hTemplate2 = (ComponentTemplateHandle) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hTemplate1.getComponent().equals(hTemplate2.getComponent())));
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: access.get()
     */
    public int getPropertyAccess(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component = hComponent.getComponent();
        Access    access    = component.getAccess();
        return Utils.assignInitializedEnum(frame, xRTType.makeAccessHandle(frame, access), iReturn);
        }

    /**
     * Implements property: doc.get()
     */
    public int getPropertyDoc(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component = hComponent.getComponent();
        String    sDoc      = component.getDocumentation();
        return frame.assignValue(iReturn, sDoc == null
                ? xNullable.NULL
                : xString.makeHandle(sDoc));
        }

    /**
     * Implements property: format.get()
     */
    public int getPropertyFormat(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component  component = hComponent.getComponent();
        EnumHandle hFormat   = makeFormatHandle(frame, component.getFormat());
        return Utils.assignInitializedEnum(frame, hFormat, iReturn);
        }

    /**
     * Implements property: isAbstract.get()
     */
    public int getPropertyIsAbstract(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component = hComponent.getComponent();
        boolean   fAbstract = component.isAbstract();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fAbstract));
        }

    /**
     * Implements property: isStatic.get()
     */
    public int getPropertyIsStatic(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component = hComponent.getComponent();
        boolean   fStatic   = component.isStatic();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fStatic));
        }

    /**
     * Implements property: name.get()
     */
    public int getPropertyName(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component = hComponent.getComponent();
        String    sName     = component.getSimpleName();
        return frame.assignValue(iReturn, xString.makeHandle(sName));
        }

    /**
     * Implements property: parent.get()
     */
    public int getPropertyParent(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component    parent  = hComponent.getComponent().getParent();
        ObjectHandle hParent = parent == null ? xNullable.NULL : makeComponentHandle(parent);
        return frame.assignValue(iReturn, hParent);
        }

    /**
     * Implements property: synthetic.get()
     */
    public int getPropertySynthetic(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component  = hComponent.getComponent();
        boolean   fSynthetic = component.isSynthetic();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fSynthetic));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code ComponentTemplate[] children()}.
     */
    public int invokeChildren(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component      component  = hComponent.getComponent();
        int            cChildren  = component.getChildrenCount();
        ObjectHandle[] ahChildren = new ObjectHandle[cChildren];

        int i = 0;
        for (Component child : component.children())
            {
            ahChildren[i++] = makeComponentHandle(child);
            }
        assert i == cChildren;

        ArrayHandle hArray = xArray.createImmutableArray(ensureComponentArrayType(), ahChildren);

        return frame.assignValue(iReturn, hArray);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        Component component = ((ComponentTemplateHandle) hTarget).getComponent();
        String    sResult   = component.getIdentityConstant().getValueString();
        return frame.assignValue(iReturn, xString.makeHandle(sResult));
        }


    // ----- Composition caching -------------------------------------------------------------------

    /**
     * @return the TypeComposition for an Array of ComponentTemplate
     */
    public static TypeComposition ensureComponentArrayType()
        {
        TypeComposition clz = COMPONENT_ARRAY_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeArray = pool.ensureArrayType(
                                            pool.ensureEcstasyTypeConstant("reflect.ComponentTemplate"));
            COMPONENT_ARRAY_COMP = clz = INSTANCE.f_templates.resolveClass(typeTypeArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the TypeComposition for an RTMultiMethodTemplate
     */
    public static TypeComposition ensureMultiMethodTemplateComposition()
        {
        TypeComposition clz = MULTI_METHOD_TEMPLATE_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            ClassTemplate templateRT   = INSTANCE.f_templates.getTemplate("_native.reflect.RTMultiMethodTemplate");
            TypeConstant  typeTemplate = pool.ensureEcstasyTypeConstant("reflect.MultiMethodTemplate");
            MULTI_METHOD_TEMPLATE_COMP = clz = templateRT.ensureClass(typeTemplate);
            assert clz != null;
            }
        return clz;
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given a "Component.Format", obtain an Ecstasy "ComponentTemplate.Format" handle.
     *
     * @param frame   the current frame
     * @param format  a Component Format
     *
     * @return the handle to the appropriate Ecstasy {@code ComponentTemplate.Format} enum value
     */
    protected static EnumHandle makeFormatHandle(Frame frame, Component.Format format)
        {
        xEnum enumForm = (xEnum) INSTANCE.f_templates.getTemplate("reflect.ComponentTemplate.Format");

        switch (format)
            {
            case INTERFACE:
                return enumForm.getEnumByName("Interface");
            case CLASS:
                return enumForm.getEnumByName("Class");
            case CONST:
                return enumForm.getEnumByName("Const");
            case ENUM:
                return enumForm.getEnumByName("Enum");
            case ENUMVALUE:
                return enumForm.getEnumByName("EnumValue");
            case MIXIN:
                return enumForm.getEnumByName("Mixin");
            case SERVICE:
                return enumForm.getEnumByName("Service");
            case PACKAGE:
                return enumForm.getEnumByName("Package");
            case MODULE:
                return enumForm.getEnumByName("Module");
            case TYPEDEF:
                return enumForm.getEnumByName("TypeDef");
            case PROPERTY:
                return enumForm.getEnumByName("Property");
            case METHOD:
                return enumForm.getEnumByName("Method");
            case RSVD_C:
                return enumForm.getEnumByName("Reserved_C");
            case RSVD_D:
                return enumForm.getEnumByName("Reserved_D");
            case MULTIMETHOD:
                return enumForm.getEnumByName("MultiMethod");
            case FILE:
                return enumForm.getEnumByName("File");

            default:
                throw new IllegalStateException("unsupported format: " + format);
            }
        }

    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Given a Component structure, create ComponentTemplateHandle for it.
     */
    public static ComponentTemplateHandle makeComponentHandle(Component component)
        {
        switch (component.getFormat())
            {
            case FILE:
                return xRTFileTemplate.makeHandle((FileStructure) component);

            case MODULE:
                return xRTModuleTemplate.makeHandle((ModuleStructure) component);

            case PACKAGE:
                return xRTPackageTemplate.makeHandle((PackageStructure) component);

            case CLASS:
            case CONST:
            case INTERFACE:
            case ENUM:
            case ENUMVALUE:
            case MIXIN:
            case SERVICE:
                return xRTClassTemplate.makeHandle((ClassStructure) component);

            case MULTIMETHOD:
                return new ComponentTemplateHandle(ensureMultiMethodTemplateComposition(), component);

            case METHOD:
                return xRTMethodTemplate.makeHandle((MethodStructure) component);

            case PROPERTY:
                return xRTPropertyClassTemplate.makeHandle((PropertyStructure) component);

            default:
                throw new UnsupportedOperationException("unsupported format " + component.getFormat());
            }
        }

    /**
     * Inner class: ComponentTemplateHandle. This is a handle to a native Component.
     */
    public static class ComponentTemplateHandle
            extends GenericHandle
        {
        protected ComponentTemplateHandle(TypeComposition clz, Component component)
            {
            super(clz);

            f_struct   = component;
            m_fMutable = false;
            }

        public Component getComponent()
            {
            return f_struct;
            }

        @Override
        public String toString()
            {
            return super.toString() + f_struct.getName();
            }

        private final Component f_struct;
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition COMPONENT_ARRAY_COMP;
    private static TypeComposition MULTI_METHOD_TEMPLATE_COMP;
    }
