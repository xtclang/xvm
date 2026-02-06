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
        $ordinal  = ordinal;
        $name     = name;
        $symbol   = symbol;
    }

    public final long    $ordinal;
    public final String  $name;
    public final String  $symbol;
    public       Ordered $reversed;

    public static Ordered Lesser;
    public static Ordered Equal;
    public static Ordered Greater;


    static {
        Lesser  = new Ordered(0, String.of(null, "Lesser"),  String.of(null, "<"));
        Equal   = new Ordered(1, String.of(null, "Equal"),   String.of(null, "="));
        Greater = new Ordered(2, String.of(null, "Greater"), String.of(null, ">"));

        Lesser .$reversed = Greater;
        Equal  .$reversed = Equal;
        Greater.$reversed = Equal;
    }

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

    @Override
    public String name$get(Ctx ctx) {
        return $name;
    }

    @Override
    public long ordinal$get$p(Ctx ctx) {
        return $ordinal;
    }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public java.lang.String toString() {
        return $name.toString();
    }
}
