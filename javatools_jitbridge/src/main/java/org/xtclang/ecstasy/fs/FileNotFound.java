package org.xtclang.ecstasy.fs;

import org.xvm.javajit.Ctx;

/**
 * Native support of FileNotFound exception.
 */
public class FileNotFound extends PathException {
    public FileNotFound(Ctx ctx) {
        super(ctx);
    }
}
