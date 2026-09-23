import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Opt-in integration test against actual separate Gradle invocations. Run from the checkout with
 * Java 25: java manualTests/src/test/persistent/PersistentBuildTest.java
 * <p>
 * The test builds its own plugin/XDK prerequisites and creates an isolated consumer project.
 * It is deliberately outside ordinary Java test source sets and the manual CI aggregates.
 */
class PersistentBuildTest {
    private static final Pattern WORKER = Pattern.compile("Worker instance=([\\w-]+) pid=(\\d+)");
    private static final Duration BUILD_GUARD = Duration.ofMinutes(5);
    private final Path checkout;
    private final Path project;
    private final Path wrapper;
    private int builds;

    PersistentBuildTest(final Path checkout) throws IOException {
        this.checkout = checkout;
        wrapper = checkout.resolve("gradlew");
        project = Files.createTempDirectory("xtc-persistent-build-test-");
    }

    public static void main(final String[] args) throws Exception {
        final var test = new PersistentBuildTest(Path.of(args.length == 0 ? "." : args[0]).toRealPath());
        System.out.println("Persistent integration evidence: " + test.project);
        test.verify();
    }

    private void verify() throws Exception {
        build(checkout, true, ":xdk:distZip", ":plugin:jar");
        final var plugin = newest(checkout.resolve("plugin/build/libs"), ".jar");
        final var distribution = newest(checkout.resolve("xdk/build/distributions"), ".zip");
        final var xdk = project.resolve("xdk.zip");
        Files.copy(distribution, xdk);
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"persistent-consumer\"\n");
        Files.writeString(project.resolve("gradle.properties"), """
            org.gradle.configuration-cache=true
            org.gradle.configuration-cache-problems=fail
            org.gradle.caching=true
            org.gradle.jvmargs=--enable-preview
            xtcPersistentRuntime=true
            xtcPersistentIdleTimeout=PT5M
            """);
        Files.writeString(project.resolve("build.gradle.kts"), """
            buildscript { dependencies { classpath(files("%s")) } }
            apply(plugin = "org.xtclang.xtc-plugin")
            version = "1.0"
            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            }
            dependencies { add("xdk", files("%s")) }
            tasks.named<org.xtclang.plugin.tasks.XtcRunTask>("runXtc") {
                moduleName("PersistentProbe")
                jvmArgs.add("--enable-preview")
            }
            """.formatted(quote(plugin), quote(xdk)));
        try {
            source(41, false);
            final var cold = build(project, true, "runXtc");
            final var warm = build(project, true, "runXtc");
            check(cold.output.contains("answer=41") && warm.output.contains("answer=41"), "Both builds must execute XTC");
            check(cold.worker().equals(warm.worker()), "Separate builds must reuse the worker");
            check(warm.output.contains("Reusing configuration cache"), "Second build must reuse configuration cache");

            source(42, false);
            final var changed = build(project, true, "runXtc");
            check(changed.output.contains("answer=42"), "Changed source must be compiled and loaded");
            check(cold.worker().equals(changed.worker()), "Source replacement should retain the core runtime");

            source(43, true);
            build(project, false, "runXtc");
            source(44, false);
            final var recovered = build(project, true, "runXtc");
            check(recovered.output.contains("answer=44") && cold.worker().equals(recovered.worker()), "Application failure must not contaminate the next build");

            mutateRuntime(xdk);
            final var replaced = build(project, true, "runXtc");
            check(!cold.worker().equals(replaced.worker()), "Changed runtime contents must select a different worker");
            check(replaced.output.contains("answer=44"), "Replacement runtime must still execute correctly");

            build(project, true, "stopXtcWorker");
            final var restarted = build(project, true, "runXtc");
            check(!replaced.worker().equals(restarted.worker()), "Explicit stop must release the old worker");

            final var crashed = ProcessHandle.of(restarted.pid()).orElseThrow();
            check(crashed.destroyForcibly(), "Test-owned worker must accept termination");
            crashed.onExit().get(BUILD_GUARD.toMillis(), TimeUnit.MILLISECONDS);
            final var afterCrash = build(project, true, "runXtc");
            check(!restarted.worker().equals(afterCrash.worker()) && afterCrash.output.contains("answer=44"),
                "A subsequent build must replace a dead worker without retaining application state");

            System.out.printf("PASS: reuse, fresh application state, recompilation, failure recovery, runtime invalidation, stop/restart, crash replacement, configuration cache%n");
            System.out.printf("Observed build durations: first %.2fs; warm %.2fs (includes Gradle work; not a controlled speedup comparison)%n",
                cold.nanos / 1e9, warm.nanos / 1e9);
        } finally {
            build(project, true, "stopXtcWorker");
        }
    }

    private void source(final int answer, final boolean fail) throws IOException {
        final var directory = project.resolve("src/main/x");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("PersistentProbe.x"), """
            module PersistentProbe {
                void run() {
                    assert ++State.calls == 1;
                    @Inject Console console;
                    console.print("answer=%d");
                    assert %s;
                }
                static service State { Int calls = 0; }
            }
            """.formatted(answer, fail ? "False" : "True"));
    }

    private Result build(final Path directory, final boolean success, final String... tasks) throws Exception {
        final var command = new ArrayList<>(List.of(wrapper.toString(), "-p", directory.toString()));
        command.addAll(List.of(tasks));
        command.addAll(List.of("--info", "--console=plain", "--stacktrace"));
        final var log = project.resolve("build-" + ++builds + ".log");
        final long started = System.nanoTime();
        final var process = new ProcessBuilder(command).directory(checkout.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        process.getOutputStream().close();
        if (!process.waitFor(BUILD_GUARD.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Build did not finish: " + log);
        }
        final var result = new Result(Files.readString(log), System.nanoTime() - started);
        check((process.exitValue() == 0) == success, "Unexpected build result; see " + log);
        return result;
    }

    private record Result(String output, long nanos) {
        long pid() {
            final var matcher = WORKER.matcher(output);
            long pid = -1;
            while (matcher.find()) {
                pid = Long.parseLong(matcher.group(2));
            }
            check(pid > 0, "Build did not report a persistent worker PID");
            return pid;
        }

        String worker() {
            final var matcher = WORKER.matcher(output);
            String last = null;
            while (matcher.find()) {
                last = matcher.group(1);
            }
            check(last != null, "Build did not report a persistent runtime identity");
            return last;
        }
    }

    private static void mutateRuntime(final Path distribution) throws IOException {
        final var replacement = distribution.resolveSibling("replacement.zip");
        boolean changed = false;
        try (var input = new ZipInputStream(Files.newInputStream(distribution));
             var output = new ZipOutputStream(Files.newOutputStream(replacement))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().endsWith("/javatools.jar")) {
                    final var bytes = new ByteArrayOutputStream();
                    try (var jarIn = new ZipInputStream(new ByteArrayInputStream(input.readAllBytes()));
                         var jarOut = new ZipOutputStream(bytes)) {
                        for (ZipEntry part; (part = jarIn.getNextEntry()) != null;) {
                            jarOut.putNextEntry(new ZipEntry(part.getName()));
                            jarIn.transferTo(jarOut);
                            jarOut.closeEntry();
                        }
                        jarOut.putNextEntry(new ZipEntry("persistent-invalidation-probe"));
                        jarOut.write(1);
                        jarOut.closeEntry();
                    }
                    output.write(bytes.toByteArray());
                    changed = true;
                } else {
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
        check(changed, "Distribution must contain javatools.jar");
        Files.move(replacement, distribution, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path newest(final Path directory, final String suffix) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(suffix)
                    && !path.toString().contains("-sources") && !path.toString().contains("-javadoc"))
                .max(Comparator.comparingLong(path -> path.toFile().lastModified())).orElseThrow();
        }
    }

    private static String quote(final Path path) {
        return path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
