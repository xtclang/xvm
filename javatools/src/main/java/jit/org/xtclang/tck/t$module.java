package jit.org.xtclang.tck;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.intrinsic.Ctx;
import org.xvm.javajit.intrinsic.xObj;
import org.xvm.javajit.intrinsic.xType;

/**
 * AUTOGEN: Type<tck.xtclang.org>
 */
public class t$module extends xType {
    public t$module(long containerId, TypeConstant type) {
        super(containerId, type);

        $instance = this;
    }

    @Override
    public xObj alloc(Ctx ctx) {
        ctx.debit($size);
        return null; // ctx.container.ensureSingleton($type);
    }

    public static xType $instance;

    public static int $size = 8;
}
