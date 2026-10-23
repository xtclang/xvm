package org.xvm.runtime.template.numbers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xvm.tool.Launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code Nibble}'s 4 bits, which the native template of the fixed-width integers keeps in the high
 * bits of a byte: made from its bits, through the table of values that {@code Nibble} makes that
 * way, from a {@code String}, and to its bits. The module runs in a JVM of its own, so that its
 * runtime does not stay in this one.
 */
public class NibbleTest {
    private static final long LIMIT_SECONDS = 120;

    @TempDir
    static Path dir;

    @Test
    public void aNibbleHasTheValueOfItsFourBits() throws Exception {
        URL modules = NibbleTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        String xdkModules = new File(modules.toURI()).toString();

        Path source = dir.resolve("Nibbles.x");
        Files.writeString(source, """
                module Nibbles {
                    void run() {
                        @Inject Console console;

                        String[] failures = new String[];

                        // from its bits, as Nibble's own table of values is made
                        check(failures, "new Nibble([0, 0, 0, 1])", new Nibble(Array<Bit>:[0, 0, 0, 1]), 1);
                        check(failures, "new Nibble([1, 0, 0, 1])", new Nibble(Array<Bit>:[1, 0, 0, 1]), 9);
                        check(failures, "new Nibble([1, 1, 1, 1])", new Nibble(Array<Bit>:[1, 1, 1, 1]), 0xF);

                        // through that table: Nibble.of(), IntLiteral.toNibble() and Char.isNibble()
                        String hexits = "0123456789ABCDEF";
                        for (Int n : 0..0xF) {
                            check(failures, $"Nibble.of({n})", Nibble.of(n), n);
                            check(failures, $"Nibble.of('{hexits[n]}')", Nibble.of(hexits[n]), n);
                        }
                        check(failures, "0xA.toNibble()", 0xA.toNibble(), 0xA);
                        if (Nibble nibble := 'c'.isNibble()) {
                            check(failures, "'c'.isNibble()", nibble, 0xC);
                        } else {
                            failures.add("'c'.isNibble() is False");
                        }

                        // from a String, and to its bits and back
                        check(failures, "new Nibble(\\"7\\")", new Nibble("7"), 7);
                        check(failures, "new Nibble(Nibble.of(9).bits)", new Nibble(Nibble.of(9).bits), 9);
                        try {
                            Nibble tooBig = new Nibble("16");
                            failures.add($"new Nibble(\\"16\\") is {tooBig.toInt64()}");
                        } catch (OutOfBounds expected) {}
                        if (Nibble.of(0xC).bits != Array<Bit>:[1, 1, 0, 0]) {
                            failures.add($"Nibble.of(0xC).bits is {Nibble.of(0xC).bits}");
                        }

                        console.print($"{failures.size} failures");
                        for (String failure : failures) {
                            console.print(failure);
                        }
                    }

                    void check(String[] failures, String what, Nibble nibble, Int expected) {
                        if (nibble.toInt64() != expected) {
                            failures.add($"{what} is {nibble.toInt64()}, not {expected}");
                        }
                    }
                }
                """);
        Result build = launch("build", "-o", dir.toString(), "-L", xdkModules, source.toString());
        assertEquals(0, build.exit(), build.output());

        Result run = launch("run", "-L", xdkModules, "-L", dir.toString(), "Nibbles");
        assertEquals(0, run.exit(), run.output());
        assertTrue(run.output().contains("0 failures"), run.output());
    }

    private record Result(int exit, String output) {}

    private static Result launch(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                Launcher.class.getName()));
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getOutputStream().close();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        if (!process.waitFor(LIMIT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("still running after " + LIMIT_SECONDS + " seconds: " + String.join(" ", args));
        }
        return new Result(process.exitValue(), output.join());
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
