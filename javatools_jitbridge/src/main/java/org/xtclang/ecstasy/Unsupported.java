package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native support of Unsupported exception.
 */
public class Unsupported extends Exception {
    public Unsupported(Ctx ctx) {
        super(ctx);
    }
}
