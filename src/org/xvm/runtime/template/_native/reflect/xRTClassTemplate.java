package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;


/**
 * Native ClassTemplate implementation.
 */
public class xRTClassTemplate
        extends xRTComponentTemplate
    {
    public static xRTClassTemplate INSTANCE;

    public xRTClassTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeProperty("classes");
        markNativeProperty("contribs");
        markNativeProperty("mixesInto");
        markNativeProperty("multimethods");
        markNativeProperty("properties");
        markNativeProperty("singleton");
        markNativeProperty("sourceInfo");
        markNativeProperty("template");
        markNativeProperty("type");
        markNativeProperty("typeParams");
        markNativeProperty("virtualChild");

        markNativeMethod("", null, null);

        super.initDeclared();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "classes":
                return getPropertyClasses(frame, hComponent, iReturn);

            case "contribs":
                return getPropertyContribs(frame, hComponent, iReturn);

            case "mixesInto":
                return getPropertyMixesInto(frame, hComponent, iReturn);

            case "multimethods":
                return getPropertyMultimethods(frame, hComponent, iReturn);

            case "properties":
                return getPropertyProperties(frame, hComponent, iReturn);

            case "singleton":
                return getPropertySingleton(frame, hComponent, iReturn);

            case "sourceInfo":
                return getPropertySourceInfo(frame, hComponent, iReturn);

            case "template":
                return getPropertyTemplate(frame, hComponent, iReturn);

            case "type":
                return getPropertyType(frame, hComponent, iReturn);

            case "typeParams":
                return getPropertyTypeParams(frame, hComponent, iReturn);

            case "virtualChild":
                return getPropertyVirtualChild(frame, hComponent, iReturn);
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
            case "ensureClass":
                return invokeEnsureClass(frame, hComponent, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ComponentTemplateHandle hComponent = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "deannotate":
                return invokeDeannotate(frame, hComponent, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: classes.get()
     */
    public int getPropertyClasses(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz    = (ClassStructure) hComponent.getComponent();
        GenericHandle  hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: contribs.get()
     */
    public int getPropertyContribs(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz    = (ClassStructure) hComponent.getComponent();
        GenericHandle  hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: mixesInto.get()
     */
    public int getPropertyMixesInto(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz       = (ClassStructure) hComponent.getComponent();
        GenericHandle  hTemplate = null; // TODO
        return frame.assignValue(iReturn, hTemplate);
        }

    /**
     * Implements property: multimethods.get()
     */
    public int getPropertyMultimethods(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz    = (ClassStructure) hComponent.getComponent();
        GenericHandle  hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertyProperties(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz    = (ClassStructure) hComponent.getComponent();
        GenericHandle  hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: singleton.get()
     */
    public int getPropertySingleton(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz        = (ClassStructure) hComponent.getComponent();
        boolean        fSingleton = clz.isSingleton();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fSingleton));
        }

    /**
     * Implements property: sourceInfo.get()
     */
    public int getPropertySourceInfo(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz   = (ClassStructure) hComponent.getComponent();
        GenericHandle  hInfo = null; // TODO
        return frame.assignValue(iReturn, hInfo);
        }

    /**
     * Implements property: template.get()
     */
    public int getPropertyTemplate(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        // the template of a Composition that is a ClassTemplate is itself:
        //   return this;
        return frame.assignValue(iReturn, hComponent);
        }

    /**
     * Implements property: type.get()
     */
    public int getPropertyType(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz  = (ClassStructure) hComponent.getComponent();
        TypeConstant   type = clz.getIdentityConstant().getType(); // REVIEW GG
        return frame.assignValue(iReturn, xRTTypeTemplate.makeHandle(type));
        }

    /**
     * Implements property: typeParams.get()
     */
    public int getPropertyTypeParams(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz    = (ClassStructure) hComponent.getComponent();
        GenericHandle  hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: virtualChild.get()
     */
    public int getPropertyVirtualChild(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz      = (ClassStructure) hComponent.getComponent();
        boolean        fVirtual = clz.isVirtualChild();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fVirtual));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Class<> ensureClass(Type... actualTypes)}.
     */
    public int invokeEnsureClass(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        ClassStructure clz = (ClassStructure) hComponent.getComponent();
        // TODO CP
        throw new UnsupportedOperationException();
        }

    /**
     * Implementation for: {@code conditional (Annotation, Composition) deannotate()}.
     */
    public int invokeDeannotate(Frame frame, ComponentTemplateHandle hComponent, int[] aiReturn)
        {
        // a Composition that is a ClassTemplate is not annotated
        return frame.assignValues(aiReturn, xBoolean.FALSE, null, null);
        }
    }
