package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;


/**
 * Native PropertyTemplate implementation.
 */
public class xRTPropertyTemplate
        extends xRTComponentTemplate
    {
    public static xRTPropertyTemplate INSTANCE;

    public xRTPropertyTemplate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("type");

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
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: type.get()
     */
    public int getPropertyType(Frame frame, ComponentTemplateHandle hProp, int iReturn)
        {
        PropertyStructure prop = (PropertyStructure) hProp.getComponent();

        return frame.assignValue(iReturn, xRTTypeTemplate.makeHandle(prop.getType()));
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
            PROPERTY_TEMPLATE_COMP = clz = templateRT.ensureClass(typeTemplate);
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
            ARRAY_PROP_COMP = clz = INSTANCE.f_templates.resolveClass(typePropertyArray);
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
