package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.CharConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xChar
        extends xConst
    {
    public static xChar INSTANCE;

    public xChar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof CharConstant)
            {
            frame.pushStack(new JavaLong(getCanonicalClass(),
                    (((CharConstant) constant).getValue())));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Character.MIN_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Character.MAX_VALUE)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(l + 1));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((JavaLong) hValue1).getValue() == ((JavaLong) hValue2).getValue();
        }

    // ----- helpers -----

    protected int overflow(Frame frame)
        {
        return frame.raiseException(xException.makeHandle("Char overflow"));
        }

    public static JavaLong makeHandle(long chValue)
        {
        return new JavaLong(INSTANCE.getCanonicalClass(), chValue);
        }
    }
