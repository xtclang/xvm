package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Ecstasy Nullable.
 */
public class Nullable
        extends xEnum {

    private Nullable() {
        super(null);
    }

    public static final String $name = String.of(null, "Null");

    public static final Nullable Null = new Nullable();

    @Override public TypeConstant $xvmType(Ctx ctx) {
        return $xvm().ecstasyPool.typeNull();
    }

    public Enumeration enumeration$get(Ctx ctx) {
        return Nullable$Enumeration.$INSTANCE;
    }

    public String name$get(Ctx ctx) {
        return $name;
    }
    public long ordinal$get$p(Ctx ctx) {
        return 0;
    }
}
