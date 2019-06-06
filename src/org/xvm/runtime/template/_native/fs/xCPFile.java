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
public class xCPFile
        extends xConst
    {
    public xCPFile(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        ConstantPool pool = pool();

        s_clz         = ensureClass(getCanonicalType(), pool.typeFile());
        s_clzStruct   = s_clz.ensureAccess(Access.STRUCT);
        s_clzDateTime = (xConst) ensureClass(pool.typeDateTime(), pool.typeDateTime()).getTemplate();
        s_constructor = f_struct.findConstructor(
                pool.typeObject(),
                pool.ensureEcstasyTypeConstant("fs.Path"),
                pool.typeDateTime(),
                pool.typeDateTime(),
                pool.typeInt());
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof FSNodeConstant && constant.getFormat() == Format.FSFile)
            {
            FSNodeConstant constFile = (FSNodeConstant) constant;

            GenericHandle hStruct = new GenericHandle(s_clzStruct);
            return callConstructor(frame, s_constructor, s_clz.ensureAutoInitializer(), hStruct,
                    new ObjectHandle[]
                            {
                            new ConstantHandle(constFile),
                            frame.f_context.f_heapGlobal.ensureConstHandle(frame, constFile.getPath()),
                            frame.f_context.f_heapGlobal.ensureConstHandle(frame, constFile.getCreatedConstant()),
                            frame.f_context.f_heapGlobal.ensureConstHandle(frame, constFile.getModifiedConstant()),
                            xInt64.makeHandle(constFile.getFileBytes().length)
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

    static private xConst           s_clzDateTime;
    static private xConst           s_clzPath;


    // ----- data members --------------------------------------------------------------------------
    }
