package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Object`.
 */
public interface Object {
    default String toString(Ctx ctx) {
        return String.of(ctx, getClass().getName());
    }
}
