package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * Native shell for "ecstasy.numbers.IntLiteral".
 */
public abstract class IntLiteral extends nConst {
    // unused by the JIT, but it must exist to satisfy javac
    private IntLiteral() {
        super(null);
    }
}