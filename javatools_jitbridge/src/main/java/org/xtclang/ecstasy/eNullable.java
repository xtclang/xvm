package org.xtclang.ecstasy;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.ModuleLoader;

/**
 * Native Enumeration<Nullable>.
 */
public class eNullable extends Enumeration {
    private eNullable() {
        super(null, ((ModuleLoader) eNullable.class.getClassLoader()).getCtx().
                pool().typeNullable());
    }

    public static final eNullable $INSTANCE = new eNullable();

    public static final String[]   $names  = new String[] {Nullable.$name};
    public static final Nullable[] $values = new Nullable[] {Nullable.Null};

    @Override
    public long count$get$p() {
        return 1;
    }

    @Override
    public String[] names$get() {
        return $names;
    }

    @Override
    public Nullable[] values$get() {
        return $values;
    }
}
