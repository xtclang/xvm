package org.xtclang.ecstasy;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * Native representation of `ecstasy.Appender`.
 */
@JitGenerated
public interface Appender extends Object {
    /**
     * The native implementation of the Appender method
     * {@code Appender add(Element v);}.
     */
    @JitName
    Appender add(Ctx ctx, Object v);
}
