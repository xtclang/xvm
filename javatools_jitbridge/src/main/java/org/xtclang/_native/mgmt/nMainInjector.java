package org.xtclang._native.mgmt;

import java.io.PrintWriter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.xtclang._native.io.TerminalConsole;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.text.String;

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
        this(xvm, null, Map.of());
    }

    /**
     * Create the resources for one application container. The output writer remains caller-owned.
     */
    public nMainInjector(Xvm xvm, PrintWriter console, Map<java.lang.String, List<java.lang.String>> injections) {
        this.xvm = xvm;
        this.console = console;
        this.injections = Map.copyOf(injections);
    }

    private final Xvm xvm;
    private final PrintWriter console;
    private final Map<java.lang.String, List<java.lang.String>> injections;

    private final Map<Resource, Function> suppliers = new HashMap<>();

    @Override
    public Function supplierOf(Resource res) {
        var values = injections.get(res.name());
        if (values != null && !values.isEmpty()) {
            var type = res.type().removeNullable();
            var pool = xvm.ecstasyPool;
            if (type.equals(pool.typeString())) {
                return _ -> String.of(xvm.getCtx(), values.getLast());
            }
            if (type.equals(pool.ensureArrayType(pool.typeString())) ||
                    type.equals(pool.ensureParameterizedTypeConstant(pool.typeList(), pool.typeString()))) {
                return _ -> ArrayᐸObjectᐳ.$makeStringArray(xvm.getCtx(), values.stream()
                        .map(value -> String.of(xvm.getCtx(), value)).toArray(String[]::new));
            }
        }
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

        Class temporaryLoadStringClassToPrimeConstTypeInfo = String.class;

        suppliers.put(new Resource(pureType, "console"), _ -> new TerminalConsole(console));
    }
}
