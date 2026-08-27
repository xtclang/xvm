package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.ModuleLoader;

/**
 * Native Enumeration<Boolean>.
 */
public class eBoolean extends Enumeration {
    private eBoolean() {
        super(null, ((ModuleLoader) eBoolean.class.getClassLoader()).getCtx().
                pool().typeBoolean());
    }

    public static final eBoolean $INSTANCE = new eBoolean();

    public static final String[]  $names  = new String[] {Boolean.False.$name, Boolean.True.$name};
    public static final Boolean[] $values = new Boolean[] {Boolean.False, Boolean.True};

    @Override
    public long count$get$p() {
        return 2;
    }

    // TODO this should be: public ArrayᐸObjectᐳ values$get() ???
    //      or even: public ArrayᐸBooleanᐳ values$get() ???
    @Override
    public Boolean[] values$get() {
        return $values;
    }

    // TODO this should be: public ArrayᐸObjectᐳ names$get() ???
    @Override
    public String[] names$get() {
        return $names;
    }
}
