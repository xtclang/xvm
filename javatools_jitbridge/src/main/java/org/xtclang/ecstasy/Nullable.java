package org.xtclang.ecstasy;

import org.xtclang.ecstasy.text.String;

import org.xvm.javajit.Ctx;

/**
 * Ecstasy Nullable.
 */
public abstract class Nullable
        extends nEnum {

    private Nullable() {
        super(null);
    }

    public static class Null extends Nullable {

        public static final Null $INSTANCE = new Null();

        public static final String $name = String.of(null, "Null");

        @Override
        public String name$get(Ctx ctx) {
            return $name;
        }

        @Override
        public long ordinal$get$p(Ctx ctx) {
            return 0;
        }
    }
}
