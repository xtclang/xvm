package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The abstract base of RTView* implementations.
 */
public abstract class xRTView
        extends xRTDelegate {
    protected xRTView(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public ClassTemplate getTemplate(TypeConstant type) {
        return this;
    }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn) {
        return getPropertySize(frame, hTarget, iReturn);
    }

    @Override
    protected int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity) {
        DelegateHandle hView = (DelegateHandle) hTarget;

        return nCapacity == hView.m_cSize
            ? Op.R_NEXT
            : frame.raiseException(xException.readOnly(frame, hView.getMutability()));
    }

    @Override
    protected int invokeInsertElement(Frame frame, ObjectHandle hTarget,
                                      JavaLong hIndex, ObjectHandle hValue, int iReturn) {
        return frame.raiseException(
                xException.readOnly(frame, ((DelegateHandle) hTarget).getMutability()));
    }

    @Override
    protected int invokeDeleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn) {
        return frame.raiseException(
                xException.readOnly(frame, ((DelegateHandle) hTarget).getMutability()));
    }

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue) {
        return null;
    }

    @Override
    public DelegateHandle deleteRange(DelegateHandle hTarget, long ofStart, long cSize) {
        return null;
    }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * The abstract base of view handles.
     */
    protected abstract static class ViewHandle
            extends DelegateHandle {
        protected ViewHandle(TypeComposition clazz, Mutability mutability) {
            super(clazz, mutability);
        }

        public abstract DelegateHandle getSource();

        /**
         * @return the underlying (fully unwrapped) delegate handle
         */
        public DelegateHandle unwrapSource() {
            DelegateHandle hSource = getSource();
            return hSource instanceof ViewHandle hView
                    ? hView.unwrapSource()
                    : hSource;
        }
    }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a view and owns no element storage");
    }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability) {
        // a view is always created over an existing delegate, never from raw content
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a view and owns no element storage");
    }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        // xArray.callEquals compares element-by-element through the array API and never asks the
        // delegate, so this states the position rather than borrowing another delegate's storage
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a view and owns no element storage");
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        if (hValue1 == hValue2) {
            return true;
        }

        // a view's identity is the identity of the delegate it projects
        if (!(hValue1 instanceof ViewHandle h1) || !(hValue2 instanceof ViewHandle h2)) {
            return false;
        }

        DelegateHandle hSource1 = h1.getSource();
        DelegateHandle hSource2 = h2.getSource();

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize         == h2.m_cSize
            && hSource1.getTemplate() == hSource2.getTemplate()
            && hSource1.getTemplate().compareIdentity(hSource1, hSource2);
    }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex) {
        // a view is fixed-size; xArray raises "ReadOnly: Fixed size array" before any delegate is
        // asked, so this states the position rather than borrowing another delegate's storage
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a view and owns no element storage");
    }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex) {
        // see insertElementImpl
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is a view and owns no element storage");
    }
}
