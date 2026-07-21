package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * Native shell for "ecstasy.numbers.IntLiteral".
 */
public abstract class IntLiteral extends nConst {
    public IntLiteral(Ctx ctx) {
        super(ctx);
    }
}