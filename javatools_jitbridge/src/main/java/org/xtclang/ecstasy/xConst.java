package org.xtclang.ecstasy;

/**
 * All Ecstasy `const` types must extend this class.
 */
public abstract class xConst extends xObj implements Const {

    public xConst(long containerId) {
        super(containerId);
    }

    @Override
    public boolean $isImmut() {
        return true;
    }
}
