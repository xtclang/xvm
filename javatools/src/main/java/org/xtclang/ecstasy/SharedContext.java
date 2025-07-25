package org.xtclang.ecstasy;

/**
 * Native shell for `ecstasy.SharedContext`.
 */
public class SharedContext extends xConst {
    public SharedContext(long containerId) {
        super(containerId);
    }

    static public class Token extends xConst {
        public Token(long containerId, SharedContext $outer) {
            super(containerId);
            this.$outer = $outer;
        }

        private final SharedContext $outer;
    }
}
