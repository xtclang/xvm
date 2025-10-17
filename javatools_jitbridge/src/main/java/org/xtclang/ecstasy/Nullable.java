package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Container;
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

    @Override public xType $type() {
        Container container = Ctx.get().container;
        return (xType) container.typeSystem.pool().typeNull().ensureXType(container);
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
