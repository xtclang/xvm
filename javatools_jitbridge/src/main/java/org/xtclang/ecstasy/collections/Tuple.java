package org.xtclang.ecstasy.collections;

import org.xtclang.ecstasy.Nullable;
import org.xtclang.ecstasy.nObject;
import org.xtclang.ecstasy.nTuple;
import org.xtclang.ecstasy.nType;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for `ecstasy.collections.Tuple`.
 */
public interface Tuple {

    /**
     * Native implementation of:
     *
     *   static <CompileType extends Tuple> Boolean equals(CompileType value1, CompileType value2)
     */
    static boolean equals$p(Ctx ctx, nType type, nTuple value1, nTuple value2) {
        if (value1 == value2) {
            return true;
        }

        nObject[] values1 = value1.$values;
        nObject[] values2 = value2.$values;
        int       count   = values1.length;
        if (count != values2.length) {
            return false;
        }

        TypeConstant[] types1 = value1.$type.getParamTypesArray();
        TypeConstant[] types2 = value2.$type.getParamTypesArray();
        TypeConstant   object = ctx.pool().typeObject();
        for (int i = 0; i < count; i++) {
            nObject element1 = values1[i];
            nObject element2 = values2[i];
            if (element1 == element2) {
                continue;
            }
            if (element1 == Nullable.Null || element2 == Nullable.Null) {
                return false;
            }

            TypeConstant type1 = (i < types1.length ? types1[i] : object).removeNullable();
            TypeConstant type2 = (i < types2.length ? types2[i] : object).removeNullable();
            if (!type1.equals(type2) ||
                    !nType.$ensureType(ctx, type1).equals$p(ctx, element1, element2)) {
                return false;
            }
        }
        return true;
    }
}
