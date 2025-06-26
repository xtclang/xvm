package jit.org.xtclang.test0;

/**
 * AUTOGEN: Type<tck.xtclang.org>
 */
public class t$module extends org.xvm.javajit.intrinsic.xType {
    public t$module(long containerId, org.xvm.asm.constants.TypeConstant type) {
        super(containerId, type);

        $instance = this;
    }

    @Override
    public org.xvm.javajit.intrinsic.xObj alloc(org.xvm.javajit.Ctx ctx) {
        ctx.debit($size);
        return null; // ctx.container.ensureSingleton($type);
    }

    public static org.xvm.javajit.intrinsic.xType $instance;

    public static int $size = 8;
}
