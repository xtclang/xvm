package org.xtclang.ecstasy.reflect;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * All Ecstasy {@code Class<Enum>} types must extend this class.
 */
public abstract class Enumeration extends Class {
    protected Enumeration(Ctx ctx, TypeConstant publicType) {
        super(ctx, publicType);
    }
}
