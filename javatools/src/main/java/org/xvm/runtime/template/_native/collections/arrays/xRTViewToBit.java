package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTViewToBit base implementation.
 */
public class xRTViewToBit
        extends xRTView
    {
    public static xRTViewToBit INSTANCE;

    public xRTViewToBit(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
            registerNativeTemplate(new xRTViewToBitFromInt16(f_templates, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt64(f_templates, f_struct, true));
            }
        }
    @Override
    public void initNative()
        {
        // register native views
        ConstantPool                    pool     = pool();
        Map<TypeConstant, xRTViewToBit> mapViews = new HashMap<>();

        TypeConstant typeInt16 = pool.ensureEcstasyTypeConstant("numbers.Int16");

        mapViews.put(pool.typeInt(), xRTViewToBitFromInt64.INSTANCE);
        mapViews.put(typeInt16     , xRTViewToBitFromInt16.INSTANCE);

        VIEWS = mapViews;
        }

    @Override
    public TypeComposition ensureParameterizedClass(ConstantPool pool, TypeConstant... atypeParams)
        {
        assert atypeParams.length == 1;

        TypeConstant typeInception = pool.ensureParameterizedTypeConstant(
            getInceptionClassConstant().getType(), atypeParams);

        return ensureClass(typeInception, typeInception);
        }

    /**
     * Create an ArrayDelegate<Bit> view into the specified ArrayDelegate<NumType> source.
     *
     * @param hSource     the source (of numeric type) delegate
     * @param mutability  the desired mutability
     */
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability)
        {
        TypeConstant typeElement = hSource.getType().getParamType(0);
        xRTViewToBit template    = VIEWS.get(typeElement);

        if (template != null)
            {
            return template.createBitViewDelegate(hSource, mutability);
            }
        throw new UnsupportedOperationException("RTViewToBitFrom" + typeElement.getValueString());
        }


    // ----- constants -----------------------------------------------------------------------------

    private static Map<TypeConstant, xRTViewToBit> VIEWS;
    }