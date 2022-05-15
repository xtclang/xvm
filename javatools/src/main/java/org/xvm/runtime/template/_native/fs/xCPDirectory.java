package org.xvm.runtime.template._native.fs;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FSNodeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ConstantHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xConst;


/**
 * Native CPDirectory implementation.
 */
public class xCPDirectory
        extends xConst
    {
    public xCPDirectory(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        s_constructor = getStructure().findConstructor(f_container.getConstantPool().typeObject());
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof FSNodeConstant constDir && constant.getFormat() == Format.FSDir)
            {
            TypeComposition clz    = ensureClass(frame.f_context.f_container,
                                        getCanonicalType(), frame.poolContext().typeDirectory());
            GenericHandle  hStruct = new GenericHandle(clz.ensureAccess(Access.STRUCT));
            ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, s_constructor.getMaxVars());
            ahVar[0] = new ConstantHandle(constDir);

            return proceedConstruction(frame, s_constructor, true, hStruct, ahVar, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static MethodStructure s_constructor;
    }