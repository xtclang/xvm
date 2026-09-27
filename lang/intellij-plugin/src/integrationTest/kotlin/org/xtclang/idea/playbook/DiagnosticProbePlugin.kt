package org.xtclang.idea.playbook

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/** Package the single IDE-side reader from test classes, without changing the shipping artifact. */
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
                </idea-plugin>
                """.trimIndent().toByteArray(),
            )
            jar.closeEntry()
            val packagePath = "org/xtclang/idea/playbook/probe"
            val classes = Path.of(requireNotNull(javaClass.getResource("/$packagePath")).toURI())
            // Include compiler-generated function-reference classes as well as the reader itself.
            Files.list(classes).use { files ->
                files.filter { it.fileName.toString().endsWith(".class") }.sorted().forEach { file ->
                    jar.putNextEntry(JarEntry("$packagePath/${file.fileName}"))
                    Files.copy(file, jar)
                    jar.closeEntry()
                }
            }
        }
        return plugin
    }
}
