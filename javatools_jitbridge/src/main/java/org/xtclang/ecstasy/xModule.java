package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Module;

import org.xvm.asm.ModuleStructure;

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
    public xType $type() {
        return (xType) $module.getIdentityConstant().getType().ensureXType($ctx().container);
    }
}
