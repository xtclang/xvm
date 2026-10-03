package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Native representation for "ecstasy.Ordered".
 */
public class Ordered
        extends nEnum {
    private Ordered(long ordinal, String name, String symbol) {
        super(null);
        $ordinal    = ordinal;
        $name       = name;
        this.symbol = symbol;
    }

    public final long   $ordinal;
    public final String $name;
    public final String symbol;

    /**
     * The native implementation of the reversed property getter.
     */
    public Ordered reversed$get(Ctx ctx) {
        return switch ((int) $ordinal) {
            case 0 -> Greater.$INSTANCE;
            case 1 -> Equal.$INSTANCE;
            case 2 -> Lesser.$INSTANCE;
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public String name$get(Ctx ctx) {
        return $name;
    }

    @Override
    public long ordinal$get$p(Ctx ctx) {
        return $ordinal;
    }

    public static class Lesser extends Ordered {
        private Lesser() {
            super(0, String.of(null, "Lesser"),  String.of(null, "<"));
        }
        public static Lesser $INSTANCE = new Lesser();
    }

    public static class Equal extends Ordered {
        private Equal() {
            super(1, String.of(null, "Equal"),   String.of(null, "="));
        }
        public static Equal $INSTANCE = new Equal();
    }

    public static class Greater extends Ordered {
        private Greater() {
            super(2, String.of(null, "Greater"), String.of(null, ">"));
        }
        public static Greater $INSTANCE = new Greater();
    }

    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return $name.toString();
    }
}
