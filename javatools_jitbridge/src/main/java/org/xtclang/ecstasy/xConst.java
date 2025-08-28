package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `const` types must extend this class.
 */
public abstract class xConst extends xObj implements Const {

    public xConst(Ctx ctx) {
        super(ctx);
    }

    @Override
    public boolean $isImmut() {
        return true;
    }
}
