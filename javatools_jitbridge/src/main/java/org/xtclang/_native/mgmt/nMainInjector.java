package org.xtclang._native.mgmt;

import java.util.HashMap;
import java.util.Map;

import java.util.function.Function;

import org.xtclang._native.io.TerminalConsole;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Injector;
import org.xvm.javajit.Xvm;

/**
 * The Injector used for "main" containers.
 */
public class nMainInjector
        extends Injector {

    public nMainInjector(Xvm xvm) {
        this.xvm = xvm;
    }

    private final Xvm xvm;

    private final Map<Resource, Function> suppliers = new HashMap<>();

    @Override
    public Function supplierOf(Resource res) {
        return suppliers.get(res);
    }

    @Override
    public Object valueOf(Resource res, Object opts) {
        Object resource = super.valueOf(res, opts);
        if (resource == null) {
            throw new RuntimeException("Unknown resource: " + res);
        }
        return resource;
    }

    /**
     * This method is called by the JitConnector via reflection.
     */
    public void addNativeResources() {
        ConstantPool pool     = xvm.ecstasyPool;
        TypeConstant pureType = pool.ensureEcstasyTypeConstant("io.Console");

        suppliers.put(new Resource(pureType, "console"), TerminalConsole::$create);
    }
}
