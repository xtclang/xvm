package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

/**
 * Native implementation for `ecstasy.Range`.
 *
 * Supports the primitive form of the `Range<Int>` type.
 */
public abstract class Range extends nConst {

    public Range(Ctx ctx) {
        super(ctx);
    }

    // ----- Range API -----------------------------------------------------------------------------

    // TODO what parts of this can be / should be implemented natively?

}
