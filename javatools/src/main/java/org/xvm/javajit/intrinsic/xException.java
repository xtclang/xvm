package org.xvm.javajit.intrinsic;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for ecstasy.Exception
 */
public class xException extends RuntimeException {
    public xException(Throwable cause) {
        super(cause);
        $ctx = Ctx.get();
    }

    public xException(String message) {
        super(message);
        $ctx = Ctx.get();
    }

    public Ctx $ctx;
}
