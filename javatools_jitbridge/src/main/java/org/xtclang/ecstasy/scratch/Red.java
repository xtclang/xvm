package org.xtclang.ecstasy.scratch;

import org.xtclang.ecstasy.text.String;
import org.xvm.javajit.Ctx;

/**
 * REMOVE ME
 */
public class Red extends Color {
    private Red(Ctx ctx) {
        super(ctx);
    }

    public static final Red $INSTANCE = new Red(Ctx.get());

    public static final String $name = String.of(null, "Red");

    public long ordinal$get$p(Ctx $ctx) {
        return 0;
    }

    public String name$get(Ctx $ctx) {
        return $name;
    }
}
