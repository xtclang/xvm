package org.xtclang.idea.playbook

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.jar.JarFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Identify the explicit local dependency before copying it into the disposable test IDE. */
data class LocalLsp4ijPlugin(
    val path: String,
    val version: String,
    val sha256: String,
) {
    companion object {
        fun read(path: Path): LocalLsp4ijPlugin {
            val files = Files.walk(path).use { it.filter(Files::isRegularFile).sorted().toList() }
            val descriptor =
                files
                    .asSequence()
                    .filter { it.toString().endsWith(".jar") }
                    .mapNotNull { file ->
                        JarFile(file.toFile()).use { jar ->
                            jar.getJarEntry("META-INF/plugin.xml")?.let { entry ->
                                jar.getInputStream(entry).use { xml ->
                                    DocumentBuilderFactory
                                        .newInstance()
                                        .apply {
                                            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                                            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                                            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                                        }.newDocumentBuilder()
                                        .parse(xml)
                                        .documentElement
                                }
                            }
                        }
                    }.single { it.getElementsByTagName("id").item(0)?.textContent == "com.redhat.devtools.lsp4ij" }
            val version =
                descriptor
                    .getElementsByTagName("version")
                    .item(0)
                    .textContent
                    .trim()
            require(version.isNotEmpty()) { "Missing LSP4IJ version in $path" }
            val digest = MessageDigest.getInstance("SHA-256")
            files.forEach { file ->
                digest.update(path.relativize(file).toString().toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(Files.readAllBytes(file))
            }
            return LocalLsp4ijPlugin(path.toAbsolutePath().toString(), version, HexFormat.of().formatHex(digest.digest()))
        }
    }
}
