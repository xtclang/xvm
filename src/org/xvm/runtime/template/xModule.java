package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;


/**
 * TODO:
 */
public class xModule
        extends ClassTemplate
    {
    public static xModule INSTANCE;

    public xModule(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ModuleConstant)
            {
            ModuleConstant   constModule = (ModuleConstant) constant;
            TypeConstant     typeModule  = constModule.getType();
            ClassComposition clazz       = ensureClass(typeModule, typeModule);

            MethodStructure methodID = clazz.ensureAutoInitializer();
            if (methodID == null)
                {
                return frame.assignValue(Op.A_STACK, new ModuleHandle(clazz, f_struct.getName()));
                }

            ModuleHandle hStruct = new ModuleHandle(clazz.ensureAccess(Access.STRUCT), f_struct.getName());
            Frame        frameID = frame.createFrame1(methodID, hStruct, Utils.OBJECTS_NONE, Op.A_IGNORE);

            frameID.setContinuation(frameCaller ->
                frameCaller.assignValue(Op.A_STACK, hStruct.ensureAccess(Access.PUBLIC)));

            return frame.callInitialized(frameID);
            }

        return super.createConstHandle(frame, constant);
        }

    public static class ModuleHandle extends GenericHandle
        {
        String m_sName;

        protected ModuleHandle(TypeComposition clazz, String sName)
            {
            super(clazz);

            m_sName = sName;
            }
        }
    }
