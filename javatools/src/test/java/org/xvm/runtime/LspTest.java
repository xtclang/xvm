package org.xvm.runtime;


import java.io.File;
import java.io.PrintStream;

import java.lang.reflect.Field;

import java.nio.file.Files;
import java.nio.file.Path;

import org.xvm.api.Connector;
import org.xvm.api.InterpreterConnector;
import org.xvm.api.LspSupport;
import org.xvm.api.LspSupport.Control;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.util.Auto;

/**
 * Run from the xvm directory after {@code ./gradlew installDist}:
 * <pre>{@code
 * java -ea --add-modules jdk.httpserver \
 *     -p ./xdk/build/install/xdk/javatools \
 *     --patch-module javatools=./javatools/build/classes/java/test \
 *     -m javatools/org.xvm.runtime.LspTest \
 *     ./xdk/build/install/xdk/lib ./xdk/build/install/xdk/javatools
 * }</pre>
 */
public class LspTest {
    private static File dirLib;
    private static File dirJavatools;
    private static Path dirOut;

    /** Tee of System.out, installed before any xvm class loads so the native console is captured. */
    private static final java.io.ByteArrayOutputStream TEE = new java.io.ByteArrayOutputStream();

    static void main(String[] asArg) throws Exception {
        PrintStream realOut = System.out;
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override public void write(int b) { realOut.write(b); TEE.write(b); }
            @Override public void write(byte[] b, int off, int len) {
                realOut.write(b, off, len); TEE.write(b, off, len);
            }
        }, true));

        dirLib       = new File(asArg[0]);
        dirJavatools = new File(asArg[1]);
        Path dirWork = Files.createTempDirectory("xvm-543-repro");
        dirOut       = Files.createDirectory(dirWork.resolve("lib"));
        LspSupport.instance().configure(repo(), null);

        testCompile();
