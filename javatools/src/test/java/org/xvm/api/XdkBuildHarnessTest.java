package org.xvm.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compiles the XDK's own libraries, in dependency order, on one warm engine.
 *
 * <p>This is a diagnostic, not a pass/fail gate: it reports how far the engine gets. It exists
 * because "modules compiled of 22" is a far better progress measure for the engine/CLI compile
 * divergence than a single-module reproducer, and because running the whole sequence twice on one
 * engine is the retention question at realistic scale rather than as a microbenchmark.</p>
 *
 * <p>It is deliberately written against {@link XdkBuildHarness}, whose scheduling is driven by the
 * dependency graph and parameterised by an executor, so the day the parallel case is worth trying
 * the only change here is which executor is passed.</p>
 */
public class XdkBuildHarnessTest {
    /** {@code module foo.xtclang.org}, optionally annotated. */
    private static final Pattern MODULE =
            Pattern.compile("^\\s*(?:@\\w[\\w.]*\\s+)*module\\s+([\\w.]+)", Pattern.MULTILINE);

    /** {@code package p import foo.xtclang.org;} */
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*(?:@\\w+\\s+)?package\\s+\\w+\\s+import\\s+([\\w.]+)", Pattern.MULTILINE);

    /** Everything depends on this, and nothing declares it. */
    private static final String CORE = "ecstasy.xtclang.org";

    @Test
    public void reportHowFarTheEngineGetsBuildingTheXdk() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        Path root  = XdkOutputs.root();
        var  nodes = discover(root);
        assumeTrue(nodes.size() > 5, "expected the XDK library sources; found " + nodes.size());

