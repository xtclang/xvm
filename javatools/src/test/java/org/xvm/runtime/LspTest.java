package org.xvm.runtime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;

import java.lang.reflect.Field;

import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Instant;

import java.util.ArrayList;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.xvm.api.Connector;
import org.xvm.api.InterpreterConnector;
import org.xvm.api.EmbeddingSupport;
import org.xvm.api.EmbeddingSupport.Control;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

/**
 * Run from the xvm directory after {@code ./gradlew installDist}:
 * <pre>{@code
 * java -ea --enable-native-access=javatools --add-modules jdk.httpserver,java.sql \
 *     -p ./xdk/build/install/xdk/javatools \
 *     --patch-module javatools=./javatools/build/classes/java/test \
 *     -m javatools/org.xvm.runtime.LspTest \
 *     ./xdk/build/install/xdk/lib ./xdk/build/install/xdk/javatools
 * }</pre>
 *
 * This test is a modification of the reproducer from
 * <a href="https://github.com/xtclang/xvm/issues/543">issue 534</a>
 */
public class LspTest {
    private static File dirLib;
    private static File dirJavatools;
    private static Path dirOut;

    static void main(String[] asArg) throws Exception {
        dirLib       = new File(asArg[0]);
        dirJavatools = new File(asArg[1]);

        Path dirWork = Files.createTempDirectory("xvm-543-repro");
        dirOut       = Files.createDirectory(dirWork.resolve("lib"));
        EmbeddingSupport.instance().configure(repo(), null);

        testCompile();
        testRun();
        testKillWaiting();
        testFileSystem();
        testRunException();
        testRunLatency();
        testPoolGrows();
    }

    private static void testCompile() {
        ErrorList       errs   = new ErrorList(25);
        ModuleStructure module = EmbeddingSupport.instance().compile(
                helloModule("Trivial"), repo(), errs);
        if (module == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of Trivial failed: " + errs.getErrors());
        }

        errs   = new ErrorList(25);
        module = EmbeddingSupport.instance().compile("module Broken { void run( }", repo(), errs);
        if (module != null || !errs.hasSeriousErrors()) {
            throw new IllegalStateException(
                    "invalid source did not report an error: " + errs.getErrors());
        }
    }

