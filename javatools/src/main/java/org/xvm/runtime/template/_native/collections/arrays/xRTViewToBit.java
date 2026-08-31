package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.util.Lazy;


/**
 * The native RTViewToBit base implementation.
 */
public class xRTViewToBit
        extends xRTView {

    public static xRTViewToBit getInstance(Frame frame) {
        return NativeTemplates.get(frame).viewToBit();
    }

    public static xRTViewToBit getInstance(Container container) {
        return NativeTemplates.get(container).viewToBit();
    }

    public xRTViewToBit(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void registerNativeTemplates() {
        if (NativeTemplates.get(this).isViewToBit(this)) {
            registerNativeTemplate(new xRTViewToBitFromNibble(f_container, f_struct));

            registerNativeTemplate(new xRTViewToBitFromInt8   (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromInt16  (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromInt32  (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromInt64  (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromInt128 (f_container, f_struct));

            registerNativeTemplate(new xRTViewToBitFromUInt8  (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromUInt16 (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromUInt32 (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromUInt64 (f_container, f_struct));
            registerNativeTemplate(new xRTViewToBitFromUInt128(f_container, f_struct));

            registerNativeTemplate(new xRTViewToBitFromFloat64(f_container, f_struct));
        }
    }
    @Override
    public void initNative() {
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
        xRTViewToBit template    = f_views.get(this).get(typeElement);

        if (template != null) {
            return template.createBitViewDelegate(hSource, mutability);
        }
        throw new UnsupportedOperationException("RTViewToBitFrom" + typeElement.getValueString());
    }


    // ----- data members --------------------------------------------------------------------------

    // The dispatch map is owner-local because both the element type keys and specialized templates
    // are ConstantPool/container owned. A static map built from subtype INSTANCE fields can cross
    // containers under parallel startup.
    private final Lazy.Bound<xRTViewToBit, Map<TypeConstant, xRTViewToBit>> f_views =
            Lazy.ofBound(xRTViewToBit::createViews);

    private Map<TypeConstant, xRTViewToBit> createViews() {
        ConstantPool                    pool     = pool();
        Map<TypeConstant, xRTViewToBit> mapViews = new HashMap<>();

        putView(mapViews, pool, pool.typeNibble());

        putView(mapViews, pool, pool.typeInt8());
        putView(mapViews, pool, pool.typeInt16());
        putView(mapViews, pool, pool.typeInt32());
        putView(mapViews, pool, pool.typeInt64());
        putView(mapViews, pool, pool.typeInt128());

        putView(mapViews, pool, pool.typeUInt8());
        putView(mapViews, pool, pool.typeUInt16());
        putView(mapViews, pool, pool.typeUInt32());
        putView(mapViews, pool, pool.typeUInt64());
        putView(mapViews, pool, pool.typeUInt128());

        putView(mapViews, pool, pool.typeFloat64());

        return mapViews;
    }

    private void putView(Map<TypeConstant, xRTViewToBit> mapViews,
                         ConstantPool pool, TypeConstant typeElement) {
        TypeConstant typeView = pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), typeElement);
        mapViews.put(typeElement, f_container.getTemplate(typeView, xRTViewToBit.class));
    }

    // ----- storage protocol ----------------------------------------------------------------------

    /*
     * This view is an intermediate: it fixes how elements are addressed, and its concrete subclasses
     * fix how they are stored. It therefore has no storage of its own to read or write, and every
     * subclass overrides the three methods below. They exist because the base declares them, and
     * because inheriting the object-array implementations - which is what happened before the base
     * stopped providing them - would answer for storage this class does not have.
     */

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                        int iReturn) {
        return frame.raiseException(xException.unsupported(frame, storageMessage()));
    }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue) {
        return frame.raiseException(xException.unsupported(frame, storageMessage()));
    }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse) {
        throw new UnsupportedOperationException(storageMessage());
    }

    private String storageMessage() {
        return getClass().getSimpleName() + " defines no element storage of its own";
    }

}
