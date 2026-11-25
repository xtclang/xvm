package org.xvm.lsp.run

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/** Which of a Gradle project's JVM options a run's JVM takes, why it leaves out the others, and which hold a relative path. */
class JvmOptionsTest {
    @Test
    fun `options that change only how the JVM runs are taken as given, in order`() {
        val options =
            listOf(
                "-ea",
                "--enable-native-access=ALL-UNNAMED",
                "-Xmx1g",
                "-Dxvm.origin=gradle",
                "-Dfile.encoding=ISO-8859-1",
                "--add-opens",
                "java.base/java.lang=ALL-UNNAMED",
                "-showversion",
                "-javaagent:/opt/agents/profiler.jar=interval=10",
                "-agentpath:/opt/jdwp-tools/libprofiler.so",
                "-XX:+UseZGC",
            )
        assertThat(JvmOptions.of(options)).isEqualTo(JvmOptions(options, emptyList()))
        assertThat(JvmOptions.of(emptyList())).isEqualTo(JvmOptions(emptyList(), emptyList()))
    }

    @Test
    fun `a class path, a main class, a debugger's agent, options that print and exit, argument and option files are left out`() {
        val checked =
            JvmOptions.of(
                listOf(
                    "-ea",
                    "-cp",
                    "lib.jar",
                    "--class-path=lib.jar",
                    "-Djava.class.path=lib.jar",
                    "-jar",
                    "app.jar",
                    "--module=app/app.Main",
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
                    "-Xrunjdwp:transport=dt_socket",
                    "-agentpath:/jdk/lib/libjdwp.so=server=y",
                    "-agentpath:C:\\jdk\\bin\\jdwp.dll",
                    "-version",
                    "-fullversion",
                    "--full-version",
                    "--help",
                    "-X",
                    "-Xshare:dump",
                    "@options.txt",
                    "-XX:VMOptionsFile=jvm.options",
                    "-XX:Flags=.hotspotrc",
                    " ",
                    "-Xss4m",
                ),
            )
        assertThat(checked.accepted).containsExactly("-ea", "-Xss4m")
        assertThat(checked.refused.map { it.option }).containsExactly(
            "-cp lib.jar",
            "--class-path=lib.jar",
            "-Djava.class.path=lib.jar",
            "-jar app.jar",
            "--module=app/app.Main",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
            "-Xrunjdwp:transport=dt_socket",
            "-agentpath:/jdk/lib/libjdwp.so=server=y",
            "-agentpath:C:\\jdk\\bin\\jdwp.dll",
            "-version",
            "-fullversion",
            "--full-version",
            "--help",
            "-X",
            "-Xshare:dump",
            "@options.txt",
            "-XX:VMOptionsFile=jvm.options",
            "-XX:Flags=.hotspotrc",
            " ",
        )
        assertThat(checked.refused.associate { it.option to it.why })
            .containsEntry("-cp lib.jar", "the run's JVM has the language server's class path")
            .containsEntry("-jar app.jar", "the run's JVM runs its own main class")
            .containsEntry(
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
                "a Java debugger's agent would hold the run's JVM; Debug debugs the program",
            ).containsEntry("-version", "the JVM would print and exit instead of running")
            .containsEntry("-Xshare:dump", "the JVM would print and exit instead of running")
            .containsEntry("@options.txt", "an argument file, whose options cannot be checked")
            .containsEntry("-XX:VMOptionsFile=jvm.options", "an options file, whose options cannot be checked")
            .containsEntry(" ", "an empty option")
    }

    @Test
    fun `an option that takes its value as the next item, last of all, is left out alone`() {
        assertThat(JvmOptions.of(listOf("-ea", "-cp"))).isEqualTo(
            JvmOptions(listOf("-ea"), listOf(JvmOptions.Refused("-cp", "the run's JVM has the language server's class path"))),
        )
    }

    @Test
    fun `the options that hold a relative path are named, as they resolve against the server's working directory`() {
        val relative =
            listOf(
                "-javaagent:libs/agent.jar",
                "-agentpath:native/libprofiler.so=interval=10",
                "-Djava.library.path=/usr/lib${File.pathSeparator}native",
                "-Xbootclasspath/a:boot.jar",
                "-Xlog:gc:file=gc.log",
                "-Xlog:gc*:logs/gc.log:uptime",
                "-Xloggc:gc.log",
                "-XX:HeapDumpPath=dumps",
                "-XX:ErrorFile=hs_err.log",
                "-XX:StartFlightRecording=duration=30s,filename=recording.jfr",
                "-XX:SharedArchiveFile=app.jsa",
                "-XX:ArchiveClassesAtExit=app.jsa",
                "--module-path=mods",
                "--patch-module=java.base=patches",
            )
        // an option whose value is the next item is named with it
        val valued = listOf("-p", "mods", "--patch-module", "java.base=patches")
        val absolute =
            listOf(
                "-javaagent:/opt/agent.jar",
                "-Xlog:gc:stdout",
                "-Xlog:gc*:stderr",
                "-Xlog:gc",
                "-XX:HeapDumpPath=/tmp/dumps",
                "-Dxvm.origin=gradle",
                "-p",
                "/opt/mods",
                "--patch-module=java.base=/opt/patches",
                // a Windows path is absolute on any host, though it has a colon, which -Xlog's syntax has too
                "-Xlog:gc:file=C:\\logs\\gc.log",
                "-Xlog:gc:file=C:/logs/gc.log:uptime",
                "-XX:SharedArchiveFile=D:\\app.jsa",
                "-javaagent:\\\\server\\share\\agent.jar",
            )
        val checked = JvmOptions.of(relative + valued + absolute)
        assertThat(checked.accepted).isEqualTo(relative + valued + absolute)
        assertThat(checked.relative).isEqualTo(relative + listOf("-p mods", "--patch-module java.base=patches"))
    }
}
