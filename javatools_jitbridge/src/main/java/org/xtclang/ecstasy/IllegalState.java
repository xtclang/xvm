package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native support of IllegalState exception.
 */
public class IllegalState extends Exception {
    public IllegalState(Ctx ctx) {
        super(ctx);
    }
}
