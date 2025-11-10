package org.xtclang._native.mgmt;

import org.xtclang.ecstasy.xService;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for _native.mgmt.ContainerControl
 */
public class ContainerControl extends xService {

    public ContainerControl(Ctx ctx) {
        super(ctx);

        $containerType = $xvm().ecstasyPool.ensureEcstasyTypeConstant("mgmt.Container.Control");
    }

    private final TypeConstant $containerType;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return $containerType;
    }
}
