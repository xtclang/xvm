package org.xvm.runtime.template;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ModuleConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;


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
            frame.pushStack(ensureModuleHandle((ModuleConstant) constant));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    /**
     * @return a ModuleHandle for the specified ModuleConstant
     */
    public ModuleHandle ensureModuleHandle(ModuleConstant constModule)
        {
        TypeConstant     typeModule = constModule.getType();
        ClassComposition clazz      = ensureClass(typeModule, typeModule);

        return f_mapModules.computeIfAbsent(constModule.getName(),
            sName -> new ModuleHandle(clazz, sName));
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

    private final Map<String, ModuleHandle> f_mapModules = new ConcurrentHashMap<>(3);
    }
