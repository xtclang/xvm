package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.Boolean;
import org.xtclang.ecstasy.Const;
import org.xtclang.ecstasy.Object;

import org.xtclang.ecstasy.nType;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitFromNatural;
import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation of `ecstasy.Type`.
 */
@JitGenerated
public interface Type extends Const {

    /**
     * {@code conditional function DataType(Struct) structConstructor(OuterType? outer = Null)}
     */
    Boolean structConstructor(Ctx ctx, Type OuterType, Object outer);

    @JitName
    @JitFromNatural
    static Boolean equals(Ctx ctx, nType CompileType, Type value1, Type value2) {
        return null;
    }

    @JitName
    @JitFromNatural
    default Boolean hashed(Ctx ctx) {
        return null;
    }
}
