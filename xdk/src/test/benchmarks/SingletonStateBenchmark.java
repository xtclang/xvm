import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Locale;
import java.util.Map;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;

import org.xvm.api.EmbeddingSupport;

import org.xvm.asm.DirRepository;
import org.xvm.asm.ErrorList;
import org.xvm.asm.FileStructure;
import org.xvm.asm.LinkedRepository;

import org.xvm.compiler.BuildRepository;

import org.xvm.runtime.MainContainer;
import org.xvm.runtime.NativeContainer;
import org.xvm.runtime.Runtime;

/**
 * Opt-in interpreter experiment; deliberately outside Gradle's test source set and CI lifecycle.
 * Run from the repository root with an installed XDK and the ownership fixture:
 * {@code java -ea -cp xdk/build/install/xdk/javatools/javatools.jar
 * xdk/src/test/benchmarks/SingletonStateBenchmark.java xdk/build/install/xdk
 * xdk/src/test/resources/ownership/Singletons.x 8}.
 *
 * <p>Each iteration copies and links the same compiled module, then executes its assertions in a
 * fresh application under one native root. Iteration zero is cold; later iterations reuse native
 * metadata. This does not share application definitions or exercise the Gradle embedding service.
 * Process CPU and allocated bytes include worker threads. Compilation, bootstrap and shutdown
 * are outside the per-iteration measurements; bootstrap is reported separately. No timing is a
 * correctness assertion. Run several fresh JVMs on each revision with identical inputs.
 */
public class SingletonStateBenchmark {
    private static final OperatingSystemMXBean OS =
            ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private static final ThreadMXBean THREADS =
            ManagementFactory.getPlatformMXBean(ThreadMXBean.class);

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4 || args.length == 4 && !args[3].equals("--heap")) {
            throw new IllegalArgumentException("Expected: XDK directory, source file, iterations [--heap]");
        }
        var installed = Path.of(args[0]);
        int iterations = Integer.parseInt(args[2]);
        if (iterations < 2) {
            throw new IllegalArgumentException("At least two iterations are needed for cold/warm comparison");
        }
        if (!THREADS.isThreadAllocatedMemorySupported()) {
            throw new UnsupportedOperationException("Thread allocation measurement is unavailable");
        }
        THREADS.setThreadAllocatedMemoryEnabled(true);
        var repository = new LinkedRepository(new DirRepository(installed.resolve("lib").toFile(), true),
                new DirRepository(installed.resolve("javatools").toFile(), true));
        var errors = new ErrorList(100);
        var compiler = EmbeddingSupport.instance().configure(repository, null);
        var module = compiler.compile(Files.readString(Path.of(args[1])), repository, errors);
        if (module == null || errors.hasSeriousErrors()) {
            throw new IllegalStateException(errors.toString());
        }
        var binary = new ByteArrayOutputStream();
        module.getFileStructure().writeTo(binary);
        module = new FileStructure(new ByteArrayInputStream(binary.toByteArray())).getModule();
        var modules = new BuildRepository();
        modules.storeModule(module);
        var runtimeRepository = new LinkedRepository(modules, repository);
        var runtime = new Runtime();
        System.out.println("iteration,phase,wall_ms,cpu_ms,allocated_bytes");
        try {
            var bootstrap = Sample.take();
            var root = new NativeContainer(runtime, runtimeRepository);
            bootstrap.report(-1, "bootstrap");
            for (int iteration = 0; iteration < iterations; iteration++) {
                var prepare = Sample.take();
                var file = root.createFileStructure(new FileStructure(module.getFileStructure()).getModule());
                var missing = file.linkModules(runtimeRepository, true);
                if (missing != null) {
                    throw new IllegalStateException("Missing module: " + missing);
                }
                var application = new MainContainer(runtime, root, file.getModuleId());
                prepare.report(iteration, "prepare");
                var execute = Sample.take();
                application.start(Map.of());
                application.invokeAsync("run").join();
                execute.report(iteration, "execute");
            }
            if (args.length == 4) {
                // Outside measurements: keep the root alive while the operator captures a live
                // histogram with jcmd. No GC latency or retained-byte count is a test assertion.
                System.err.println("Ready for heap inspection: " + ProcessHandle.current().pid());
                System.in.read();
                Reference.reachabilityFence(root);
            }
        } finally {
            runtime.shutdownXVM();
        }
    }

    private record Sample(long wall, long cpu, long allocated) {
        private static Sample take() {
            return new Sample(System.nanoTime(), OS.getProcessCpuTime(), THREADS.getTotalThreadAllocatedBytes());
        }

        private void report(int iteration, String phase) {
            var end = take();
            System.out.printf(Locale.ROOT, "%d,%s,%.3f,%.3f,%d%n", iteration, phase,
                    (end.wall - wall) / 1_000_000.0, (end.cpu - cpu) / 1_000_000.0, end.allocated - allocated);
        }
    }
}
