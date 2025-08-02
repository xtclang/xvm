package org.xtclang.ecstasy;

/**
 * All Ecstasy `enum` types must extend this class.
 */
public abstract class xEnum extends xObj implements Enum {
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
