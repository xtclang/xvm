package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Boolean;
import org.xtclang.ecstasy.Const;
import org.xtclang.ecstasy.xObj;

import org.xvm.javajit.Ctx;

/**
 * Native representation of `ecstasy.Type`.
 */
public interface Type extends Const {

    /**
     * {@code conditional function DataType(Struct) structConstructor(OuterType? outer = Null)}
     */
    Boolean structConstructor(Ctx $ctx, Type OuterType, xObj outer);
}
