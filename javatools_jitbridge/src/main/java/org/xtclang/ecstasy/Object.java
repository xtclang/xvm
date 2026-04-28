package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Object`.
 *
 * I hit a bug in Java compiler: Object extends Comparable, but Java reflection does not show it.
 */
public interface Object extends Comparable {
    default String toString(Ctx ctx) {
        return String.of(ctx, getClass().getName());
    }
}
