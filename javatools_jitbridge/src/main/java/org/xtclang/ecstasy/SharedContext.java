package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native shell for `ecstasy.SharedContext`.
 */
public class SharedContext extends nConst {
    public SharedContext(Ctx ctx) {
        super(ctx);
    }

    static public class Token extends nConst {
        public Token(Ctx ctx, SharedContext $outer) {
            super(ctx);
            this.$outer = $outer;
        }

        private final SharedContext $outer;
    }
}
