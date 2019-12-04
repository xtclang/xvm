package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xNullable;
import org.xvm.runtime.template.xString;

import org.xvm.runtime.template.collections.xArray;


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
    public void initDeclared()
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
        markNativeMethod("toString", null, null);

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
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "children":
                return invokeIsA(frame, hComponent, iReturn);

            case "toString":
                return invokeToString(frame, hComponent, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }


    // ----- ComponentTemplateHandle support -------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified component.
     *
     * @param component  the {@link Component} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(Component component)
        {
        ClassComposition clz = INSTANCE.ensureClass(INSTANCE.getCanonicalType(),
                INSTANCE.pool().ensureEcstasyTypeConstant("reflect.ComponentTemplate"));
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(clz, component);
        }

    /**
     * Inner class: ComponentTemplateHandle. This is a handle to a native Component.
     */
    public static class ComponentTemplateHandle
            extends GenericHandle
        {
        protected ComponentTemplateHandle(ClassComposition clz, Component component)
            {
            super(clz);
            m_struct = component;
            }

        public Component getComponent()
            {
            return m_struct;
            }

        private Component m_struct;
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: access.get()
     */
    public int getPropertyAccess(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component = hComponent.getComponent();
        Access    access    = component.getAccess();
        ObjectHandle hEnum = Utils.ensureInitializedEnum(frame,
                makeAccessHandle(frame, access));

        if (Op.isDeferred(hEnum))
            {
            ObjectHandle[] ahValue = new ObjectHandle[] {hEnum};
            Frame.Continuation stepNext = frameCaller ->
                    frameCaller.assignValue(iReturn, ahValue[0]);

            return new Utils.GetArguments(ahValue, stepNext).doNext(frame);
            }

        return frame.assignValue(iReturn, hEnum);
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
        EnumHandle hFormat   = null; // TODO
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
        String    sName     = component.getName();
        return frame.assignValue(iReturn, xString.makeHandle(sName));
        }

    /**
     * Implements property: parent.get()
     */
    public int getPropertyParent(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component     component = hComponent.getComponent();
        GenericHandle hParent   = null; // TODO
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
     * Implementation for: {@code Iterator<ComponentTemplate> children()}.
     */
    public int invokeIsA(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component  = hComponent.getComponent();
        // TODO CP
        throw new UnsupportedOperationException();
        }

    /**
     * Implementation for: {@code String toString()}.
     */
    public int invokeToString(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        Component component  = hComponent.getComponent();
        String    sResult    = component.toString();
        return frame.assignValue(iReturn, xString.makeHandle(sResult));
        }


    // ----- Template caching -----------------------------------------------------------------------

    /**
     * @return the ClassTemplate for an Array of TypeTemplate
     */
    public xArray ensureTypeTemplateArrayTemplate()
        {
        xArray template = TYPETEMPLATE_ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeTypeTemplateArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeType()); // TODO
            TYPETEMPLATE_ARRAY_TEMPLATE = template = ((xArray) f_templates.getTemplate(typeTypeTemplateArray));
            assert template != null;
            }
        return template;
        }

    private static xArray TYPETEMPLATE_ARRAY_TEMPLATE;


    // ----- ClassComposition caching and helpers --------------------------------------------------

    /**
     * @return the ClassComposition for an Array of TypeTemplate
     */
    public ClassComposition ensureClassComposition()
        {
        ClassComposition clz = TYPETEMPLATE;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant type = pool.ensureEcstasyTypeConstant("reflect.TypeTemplate");
            TYPETEMPLATE = clz = f_templates.resolveClass(type);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for an Array of TypeTemplate
     */
    public ClassComposition ensureArrayClassComposition()
        {
        ClassComposition clz = TYPETEMPLATE_ARRAY;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant type = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                    pool.ensureEcstasyTypeConstant("reflect.TypeTemplate"));
            TYPETEMPLATE_ARRAY = clz = f_templates.resolveClass(type);
            assert clz != null;
            }
        return clz;
        }

    private static ClassComposition TYPETEMPLATE;
    private static ClassComposition TYPETEMPLATE_ARRAY;


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
        xEnum enumAccess = (xEnum) f_templates.getTemplate("reflect.Access");
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
        xEnum enumForm = (xEnum) f_templates.getTemplate("reflect.TypeTemplate.Form");

        switch (type.getFormat())
            {
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

            case ParameterizedType:
                return enumForm.getEnumByName("Parameterized");

            case TurtleType:
                return enumForm.getEnumByName("Sequence");

            case VirtualChildType:
                return enumForm.getEnumByName("Child");

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
    }
