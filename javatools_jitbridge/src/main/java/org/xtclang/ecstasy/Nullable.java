package org.xtclang.ecstasy;

/**
 * Ecstasy Nullable.
 */
public class Nullable
        extends xEnum {

    private Nullable() {
        super(-1);
    }

    @Override
    public String toString() {
        return "Null";
    }

    public static final Nullable Null = new Nullable();
}
