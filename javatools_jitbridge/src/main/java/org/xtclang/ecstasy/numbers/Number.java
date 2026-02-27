package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * Native Number wrapper.
 */
public abstract class Number
        extends nConst
        implements FPConvertible {

    protected Number() {
        super(null);
    }

    /**
     * Native support of IllegalMath exception.
     */
    public static class IllegalMath extends Exception {
        public IllegalMath(Ctx ctx) {
            super(ctx);
        }
    }

    /**
     * Native support of DivisionByZero exception.
     */
    public static class DivisionByZero extends IllegalMath {
        public DivisionByZero(Ctx ctx) {
            super(ctx);
        }
    }
}
