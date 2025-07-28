package org.xtclang.ecstasy.reflect;

import org.xtclang.ecstasy.collections.Tuple;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for `ecstasy.reflect.Function`.
 */
public interface Function {
    /**
     * {@code ReturnTypes invoke(ParamTypes args)}
     */
    Tuple invoke(Ctx $ctx, Tuple args);
}
