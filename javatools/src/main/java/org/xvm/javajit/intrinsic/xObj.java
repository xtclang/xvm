package org.xvm.javajit.intrinsic;


public abstract class xObj {
    public static Ctx $ctx() {
        return Ctx.get();
    }

    /**
     * A bunch of "header bits" used to encode information about this object, including:
     *
     * * Container ID
     * * immutability flag
     * * construction state
     */
    public long $meta;

    public abstract xType $type();
    public abstract boolean $isImmut();
    public abstract void $makeImmut();
    public abstract boolean $isA(xType t);
    public abstract xContainer $container();

    // ----- static helpers for when references may be non-xObj classes ----------------------------

    public static Object $makeImmut(Object ref) {
        if (ref instanceof xObj xRef) {
            xRef.$makeImmut();
            return xRef;
        }

        // handle all of the intrinsic types
        // TODO

        // not an Ecstasy ref
        throw new IllegalStateException();
    }

    public static boolean $isA(Object ref, xType t) {
        if (ref instanceof xObj xRef) {
            return xRef.$isA(t);
        }

        // handle all of the intrinsic types
        if (ref instanceof long[] longs) {
            long meta = longs[0]; // encodes type info, size, "Mutability" enum value, etc.
            // TODO
        }
        // TODO

        return false;
    }

    public static boolean $isImmut(Object ref) {
        if (ref instanceof xObj xRef) {
            return xRef.$isImmut();
        }

        // handle all of the **mutable** intrinsic types (note: might not be any)
        // TODO

        return true;
    }

    public static xType $type(Object ref) {
        if (ref instanceof xObj xRef) {
            return xRef.$type();
        }

        // handle all of the intrinsic types
        // TODO

        // not an Ecstasy ref
        throw new IllegalStateException();
    }
}
