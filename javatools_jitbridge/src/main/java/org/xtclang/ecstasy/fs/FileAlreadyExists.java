package org.xtclang.ecstasy.fs;

import org.xtclang.ecstasy.Exception;
import org.xvm.javajit.Ctx;

/**
 * Native support of FileAlreadyExists exception.
 */
public class FileAlreadyExists extends PathException {
    public FileAlreadyExists(Ctx ctx) {
        super(ctx);
    }
}
