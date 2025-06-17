package org.xvm.javajit.intrinsic;

/**
 * Native Const.
 */
public abstract class xConst extends xObj {

    public xConst(long containerId) {
        super(containerId);
    }

    @Override
    public boolean $isImmut() {
        return false;
    }

    @Override
    public void $makeImmut() {}
}
