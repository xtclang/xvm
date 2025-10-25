package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Module;

import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `module` types must extend this class.
 */
public class xModule
        extends xConst
        implements Module {

    public xModule(Ctx ctx) {
        super(ctx);

        $module = ctx.container.typeSystem.mainModule();
    }

    public final ModuleStructure $module;

    @Override
    public TypeConstant $xvmType() {
        return $module.getIdentityConstant().getType();
    }
}
