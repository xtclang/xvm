package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy `const` types must extend this class.
 */
public abstract class nConst
    extends nObj
    implements Const {

    public nConst(Ctx ctx) {
        super(ctx);
    }

    @Override
    public boolean $isImmut() {
        return true;
    }
}
