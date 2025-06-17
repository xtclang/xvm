package jit.org.xtclang.tck;

import org.xvm.javajit.intrinsic.xObj;
import org.xvm.javajit.intrinsic.xType;

/**
 * module tck.xtclang.org
 */
public class $module extends xObj {
    public $module(long containerId) {
        super(containerId);
    }

    public void run() {
        // @Inject Console console;
        // TODO

        // Module tck = this:module;
        // org.xvm.javajit.intrinsic.xModule

        System.err.println("run!");

    }

    @Override
    public xType $type() {
        return null;
    }

    @Override
    public boolean $isImmut() {
        return true;
    }

    @Override
    public void $makeImmut() {}

    @Override
    public boolean $isA(xType t) {
        return false;
    }
}
