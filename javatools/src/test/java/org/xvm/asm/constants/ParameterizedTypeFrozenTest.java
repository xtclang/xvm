package org.xvm.asm.constants;


import org.junit.jupiter.api.Test;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.FileStructure;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage-3 Family B contract (array-exposure audit): a parameterized type's parameter storage is
 * frozen. The old shape returned the interned raw array from getParamTypesArray() and a LIVE
 * Arrays.asList view from getParamTypes() - a set()-writable window into a constant shared by
 * every container (red on master) - and the varargs ensure* factory adopted caller arrays, so
 * one raw array could back two interned constants with nothing but clone conventions keeping
 * writes out. Frozen wrappers make the sharing structural.
 */
public class ParameterizedTypeFrozenTest {
    @Test
    public void parameterizedTypeStorageIsFrozen() {
        var file = new FileStructure("frozenptc");
        var pool = file.getConstantPool();

        var typeParam = pool.typeString();
        var typeList  = pool.ensureParameterizedTypeConstant(pool.typeList(), typeParam);

        // the List view is an immutable snapshot, not a live window into the constant
        assertThrows(UnsupportedOperationException.class,
                () -> typeList.getParamTypes().set(0, pool.typeBoolean()),
                "getParamTypes() must never be a writable view of interned storage");

        // a copy() is the caller's own array; mutating it cannot touch the constant
        TypeConstant[] aCopy = typeList.getParamTypesArray().copy();
        aCopy[0] = pool.typeBoolean();
        assertSame(typeParam, typeList.getParamTypesArray().get(0),
                "mutating a copy must not affect the interned type");

        // cross-constant adoption shares the frozen wrapper - the safe form of the old
        // raw-array aliasing between interned constants
        var typeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                typeList.getParamTypesArray());
        assertSame(typeList.getParamTypesArray().unsafeArray(),
                typeArray.getParamTypesArray().unsafeArray(),
                "wrapper sharing keeps zero-copy interning without a mutation channel");
    }
}
