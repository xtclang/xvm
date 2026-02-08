package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Ecstasy Nullable.
 */
public class Nullable
        extends nEnum {

    private Nullable() {
        super(null);
    }

    public static final String $name = String.of(null, "Null");

    public static final Nullable Null = new Null();

    @Override public TypeConstant $xvmType(Ctx ctx) {
        return $xvm().ecstasyPool.typeNull();
    }

    public Enumeration enumeration$get(Ctx ctx) {
        return eNullable.$INSTANCE;
    }

    public String name$get(Ctx ctx) {
        return $name;
    }
    public long ordinal$get$p(Ctx ctx) {
        return 0;
    }

    public static class Null extends Nullable {}
}
