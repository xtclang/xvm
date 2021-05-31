package org.xvm.runtime.template._native.collections.arrays;


import java.util.HashMap;
import java.util.Map;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * The native RTViewToBit base implementation.
 */
public class xRTViewToBit
        extends xRTDelegate
    {
    public static xRTViewToBit INSTANCE;

    public xRTViewToBit(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
            registerNativeTemplate(new xRTViewToBitFromInt(f_templates, f_struct, true));
            }
        }
    @Override
    public void initNative()
        {
        // register native views
        Map<TypeConstant, xRTViewToBit> mapViews = new HashMap<>();

        mapViews.put(pool().typeInt(), xRTViewToBitFromInt.INSTANCE);

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

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
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


    // ----- constants -----------------------------------------------------------------------------

    private static Map<TypeConstant, xRTViewToBit> VIEWS;
    }