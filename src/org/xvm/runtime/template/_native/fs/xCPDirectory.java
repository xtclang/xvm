package org.xvm.runtime.template._native.fs;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.FSNodeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ConstantHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xConst;


/**
 * Native OSFileStore implementation.
 */
public class xCPDirectory
        extends xConst
    {
    public xCPDirectory(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        ConstantPool pool = pool();

        s_clz         = ensureClass(getCanonicalType(), pool.typeDirectory());
        s_clzStruct   = s_clz.ensureAccess(Access.STRUCT);
        s_constructor = f_struct.findConstructor(pool.typeObject());
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof FSNodeConstant && constant.getFormat() == Format.FSDir)
            {
            FSNodeConstant constDir = (FSNodeConstant) constant;

            GenericHandle  hStruct = new GenericHandle(s_clzStruct);
            ObjectHandle[] ahVar   = Utils.ensureSize(Utils.OBJECTS_NONE, s_constructor.getMaxVars());
            ahVar[0] = new ConstantHandle(constDir);

            return callConstructor(frame, s_constructor, hStruct, ahVar, Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }


    // ----- constants -----------------------------------------------------------------------------

    private static ClassComposition s_clz;
    private static ClassComposition s_clzStruct;
    private static MethodStructure  s_constructor;


    // ----- data members --------------------------------------------------------------------------
    }
