package org.xvm.javajit.intrinsic;

public abstract class xService extends xObj {

    public xService(long containerId) {
        super(containerId);
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    @Override
    public void $makeImmut() {
        throw new xException("Unsupported"); // TODO: new Unsupported
    }
}
