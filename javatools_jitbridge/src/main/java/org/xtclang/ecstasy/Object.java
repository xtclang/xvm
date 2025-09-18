package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Type;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Object`.
 */
public interface Object {
    /**
     * {@code static <CompileType extends Object> Boolean equals(CompileType o1, CompileType o2)}
     */
    static boolean equals$p(Ctx ctx, Type CompileType, org.xtclang.ecstasy.Object o1, org.xtclang.ecstasy.Object o2) {
        return o1 == o2; // TODO CP: check unwrap
    }

    default String toString(Ctx ctx) {
        return String.of(ctx, getClass().getName());
    }
}
