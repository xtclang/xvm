package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation for "ecstasy.Boolean".
 */
public class Boolean
        extends xEnum {
    private Boolean(boolean value, long ordinal, String name) {
        super(null);
        $value   = value;
        $ordinal = ordinal;
        $name    = name;
    }

    public final boolean $value;
    public final long $ordinal;
    public final String $name;

    public static Boolean False = new Boolean(false, 0, String.of(null, "False"));
    public static Boolean True  = new Boolean(true, 1, String.of(null, "True"));

    public Enumeration enumeration$get(Ctx ctx) {
        return Boolean$Enumeration.$INSTANCE;
    }

    @Override
    public String name$get(Ctx ctx) {
        return $name;
    }

    @Override
    public long ordinal$get$p(Ctx ctx) {
        return $ordinal;
    }

    public static Boolean $box(boolean value) {
        return value ? True : False;
    }

    public boolean and$p(Ctx ctx, boolean that) {
        return this.$value & that;
    }

    public boolean or$p(Ctx ctx, boolean that) {
        return this.$value | that;
    }

    public boolean xor$p(Ctx ctx, boolean that) {
        return this.$value ^ that;
    }

    public boolean not$p(Ctx ctx) {
        return !this.$value;
    }

    public int toBit$p(Ctx ctx) {
        return this.$value ? 1 : 0;
    }

    public int toByte$p(Ctx ctx) {
        return this.$value ? (byte) 1 : (byte) 0;
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return $name.toString();
    }
}
