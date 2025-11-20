package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native support of TypeMismatch exception.
 */
public class TypeMismatch extends Exception {
    public TypeMismatch(Ctx ctx) {
        super(ctx);
    }
}
