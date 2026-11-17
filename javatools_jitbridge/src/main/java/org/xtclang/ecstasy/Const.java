package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Const`.
 */
public interface Const extends Object {
    /**
     * Native implementation of:
     *
     * {@code static <CompileType extends Const> Boolean equals(CompileType value1, CompileType value2)}.
     */
    static boolean equals$p(Ctx ctx, nType CompileType, Const value1, Const value2) {
        if (CompileType.$dataType.equals(ctx.pool().typeConst())) {
            throw Exception.$unsupported(ctx, "Const.equals() requires a concrete comparison type");
        }
        return CompileType.equals$p(ctx, value1, value2);
    }
}
