package org.xvm.javajit;

import java.util.HashMap;
import java.util.Map;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;

import org.xvm.javajit.intrinsic.io.TerminalConsole;
import org.xvm.javajit.intrinsic.xException;

/**
 * The Injector used for "main" containers.
 */
public class MainInjector
        extends Injector {

    public MainInjector(Xvm xvm) {
        this.xvm = xvm;

        Map<Resource, Supplier> suppliers = new HashMap<>();

        ConstantPool pool = xvm.ecstasyPool;
        suppliers.put(
            new Resource(pool.ensureEcstasyTypeConstant("io.Console"), "console"), TerminalConsole::new);

        this.suppliers = suppliers;
    }

    private final Xvm xvm;

    private final Map<Resource, Supplier> suppliers;

    @Override
    public <T> Supplier<T> supplierOf(Resource res) {
        return suppliers.get(res);
    }

    @Override
    public <T> T valueOf(Resource res) {
        T resource = super.valueOf(res);
        if (resource == null) {
            throw new xException("Unknown resource: " + res);
        }
        return resource;
    }
}
