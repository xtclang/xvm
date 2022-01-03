package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;


/**
 * Native MethodTemplate implementation.
 */
public class xRTMethodTemplate
        extends xRTComponentTemplate
    {
    public static xRTMethodTemplate INSTANCE;

    public xRTMethodTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("annotations");
        markNativeProperty("hasCode");
        markNativeProperty("parameterCount");
        markNativeProperty("returnCount");

        markNativeMethod("getParameter", INT, null);
        markNativeMethod("getReturn", INT, null);

        super.initNative();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        ComponentTemplateHandle hMethod = (ComponentTemplateHandle) hTarget;
        switch (sPropName)
            {
            case "annotations":
                return getPropertyAnnotations(frame, hMethod, iReturn);

            case "hasCode":
                return getPropertyHasCode(frame, hMethod, iReturn);

            case "parameterCount":
                return frame.assignValue(iReturn,
                    xInt64.makeHandle(((MethodStructure) hMethod.getComponent()).getParamCount()));

            case "returnCount":
                return frame.assignValue(iReturn,
                    xInt64.makeHandle(((MethodStructure) hMethod.getComponent()).getReturnCount()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        ComponentTemplateHandle hMethod = (ComponentTemplateHandle) hTarget;
        switch (method.getName())
            {
            case "getParameter":
                return invokeGetParameter(frame, hMethod, (JavaLong) ahArg[0], aiReturn);

            case "getReturn":
                return invokeGetReturn(frame, hMethod, (JavaLong) ahArg[0], aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: annotations.get()
     */
    protected int getPropertyAnnotations(Frame frame, ComponentTemplateHandle hMethod, int iReturn)
        {
        MethodStructure method = (MethodStructure) hMethod.getComponent();
        Annotation[]    aAnno  = method.getAnnotations();

        return aAnno.length > 0
                ? new Utils.CreateAnnos(aAnno, iReturn).doNext(frame)
                : frame.assignValue(iReturn,
                    Utils.makeAnnoArrayHandle(frame.poolContext(), Utils.OBJECTS_NONE));
        }

    /**
     * Implements property: hasCode.get()
     */
    protected int getPropertyHasCode(Frame frame, ComponentTemplateHandle hMethod, int iReturn)
        {
        MethodStructure method = (MethodStructure) hMethod.getComponent();
        return frame.assignValue(iReturn, xBoolean.makeHandle(method.hasCode()));
        }


    // ----- methods implementations ---------------------------------------------------------------

    /**
     * Implements method:
     *  (String? name, TypeTemplate type, Boolean formal, Boolean hasDefault, Const? defaultValue)
     *         getParameter(Int index)
     */
    protected int invokeGetParameter(Frame frame, ComponentTemplateHandle hMethod, JavaLong hIndex,
                                     int[] aiReturn)
        {
        MethodStructure method = (MethodStructure) hMethod.getComponent();

        Parameter parameter = method.getParam((int) hIndex.getValue());
        String    sName     = parameter.getName();
        boolean   fDefault  = parameter.hasDefaultValue();

        ObjectHandle[] ahReturn = new ObjectHandle[5];

        ahReturn[0] = sName == null ? xNullable.NULL : xString.makeHandle(sName);
        ahReturn[1] = xRTTypeTemplate.makeHandle(parameter.getType());
        ahReturn[2] = xBoolean.makeHandle(parameter.isTypeParameter());
        ahReturn[3] = xBoolean.makeHandle(fDefault);
        ahReturn[4] = fDefault ? frame.getConstHandle(parameter.getDefaultValue()) : xNullable.NULL;

        return frame.assignValues(aiReturn, ahReturn);
        }

    /**
     * Implements method:
     *  (String? name, TypeTemplate type, Boolean cond)
     *         getReturn(Int index)
     */
    protected int invokeGetReturn(Frame frame, ComponentTemplateHandle hMethod, JavaLong hIndex,
                                     int[] aiReturn)
        {
        MethodStructure method = (MethodStructure) hMethod.getComponent();

        Parameter parameter = method.getReturn((int) hIndex.getValue());
        String    sName     = parameter.getName();

        ObjectHandle[] ahReturn = new ObjectHandle[3];

        ahReturn[0] = sName == null ? xNullable.NULL : xString.makeHandle(sName);
        ahReturn[1] = xRTTypeTemplate.makeHandle(parameter.getType());
        ahReturn[2] = xBoolean.makeHandle(parameter.isConditionalReturn());

        return frame.assignValues(aiReturn, ahReturn);
        }


    // ----- Composition caching -------------------------------------------------------------------

    /**
     * @return the TypeComposition for an RTMethodTemplate
     */
    public static TypeComposition ensureMethodTemplateComposition()
        {
        TypeComposition clz = METHOD_TEMPLATE_COMP;
        if (clz == null)
            {
            ClassTemplate templateRT   = INSTANCE;
            ConstantPool  pool         = templateRT.pool();
            TypeConstant  typeTemplate = pool.ensureEcstasyTypeConstant("reflect.MethodTemplate");
            METHOD_TEMPLATE_COMP = clz = templateRT.ensureClass(typeTemplate);
            assert clz != null;
            }
        return clz;
        }

    // ----- ObjectHandle support ------------------------------------------------------------------

    /**
     * Create a handle for a MethodTemplate class.
     *
     * @param method  the corresponding MethodStructure
     *
     * @return the newly created handle
     */
    static ComponentTemplateHandle makeHandle(MethodStructure method)
        {
        return new ComponentTemplateHandle(ensureMethodTemplateComposition(), method);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition METHOD_TEMPLATE_COMP;
    }
