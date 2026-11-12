package org.xtclang.idea.playbook

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** Package IDE-side test helpers without changing the shipping artifact. */
object DiagnosticProbePlugin {
    fun create(directory: Path): Path {
        val plugin = directory.resolve("xtc-playbook-probe")
        val archive = Files.createDirectories(plugin.resolve("lib")).resolve("probe.jar")
        JarOutputStream(Files.newOutputStream(archive)).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/plugin.xml"))
            jar.write(
                """
                <idea-plugin>
                  <id>org.xtclang.playbook.probe</id>
                  <name>XTC Playbook Diagnostics Probe</name>
                  <version>1</version>
                  <vendor>xtclang.org</vendor>
                  <depends>com.intellij.modules.platform</depends>
                  <depends>com.intellij.modules.lang</depends>
                  <depends>com.redhat.devtools.lsp4ij</depends>
                  <depends>org.xtclang.idea</depends>
                  <depends>intellij.testRunner.plugin</depends>
                  <!-- the Problems window, which ProblemFiles reads: part of the platform up to 2026.2, and from 2026.3 a
                       bundled plugin of its own, whose classes a plugin sees only by depending on it -->
                  <depends optional="true" config-file="probe-problems-view.xml">com.intellij.problemsView</depends>
                </idea-plugin>
                """.trimIndent()
                    .toByteArray(),
            )
            jar.closeEntry()
            jar.putNextEntry(JarEntry("META-INF/probe-problems-view.xml"))
            jar.write("<idea-plugin/>".toByteArray())
            jar.closeEntry()
            val packagePath = "org/xtclang/idea/playbook/probe"
            val classes = Path.of(requireNotNull(javaClass.getResource("/$packagePath")).toURI())
            // Include compiler-generated function-reference classes as well as the reader itself.
            Files.list(classes).use { files ->
                files
                    .filter { it.fileName.toString().endsWith(".class") }
                    .sorted()
                    .forEach { file ->
                        jar.putNextEntry(JarEntry("$packagePath/${file.fileName}"))
                        Files.copy(file, jar)
                        jar.closeEntry()
                    }
            }
        }
        return plugin
    }
}
