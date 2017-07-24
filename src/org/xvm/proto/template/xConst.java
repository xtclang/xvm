package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.GenericHandle;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xConst
        extends ClassTemplate
    {
    public xConst(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        GenericHandle hConst = (GenericHandle) hTarget;

        switch (method.getName())
            {
            case "to":
                return frame.assignValue(iReturn, xString.makeHandle(
                        hConst.m_mapFields.toString()));
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }


    }
