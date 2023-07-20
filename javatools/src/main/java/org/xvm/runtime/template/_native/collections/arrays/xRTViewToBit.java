package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTViewToBit base implementation.
 */
public class xRTViewToBit
        extends xRTView
    {
    public static xRTViewToBit INSTANCE;

    public xRTViewToBit(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

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
            registerNativeTemplate(new xRTViewToBitFromNibble(f_container, f_struct, true));

            registerNativeTemplate(new xRTViewToBitFromInt    (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt8   (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt16  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt32  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt64  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt128 (f_container, f_struct, true));

            registerNativeTemplate(new xRTViewToBitFromUInt   (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt8  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt16 (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt32 (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt64 (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt128(f_container, f_struct, true));
            }
        }
    @Override
    public void initNative()
        {
        if (this == INSTANCE)
            {
            // register native views
            ConstantPool                    pool     = pool();
            Map<TypeConstant, xRTViewToBit> mapViews = new HashMap<>();

            mapViews.put(pool.typeNibble(), xRTViewToBitFromNibble.INSTANCE);

            mapViews.put(pool.typeInt()    , xRTViewToBitFromInt    .INSTANCE);
            mapViews.put(pool.typeInt8()   , xRTViewToBitFromInt8   .INSTANCE);
            mapViews.put(pool.typeInt16()  , xRTViewToBitFromInt16  .INSTANCE);
            mapViews.put(pool.typeInt32()  , xRTViewToBitFromInt32  .INSTANCE);
            mapViews.put(pool.typeInt64()  , xRTViewToBitFromInt64  .INSTANCE);
            mapViews.put(pool.typeInt128() , xRTViewToBitFromInt128 .INSTANCE);

            mapViews.put(pool.typeUInt()   , xRTViewToBitFromUInt   .INSTANCE);
            mapViews.put(pool.typeUInt8()  , xRTViewToBitFromUInt8  .INSTANCE);
            mapViews.put(pool.typeUInt16() , xRTViewToBitFromUInt16 .INSTANCE);
            mapViews.put(pool.typeUInt32() , xRTViewToBitFromUInt32 .INSTANCE);
            mapViews.put(pool.typeUInt64() , xRTViewToBitFromUInt64 .INSTANCE);
            mapViews.put(pool.typeUInt128(), xRTViewToBitFromUInt128.INSTANCE);

            VIEWS = mapViews;
            }
        }

    @Override
    public TypeComposition ensureParameterizedClass(Container container, TypeConstant... atypeParams)
        {
        assert atypeParams.length == 1;

        TypeConstant typeInception = container.getConstantPool().ensureParameterizedTypeConstant(
            getInceptionClassConstant().getType(), atypeParams);

        return ensureClass(container, typeInception, typeInception);
        }

    /**
     * Create an ArrayDelegate<Bit> view into the specified ArrayDelegate<NumType> source.
     *
     * @param hSource     the source (of numeric type) delegate
     * @param mutability  the desired mutability (Constant of Fixed)
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