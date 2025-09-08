package org.xtclang.ecstasy;

/**
 * Native representation for "ecstasy.Boolean".
 */
public class Boolean
        extends xEnum {
    private Boolean(boolean value) {
        super(null);
        $value = value;
    }

    public final boolean $value;

    public static Boolean True  = new Boolean(true);
    public static Boolean False = new Boolean(false);

    public static Boolean $box(boolean value) {
        return value ? True : False;
    }

    @Override
    public String toString() {
        return $value ? "True" : "False";
    }
}
