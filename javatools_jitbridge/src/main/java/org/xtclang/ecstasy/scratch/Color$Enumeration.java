package org.xtclang.ecstasy.scratch;

import org.xtclang.ecstasy.reflect.Enumeration;
import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * REMOVE ME
 */
public class Color$Enumeration extends Enumeration {
    private Color$Enumeration(Ctx ctx) {
        super(ctx, ctx.container.typeSystem.pool().ensureEcstasyTypeConstant("scratch.Color"));
    }

    public static final Color$Enumeration $INSTANCE = new Color$Enumeration(Ctx.get());

    public static final String[] $names = new String[] {Red.$name, Green.$name};

    public static final Color[] $values = new Color[] {Red.$INSTANCE, Green.$INSTANCE};

    public long count$get$p() {
        return 2;
    }
    public String[] names$get() {
        return $names;
    }
    public Color[] values$get() {
        return $values;
    }
}