        ModuleRepository repoLibrary = new LinkedRepository(true,
                new DirRepository(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("javatools_bridge/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/javatools").toFile(), true));

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile())
                .build()) {

            // SEQUENTIAL: one thread, so per-call compiler state cannot bleed between compiles.
            // Swapping in Executors.newVirtualThreadPerTaskExecutor() is the whole of the parallel
            // experiment - the dependency graph and the code below are unchanged.
            try (var executor = Executors.newSingleThreadExecutor();
                 var sampler  = new CompileSampler(5);
                 var jfr      = new JfrProfile("xdk-build")) {
                var first = XdkBuildHarness.build(engine, repoLibrary, nodes, executor);
                System.out.println("=== XDK build, pass 1 ===");
                System.out.print(first.render());

                // Pass 2 on the SAME engine. Anything that differs is state carried between
                // compiles, which is what a warm long-lived engine has to get right.
                var second = XdkBuildHarness.build(engine, repoLibrary, nodes, executor);
                System.out.println("=== XDK build, pass 2, same engine ===");
                System.out.print(second.render());

                System.out.printf("pass 1: %d built / %d failed / %d skipped in %s%n",
                        first.built(), first.failed(), first.skipped(), first.wall());
                System.out.printf("pass 2: %d built / %d failed / %d skipped in %s%n",
                        second.built(), second.failed(), second.skipped(), second.wall());
                System.out.printf("heap after pass 1: %d MB, after pass 2: %d MB%n",
                        first.heapUsed() >> 20, second.heapUsed() >> 20);

                // T15's retainer, if it is this one: the engine caches a fully prepared library
                // per INPUT REPOSITORY INSTANCE and never evicts. Feeding outputs forward means a
                // fresh repository per compile, so this should equal the number of compiles.
                var field = XtcEngine.class.getDeclaredField("f_mapPreparedLibraries");
                field.setAccessible(true);
                var prepared = (java.util.Map<?, ?>) field.get(engine);
                System.out.printf("prepared libraries retained: %d  (compiles performed: %d)%n",
                        prepared.size(), nodes.size() * 2);

                // PARALLEL: the only change is the executor. One task per thread, per the plan's
                // execution model - never a work-stealing pool, which would run several compiles
                // on one carrier thread and bleed TypeSystemThread state between them.
                try (var parallel = Executors.newVirtualThreadPerTaskExecutor()) {
                    var third = XdkBuildHarness.build(engine, repoLibrary, nodes, parallel);
                    System.out.println("=== XDK build, pass 3, VIRTUAL THREADS ===");
                    System.out.print(third.render());
                    System.out.printf("pass 3 (parallel): %d built / %d failed / %d skipped in %s%n",
                            third.built(), third.failed(), third.skipped(), third.wall());
                    System.out.printf("prepared libraries retained after parallel: %d%n",
                            prepared.size());
                }
                System.out.println("=== where the time goes: stack sampler ===");
                System.out.print(sampler.render(15));
                System.out.println("=== where the time goes: JFR ===");
                System.out.print(jfr.stopAndRender(15));
            }
        }
    }

    /**
     * Find every XDK library module and its declared imports.
     */
    private static List<XdkBuildHarness.Node> discover(Path root) throws IOException {
        record Found(String moduleName, String label, Path source, List<File> resources,
                     Set<String> declared) {}

        var found = new ArrayList<Found>();
        var dirs  = new ArrayList<File>();
        try (var stream = Files.list(root)) {
            stream.map(Path::toFile)
                  .filter(f -> f.isDirectory() && f.getName().startsWith("lib_"))
                  .forEach(dirs::add);
        }
        dirs.add(root.resolve("javatools_turtle").toFile());
        dirs.add(root.resolve("javatools_bridge").toFile());

        for (File dir : dirs) {
            Path src = dir.toPath().resolve("src/main/x");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (var stream = Files.list(src)) {
                for (Path f : stream.filter(p -> p.toString().endsWith(".x")).sorted().toList()) {
                    String  text = Files.readString(f);
                    Matcher m    = MODULE.matcher(text);
                    if (m.find()) {
                        var declared = new HashSet<String>();
                        Matcher im = IMPORT.matcher(text);
                        while (im.find()) {
                            declared.add(im.group(1));
                        }
                        // a module's resources are part of its source: lib_ecstasy reads
                        // $/implicit.x, and without this it fails to parse
                        Path res = dir.toPath().resolve("src/main/resources");
                        found.add(new Found(m.group(1), dir.getName(), f,
                                Files.isDirectory(res) ? List.of(res.toFile()) : List.of(),
                                declared));
                        break;
                    }
                }
            }
        }

        Set<String> known = found.stream().map(Found::moduleName).collect(java.util.stream.Collectors.toSet());
        var nodes = new ArrayList<XdkBuildHarness.Node>();
        for (Found f : found) {
            var deps = new HashSet<>(f.declared());
            deps.retainAll(known);
            deps.remove(f.moduleName());
            if (!f.moduleName().equals(CORE) && known.contains(CORE)) {
                deps.add(CORE);   // implicit, and never declared
            }
            nodes.add(new XdkBuildHarness.Node(f.moduleName(), f.label(), f.source(),
                    f.resources(), deps));
        }
        return topological(nodes);
    }

    /**
     * Order so that every module appears after the modules it depends on.
     *
     * <p>Required, not cosmetic: the harness wires each node's future to its dependencies' futures,
     * so those have to exist first. A comparator cannot express this - "depends on" is a partial
     * order and sorting by it violates the contract {@code List.sort} requires.</p>
     */
    private static List<XdkBuildHarness.Node> topological(List<XdkBuildHarness.Node> nodes) {
        var byName    = new java.util.LinkedHashMap<String, XdkBuildHarness.Node>();
        nodes.forEach(n -> byName.put(n.moduleName(), n));

        var ordered   = new ArrayList<XdkBuildHarness.Node>(nodes.size());
        var placed    = new HashSet<String>();
        var remaining = new ArrayList<>(nodes);
        while (!remaining.isEmpty()) {
            var ready = remaining.stream().filter(n -> placed.containsAll(n.deps())).toList();
            if (ready.isEmpty()) {
                // a cycle: emit the rest in declaration order rather than lose them, and let the
                // build report the failure instead of the harness hiding it
                ordered.addAll(remaining);
                break;
            }
            ordered.addAll(ready);
            ready.forEach(n -> placed.add(n.moduleName()));
            remaining.removeAll(ready);
        }
        return ordered;
    }
}
