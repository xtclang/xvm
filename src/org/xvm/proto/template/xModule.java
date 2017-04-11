package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool.ModuleConstant;

import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeCompositionTemplate;
import org.xvm.proto.TypeSet;

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
    public xModule(TypeSet types)
        {
        super(types, "x:Module", "x:Object", Shape.Interface);
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
    public ObjectHandle createConstHandle(Constant constant)
        {
        if (constant instanceof ModuleConstant)
            {
            ModuleConstant constModule = (ModuleConstant) constant;

            return f_mapModules.computeIfAbsent(constModule.getQualifiedName(),
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
