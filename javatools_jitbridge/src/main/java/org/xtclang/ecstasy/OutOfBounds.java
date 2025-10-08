package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native support of OutOfBounds exception.
 */
public class OutOfBounds extends Exception {
    public OutOfBounds(Ctx ctx) {
        super(ctx);
    }
}
