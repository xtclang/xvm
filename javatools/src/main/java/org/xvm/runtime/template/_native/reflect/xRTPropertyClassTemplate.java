package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;


/**
 * Native RTPropertyClassTemplate implementation.
 */
public class xRTPropertyClassTemplate
        extends xRTComponentTemplate
    {
    public static xRTPropertyClassTemplate INSTANCE;

    public xRTPropertyClassTemplate(Container container, ClassStructure structure, boolean fInstance)
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
        if (this == INSTANCE)
            {
            TypeConstant typeMask = pool().ensureEcstasyTypeConstant("reflect.ClassTemplate");

            PROPERTY_CLASS_TEMPLATE_COMP = ensureClass(f_container, getCanonicalType(), typeMask);

            markNativeProperty("classes");
            markNativeProperty("contribs");
            markNativeProperty("multimethods");
            markNativeProperty("properties");
            markNativeProperty("singleton");
            markNativeProperty("sourceInfo");
            markNativeProperty("type");

            markNativeMethod("deannotate", null, null);
            markNativeMethod("fromProperty", null, null);

            // this native implementation explicitly incorporates the native implementation of
            // RTComponentTemplate
            super.initNative();
            }
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

            case "multimethods":
                return getPropertyMultimethods(frame, hComponent, iReturn);

            case "properties":
                return getPropertyProperties(frame, hComponent, iReturn);

            case "singleton":
                return getPropertySingleton(frame, hComponent, iReturn);

            case "sourceInfo":
                return getPropertySourceInfo(frame, hComponent, iReturn);

            case "type":
                return getPropertyType(frame, hComponent, iReturn);
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

            case "fromProperty":
                return invokeFromProperty(frame, hComponent, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: classes.get()
     */
    public int getPropertyClasses(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hComponent.getComponent();
        if (!prop.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        Container                     container     = frame.f_context.f_container;
        List<ComponentTemplateHandle> listTemplates = new ArrayList<>();
        for (Component child : prop.children())
            {
            switch (child.getFormat())
                {
                case CLASS:
                case CONST:
                    listTemplates.add(xRTClassTemplate.makeHandle(container, (ClassStructure) child));
                    break;
                }
            }

        ObjectHandle hArray = xArray.createImmutableArray(
                xRTClassTemplate.ensureClassTemplateArrayComposition(container),
                listTemplates.toArray(xRTClassTemplate.NO_TEMPLATES));
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: contribs.get()
     */
    public int getPropertyContribs(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hComponent.getComponent();
        if (!prop.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        List<Contribution>  listContrib = prop.getContributionsAsList();
        Utils.ValueSupplier supplier    = (frameCaller, index) ->
            {
            Contribution contrib     = listContrib.get(index);
            TypeConstant typeContrib = contrib.getTypeConstant();
            ObjectHandle hDelegatee  = xNullable.NULL; // TODO
            ObjectHandle haNames     = xNullable.NULL;
            ObjectHandle haTypes     = xNullable.NULL;

            String sAction;
            switch (contrib.getComposition())
                {
                case Annotation:
                    sAction = "AnnotatedBy";
                    break;
                case Extends:
                    sAction = "Extends";
                    break;
                case Implements:
                    sAction = "Implements";
                    break;

                default:
                    throw new IllegalStateException();
                }

            MethodStructure methodCreateContrib = xRTClassTemplate.CREATE_CONTRIB_METHOD;
            xEnum           enumAction          = xRTClassTemplate.ACTION_TEMPLATE;

            ObjectHandle[] ahVar = new ObjectHandle[methodCreateContrib.getMaxVars()];
            ahVar[0] = Utils.ensureInitializedEnum(frameCaller, enumAction.getEnumByName(sAction));
            ahVar[1] = typeContrib.ensureTypeHandle(frameCaller.f_context.f_container);
            ahVar[2] = hDelegatee;
            ahVar[3] = haNames;
            ahVar[4] = haTypes;

            return frameCaller.call1(methodCreateContrib, null, ahVar, Op.A_STACK);
            };

        return xArray.createAndFill(frame,
                xRTClassTemplate.ensureContribArrayComposition(frame.f_context.f_container),
                listContrib.size(), supplier, iReturn);
        }

    /**
     * Implements property: multimethods.get()
     */
    public int getPropertyMultimethods(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop   = (PropertyStructure) hComponent.getComponent();
        GenericHandle     hArray = null; // TODO
        return frame.raiseException("Not implemented");
        }

    /**
     * Implements property: properties.get()
     */
    public int getPropertyProperties(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hComponent.getComponent();
        if (!prop.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        List<ComponentTemplateHandle> listProps = new ArrayList<>();
        for (Component child : prop.children())
            {
            if (child instanceof PropertyStructure)
                {
                listProps.add(xRTPropertyTemplate.makePropertyHandle((PropertyStructure) child));
                }
            }

        ComponentTemplateHandle[] ahProp = listProps.toArray(xRTClassTemplate.NO_TEMPLATES);
        ObjectHandle hArray = xArray.createImmutableArray(
                xRTPropertyTemplate.ensureArrayComposition(), ahProp);
        return frame.assignValue(iReturn, hArray);
        }

    /**
     * Implements property: singleton.get()
     */
    public int getPropertySingleton(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop       = (PropertyStructure) hComponent.getComponent();
        boolean           fSingleton = prop.isStatic();
        return frame.assignValue(iReturn, xBoolean.makeHandle(fSingleton));
        }

    /**
     * Implements property: sourceInfo.get()
     */
    public int getPropertySourceInfo(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop  = (PropertyStructure) hComponent.getComponent();
        GenericHandle     hInfo = null; // TODO
        return frame.raiseException("Not implemented");
        }

    /**
     * Implements property: type.get()
     */
    public int getPropertyType(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hComponent.getComponent();
        if (!prop.getFileStructure().isLinked())
            {
            return frame.raiseException(xException.illegalState(frame, "FileTemplate is not resolved"));
            }

        return frame.assignValue(iReturn,
            xRTTypeTemplate.makeHandle(frame.f_context.f_container, prop.getType()));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code conditional (Annotation, Composition) deannotate()}.
     */
    protected int invokeDeannotate(Frame frame, ComponentTemplateHandle hComponent, int[] aiReturn)
        {
        // a Composition that is a ClassTemplate is not annotated
        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    /**
     * Implementation for: {@code conditional PropertyTemplate fromProperty()}.
     */
    protected int invokeFromProperty(Frame frame, ComponentTemplateHandle hComponent, int[] aiReturn)
        {
        PropertyStructure prop = (PropertyStructure) hComponent.getComponent();
        return frame.assignValues(aiReturn,
            xBoolean.TRUE, xRTPropertyTemplate.makePropertyHandle(prop));
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Obtain a {@link ComponentTemplateHandle} for the specified {@link PropertyStructure}.
     *
     * @param prop  the {@link PropertyStructure} to obtain a {@link ComponentTemplateHandle} for
     *
     * @return the resulting {@link ComponentTemplateHandle}
     */
    public static ComponentTemplateHandle makeHandle(PropertyStructure prop)
        {
        // note: no need to initialize the struct because there are no natural fields
        return new ComponentTemplateHandle(PROPERTY_CLASS_TEMPLATE_COMP, prop);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition PROPERTY_CLASS_TEMPLATE_COMP;
    }