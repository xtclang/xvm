package org.xtclang.ecstasy;

import org.xvm.javajit.Container;
import org.xvm.javajit.Ctx;
import org.xvm.javajit.Xvm;

public abstract class xObj implements Object {
    public xObj(long containerId) {
        super();
        $meta = containerId;
    }

    /**
     * (Helper)
     *
     * @return the current context object
     */
    public static Ctx $ctx() {
        return Ctx.get();
    }

    /**
     * (Helper)
     *
     * @return the XVM that this object exists within
     */
    public static Xvm $xvm() {
        return $ctx().xvm;
    }

    /**
     * A bunch of "header bits" used to encode information about this object, including:
     *
     * * Container ID
     * * immutability flag
     * * construction state
     * * some bits available to native subclasses
     */
    public long $meta;

    /**
     * (Helper)
     *
     * @return the container that "pays for" this object
     */
    public Container $owner() {
        return $xvm().getContainer($ownerId());
    }

    /**
     * @return container ID that "pays for" this object
     */
    public long $ownerId() {
        return $meta; // TODO: ($meta & ID_MASK)
    }

    public abstract xType $type();
    public abstract boolean $isImmut();

    public void $makeImmut() {
        if (!$isImmut()) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * This should be overridden by duck-type wrappers.
     */
    public boolean $isA(xType t) {
        return $type().$type.isA(t.$type);
    }

    // ----- static helpers for when references may be non-xObj classes ----------------------------

    public static java.lang.Object $makeImmut(java.lang.Object ref) {
        if (ref instanceof xObj xRef) {
            xRef.$makeImmut();
            return xRef;
        }

        // handle all of the intrinsic types
        // TODO

        // not an Ecstasy ref
        throw new IllegalStateException();
    }

    public static boolean $isA(java.lang.Object ref, xType t) {
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

    public static boolean $isImmut(java.lang.Object ref) {
        if (ref instanceof xObj xRef) {
            return xRef.$isImmut();
        }

        // handle all of the **mutable** intrinsic types (note: might not be any)
        // TODO

        return true;
    }

    public static xType $type(java.lang.Object ref) {
        if (ref instanceof xObj xRef) {
            return xRef.$type();
        }

        // handle all of the intrinsic types
        // TODO

        // not an Ecstasy ref
        throw new IllegalStateException();
    }
}
