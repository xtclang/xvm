package org.xtclang.ecstasy.scratch;

import org.xtclang.ecstasy.text.String;
import org.xvm.javajit.Ctx;

/**
 * REMOVE ME
 */
public class Green extends Color {
    private Green(Ctx ctx) {
        super(ctx);
    }

    public static final Green $INSTANCE = new Green(Ctx.get());

    public static final String $name = String.of(null, "Green");

    public long ordinal$get$p(Ctx $ctx) {
        return 1;
    }

    public String name$get(Ctx $ctx) {
        return $name;
    }
}
