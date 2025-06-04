package org.xtclang.ecstasy;

import org.xvm.asm.ModuleStructure;

/**
 * All Ecstasy `module` types must extend this class.
 */
public class xModule extends xConst {

    public xModule(long containerId, ModuleStructure module) {
        super(containerId);

        $module = module;
    }

    public final ModuleStructure $module;

    @Override
    public xType $type() {
        return (xType) $module.getIdentityConstant().getType().ensureXType($ctx().container);
    }
}
