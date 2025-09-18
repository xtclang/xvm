package org.xtclang.ecstasy.io;

import org.xtclang.ecstasy.Exception;

import org.xvm.javajit.Ctx;

/**
 * Native support of IOException
 */
public class IOException extends Exception {
    public IOException(Ctx ctx) {
        super(ctx);
    }
}
