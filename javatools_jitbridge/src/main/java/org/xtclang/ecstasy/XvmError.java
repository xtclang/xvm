package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native implementation of the XvmError exception.
 *
 * <p>An XvmError is thrown when an unrecoverable error occurs, for example due to a Java issue
 * or JIT error, etc.
 */
public class XvmError extends Exception {
    public XvmError(Ctx ctx) {
        super(ctx);
    }
}
