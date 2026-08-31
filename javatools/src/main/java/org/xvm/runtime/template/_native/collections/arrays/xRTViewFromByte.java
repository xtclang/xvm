package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Container;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;

import org.xvm.util.Lazy;


/**
 * The native RTViewFromByte base implementation.
 */
public class xRTViewFromByte
        extends xRTView {
    public static xRTViewFromByte getInstance(Container container) {
        return NativeTemplates.get(container).viewFromByte();
    }

    public xRTViewFromByte(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void registerNativeTemplates() {
        if (NativeTemplates.get(this).isViewFromByte(this)) {
            registerNativeTemplate(new xRTViewFromByteToInt8   (f_container, f_struct));
            registerNativeTemplate(new xRTViewFromByteToInt16  (f_container, f_struct));
            registerNativeTemplate(new xRTViewFromByteToInt64  (f_container, f_struct));
            registerNativeTemplate(new xRTViewFromByteToFloat64(f_container, f_struct));
        }
    }

    @Override
    public void initNative() {
    }

    /**
     * Create an ArrayDelegate<NumType> view into the specified ArrayDelegate<Byte> source.
     *
     * @param hSource     the source (of byte type) delegate
     * @param mutability  the desired mutability
     * @param nBytes      the number of bytes for the numeric type of this byte-based view
     */
    public DelegateHandle createByteView(DelegateHandle hSource, Mutability mutability,
                                         int nBytes) {
        ClassComposition clzView = getCanonicalClass();
        if (hSource instanceof SliceHandle hSlice) {
            // e.g.: bytes.slice().asInt64Array() -> bytes.asInt64Array().slice()
            ViewHandle hView = new ViewHandle(clzView, hSlice.f_hSource,
                                    hSlice.f_hSource.m_cSize/nBytes, mutability);

            return slice(hView, hSlice.f_ofStart/nBytes, hSlice.m_cSize/nBytes, hSlice.f_fReverse);
        }
        return new ViewHandle(clzView, hSource, hSource.m_cSize/nBytes, mutability);
    }

    /**
     * Create a typed numeric view into the specified ArrayDelegate<Byte> source.
     */
    public DelegateHandle createByteView(TypeConstant typeElement, DelegateHandle hSource,
                                         Mutability mutability, int nBytes) {
        xRTViewFromByte template = f_views.get(this).get(typeElement);
        if (template != null) {
            return template.createByteView(hSource, mutability, nBytes);
        }
        throw new UnsupportedOperationException("RTViewFromByteTo" + typeElement.getValueString());
    }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * DelegateArray<NumType> view delegate.
     */
    protected static class ViewHandle
            extends xRTView.ViewHandle {
        public final DelegateHandle f_hSource;

        protected ViewHandle(TypeComposition clazz, DelegateHandle hSource,
                             long cSize, Mutability mutability) {
            super(clazz, mutability);

            f_hSource = hSource;
            m_cSize   = cSize;
        }

        @Override
        public DelegateHandle getSource() {
            return f_hSource;
        }
    }


    // ----- data members --------------------------------------------------------------------------

    private final Lazy.Bound<xRTViewFromByte, Map<TypeConstant, xRTViewFromByte>> f_views =
            Lazy.ofBound(xRTViewFromByte::createViews);

    private Map<TypeConstant, xRTViewFromByte> createViews() {
        ConstantPool                       pool     = pool();
        Map<TypeConstant, xRTViewFromByte> mapViews = new HashMap<>();

        putView(mapViews, pool, pool.typeInt8());
        putView(mapViews, pool, pool.typeInt16());
        putView(mapViews, pool, pool.typeInt64());
        putView(mapViews, pool, pool.typeFloat64());

        return Map.copyOf(mapViews);
    }

    private void putView(Map<TypeConstant, xRTViewFromByte> mapViews,
                         ConstantPool pool, TypeConstant typeElement) {
        TypeConstant typeView = pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), typeElement);
        mapViews.put(typeElement, f_container.getTemplate(typeView, xRTViewFromByte.class));
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
