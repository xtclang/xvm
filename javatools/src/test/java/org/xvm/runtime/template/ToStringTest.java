package org.xvm.runtime.template;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The native {@code toString()} of the integer types, {@code Nibble}, {@code Char} and
 * {@code String}: what it and a {@code String}'s {@code +} make of a value is what the value's own
 * {@code appendTo()} writes, which is the Ecstasy code that the native one replaces. The module runs
 * in a JVM of its own, so that its runtime does not stay in this one.
 */
public class ToStringTest {
    private static final long LIMIT_SECONDS = 120;

    @TempDir
    static Path dir;

    @Test
    public void aValueReadsAsItsAppendToWritesIt() throws Exception {
        URL modules = ToStringTest.class.getResource("/xdk-modules");
        assertNotNull(modules, "the build puts the compiled XDK modules in the test resources");
        String xdkModules = new File(modules.toURI()).toString();

        Path source = dir.resolve("ToStrings.x");
        Files.writeString(source, """
                module ToStrings {
                    void run() {
                        @Inject Console console;

                        String[] failures = new String[];
                        check(failures, Int8.MinValue.toInt8(),     "-128");
                        check(failures, Int8.MaxValue.toInt8(),     "127");
                        check(failures, Int16.MinValue.toInt16(),   "-32768");
                        check(failures, Int16.MaxValue.toInt16(),   "32767");
                        check(failures, Int32.MinValue.toInt32(),   "-2147483648");
                        check(failures, Int32.MaxValue.toInt32(),   "2147483647");
                        check(failures, Int64.MinValue.toInt64(),   "-9223372036854775808");
                        check(failures, Int64.MaxValue.toInt64(),   "9223372036854775807");
                        check(failures, (-7).toInt64(),             "-7");
                        check(failures, 0.toInt64(),                "0");
                        check(failures, UInt8.MaxValue.toUInt8(),   "255");
                        check(failures, UInt16.MaxValue.toUInt16(), "65535");
                        check(failures, UInt32.MaxValue.toUInt32(), "4294967295");
                        check(failures, UInt64.MaxValue.toUInt64(), "18446744073709551615");
                        check(failures, 0.toUInt64(),               "0");
                        check(failures, 0.toInt64().toNibble(),     "0");
                        check(failures, 9.toInt64().toNibble(),     "9");
                        check(failures, 0xA.toInt64().toNibble(),   "A");
                        check(failures, 0xF.toInt64().toNibble(),   "F");
                        check(failures, 'a',                        "a");
                        check(failures, 0xE9.toChar(),              "é");
                        check(failures, "text",                     "text");
                        check(failures, "",                         "");

                        // a Char beyond the Basic Multilingual Plane reads as its appendTo() writes it
                        Char beyond = 0x1F600.toChar();
                        check(failures, beyond, beyond.appendTo(new StringBuffer()).toString());

                        console.print($"{failures.size} failures");
                        for (String failure : failures) {
                            console.print(failure);
                        }
                    }

                    void check(String[] failures, Stringable value, String text) {
                        String made     = value.toString();
                        String joined   = "" + value;
                        String appended = value.appendTo(new StringBuffer()).toString();
                        if (made != text || joined != text || appended != text) {
                            failures.add($"expected {text}: toString() {made}, + {joined}, appendTo() {appended}");
                        }
                    }
                }
                """);
        Result build = launch("build", "-o", dir.toString(), "-L", xdkModules, source.toString());
        assertEquals(0, build.exit(), build.output());

        Result run = launch("run", "-L", xdkModules, "-L", dir.toString(), "ToStrings");
        assertEquals(0, run.exit(), run.output());
        assertTrue(run.output().contains("0 failures"), run.output());
        // a toString() that could not be marked native is reported, and its Ecstasy code still runs
        assertFalse(run.output().contains("Missing method"), run.output());
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
