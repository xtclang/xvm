package jit.org.xtclang.test0;

/**
 * AUTOGEN: module tck.xtclang.org
 */
public class $module extends org.xvm.javajit.intrinsic.xModule {
    public $module(long containerId) {
        super(containerId, org.xvm.javajit.Ctx.get().container.typeSystem.mainModule());
    }

    @Override
    public org.xvm.javajit.intrinsic.xType $type() {
        return t$module.$instance;
    }

    // constants
    public static final org.xvm.javajit.intrinsic.xStr c$0;
    static {
        org.xvm.javajit.Ctx ctx = org.xvm.javajit.Ctx.get();
        org.xvm.asm.ConstantPool pool = ctx.container.typeSystem.pool();

        c$0 = pool.ensureStringConstant("hello").ensureValue();
    }

    // injections
    private static final org.xvm.javajit.Injector.Resource $io_console =
        new org.xvm.javajit.Injector.Resource(
            $xvm().ecstasyPool.ensureEcstasyTypeConstant("io.Console"), "console");

    // methods
    public void run(org.xvm.javajit.Ctx $ctx) {
        // @Inject Console console;
        jit.org.xtclang.ecstasy.io.Console console = $ctx.container.injector.valueOf($io_console);

        // console.print("Hello");
        console.print($ctx, c$0, false, false);

        // Int i1 = call1(0);
        long i1 = call1(0, true, 0);

        // i1 = call1(0, 5);
        i1 = call1(0, false, 5);

        // (i1, Int i2) = call2(0);
        i1 = call2(0);
        long i2 = $ctx().i1;

        // if (Int i3 := call3(0)) {
        if (call3(0)) {
            long i3 = $ctx().i1;
            i3++;
        }
    }

    // Int call1(Int i, Int j = 2)
    public long call1(long i, boolean j$default, long j) {
        if (j$default) {
            j = 2;
        }
        return i + j;
    }

    public long call1$w(org.xvm.javajit.intrinsic.xInt64 i,
                        boolean j$default, org.xvm.javajit.intrinsic.xInt64 j) {
        return call1(i.$value, j$default, j.$value);
    }

    // (Int, Int) call2(Int i)
    public long call2(long i) {
        // return i++, i;
        long $r = i++;
        $ctx().i1 = i;
        return $r;
    }

    // conditional Int call3(Int i)
    public boolean call3(long i) {
        // return True, i;
        $ctx().i1 = i;
        return true;
    }
}
