package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xException;


/**
 * The abstract base of RTView* implementations.
 */
abstract public class xRTView
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
    }