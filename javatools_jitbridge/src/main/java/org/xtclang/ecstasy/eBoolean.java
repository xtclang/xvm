package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

/**
 * Native Enumeration<Boolean>.
 */
public class eBoolean extends Enumeration {
    private eBoolean() {
        super(null, $ctx().container.typeSystem.pool().typeBoolean());
    }

    public static final eBoolean $INSTANCE = new eBoolean();

    public static final String[]  $names  = new String[] {Boolean.False.$name, Boolean.True.$name};
    public static final Boolean[] $values = new Boolean[] {Boolean.False, Boolean.True};

    @Override
    public long count$get$p() {
        return 2;
    }

    @Override
    public Boolean[] values$get() {
        return $values;
    }

    @Override
    public String[] names$get() {
        return $names;
    }
}
