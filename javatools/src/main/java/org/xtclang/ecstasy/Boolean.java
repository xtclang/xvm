package org.xtclang.ecstasy;


public class Boolean
        extends xEnum {
    public Boolean(boolean value) {
        super(-1);
        $value = value;
    }

    public final boolean $value;
}
