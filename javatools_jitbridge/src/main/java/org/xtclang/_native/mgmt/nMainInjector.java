package org.xtclang._native.mgmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

import org.xtclang._native.io.TerminalConsole;

import org.xtclang._native.numbers.RTRandom;

import org.xtclang._native.temporal.LocalClock;
import org.xtclang._native.temporal.NanosTimer;

import org.xtclang.ecstasy.Exception;
import org.xtclang.ecstasy.Nullable;

import org.xtclang.ecstasy.text.String;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Injector;
import org.xvm.javajit.Xvm;

/**
 * The Injector used for "main" containers.
 */
@SuppressWarnings("rawtypes")
public class nMainInjector
        extends Injector {

    public nMainInjector(Xvm xvm, Map<java.lang.String, List<java.lang.String>> cliInjectables) {
        this.xvm         = xvm;
        this.cliInjector = new nCommandLineInjector(xvm, cliInjectables);
    }

    private final Xvm xvm;

    private final Map<Resource, Function> suppliers = new HashMap<>();

    /**
     * The helper class to produce injectables from the command line.
     */
    private final nCommandLineInjector cliInjector;

    /**
     * A {@link Function} that always returns {@link Nullable.Null}.
     */
    private static final Function NullSupplier = _ -> Nullable.Null.$INSTANCE;

    @Override
    public Function supplierOf(Resource res) {
        Function supplier = suppliers.get(res);
        if (supplier == null) {
            // no registered supplier, try CLI injectables
            supplier = cliInjector.getInjectable(res);
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
            throw Exception.$illegalArg(xvm.getCtx(), "Unknown resource: " + res);
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

        TypeConstant timerType = pool.ensureEcstasyTypeConstant("temporal.Timer");
        suppliers.put(new Resource(timerType, "timer"), NanosTimer::$create);
    }
}
