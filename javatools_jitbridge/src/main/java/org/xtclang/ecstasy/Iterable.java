package org.xtclang.ecstasy;

import org.xtclang.ecstasy.collections.Array;
import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Iterable`.
 */
public interface Iterable extends Object {

//    xType Element$get(Ctx ctx);

//    long size$get$p(Ctx ctx);

//    default boolean empty$get$p(Ctx ctx) {
//        return size$get$p(ctx) == 0;
//    }

    Iterator iterator(Ctx ctx);

//    default Array toArray(Ctx ctx, xObj mutability) {
//        TODO ...
//        return null;
//    }
}
