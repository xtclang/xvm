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
        extends xRTView {
    public xRTViewToBit(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure);
    }

    @Override
    public void registerNativeTemplates() {
        if (isNativeInstance(xRTViewToBit.class)) {
            registerNativeTemplate(new xRTViewToBitFromNibble(f_container, f_struct, true));

            registerNativeTemplate(new xRTViewToBitFromInt8   (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt16  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt32  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt64  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromInt128 (f_container, f_struct, true));

            registerNativeTemplate(new xRTViewToBitFromUInt8  (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt16 (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt32 (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt64 (f_container, f_struct, true));
            registerNativeTemplate(new xRTViewToBitFromUInt128(f_container, f_struct, true));

            registerNativeTemplate(new xRTViewToBitFromFloat64(f_container, f_struct, true));
        }
    }
    @Override
    public void initNative() {
        if (isNativeInstance(xRTViewToBit.class)) {
            // register native views
            ConstantPool                    pool     = pool();
            Map<TypeConstant, xRTViewToBit> mapViews = new HashMap<>();

            mapViews.put(pool.typeNibble(), f_container.nativeTemplate(xRTViewToBitFromNibble.class));

            mapViews.put(pool.typeInt8()   , f_container.nativeTemplate(xRTViewToBitFromInt8.class));
            mapViews.put(pool.typeInt16()  , f_container.nativeTemplate(xRTViewToBitFromInt16.class));
            mapViews.put(pool.typeInt32()  , f_container.nativeTemplate(xRTViewToBitFromInt32.class));
            mapViews.put(pool.typeInt64()  , f_container.nativeTemplate(xRTViewToBitFromInt64.class));
            mapViews.put(pool.typeInt128() , f_container.nativeTemplate(xRTViewToBitFromInt128.class));

            mapViews.put(pool.typeUInt8()  , f_container.nativeTemplate(xRTViewToBitFromUInt8.class));
            mapViews.put(pool.typeUInt16() , f_container.nativeTemplate(xRTViewToBitFromUInt16.class));
            mapViews.put(pool.typeUInt32() , f_container.nativeTemplate(xRTViewToBitFromUInt32.class));
            mapViews.put(pool.typeUInt64() , f_container.nativeTemplate(xRTViewToBitFromUInt64.class));
            mapViews.put(pool.typeUInt128(), f_container.nativeTemplate(xRTViewToBitFromUInt128.class));

            mapViews.put(pool.typeFloat64(), f_container.nativeTemplate(xRTViewToBitFromFloat64.class));

            VIEWS = mapViews;
        }
    }

    @Override
    public TypeComposition ensureParameterizedClass(Container container, TypeConstant... atypeParams) {
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
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability) {
        TypeConstant typeElement = hSource.getType().getParamType(0);
        xRTViewToBit template    = VIEWS.get(typeElement);

        if (template != null) {
            return template.createBitViewDelegate(hSource, mutability);
        }
        throw new UnsupportedOperationException("RTViewToBitFrom" + typeElement.getValueString());
    }

    // ----- constants -----------------------------------------------------------------------------

    private static Map<TypeConstant, xRTViewToBit> VIEWS;
}
