package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Module;

import org.xvm.asm.ModuleStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `module` types must extend this class.
 */
public class nModule
    extends nConst
        implements Module {

    public nModule(Ctx ctx) {
        super(ctx);

        $module = ctx.container.typeSystem.mainModule();
    }

    public final ModuleStructure $module;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return $module.getIdentityConstant().getType();
    }
}
