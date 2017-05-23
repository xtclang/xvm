package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.constants.ModuleConstant;

import org.xvm.proto.*;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xModule
        extends TypeCompositionTemplate
    {
    public static xModule INSTANCE;

    public xModule(TypeSet types)
        {
        super(types, "x:Module", "x:Object", Shape.Interface);

        INSTANCE = this;
        }

    // subclassing
    protected xModule(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant, ObjectHeap heap)
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
