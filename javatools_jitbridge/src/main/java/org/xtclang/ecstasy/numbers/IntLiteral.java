package org.xtclang.ecstasy.numbers;

import org.xtclang.ecstasy.nConst;

import org.xvm.javajit.Ctx;

/**
 * Native shell for "ecstasy.numbers.IntLiteral".
 */
public class IntLiteral extends nConst {
    private IntLiteral() {
        super(null);
    }

    public UIntN magnitude;
}
