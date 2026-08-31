package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Container;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.util.Lazy;


/**
 * The native RTViewFromBit base implementation.
 */
public class xRTViewFromBit
        extends xRTView<xRTViewFromBit.ViewHandle>
        implements ByteView {
    public static xRTViewFromBit getInstance(Container container) {
        return NativeTemplates.get(container).viewFromBit();
    }

    public xRTViewFromBit(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void registerNativeTemplates() {
        if (NativeTemplates.get(this).isViewFromBit(this)) {
            registerNativeTemplate(new xRTViewFromBitToBoolean (f_container, f_struct));
            registerNativeTemplate(new xRTViewFromBitToByte    (f_container, f_struct));
            registerNativeTemplate(new xRTViewFromBitToNibble  (f_container, f_struct));
        }
    }

    @Override
    public void initNative() {
    }

    /**
     * Create an ArrayDelegate<NumType> view into the specified ArrayDelegate<Bit> source.
     *
     * @param hSource     the source (of bit type) delegate
     * @param mutability  the desired mutability
     */
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, Mutability mutability) {
        throw new UnsupportedOperationException("unsupported delegate: " + hSource);
    }

    /**
     * Create a typed view into the specified ArrayDelegate<Bit> source.
     */
    public DelegateHandle createBitViewDelegate(TypeConstant typeElement,
                                                DelegateHandle hSource,
                                                Mutability mutability) {
        xRTViewFromBit template = f_views.get(this).get(typeElement);
        if (template != null) {
            return template.createBitViewDelegate(hSource, mutability);
        }
        throw new UnsupportedOperationException("RTViewFromBitTo" + typeElement.getValueString());
    }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse) {
        ViewHandle     hView   = (ViewHandle) hDelegate;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView) {
            return tView.getBytes(hSource, ofStart, cBytes, fReverse);
        }

        throw new UnsupportedOperationException("unsupported delegate: " + hSource);
    }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of) {
        ViewHandle     hView   = (ViewHandle) hDelegate;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView) {
            return tView.extractByte(hSource, of);
        }

        throw new UnsupportedOperationException("unsupported delegate: " + hSource);
    }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue) {
        ViewHandle     hView   = (ViewHandle) hDelegate;
        DelegateHandle hSource = hView.f_hSource;
        ClassTemplate  tSource = hSource.getTemplate();

        if (tSource instanceof ByteView tView) {
            tView.assignByte(hSource, of, bValue);
            return;
        }

        throw new UnsupportedOperationException("unsupported delegate: " + hSource);
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

    private final Lazy.Bound<xRTViewFromBit, Map<TypeConstant, xRTViewFromBit>> f_views =
            Lazy.ofBound(xRTViewFromBit::createViews);

    private Map<TypeConstant, xRTViewFromBit> createViews() {
        ConstantPool                      pool     = pool();
        Map<TypeConstant, xRTViewFromBit> mapViews = new HashMap<>();

        putView(mapViews, pool, pool.typeBoolean());
        putView(mapViews, pool, pool.typeByte());
        putView(mapViews, pool, pool.typeNibble());

        return Map.copyOf(mapViews);
    }

    private void putView(Map<TypeConstant, xRTViewFromBit> mapViews,
                         ConstantPool pool, TypeConstant typeElement) {
        TypeConstant typeView = pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(), typeElement);
        mapViews.put(typeElement, f_container.getTemplate(typeView, xRTViewFromBit.class));
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
    protected int extractArrayValueImpl(Frame frame, ViewHandle hTarget, long lIndex,
                                        int iReturn) {
        return frame.raiseException(xException.unsupported(frame, storageMessage()));
    }

    @Override
    protected int assignArrayValueImpl(Frame frame, ViewHandle hTarget, long lIndex,
                                       ObjectHandle hValue) {
        return frame.raiseException(xException.unsupported(frame, storageMessage()));
    }

    @Override
    protected DelegateHandle createCopyImpl(ViewHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse) {
        throw new UnsupportedOperationException(storageMessage());
    }

    private String storageMessage() {
        return getClass().getSimpleName() + " defines no element storage of its own";
    }

}
