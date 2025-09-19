package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation for "ecstasy.Boolean".
 */
public class Boolean
        extends xEnum {
    private Boolean(long ordinal, String name) {
        super(null);
        $ordinal = ordinal;
        $name    = name;
    }

    public final String $name;
    public final long $ordinal;

    public static Boolean False = new Boolean(0, String.of(null, "False"));
    public static Boolean True  = new Boolean(0, String.of(null, "True"));

    public Enumeration enumeration$get() {
        return Boolean$Enumeration.$INSTANCE;
    }

    @Override
    public String name$get(Ctx $ctx) {
        return $name;
    }

    @Override
    public long ordinal$get$p(Ctx $ctx) {
        return $ordinal;
    }

    public static Boolean $box(boolean value) {
        return value ? True : False;
    }

    @Override
    public java.lang.String toString() {
        return $name.toString();
    }
}
