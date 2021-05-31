package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTViewFromBit base implementation.
 */
public class xRTViewFromBit
        extends xRTDelegate
    {
    public static xRTViewFromBit INSTANCE;

    public xRTViewFromBit(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

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
            registerNativeTemplate(new xRTViewFromBitToByte(f_templates, f_struct, true));
            }
        }

    @Override
    public void initNative()
        {
        // register native views
        Map<TypeConstant, xRTViewFromBit> mapViews = new HashMap<>();

        mapViews.put(pool().typeByte(), xRTViewFromBitToByte.INSTANCE);

        VIEWS = mapViews;
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
        }

    /**
     * Create an ArrayDelegate<NumType> view into the specified ArrayDelegate<Bit> source.
     *
     * @param hSource      the source (of bit type) delegate
     * @param typeElement  the numeric type to create the view for
     * @param mutability   the desired mutability
     */
    public DelegateHandle createBitViewDelegate(DelegateHandle hSource, TypeConstant typeElement,
                                                Mutability mutability)
        {
        xRTViewFromBit template = VIEWS.get(typeElement);

        if (template != null)
            {
            return template.createBitViewDelegate(hSource, typeElement, mutability);
            }
        throw new UnsupportedOperationException();
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
            : frame.raiseException(xException.readOnly(frame));
        }

    @Override
    protected int invokeInsertElement(Frame frame, ObjectHandle hTarget,
                                      JavaLong hIndex, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(xException.readOnly(frame));
        }

    @Override
    protected int invokeDeleteElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        return frame.raiseException(xException.readOnly(frame));
        }

    @Override
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        throw new IllegalStateException();
        }


    private static Map<TypeConstant, xRTViewFromBit> VIEWS;
    }