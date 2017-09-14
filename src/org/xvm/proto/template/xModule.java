package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xModule
        extends ClassTemplate
    {
    public static xModule INSTANCE;

    public xModule(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

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
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof ModuleConstant)
            {
            ModuleConstant constModule = (ModuleConstant) constant;

            return f_mapModules.computeIfAbsent(constModule.getName(),
                    sName -> new ModuleHandle(f_clazzCanonical, sName));
            }
        return null;
        }

    public static class ModuleHandle extends ObjectHandle
        {
        String m_sName;

        protected ModuleHandle(TypeComposition clazz, String sName)
            {
            super(clazz);
            m_sName = sName;
            }
        }

    private final Map<String, ModuleHandle> f_mapModules = new HashMap<>();
    }
