package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native support of IllegalArgument exception.
 */
public class IllegalArgument extends Exception {
    public IllegalArgument(Ctx ctx) {
        super(ctx);
    }
}