//        reproA1_failureIsNotAttributable();
//        reproA4_joinLatencyFloor();
//        reproB1_sharedPoolGrowsUnbounded();
    }

    private static void testCompile() {
        ErrorList       errs   = new ErrorList(25);
        ModuleStructure module = LspSupport.instance().compile(helloModule("Trivial"), repo(), errs);
        if (module == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of Trivial failed: " + errs.getErrors());
        }

        errs   = new ErrorList(25);
        module = LspSupport.instance().compile("module Broken { void run( }", repo(), errs);
        if (module != null || !errs.hasSeriousErrors()) {
            throw new IllegalStateException(
                    "invalid source did not report an error: " + errs.getErrors());
        }
    }

    // ----- A1: a failed run yields no failure object, only a bare int and a shared log line ------

    private static void reproA1_failureIsNotAttributable() throws Exception {
        compile("Crasher", """
                module Crasher {
                    void run() {
                        @Inject Console console;
                        console.print("Crasher.run about to throw");
                        throw new IllegalState("deliberate failure the host must learn about");
                    }
                }
                """);

        InterpreterConnector connector = new InterpreterConnector(repo());
        connector.loadModule("Crasher");
        connector.start(null);
        try (Auto ignore = ConstantPool.withPool(connector.getConstantPool())) {
            connector.invoke0(connector.findMethods("run").iterator().next());
        }
        int result = connector.join();

        // the run threw, yet join() returns only an int: no exception, no cause chain, no object
        // attributable to THIS request. The failure surfaced only as text on a shared stream
        // (printed above). In a host multiplexing requests, that log line cannot be linked back
        // to the invocation that failed.
        boolean sawExceptionOnSharedStream = TEE.toString().contains("Unhandled exception");
        System.out.println("REPRO A1: run() threw; join() returned only int=" + result
                + "; no failure object / cause / request attribution."
                + " (failure appeared only on the shared stream: " + sawExceptionOnSharedStream + ")");
    }

    // ----- A4: join() imposes a ~500ms latency floor ---------------------------------------------

    private static void reproA4_joinLatencyFloor() throws Exception {
        compile("Quick", helloModule("Quick"));
        InterpreterConnector connector = new InterpreterConnector(repo());
        connector.loadModule("Quick");
        connector.start(null);

        long t0 = System.nanoTime();
        try (Auto ignore = ConstantPool.withPool(connector.getConstantPool())) {
            connector.invoke0(connector.findMethods("run").iterator().next());
        }
        connector.join();
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("REPRO A4: a trivial run took " + ms + "ms wall-clock; join()'s 500ms "
                + "sleep-poll dominates (a per-cycle latency floor)"
                + (ms >= 400 ? "" : " [unexpectedly fast this run]"));
    }

    // ----- B1: the shared native pool grows for every novel type and never evicts ----------------

    private static void reproB1_sharedPoolGrowsUnbounded() throws Exception {
        LspSupport support = LspSupport.instance();
        StringBuilder trace = new StringBuilder();
        int prev  = support.getConstantPool().size();
        trace.append(prev);
        boolean everGrew = false, everShrank = false;

        // each run uses a DISTINCT core parameterized-type combination, so each interns novel
        // constants into the shared native plane - the "many different generic shapes over one
        // long session" case
        for (int i = 0; i < 20; i++) {
            compile("Grow" + i, growthModule("Grow" + i, ELEM_TYPES[i % ELEM_TYPES.length]));
            runOnce(support, "Grow" + i);
            int now = nativePoolSize(support.ensureConnector());
            trace.append(" -> ").append(now);
            everGrew   |= now > prev;
            everShrank |= now < prev;
            prev = now;
        }

        System.out.println("REPRO B1: shared native ConstantPool size across runs: " + trace
                + (everGrew && !everShrank
                        ? "  (only ever grows, never shrinks - unbounded over a hot session)"
                        : "  (unexpected: grew=" + everGrew + " shrank=" + everShrank + ")"));
    }

    // ----- helpers -------------------------------------------------------------------------------

    private static final String[] ELEM_TYPES =
            {"Int8", "Int16", "Int32", "Int64", "UInt8", "UInt16", "UInt32", "Dec64"};

    private static int nativePoolSize(Connector connector) throws Exception {
        // the native ("-1") container's own pool is the plane every run shares; a main
        // container's pool dies with its run, but interning routinely reaches this shared pool
        if (connector instanceof InterpreterConnector) {
            Field fNative = InterpreterConnector.class.getDeclaredField("f_containerNative");
            fNative.setAccessible(true);
            Container containerNative = (Container) fNative.get(connector);
            return containerNative.getConstantPool().size();
        }
        throw new UnsupportedOperationException("TODO: add JitConnector scenario");
    }

    private static void runOnce(LspSupport support, String moduleName) {
        ErrorList errs    = new ErrorList(256);
        Control   control = support.run(repo(), moduleName, null, System.out, /*root*/null,
                /*injections*/null, /*custom*/null, errs);
    }

    private static String helloModule(String moduleName) {
        return """
                module %s {
                    void run() {
                        @Inject Console console;
                        console.print("hello from %s");
                    }
                }
                """.formatted(moduleName, moduleName);
    }

    private static String growthModule(String moduleName, String elem) {
        return ("""
                module %s {
                    void run() {
                        Map<String, %s> m = new HashMap();
                        Array<Map<%s, String>> nested = new Array();
                        @Inject Console console;
                        console.print(m.size.toString() + nested.size.toString());
                    }
                }
                """).formatted(moduleName, elem, elem);
    }

    private static void compile(String moduleName, String source) throws Exception {
        ErrorList       errs   = new ErrorList(25);
        ModuleStructure module = LspSupport.instance().compile(source, repo(), errs);
        if (module == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of " + moduleName + " failed: " + errs.getErrors());
        }
        if (!moduleName.equals(module.getName())) {
            throw new IllegalStateException(
                    "compile of " + moduleName + " returned module " + module.getName());
        }
        new DirRepository(dirOut.toFile(), false).storeModule(module);
    }

    private static ModuleRepository repo() {
        var list = new java.util.ArrayList<ModuleRepository>();
        for (File dir : new File[] {dirOut.toFile(), dirLib, dirJavatools}) {
            if (dir != null && dir.isDirectory()) {
                list.add(new DirRepository(dir, true));
            }
        }
        return list.size() == 1
                ? list.get(0)
                : new LinkedRepository(list.toArray(ModuleRepository.NO_REPOS));
    }

}
