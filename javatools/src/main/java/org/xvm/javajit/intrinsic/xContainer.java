package org.xvm.javajit.intrinsic;

/**
 * Native implementation for _native.mgmt.Container
 */
public class xContainer extends xService {

    public xContainer(long containerId) {
        super(containerId);

        $containerType = $xvm().ecstasyPool.
            ensureEcstasyTypeConstant("mgmt.Container").ensureXType($ctx().container);
    }

    private final xType $containerType;

    @Override
    public xType $type() {
        return $containerType;
    }
}
