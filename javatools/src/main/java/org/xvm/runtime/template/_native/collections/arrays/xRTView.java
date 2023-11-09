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
        extends xRTDelegate
    {
    protected xRTView(Container container, ClassStructure structure)
        {
        super(container, structure, false);
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    protected int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return getPropertySize(frame, hTarget, iReturn);
        }

    @Override
    protected int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        DelegateHandle hView = (DelegateHandle) hTarget;

        return nCapacity == hView.m_cSize
            ? Op.R_NEXT
            : frame.raiseException(xException.readOnly(frame, hView.getMutability()));
        }

    @Override
    protected int invokeInsertElement(Frame frame, ObjectHandle hTarget,
                                      JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(
                xException.readOnly(frame, ((DelegateHandle) hTarget).getMutability()));
        }

    @Override
    protected int invokeDeleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(
                xException.readOnly(frame, ((DelegateHandle) hTarget).getMutability()));
        }

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        return null;
        }

    @Override
    public DelegateHandle deleteRange(DelegateHandle hTarget, long ofStart, long cSize)
        {
        return null;
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * The abstract base of view handles.
     */
    protected abstract static class ViewHandle
            extends DelegateHandle
        {
        protected ViewHandle(TypeComposition clazz, Mutability mutability)
            {
            super(clazz, mutability);
            }

        public abstract DelegateHandle getSource();

        /**
         * @return the underlying (fully unwrapped) delegate handle
         */
        public DelegateHandle unwrapSource()
            {
            DelegateHandle hSource = getSource();
            return hSource instanceof ViewHandle hView
                    ? hView.unwrapSource()
                    : hSource;
            }
        }
    }