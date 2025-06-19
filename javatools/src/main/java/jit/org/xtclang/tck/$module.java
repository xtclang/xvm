package jit.org.xtclang.tck;

import org.xvm.asm.constants.ModuleConstant;

import org.xvm.javajit.Injector;
import org.xvm.javajit.intrinsic.xConst;
import org.xvm.javajit.intrinsic.xStr;
import org.xvm.javajit.intrinsic.xType;

/**
 * AUTOGEN: module tck.xtclang.org
 */
public class $module extends xConst {
    public $module(long containerId, ModuleConstant moduleId) {
        super(containerId);
        $mc = moduleId;
    }

    public final ModuleConstant $mc;

    final private org.xvm.javajit.Injector.Resource $io_console =
        new Injector.Resource($xvm().ecstasyPool.ensureEcstasyTypeConstant("io.Console"), "console");

    public void run(org.xvm.javajit.intrinsic.Ctx $ctx) {
        // @Inject Console console;
        jit.org.xtclang.ecstasy.io.Console console = $ctx.container.injector.valueOf($io_console);

        // console.print("Hello");
        console.print($ctx, new xStr($ctx.container.id, "Hello"), false, false);

        // Module tck = this:module;
        // org.xvm.javajit.intrinsic.xModule
    }

    @Override
    public xType $type() {
        return t$module.$instance;
    }
}
