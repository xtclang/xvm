package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class xVar
        extends Ref
    {
    public static xVar INSTANCE;

    public xVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("set", new String[]{"RefType"}, VOID);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "set":
                ObjectHandle.ExceptionHandle hException = hThis.set(hArg);
                return hException == null ? Op.R_NEXT : frame.raiseException(hException);
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    }
