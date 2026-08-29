package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.ModuleStructure;
import org.xvm.asm.MultiMethodStructure;
import org.xvm.asm.PackageStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Lazy;


/**
 * Native ComponentTemplate (abstract base class) implementation.
 */
public class xRTComponentTemplate
        extends ClassTemplate {

    public static xRTComponentTemplate getInstance(Frame frame) {
        return NativeTemplates.get(frame).componentTemplate();
    }

    public static xRTComponentTemplate getInstance(Container container) {
        return NativeTemplates.get(container).componentTemplate();
    }

    public xRTComponentTemplate(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void registerNativeTemplates() {
        if (NativeTemplates.get(this).isComponentTemplate(this)) {
            ClassStructure struct = f_container.getClassStructure("_native.reflect.RTMultiMethodTemplate");
            registerNativeTemplate(new xRTComponentTemplate(f_container, struct));
        }
    }

    @Override
    public void initNative() {
        markNativeProperty("access");
        markNativeProperty("doc");
        markNativeProperty("format");
        markNativeProperty("isAbstract");
        markNativeProperty("isStatic");
        markNativeProperty("name");
        markNativeProperty("parent");
        markNativeProperty("synthetic");

        markNativeMethod("children", null, null);

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        ComponentTemplateHandle<?> hComponent = (ComponentTemplateHandle<?>) hTarget;
        switch (sPropName) {
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
                             ObjectHandle[] ahArg, int iReturn) {
        ComponentTemplateHandle<?> hComponent = (ComponentTemplateHandle<?>) hTarget;
        switch (method.getName()) {
        case "children":
            return invokeChildren(frame, hComponent, iReturn);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        ComponentTemplateHandle<?> hTemplate1 = (ComponentTemplateHandle<?>) hValue1;
        ComponentTemplateHandle<?> hTemplate2 = (ComponentTemplateHandle<?>) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(frame, hTemplate1.getComponent().equals(hTemplate2.getComponent())));
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        ComponentTemplateHandle<?> hTemplate1 = (ComponentTemplateHandle<?>) hValue1;
        ComponentTemplateHandle<?> hTemplate2 = (ComponentTemplateHandle<?>) hValue2;

        return hTemplate1.getComponent() == hTemplate2.getComponent();
    }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: access.get()
     */
    protected int getPropertyAccess(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        Access    access    = component.getAccess();
        return frame.assignDeferredValue(iReturn, xRTType.ensureAccessHandle(frame, access));
    }

    /**
     * Implements property: doc.get()
     */
    protected int getPropertyDoc(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        String    sDoc      = component.getDocumentation();
        return frame.assignValue(iReturn, sDoc == null
                ? xNullable.makeHandle(frame)
                : xString.makeHandle(frame, sDoc));
    }

    /**
     * Implements property: format.get()
     */
    protected int getPropertyFormat(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        return frame.assignDeferredValue(iReturn, ensureFormatHandle(frame, component.getFormat()));
    }

    /**
     * Implements property: isAbstract.get()
     */
    protected int getPropertyIsAbstract(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, component.isAbstract()));
    }

    /**
     * Implements property: isStatic.get()
     */
    protected int getPropertyIsStatic(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, component.isStatic()));
    }

    /**
     * Implements property: name.get()
     */
    protected int getPropertyName(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        return frame.assignValue(iReturn, xString.makeHandle(frame, component.getSimpleName()));
    }

    /**
     * Implements property: parent.get()
     */
    protected int getPropertyParent(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component    parent  = hComponent.getComponent().getParent();
        ObjectHandle hParent = parent == null
                ? xNullable.makeHandle(frame)
                : makeComponentHandle(frame.container(), parent);
        return frame.assignValue(iReturn, hParent);
    }

    /**
     * Implements property: synthetic.get()
     */
    protected int getPropertySynthetic(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Component component = hComponent.getComponent();
        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, component.isSynthetic()));
    }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code ComponentTemplate[] children()}.
     */
    protected int invokeChildren(Frame frame, ComponentTemplateHandle<?> hComponent, int iReturn) {
        Container      container  = frame.container();
        Component      component  = hComponent.getComponent();
        int            cChildren  = component.getChildrenCount();
        ObjectHandle[] ahChildren = new ObjectHandle[cChildren];

        int i = 0;
        for (Component child : component.children()) {
            ahChildren[i++] = makeComponentHandle(container, child);
        }
        assert i == cChildren;

        // the only possible child type of MultiMethodTemplate is the MethodTemplate
        TypeComposition clzArray = component instanceof MultiMethodStructure
                ? xRTClassTemplate.ensureMethodTemplateArrayComposition(container)
                : container.resolveClass(ensureComponentArrayType(container));

        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahChildren));
    }


    // ----- Composition caching -------------------------------------------------------------------

    /**
     * @return the TypeConstant for an Array of ComponentTemplate
     */
    public static TypeConstant ensureComponentArrayType(Container container) {
        // The pool dependency is inside the container-owned template's Lazy field. Keeping the
        // container parameter here preserves the old cache behavior without using a process-global
        // TypeConstant from whichever container initialized last.
        xRTComponentTemplate template = NativeTemplates.get(container).componentTemplate();
        return template.f_typeComponentArray.get(template);
    }

    /**
     * @return the TypeComposition for an RTMultiMethodTemplate
     */
    public static TypeComposition getMultiMethodTemplateComposition(Container container) {
        xRTComponentTemplate template   = NativeTemplates.get(container).componentTemplate();
        ClassTemplate        templateRT = template.f_templateMultiMethod.get(template);

        ConstantPool pool         = container.getConstantPool();
        TypeConstant typeTemplate = pool.ensureEcstasyTypeConstant("reflect.MultiMethodTemplate");
        return templateRT.ensureClass(container, typeTemplate);
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Given a "Component.Format", obtain an Ecstasy "ComponentTemplate.Format" handle.
     *
     * @param frame   the current frame
     * @param format  a Component Format
     *
     * @return the initialized handle to the appropriate Ecstasy {@code ComponentTemplate.Format}
     *         enum value
     *
     * Note: this returns {@link ObjectHandle}, not {@code EnumHandle}, because natural enum lookup
     * can initially produce a construction struct. The public publication boundary must resolve
     * through the enum's SingletonConstant, and that resolution can be deferred.
     */
    protected static ObjectHandle ensureFormatHandle(Frame frame, Component.Format format) {
        xEnum enumForm = frame.container().
                getEnumTemplate("reflect.ComponentTemplate.Format");

        switch (format) {
        case INTERFACE:
            return enumForm.ensureEnumByName(frame, "Interface");
        case CLASS:
            return enumForm.ensureEnumByName(frame, "Class");
        case CONST:
            return enumForm.ensureEnumByName(frame, "Const");
        case ENUM:
            return enumForm.ensureEnumByName(frame, "Enum");
        case ENUMVALUE:
            return enumForm.ensureEnumByName(frame, "EnumValue");
        case ANNOTATION:
            return enumForm.ensureEnumByName(frame, "Annotation");
        case MIXIN:
            return enumForm.ensureEnumByName(frame, "Mixin");
        case SERVICE:
            return enumForm.ensureEnumByName(frame, "Service");
        case PACKAGE:
            return enumForm.ensureEnumByName(frame, "Package");
        case MODULE:
            return enumForm.ensureEnumByName(frame, "Module");
        case TYPEDEF:
            return enumForm.ensureEnumByName(frame, "TypeDef");
        case PROPERTY:
            return enumForm.ensureEnumByName(frame, "Property");
        case METHOD:
            return enumForm.ensureEnumByName(frame, "Method");
        case RSVD_D:
            return enumForm.ensureEnumByName(frame, "Reserved_D");
        case MULTIMETHOD:
            return enumForm.ensureEnumByName(frame, "MultiMethod");
        case FILE:
            return enumForm.ensureEnumByName(frame, "File");

        default:
            throw new IllegalStateException("unsupported format: " + format);
        }
    }

    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Given a Component structure, create ComponentTemplateHandle<?> for it.
     */
    public static ComponentTemplateHandle<?> makeComponentHandle(Container container, Component component) {
        switch (component.getFormat()) {
        case FILE:
            return xRTFileTemplate.makeHandle(container, (FileStructure) component);

        case MODULE:
            return xRTModuleTemplate.makeHandle(container, (ModuleStructure) component);

        case PACKAGE:
            return xRTPackageTemplate.makeHandle(container, (PackageStructure) component);

        case CLASS:
        case CONST:
        case INTERFACE:
        case ENUM:
        case ENUMVALUE:
        case ANNOTATION:
        case MIXIN:
        case SERVICE:
            return xRTClassTemplate.makeHandle(container, (ClassStructure) component);

        case MULTIMETHOD:
            return new ComponentTemplateHandle<>(getMultiMethodTemplateComposition(container), component);

        case METHOD:
            return xRTMethodTemplate.makeHandle(container, (MethodStructure) component);

        case PROPERTY:
            return xRTPropertyTemplate.makePropertyHandle(container, (PropertyStructure) component);

        default:
            throw new UnsupportedOperationException("unsupported format " + component.getFormat());
        }
    }

    /**
     * Inner class: ComponentTemplateHandle<?>. This is a handle to a native Component.
     */
    public static class ComponentTemplateHandle<C extends Component>
            extends GenericHandle {
        protected ComponentTemplateHandle(TypeComposition clz, C component) {
            super(clz);

            f_struct   = component;
            m_fMutable = false;
        }

        /** @return the component this handle wraps, at the type the factory knew it to be */
        public C getComponent() {
            return f_struct;
        }

        @Override
        public String toString() {
            return super.toString() + f_struct.getName();
        }

        private final C f_struct;
    }

    // ----- fields --------------------------------------------------------------------------------

    private final Lazy.Bound<xRTComponentTemplate, TypeConstant> f_typeComponentArray =
            Lazy.ofBound(owner -> owner.pool().ensureArrayType(
                    owner.pool().ensureEcstasyTypeConstant("reflect.ComponentTemplate")));

    private final Lazy.Bound<xRTComponentTemplate, ClassTemplate> f_templateMultiMethod =
            Lazy.ofBound(owner -> owner.container().getTemplate("_native.reflect.RTMultiMethodTemplate"));
}
