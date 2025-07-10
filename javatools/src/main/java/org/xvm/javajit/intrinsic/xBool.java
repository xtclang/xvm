package org.xvm.javajit.intrinsic;


public class xBool
        extends xEnum {
    public xBool(boolean value) {
        super(-1);
        $value = value;
    }

    public final boolean $value;
}
