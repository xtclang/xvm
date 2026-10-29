package org.xvm.tool;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import java.net.URISyntaxException;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.api.DebuggerProvider;

import org.xvm.asm.ModuleRepository;
import org.xvm.asm.ModuleStructure;

import org.xvm.runtime.Debugger;
import org.xvm.runtime.SessionDebugger;
import org.xvm.runtime.SessionDebugger.Line;
import org.xvm.runtime.SessionDebugger.Stop;

import org.xvm.tool.LauncherOptions.RunnerOptions;

import org.xvm.util.Severity;

import static org.xvm.asm.ErrorListener.Silence.DISCARD;
import static org.xvm.asm.ErrorListener.silent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a module with {@code xec --debug}: the runner finds a debugger provider in the jar that
 * {@code agent=<jar>} names, starts it with the other options, installs its debugger before the
 * program starts, so that a breakpoint on the program's first line stops it, and tells the
 * debugging the exit code before closing it. Without a provider, with an agent jar that is not
 * there, with options the provider refuses and with {@code --jit}, the program does not run. The
 * compiled XDK modules come from the test resources.
 */
public class DebugOptionTest {
    private static final long LIMIT_SECONDS = 60;

    /**
     * A module whose run() prints a number; its first line with code is line 4.
     */
    private static final String DEBUGGED = """
            module Debugged {
                void run() {
                    @Inject Console console;
                    Int n = 2;
                    console.print($"n {n}");
                }
            }
            """;

    /**
     * What the provider was asked and what its debugging was told, in order.
     */
    private static final BlockingQueue<String> EVENTS = new LinkedBlockingQueue<>();

    /**
     * The stops of the provider's debugger.
     */
    private static final BlockingQueue<Stop> STOPS = new LinkedBlockingQueue<>();

    /**
     * The source file in which the provider's debugger sets its breakpoint.
     */
    private static volatile Path source;

    /**
     * The debugger that the provider made last.
     */
    private static volatile SessionDebugger debugger;

    @TempDir
    Path dir;

    /**
     * A debugger provider as an IDE's agent is: it records what it is asked, refuses options with
     * {@code bad} in them, and makes a debugger with a breakpoint at line 4 of {@link #source}.
     */
    public static final class RecordingProvider implements DebuggerProvider {
        @Override
        public Session start(String options, ModuleRepository repository, ModuleStructure module) {
            EVENTS.add("start " + options + " " + module.getName());
            if (options.contains("bad")) {
                throw new IllegalArgumentException("bad options: " + options);
            }
            var session = new SessionDebugger(STOPS::add);
            session.addModule(module);
            session.setBreakpoints(source.toString(), List.of(new Line(1, 4, null, null, null)));
            debugger = session;
            return new Session() {
                @Override
                public Debugger debugger() {
                    return session;
                }

                @Override
                public void ended(int code) {
                    EVENTS.add("ended " + code);
                }

                @Override
                public void close() {
                    EVENTS.add("closed");
                }
            };
        }
    }

    @AfterEach
    void clear() {
        EVENTS.clear();
        STOPS.clear();
        debugger = null;
    }

    @Test
    public void theAgentsDebuggerStopsTheProgramAtItsFirstLineAndIsToldTheExitCode() throws Exception {
        String options = "listen:127.0.0.1:0,suspend,agent=" + agent();
        var    run     = CompletableFuture.supplyAsync(() -> launch(options, false, new Messages()));

        Stop stop = STOPS.poll(LIMIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(stop, "the program did not stop at its breakpoint");
        assertEquals("breakpoint", stop.reason());
        assertEquals(List.of(1), stop.breakpointIds());
        debugger.resume();

        assertEquals(0, run.get(LIMIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(List.of("start listen:127.0.0.1:0,suspend Debugged", "ended 0", "closed"), events());
    }

    @Test
    public void withoutAProviderTheProgramDoesNotRun() throws Exception {
        var messages = new Messages();
        assertEquals(1, launch("listen:127.0.0.1:0", false, messages));
        messages.assertError("--debug needs a debugger provider");
        assertEquals(List.of(), events());
    }

    @Test
    public void anAgentThatIsNotThereIsRefused() throws Exception {
        var  messages = new Messages();
        File missing  = dir.resolve("missing.jar").toFile();
        assertEquals(1, launch("listen:127.0.0.1:0,agent=" + missing, false, messages));
        messages.assertError("--debug names an agent");
        assertEquals(List.of(), events());
    }

    @Test
    public void optionsTheProviderRefusesEndTheRunBeforeTheProgramStarts() throws Exception {
        var messages = new Messages();
        assertEquals(1, launch("bad,agent=" + agent(), false, messages));
        messages.assertError("The debugger could not start: bad options: bad");
        assertEquals(List.of("start bad Debugged"), events());
    }

    @Test
    public void debuggingIsRefusedWithTheJit() throws Exception {
        var messages = new Messages();
        assertEquals(1, launch("listen:127.0.0.1:0,agent=" + agent(), true, messages));
        messages.assertError("--debug is not supported with --jit");
        assertEquals(List.of(), events());
    }

    @Test
    public void theOptionIsParsedAndPassedOn() {
        var opts = RunnerOptions.parse(new String[] {"--debug", "connect:localhost:5005,token=t", "App.x"});
        assertEquals(Optional.of("connect:localhost:5005,token=t"), opts.getDebug());
        assertEquals(opts.getDebug(), RunnerOptions.parse(opts.toCommandLine()).getDebug());
        assertEquals(Optional.empty(), RunnerOptions.parse(new String[] {"App.x"}).getDebug());
    }

    /**
     * Run the module from its source, which the runner compiles, with {@code --debug}.
     *
     * @return the exit code
     */
    private int launch(String options, boolean jit, Messages messages) {
        try {
            source = dir.resolve("Debugged.x");
            Files.writeString(source, DEBUGGED);
            var builder = RunnerOptions.builder()
                    .addModulePath(xdk())
                    .setOutputLocation(dir.toFile())
                    .setDebug(options)
                    .enableJit(jit)
                    .setTarget(source.toFile());
            return Launcher.launch(builder.build(), messages, silent(DISCARD));
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return a jar whose service file names {@link RecordingProvider}, whose class the runner
     *         loads through the test's class path
     */
    private String agent() throws IOException {
        Path jar = dir.resolve("agent.jar");
        try (OutputStream out = Files.newOutputStream(jar); var entries = new JarOutputStream(out)) {
            entries.putNextEntry(new JarEntry("META-INF/services/" + DebuggerProvider.class.getName()));
            entries.write((RecordingProvider.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            entries.closeEntry();
        }
        return jar.toString();
    }

    private static List<String> events() {
        return new ArrayList<>(EVENTS);
    }

    private static File xdk() throws URISyntaxException {
        URL modules = DebugOptionTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        return new File(modules.toURI());
    }

    /**
     * The launcher's messages.
     */
    private static final class Messages implements Console {
        private final List<String> errors = new ArrayList<>();

        @Override
        public synchronized String log(Severity sev, Throwable cause, String template, Object... params) {
            String text = Console.super.log(sev, cause, template, params);
            if (sev.compareTo(Severity.ERROR) >= 0) {
                errors.add(text);
            }
            return text;
        }

        synchronized void assertError(String text) {
            assertTrue(errors.stream().anyMatch(error -> error.contains(text)),
                    () -> "no error with \"" + text + "\" in " + errors);
        }
    }
}
