package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.collections.xArray;


/**
 * Native PropertyTemplate implementation.
 */
public class xRTPropertyTemplate
        extends xRTComponentTemplate
    {
    public static xRTPropertyTemplate INSTANCE;

    public xRTPropertyTemplate(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("type");
        markNativeProperty("isConstant");
        markNativeProperty("annotations");

        markNativeMethod("hasInitialValue", null, null);
        markNativeMethod("hasInitializer", null, null);

        super.initNative();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hProp = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "type":
                return getPropertyType(frame, hProp, iReturn);

            case "isConstant":
                return getPropertyIsConstant(frame, hProp, iReturn);

            case "annotations":
                return getPropertyAnnotations(frame, hProp, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ComponentTemplateHandle hProp = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "hasInitialValue":
                return invokeInitialValue(frame, hProp, aiReturn);

            case "hasInitializer":
                return frame.raiseException("Not implemented: hasInitializer()");
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: {@code type.get()}.
     */
    public int getPropertyType(Frame frame, ComponentTemplateHandle hProp, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hProp.getComponent();

        return frame.assignValue(iReturn,
                xRTTypeTemplate.makeHandle(frame.f_context.f_container, prop.getType()));
        }

    /**
     * Implements property: {@code isConstant.get()}.
     */
    public int getPropertyIsConstant(Frame frame, ComponentTemplateHandle hProp, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hProp.getComponent();

        return frame.assignValue(iReturn, xBoolean.makeHandle(prop.isConstant()));
        }

    /**
     * Implements property: {@code immutable AnnotationTemplate[] annotations.get()}.
     */
    public int getPropertyAnnotations(Frame frame, ComponentTemplateHandle hProp, int iReturn)
        {
        Container         container = frame.f_context.f_container;
        PropertyStructure prop      = (PropertyStructure) hProp.getComponent();
        TypeComposition   clzArray  = xRTClassTemplate.ensureAnnotationTemplateArrayComposition(container);

        Annotation[] aAnnotation = prop.getPropertyAnnotations();
        int          cAnnos      = aAnnotation.length;

        if (cAnnos == 0)
            {
            return frame.assignValue(iReturn,
                    xArray.createEmptyArray(clzArray, 0, xArray.Mutability.Constant));
            }

        Utils.ValueSupplier supplier = (frameCaller, index) ->
            {
            Annotation       anno      = aAnnotation[index];
            IdentityConstant idClass   = (IdentityConstant) anno.getAnnotationClass();
            Constant[]       aconstArg = anno.getParams();
            int              cArgs     = aconstArg.length;

            ComponentTemplateHandle hClass =
                    xRTComponentTemplate.makeComponentHandle(container, idClass.getComponent());

            ObjectHandle[] ahArg;
            if (cArgs == 0)
                {
                ahArg = Utils.OBJECTS_NONE;
                }
            else
                {
                ahArg = new ObjectHandle[cArgs];
                for (int i = 0; i < cArgs; i++)
                    {
                    ahArg[i] = xRTType.makeArgumentHandle(frame, aconstArg[i]);
                    }
                }

            if (Op.anyDeferred(ahArg))
                {
                Frame.Continuation stepNext = frameCaller2 ->
                    Utils.constructAnnotationTemplate(frameCaller2, hClass, ahArg, Op.A_STACK);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frameCaller);
                }

            return Utils.constructAnnotationTemplate(frameCaller, hClass, ahArg, Op.A_STACK);
            };

        return xArray.createAndFill(frame, clzArray, cAnnos, supplier, iReturn);
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implements method: {@code conditional Const hasInitialValue()}.
     */
    public int invokeInitialValue(Frame frame, ComponentTemplateHandle hProp, int[] aiReturn)
        {
        PropertyStructure prop = (PropertyStructure) hProp.getComponent();

        if (prop.hasInitialValue())
            {
            ObjectHandle hInitial = frame.getConstHandle(prop.getInitialValue());

            return Op.isDeferred(hInitial)
                ? hInitial.proceed(frame, frameCaller ->
                    frameCaller.assignValues(aiReturn, xBoolean.TRUE, frameCaller.popStack()))
                : frame.assignValues(aiReturn, xBoolean.TRUE, hInitial);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }


    // ----- Composition caching -------------------------------------------------------------------

    /**
     * @return the TypeComposition for an RTPropertyTemplate
     */
    public static TypeComposition ensurePropertyTemplateComposition()
        {
        TypeComposition clz = PROPERTY_TEMPLATE_COMP;
        if (clz == null)
            {
            ClassTemplate templateRT   = INSTANCE;
            ConstantPool  pool         = templateRT.pool();
            TypeConstant  typeTemplate = pool.ensureEcstasyTypeConstant("reflect.PropertyTemplate");
            PROPERTY_TEMPLATE_COMP = clz = templateRT.ensureClass(templateRT.f_container, typeTemplate);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the TypeComposition for an Array of PropertyTemplate
     */
    public static TypeComposition ensureArrayComposition()
        {
        TypeComposition clz = ARRAY_PROP_COMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typePropertyTemplate = pool.ensureEcstasyTypeConstant("reflect.PropertyTemplate");
            TypeConstant typePropertyArray = pool.ensureArrayType(typePropertyTemplate);
            ARRAY_PROP_COMP = clz = INSTANCE.f_container.resolveClass(typePropertyArray);
            assert clz != null;
            }
        return clz;
        }


    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Create a handle for a PropertyTemplate class.
     *
     * @param prop  the corresponding PropertyStructure
     *
     * @return the newly created handle
     */
    static ComponentTemplateHandle makePropertyHandle(PropertyStructure prop)
        {
        return new ComponentTemplateHandle(ensurePropertyTemplateComposition(), prop);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition PROPERTY_TEMPLATE_COMP;
    private static TypeComposition ARRAY_PROP_COMP;
    }