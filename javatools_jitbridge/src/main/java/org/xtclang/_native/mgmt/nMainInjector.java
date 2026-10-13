package org.xtclang._native.mgmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.xtclang._native.io.TerminalConsole;

import org.xtclang._native.numbers.RTRandom;

import org.xtclang._native.temporal.LocalClock;

import org.xtclang.ecstasy.Nullable;

import org.xtclang.ecstasy.collections.ArrayᐸObjectᐳ;

import org.xtclang.ecstasy.reflect.Enumeration;

import org.xtclang.ecstasy.text.Destringable;
import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;
import org.xvm.javajit.Injector;
import org.xvm.javajit.Xvm;

/**
 * The Injector used for "main" containers.
 */
@SuppressWarnings("rawtypes")
public class nMainInjector
        extends Injector {

    public nMainInjector(Xvm xvm, Map<java.lang.String, List<java.lang.String>> injections) {
        this.xvm        = xvm;
        this.injections = injections;

        ConstantPool pool = xvm.ecstasyPool;
        destringableType = pool.ensureEcstasyTypeConstant("text.Destringable");
        stringType       = pool.typeString();
        stringArrayType  = pool.ensureParameterizedTypeConstant(pool.typeList(), stringType);
    }

    private final Xvm xvm;

    private final Map<Resource, Function> suppliers = new HashMap<>();

    /**
     * The map of injections, usually parsed from the command line.
     */
    private final Map<java.lang.String, List<java.lang.String>> injections;

    /**
     * The Destringable type constant.
     */
    private final TypeConstant destringableType;

    /**
     * The String type constant.
     */
    private final TypeConstant stringType;

    /**
     * The String array type constant.
     */
    private final TypeConstant stringArrayType;

    /**
     * A {@link Function} that always returns {@link Nullable.Null}.
     */
    private static final Function NullSupplier = _ -> Nullable.Null.$INSTANCE;

    @Override
    public Function supplierOf(Resource res) {
        Function supplier = suppliers.get(res);
        if (supplier == null) {
            // no registered supplier, try injectables
            supplier = getInjectable(res);
        }
        if (supplier == null && res.type().isNullable()) {
            // no registered supplier or injectable, but type is nullable so inject Null
            supplier = NullSupplier;
        }
        return supplier;
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
        ConstantPool pool = xvm.ecstasyPool;

        TypeConstant consoleType = pool.ensureEcstasyTypeConstant("io.Console");
        java.lang.Class temporaryLoadStringClassToPrimeConstTypeInfo = String.class;
        suppliers.put(new Resource(consoleType, "console"), TerminalConsole::$create);

        TypeConstant randomType = pool.ensureEcstasyTypeConstant("numbers.Random");
        suppliers.put(new Resource(randomType, "rnd"),    RTRandom::$create);
        suppliers.put(new Resource(randomType, "random"), RTRandom::$create);

        TypeConstant clockType = pool.ensureEcstasyTypeConstant("temporal.Clock");
        suppliers.put(new Resource(clockType, "clock"),      LocalClock::$createUtcClock);
        suppliers.put(new Resource(clockType, "utcClock"),   LocalClock::$createUtcClock);
        suppliers.put(new Resource(clockType, "localClock"), LocalClock::$createLocalClock);
    }

    /**
     * @return the a {@link Function} that will supply the required type from a command line
     *         injection key and value, or return {@code null} if there is no injection value for
     *         the specified name.
     */
    private Function getInjectable(Resource res) {
        java.lang.String name = res.name();
        TypeConstant     type = res.type();

        // check the custom injections first
        List<java.lang.String> values = injections.get(name);
        if (values == null) {
            // there is no matching custom injection
            return null;
        }

        if (type.isNullable()) {
            // strip nullable from the required type
            type = type.removeNullable();
        }

        Ctx ctx = xvm.getCtx();
        if (!values.isEmpty()) {
            if (type.equals(stringType)) {
                // require a single String, return the last String element
                return _ -> String.of(ctx, values.getLast());
            }
            if (type.equals(stringArrayType)) {
                // require String[], return the whole List<String> as an array
                String[] allValues = values.stream()
                                           .map(s -> String.of(ctx, s))
                                           .toArray(String[]::new);
                return _ -> ArrayᐸObjectᐳ.$makeStringArray(ctx, allValues);
            }
            if (type.isEnum()) {
                // require Destringable, use the last String element as the enum value name
                TypeConstant enumType = type;
                return _ -> Enumeration.$getEnumValue(ctx, enumType, values.getLast());
            }
            if (type.isA(destringableType)) {
                // require Destringable, use the last String element as the Destringable value
                TypeConstant requiredType = type;
                return _ ->
                        Destringable.$createInstance(xvm.getCtx(), requiredType, values.getLast());
            }
        }
        return null;
    }
}
