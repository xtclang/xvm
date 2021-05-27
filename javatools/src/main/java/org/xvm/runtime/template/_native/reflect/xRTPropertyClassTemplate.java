package org.xvm.runtime.template._native.reflect;


import java.util.ArrayList;
import java.util.List;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
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

    public xRTPropertyClassTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

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
            TypeConstant typeClassTemplate = pool().ensureEcstasyTypeConstant("reflect.ClassTemplate");

            PROPERTY_CLASS_TEMPLATE_COMP = ensureClass(getCanonicalType(), typeClassTemplate);

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

        List<ComponentTemplateHandle> listTemplates = new ArrayList<>();
        for (Component child : prop.children())
            {
            switch (child.getFormat())
                {
                case CLASS:
                case CONST:
                    listTemplates.add(xRTClassTemplate.makeHandle((ClassStructure) child));
                    break;
                }
            }

        ObjectHandle hArray = xArray.createImmutableArray(
                xRTClassTemplate.ensureClassTemplateArrayComposition(),
                listTemplates.toArray(new ComponentTemplateHandle[0]));
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
            ConstantPool pool        = frameCaller.poolContext();
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
            xEnum           enumAction          = xRTClassTemplate.ACTION;

            ObjectHandle[] ahVar = new ObjectHandle[methodCreateContrib.getMaxVars()];
            ahVar[0] = Utils.ensureInitializedEnum(frameCaller, enumAction.getEnumByName(sAction));
            ahVar[1] = typeContrib.ensureTypeHandle(pool);
            ahVar[2] = hDelegatee;
            ahVar[3] = haNames;
            ahVar[3] = haTypes;

            return frameCaller.call1(methodCreateContrib, null, ahVar, Op.A_STACK);
            };

        return xArray.createAndFill(frame, xRTClassTemplate.ensureContribArrayComposition(),
                listContrib.size(), supplier, iReturn);
        }

    /**
     * Implements property: multimethods.get()
     */
    public int getPropertyMultimethods(Frame frame, ComponentTemplateHandle hComponent, int iReturn)
        {
        PropertyStructure prop   = (PropertyStructure) hComponent.getComponent();
        GenericHandle     hArray = null; // TODO
        return frame.assignValue(iReturn, hArray);
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

        ComponentTemplateHandle[] ahProp = listProps.toArray(new ComponentTemplateHandle[0]);
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
        return frame.assignValue(iReturn, hInfo);
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
        TypeConstant type = prop.getType();
        return frame.assignValue(iReturn, xRTTypeTemplate.makeHandle(type));
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
