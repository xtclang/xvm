package org.xtclang.ecstasy.fs;

import org.xvm.javajit.Ctx;

/**
 * Native support of AccessDenied exception.
 */
public class AccessDenied extends PathException {
    public AccessDenied(Ctx ctx) {
        super(ctx);
    }
}
