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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;

import org.xvm.test.XdkOutputs;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The case the XDK build is a poor proxy for: many modules compiled against a COMPLETE, stable,
 * prebuilt library.
 *
 * <p>Building the XDK puts {@code lib_ecstasy} - 48% of the source - in a level of its own that
 * everything waits on, which caps parallelism at about 1.3x no matter how the work is scheduled.
 * The platform has no such prefix: the library is already built and on the module path, so this
 * measures what a warm engine actually offers an LSP server or a test runner.</p>
 */
public class PlatformBuildBenchmarkTest {
    private static final Pattern MODULE =
            Pattern.compile("^\\s*(?:@\\w[\\w.]*\\s+)*module\\s+([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*(?:@\\w+\\s+)?package\\s+\\w+\\s+import\\s+([\\w.]+)", Pattern.MULTILINE);

    @Test
    public void buildThePlatformSeriallyAndInParallel() throws Exception {
        assumeTrue(XdkOutputs.systemModulesAvailable(), "compiled XDK system modules are required");

        Path platform = Path.of("/Users/marcus/src/platform");
        assumeTrue(Files.isDirectory(platform), "platform sources not present at " + platform);

        var nodes = discover(platform);
        assumeTrue(nodes.size() > 5, "expected the platform modules; found " + nodes.size());
        System.out.printf("platform: %d modules%n", nodes.size());

        Path root = XdkOutputs.root();
        // a COMPLETE prebuilt XDK: nothing here is being rebuilt, which is the whole point
        ModuleRepository repoLibrary = new LinkedRepository(true,
                new DirRepository(root.resolve("xdk/build/install/xdk/lib").toFile(), true),
                new DirRepository(root.resolve("xdk/build/install/xdk/javatools").toFile(), true),
                new DirRepository(root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(), true),
                new DirRepository(root.resolve("javatools_bridge/build/xtc/main/lib").toFile(), true));

        try (var engine = XtcEngine.builder()
                .modulePath(root.resolve("xdk/build/install/xdk/lib").toFile(),
                            root.resolve("xdk/build/install/xdk/javatools").toFile(),
                            root.resolve("lib_ecstasy/build/xtc/main/lib").toFile(),
                            root.resolve("javatools_bridge/build/xtc/main/lib").toFile())
                .build();
             var jfr = new JfrProfile("platform-build")) {

            try (var single = Executors.newSingleThreadExecutor()) {
                var warm = XdkBuildHarness.build(engine, repoLibrary, nodes, single);
                System.out.println("=== platform, pass 1 (cold, sequential) ===");
                System.out.print(warm.render());

                var seq = XdkBuildHarness.build(engine, repoLibrary, nodes, single);
                System.out.println("=== platform, pass 2 (warm, sequential) ===");
                System.out.print(seq.render());

                try (var parallel = Executors.newVirtualThreadPerTaskExecutor()) {
                    var par = XdkBuildHarness.build(engine, repoLibrary, nodes, parallel);
                    System.out.println("=== platform, pass 3 (warm, VIRTUAL THREADS) ===");
                    System.out.print(par.render());

                    System.out.printf("%nsequential warm: %s   parallel: %s   speedup: %.2fx%n",
                            seq.wall(), par.wall(),
                            seq.wall().toNanos() / (double) Math.max(par.wall().toNanos(), 1));
                }
            }
            System.out.println("=== JFR ===");
            System.out.print(jfr.stopAndRender(12));
        }
    }

    private static List<XdkBuildHarness.Node> discover(Path platform) throws IOException {
        record Found(String moduleName, String label, Path source, List<File> resources,
                     Set<String> declared) {}

        var found = new ArrayList<Found>();
        try (var dirs = Files.list(platform)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path src = dir.resolve("src/main/x");
                if (!Files.isDirectory(src)) {
                    continue;
                }
                try (var files = Files.list(src)) {
                    for (Path f : files.filter(p -> p.toString().endsWith(".x")).sorted().toList()) {
                        String  text = Files.readString(f);
                        Matcher m    = MODULE.matcher(text);
                        if (m.find()) {
                            var declared = new HashSet<String>();
                            Matcher im = IMPORT.matcher(text);
                            while (im.find()) {
                                declared.add(im.group(1));
                            }
                            // Resource roots are BUILD configuration, not a source-layout
                            // convention, and cannot be discovered from the tree. Most platform
                            // modules use src/main/resources; platformUI's build adds gui/dist as
                            // a resource srcDir, which is where its `Directory:/spa` lives. A real
                            // integration has to be told these, the way Gradle tells xcc.
                            var resources = new ArrayList<File>();
                            for (String candidate : List.of("src/main/resources", "gui/dist")) {
                                Path res = dir.resolve(candidate);
                                if (Files.isDirectory(res)) {
                                    resources.add(res.toFile());
                                }
                            }
                            found.add(new Found(m.group(1), dir.getFileName().toString(), f,
                                    List.copyOf(resources), declared));
                            break;
                        }
                    }
                }
            }
        }

        Set<String> known = found.stream().map(Found::moduleName).collect(Collectors.toSet());
        var nodes = new ArrayList<XdkBuildHarness.Node>();
        for (Found f : found) {
            var deps = new HashSet<>(f.declared());
            deps.retainAll(known);          // XDK imports are already on the module path
            deps.remove(f.moduleName());
            nodes.add(new XdkBuildHarness.Node(f.moduleName(), f.label(), f.source(),
                    f.resources(), deps));
        }
        // dependency order, so each node's futures exist before it is wired to them
        var ordered = new ArrayList<XdkBuildHarness.Node>(nodes.size());
        var placed  = new HashSet<String>();
        var rest    = new ArrayList<>(nodes);
        while (!rest.isEmpty()) {
            var ready = rest.stream().filter(n -> placed.containsAll(n.deps())).toList();
            if (ready.isEmpty()) {
                ordered.addAll(rest);
                break;
            }
            ordered.addAll(ready);
            ready.forEach(n -> placed.add(n.moduleName()));
            rest.removeAll(ready);
        }
        return ordered;
    }
}
