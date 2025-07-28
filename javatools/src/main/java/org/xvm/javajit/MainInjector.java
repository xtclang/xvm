package org.xvm.javajit;

import java.util.HashMap;
import java.util.Map;

import java.util.function.Supplier;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xtclang._native.io.TerminalConsole;

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
            throw new RuntimeException("Unknown resource: " + res);
        }
        return resource;
    }

    public void addNativeResources()
            throws  ClassNotFoundException {
        NativeTypeSystem typeSystem = xvm.nativeTypeSystem;
        ConstantPool     pool       = xvm.ecstasyPool;
        TypeConstant     pureType   = pool.ensureEcstasyTypeConstant("io.Console");
        String           pureName   = typeSystem.ensureJitClassName(pureType);
        String           implName   = typeSystem.getBridgeClassName("_native.io.TerminalConsole");

        // load the pure type class
        Class pureClass = Class.forName(pureName, true, typeSystem.loader);
        Class implClass = Class.forName(implName, true, typeSystem.bridgeLoader);

        suppliers.put(new Resource(pureType, "console"), TerminalConsole::new);
    }
}
