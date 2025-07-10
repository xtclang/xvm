package org.xvm.javajit;

import java.util.HashMap;
import java.util.Map;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.intrinsic.xException;

/**
 * The Injector used for "main" containers.
 */
public class MainInjector
        extends Injector {

    public MainInjector(Xvm xvm) {
        this.xvm = xvm;
    }

    private final Xvm xvm;

    private final Map<Resource, Supplier> suppliers = new HashMap<>();

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

    public void addNativeResources()
            throws  ClassNotFoundException {
        TypeSystem   typeSystem = xvm.nativeTypeSystem;
        ConstantPool pool       = xvm.ecstasyPool;
        ClassLoader  loader     = xvm.ecstasyLoader;
        TypeConstant pureType   = pool.ensureEcstasyTypeConstant("io.Console");
        String       pureName   = typeSystem.ensureJitClassName(pureType);
        String       implName   = "org.xvm.javajit.bridge.TerminalConsole";

        // load the pure type class
        Class pureClass = Class.forName(pureName, true, loader);
        Class implClass = Class.forName(implName, true, loader);

        // suppliers.put(type, new Resource(, "console"), TerminalConsole::new);
    }
}
