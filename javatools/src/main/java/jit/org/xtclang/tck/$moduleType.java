package jit.org.xtclang.tck;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.intrinsic.Ctx;
import org.xvm.javajit.intrinsic.xObj;
import org.xvm.javajit.intrinsic.xType;

/**
 * Type<tck.xtclang.org>
 */
public class $moduleType extends xType {
    public $moduleType(long containerId, TypeConstant type) {
        super(containerId, type);
    }

    @Override
    public xObj alloc(Ctx ctx) {
        ctx.debit($size);
        return null; // $container().ensureSingleton($type);
    }

    public static int $size = 8;
}
