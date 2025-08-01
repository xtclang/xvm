package org.xtclang.ecstasy;

/**
 * Native representation for "ecstasy.Boolean".
 */
public class Boolean
        extends xEnum {
    private Boolean(boolean value) {
        super(-1);
        $value = value;
    }

    public final boolean $value;

    public static Boolean True  = new Boolean(true);
    public static Boolean False = new Boolean(false);

    public static Boolean $box(boolean value) {
        return value ? True : False;
    }
}
