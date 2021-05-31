package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTViewFromByte base implementation.
 */
public class xRTViewFromByte
        extends xRTView
    {
    public static xRTViewFromByte INSTANCE;

    public xRTViewFromByte(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
            registerNativeTemplate(new xRTViewFromByteToInt64(f_templates, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        // register native views
        Map<TypeConstant, xRTViewFromByte> mapViews = new HashMap<>();

        mapViews.put(pool().typeInt(), xRTViewFromByteToInt64.INSTANCE);

        VIEWS = mapViews;
        }

    /**
     * Create an ArrayDelegate<NumType> view into the specified ArrayDelegate<Byte> source.
     *
     * @param hSource      the source (of byte type) delegate
     * @param typeElement  the numeric type to create the view for
     * @param mutability   the desired mutability
     */
    public DelegateHandle createByteViewDelegate(DelegateHandle hSource, TypeConstant typeElement,
                                                 Mutability mutability)
        {
        xRTViewFromByte template = VIEWS.get(typeElement);

        if (template != null)
            {
            return template.createByteViewDelegate(hSource, typeElement, mutability);
            }
        throw new UnsupportedOperationException();
        }


    // ----- constants -----------------------------------------------------------------------------

    private static Map<TypeConstant, xRTViewFromByte> VIEWS;
    }