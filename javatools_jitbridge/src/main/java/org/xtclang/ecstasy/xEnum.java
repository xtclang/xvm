package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `enum` types must extend this class.
 */
public abstract class xEnum extends xObj implements Enum {
    public xEnum(Ctx ctx) {
        super(ctx);
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
