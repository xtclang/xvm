package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

import static org.xvm.javajit.TypeSystem.removeJitSuffix;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation of `ecstasy.Object`.
 */
@JitGenerated
public interface Object extends Comparable {
    /**
     * {@code static <CompileType extends Object> Boolean equals(CompileType o1, CompileType o2)}
     */
    @JitName
    static boolean equals$p(Ctx ctx, nType CompileType, Object o1, Object o2) {
        return o1 == o2; // TODO CP: check unwrap
    }

    default String toString(Ctx ctx) {
        return String.of(ctx, removeJitSuffix(getClass().getName()));
    }
}