    private static void testRun() {
        ErrorList       errs   = new ErrorList(25);
        ModuleStructure module = EmbeddingSupport.instance().compile(
                helloModule("Hello"), repo(), errs);
        if (module == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of Hello failed: " + errs.getErrors());
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Control control = EmbeddingSupport.instance().run(
                module, new PrintWriter(bytes, true), null, null, errs);
        if (control == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("run of Hello failed to start: " + errs.getErrors());
        }

        try (control) {
            await(control, "Hello");
        }
        if (!bytes.toString().contains("hello from Hello")) {
            throw new IllegalStateException("run of Hello produced unexpected output: " + bytes);
        }
    }

    private static void testKillWaiting() throws Exception {
        ErrorList errs = new ErrorList(25);
        ModuleStructure module = EmbeddingSupport.instance().compile("""
                module Waiting {
                    void run() {
                        @Inject Console console;
                        @Inject Timer timer;
                        try {
                            @Future Tuple done;
                            timer.schedule(Duration:1H, () -> {done = ();});
                            console.print("waiting");
                            return done;
                        } catch (Exception e) {
                            console.print("caught termination");
                        }
                    }
                }
                """, repo(), errs);
        if (module == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of Waiting failed: " + errs.getErrors());
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CountDownLatch waiting = new CountDownLatch(1);
        PrintWriter console = new PrintWriter(bytes, true) {
            @Override
            public void println(char[] chars) {
                super.println(chars);
                if (new String(chars).contains("waiting")) {
                    waiting.countDown();
                }
            }
        };

        Control control = EmbeddingSupport.instance().run(module, console, null, null, errs);
        if (control == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException("run of Waiting failed to start: " + errs.getErrors());
        }

        try (control) {
            if (!waiting.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Waiting did not reach its suspended state");
            }

            long started = System.nanoTime();
            control.close();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (elapsedMillis > 1_000) {
                throw new IllegalStateException(
                        "terminating Waiting took " + elapsedMillis + "ms");
            }
        }

        String output = bytes.toString();
        if (!output.contains("Service terminated") || output.contains("caught termination")) {
            throw new IllegalStateException("Waiting produced unexpected output: " + output);
        }
    }

    private static void testFileSystem() throws Exception {
        ErrorList errs = new ErrorList(25);
        ModuleStructure module = EmbeddingSupport.instance().compile("""
                module FileSystemTest {
                    void run() {
                        @Inject FileStore storage;
                        @Inject Directory rootDir;
                        @Inject Directory homeDir;
                        @Inject Directory curDir;
                        @Inject Directory tmpDir;

                        assert rootDir.path == storage.root.path;
                        assert homeDir.path == storage.root.path;
                        assert curDir.path  == storage.root.path;
                        assert tmpDir.name == ".temp";

                        rootDir.fileFor("root.txt").ensure();
                        tmpDir.fileFor("temporary.txt").ensure();
                    }
                }
                """, repo(), errs);
        if (module == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException(
                    "compile of FileSystemTest failed: " + errs.getErrors());
        }

        Path root = Files.createDirectory(dirOut.getParent().resolve("root"));
        Control control = EmbeddingSupport.instance().run(module, null, root.toFile(), null, errs);
        if (control == null || errs.hasSeriousErrors()) {
            throw new IllegalStateException(
                    "run of FileSystemTest failed to start: " + errs.getErrors());
        }
        try (control) {
            await(control, "FileSystemTest");
        }
        if (!Files.isRegularFile(root.resolve("root.txt"))
                || !Files.isRegularFile(root.resolve(".temp/temporary.txt"))) {
            throw new IllegalStateException("FileSystemTest did not use " + root);
        }
    }

    // ----- a failed run reports its exception through the supplied console ----------------------

    private static void testRunException() {
        String message = "deliberate failure the host must learn about";
        ErrorList compileErrs = new ErrorList(25);
        ModuleStructure module = EmbeddingSupport.instance().compile("""
                module Crasher {
                    void run() {
                        @Inject Console console;
                        console.print("Crasher.run about to throw");
                        throw new IllegalState("%s");
                    }
                }
                """.formatted(message), repo(), compileErrs);
        if (module == null || compileErrs.hasSeriousErrors()) {
            throw new IllegalStateException("compile of Crasher failed: " + compileErrs.getErrors());
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ErrorList runErrs = new ErrorList(25);
        Control control = EmbeddingSupport.instance().run(
                module, new PrintWriter(bytes, true), null, null, runErrs);
        if (control == null) {
            throw new IllegalStateException("run of Crasher failed to start: " + runErrs.getErrors());
        }

        try (control) {
            await(control, "Crasher");
        }
        String output = bytes.toString();
        if (!output.contains(message)) {
            throw new IllegalStateException("run of Crasher did not report its exception: " + output);
        }
    }

    private static void await(Control control, String moduleName) {
        Instant started = control.whenStarted();
        if (started == null) {
            throw new IllegalStateException("run of " + moduleName + " has no start time");
        }

        control.join();

        Instant stopped = control.whenStopped();
        if (stopped == null || stopped.isBefore(started)) {
            throw new IllegalStateException(
                    "run of " + moduleName + " has invalid timing: " + started + " to " + stopped);
        }
    }

    // ----- a trivial run completes run very quickly ----------------------------------------------

    private static void testRunLatency() throws Exception {
        compile("Quick", helloModule("Quick"));

        EmbeddingSupport support = EmbeddingSupport.instance();
        for (int run = 1; run <= 5; run++) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ErrorList errs = new ErrorList(25);
            Control control = support.run(
                    repo(), "Quick", null, new PrintWriter(bytes, true), null, null, null, errs);
            if (control == null) {
                throw new IllegalStateException(
                        "run " + run + " of Quick failed to start: " + errs.getErrors());
            }

            long elapsedMillis;
            try (control) {
                long t0 = System.nanoTime();
                await(control, "Quick " + run);
                elapsedMillis = (System.nanoTime() - t0) / 1_000_000;
            }

            if (errs.hasSeriousErrors()) {
                throw new IllegalStateException(
                        "run " + run + " of Quick failed: " + errs.getErrors());
            }
            if (!bytes.toString().contains("hello from Quick")) {
                throw new IllegalStateException(
                        "run " + run + " of Quick produced unexpected output: " + bytes);
            }
            System.out.println("testRunLatency run " + run + ": observed completion in "
                    + elapsedMillis + "ms");
        }
    }

    // ----- check the shared native pool growth ---------------------------------------------------

    private static void testPoolGrows() throws Exception {
        EmbeddingSupport support = EmbeddingSupport.instance();
        ErrorList  errs    = new ErrorList(256);

        // each run uses a DISTINCT core parameterized-type combination, so each interns novel
        // constants into the shared native plane - the "many different generic shapes over one
        // long session" case
        for (int i = 0; i <= 12; i++) {
            compile("Grow" + i, growthModule("Grow" + i, ELEM_TYPES[i % ELEM_TYPES.length]));

            Control control =
                    support.run(repo(), "Grow" + i, null, null, null, null, null, errs);
            if (control == null) {
                throw new IllegalStateException(
                        "run of Grow" + i + " failed to start: " + errs.getErrors());
            }
            try (control) {
                await(control, "Grow" + i);
            }

            int size = nativePoolSize(support.ensureConnector());
            System.out.println("testPoolGrows run " + i + ": ConstantPool size = " + size);
        }
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
                    }
                }
                """).formatted(moduleName, elem, elem);
    }

    private static void compile(String moduleName, String source) throws Exception {
        ErrorList       errs   = new ErrorList(25);
        ModuleStructure module = EmbeddingSupport.instance().compile(source, repo(), errs);
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
        var list = new ArrayList<ModuleRepository>();
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
