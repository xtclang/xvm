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

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xInt64;


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
        s_constructor = f_struct.findConstructor(
                pool.typeObject(),
                pool.typePath(),
                pool.typeDateTime(),
                pool.typeDateTime(),
                pool.typeInt());
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof FSNodeConstant && constant.getFormat() == Format.FSDir)
            {
            FSNodeConstant constDir = (FSNodeConstant) constant;

            GenericHandle hStruct = new GenericHandle(s_clzStruct);

            return callConstructor(frame, s_constructor, s_clz.ensureAutoInitializer(), hStruct,
                    new ObjectHandle[]
                            {
                            new ConstantHandle(constDir),
                            frame.f_context.f_heapGlobal.ensureConstHandle(frame, constDir.getPathConstant()),
                            frame.f_context.f_heapGlobal.ensureConstHandle(frame, constDir.getCreatedConstant()),
                            frame.f_context.f_heapGlobal.ensureConstHandle(frame, constDir.getModifiedConstant()),
                            xInt64.makeHandle(0) // TODO CP
                            },
                    Op.A_STACK);
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected boolean isConstructImmutable()
        {
        return true;
        }

    // ----- constants -----------------------------------------------------------------------------

    static private ClassComposition s_clz;
    static private ClassComposition s_clzStruct;
    static private MethodStructure  s_constructor;


    // ----- data members --------------------------------------------------------------------------
    }
