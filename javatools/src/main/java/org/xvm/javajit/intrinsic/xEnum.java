package org.xvm.javajit.intrinsic;


/**
 * All Ecstasy `enum` types must extend this class.
 */
public class xEnum extends xObj {
    public xEnum(long containerId) {
        super(containerId);
    }

    @Override
    public xType $type() {
        return null; // TODO
    }

    @Override
    public boolean $isImmut() {
        return true;
    }
}
