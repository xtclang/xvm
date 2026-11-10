package org.xtclang.ecstasy.reflect;

import java.lang.invoke.MethodHandle;

import org.xtclang.ecstasy.nObject;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * A reference view of a property whose state and implementation belong to its containing object.
 */
public class nRefProp extends nRef {
    public nRefProp(Ctx ctx, TypeConstant type, boolean isVar, nObject outer,
                    MethodHandle getter, MethodHandle setter, MethodHandle assigned) {
        super(ctx, type.resolveGenericType("Referent"), isVar, getter, setter, assigned);

        $outer = outer;
        $type  = type;
    }

    public final nObject $outer;
    public final TypeConstant $type;

    @Override
    public TypeConstant $xvmType(Ctx ctx) {
        return $type;
    }
}
