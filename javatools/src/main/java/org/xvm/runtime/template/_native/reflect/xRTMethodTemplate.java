package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

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
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: annotations.get()
     */
    public int getPropertyAnnotations(Frame frame, ComponentTemplateHandle hMethod, int iReturn)
        {
        MethodStructure method = (MethodStructure) hMethod.getComponent();
        Annotation[]    aAnno  = method.getAnnotations();

        return aAnno.length > 0
                ? new Utils.CreateAnnos(aAnno, iReturn).doNext(frame)
                : frame.assignValue(iReturn,
                    Utils.makeAnnoArrayHandle(frame.poolContext(), Utils.OBJECTS_NONE));
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
    static ComponentTemplateHandle makeMethodHandle(MethodStructure method)
        {
        return new ComponentTemplateHandle(ensureMethodTemplateComposition(), method);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static TypeComposition METHOD_TEMPLATE_COMP;
    }
