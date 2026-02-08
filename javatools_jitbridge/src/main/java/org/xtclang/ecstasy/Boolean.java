package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.javajit.Ctx;

/**
 * Native representation for "ecstasy.Boolean".
 */
public class Boolean
        extends nEnum {
    private Boolean(boolean value, long ordinal, String name) {
        super(null);
        $value   = value;
        $ordinal = ordinal;
        $name    = name;
    }

    public final boolean $value;
    public final long $ordinal;
    public final String $name;

    public static Boolean False = new False();
    public static Boolean True  = new True();

    @Override public TypeConstant $xvmType(Ctx ctx) {
        ConstantPool pool = $xvm().ecstasyPool;
        return $value ? pool.typeTrue() : pool.typeFalse();
    }

    public Enumeration enumeration$get(Ctx ctx) {
        return eBoolean.$INSTANCE;
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

    public static class False extends Boolean {
        public False() {
            super(false, 0, String.of(null, "False"));
        }
    }

    public static class True extends Boolean {
        public True() {
            super(true, 1, String.of(null, "True"));
        }
    }
    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return $name.toString();
    }
}
