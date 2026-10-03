package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation for "ecstasy.Boolean".
 */
public abstract class Boolean
        extends nEnum {

    public static class False extends Boolean {
        public static final False $INSTANCE = new False();

        public False() {
            super(false, 0, String.of(null, "False"));
        }
    }

    public static class True extends Boolean {
        public static final True $INSTANCE = new True();

        public True() {
            super(true, 1, String.of(null, "True"));
        }
    }

    private Boolean(boolean value, long ordinal, String name) {
        super(null);
        $value   = value;
        $ordinal = ordinal;
        $name    = name;
    }

    public final boolean $value;
    public final long    $ordinal;
    public final String  $name;

    public static Boolean $box(boolean value) {
        return value ? True.$INSTANCE : False.$INSTANCE;
    }

    @Override public String name$get(Ctx ctx) {
        return $name;
    }

    public static String name$get$p(boolean thi$, Ctx ctx) {
        return (thi$ ? True.$INSTANCE : False.$INSTANCE).$name;
    }

    @Override public long ordinal$get$p(Ctx ctx) {
        return $ordinal;
    }

    public static long ordinal$get$p(boolean thi$, Ctx ctx) {
        return thi$ ? 1 : 0;
    }

    public static boolean and$p(boolean thi$, Ctx ctx, boolean that) {
        return thi$  & that;
    }

    public static boolean or$p(boolean thi$, Ctx ctx, boolean that) {
        return thi$;
    }

    public static boolean xor$p(boolean thi$, Ctx ctx, boolean that) {
        return thi$ ^ that;
    }

    public static boolean not$p(boolean thi$, Ctx ctx) {
        return !thi$;
    }

    public static int toBit$p(boolean thi$, Ctx ctx) {
        return thi$ ? 1 : 0;
    }

    public static int toByte$p(boolean thi$, Ctx ctx) {
        return thi$ ? (byte) 1 : (byte) 0;
    }

    public static String toString$p(boolean thi$, Ctx ctx) {
        return name$get$p(thi$, ctx);
    }

    public static long estimateStringLength$p(boolean thi$, Ctx ctx) {
        return name$get$p(thi$, ctx).size$get$p(ctx);
    }

    public static AppenderᐸCharᐳ appendTo$p(boolean thi$, Ctx ctx, AppenderᐸCharᐳ appender) {
        return name$get$p(thi$, ctx).appendTo(ctx, appender);
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override public java.lang.String toString() {
        return $name.toString();
    }
}
