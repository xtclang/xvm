package org.xtclang._native.mgmt;

import org.xtclang.ecstasy.xService;
import org.xtclang.ecstasy.xType;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for _native.mgmt.ContainerControl
 */
public class ContainerControl extends xService {

    public ContainerControl(Ctx ctx) {
        super(ctx);

        $containerType = (xType) $xvm().ecstasyPool.
            ensureEcstasyTypeConstant("mgmt.Container.Control").ensureXType($ctx().container);
    }

    private final xType $containerType;

    @Override
    public xType $type() {
        return $containerType;
    }
}
