package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native shell for `ecstasy.SharedContext`.
 */
public class SharedContext extends xConst {
    public SharedContext(Ctx ctx) {
        super(ctx);
    }

    static public class Token extends xConst {
        public Token(Ctx ctx, SharedContext $outer) {
            super(ctx);
            this.$outer = $outer;
        }

        private final SharedContext $outer;
    }
}
