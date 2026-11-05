package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.Slow
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.awaited
import org.xvm.tool.Launcher
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * `xtc test` as IntelliJ's command-line test runs give it, from the project's directory with the test module's
 * name: a test module in `src/test/x`, as `xtc init` and Gradle lay a project out, imports the project's module
 * in `src/main/x`; `xtc build` compiles it the same way. The launcher runs from this server's class path with the
 * bundled XDK, as [TestProcess] does.
 */
class CommandLineTestsTest {
    @TempDir lateinit var project: Path

    @Test
    @Slow
    fun `xtc test runs a project's test module from its name, with the project's module it imports`() {
        layOut()

        val (exit, output) = xtc("test", "--deduce", "-I", TestProcess.REPORT, "appTest")

        assertThat(exit).describedAs(output).isZero()
        assertThat(output).contains("(passed=1 failed=0 skipped=0 errors=0)")
        assertThat(project.resolve("build/app.xtc")).exists()
        assertThat(project.resolve("build/appTest.xtc")).exists()

        // a change to the project's module reaches the tests, which compile again with it; the source is dated
        // after its binary, whatever the file system's timestamps resolve
        val app = Files.writeString(project.resolve("src/main/x/app.x"), "module app {\n    static Int answer = 41;\n}\n")
        val built = Files.getLastModifiedTime(project.resolve("build/app.xtc")).toMillis()
        Files.setLastModifiedTime(app, FileTime.fromMillis(built + 60_000))
        val (changed, again) = xtc("test", "--deduce", "-I", TestProcess.REPORT, "appTest")
        assertThat(changed).describedAs(again).isNotZero()
        assertThat(again).contains("(passed=0 failed=1 skipped=0 errors=0)")
    }

    @Test
    @Slow
    fun `xtc build compiles a project's test module from its name with the project's module, as xtc test does`() {
        layOut()

        val (exit, output) = xtc("build", "--deduce", "appTest")

        assertThat(exit).describedAs(output).isZero()
        assertThat(project.resolve("build/app.xtc")).exists()
        assertThat(project.resolve("build/appTest.xtc")).exists()
        // the tests run what xtc build compiled, without compiling
        val (ran, tested) = xtc("test", "--deduce", "--no-recompile", "-I", TestProcess.REPORT, "appTest")
        assertThat(ran).describedAs(tested).isZero()
        assertThat(tested).contains("(passed=1 failed=0 skipped=0 errors=0)")
    }

    /** The project: its module `app` in `src/main/x`, and in `src/test/x` the test module `appTest`, which imports it. */
    private fun layOut() {
        Files.createDirectories(project.resolve("src/main/x/app"))
        Files.writeString(project.resolve("src/main/x/app.x"), "module app {\n    static Int answer = 42;\n}\n")
        Files.createDirectories(project.resolve("src/test/x"))
        Files.writeString(
            project.resolve("src/test/x/appTest.x"),
            "module appTest {\n    package app import app;\n\n    @Test\n    void answers() {\n" +
                "        assert app.answer == 42;\n    }\n}\n",
        )
    }

    /** Runs the launcher with [args] in the project's directory, and returns its exit code and output. */
    private fun xtc(vararg args: String): Pair<Int, String> {
        val command =
            listOf(
                ProcessHandle
                    .current()
                    .info()
                    .command()
                    .orElse("java"),
                "-Dfile.encoding=UTF-8",
                "-cp",
                System.getProperty("java.class.path"),
                Launcher::class.java.name,
                args.first(),
                "-L",
                XdkLibraries.directory.path,
            ) + args.drop(1)
        val process = ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.awaited()) { "xtc ${args.joinToString(" ")} did not end" }
        return process.exitValue() to output
    }
}
