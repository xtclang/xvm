package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

/**
 * Native representation for "ecstasy.Ordered".
 */
public class Ordered
        extends nEnum {
    private Ordered(long ordinal, String name, String symbol) {
        super(null);
        $ordinal = ordinal;
        $name    = name;
        $symbol  = symbol;
    }

    public final long   $ordinal;
    public final String $name;
    public final String $symbol;

    @Override public TypeConstant $xvmType(Ctx ctx) {
        ConstantPool pool = $xvm().ecstasyPool;
        return switch ((int) $ordinal) {
            case 0  -> pool.valLesser() .getType();
            case 1  -> pool.valEqual()  .getType();
            case 2  -> pool.valGreater().getType();
            default -> throw new IllegalStateException();
        };
    }

    public Enumeration enumeration$get(Ctx ctx) {
        return eBoolean.$INSTANCE;
    }

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
