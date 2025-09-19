package org.xtclang.ecstasy.scratch;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.xEnum;

import org.xvm.javajit.Ctx;

/**
 * REMOVE ME
 */
public abstract class Color extends xEnum {
    protected Color(Ctx ctx) {
        super(ctx);
    }

    @Override
    public Enumeration enumeration$get() {
        return Color$Enumeration.$INSTANCE;
    }
}
