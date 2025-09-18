package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native support of ReadOnly exception.
 */
public class ReadOnly extends Exception {
    public ReadOnly(Ctx ctx) {
        super(ctx);
    }
}
