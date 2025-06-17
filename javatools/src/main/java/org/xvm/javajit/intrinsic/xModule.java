package org.xvm.javajit.intrinsic;


import org.xvm.asm.ModuleStructure;

/**
 * Native Module impl.
 */
public class xModule extends xConst {

    public xModule(long containerId, ModuleStructure module) {
        super(containerId);

        $module = module;
    }

    public final ModuleStructure $module;

    @Override
    public xType $type() {
        return $module.getIdentityConstant().getType().ensureXType($ctx().container);
    }
}
