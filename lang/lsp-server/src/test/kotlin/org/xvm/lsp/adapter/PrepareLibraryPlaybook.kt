package org.xvm.lsp.adapter

import ch.qos.logback.classic.LoggerContext
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Files
import java.nio.file.Path

/** Builds the shared binary fixture from source; no checked-in compiler artifacts or external XDK. */
object PrepareLibraryPlaybook {
    @JvmStatic
    fun main(args: Array<String>) {
        check(
            LoggerFactory.getILoggerFactory() is LoggerContext,
        ) { "Playbook fixture logging must use Logback, not javatools' no-op provider" }
        val fixture =
            JsonParser
                .parseString(Files.readString(Path.of(args[0])))
                .asJsonObject
                .getAsJsonObject("common")
                .getAsJsonObject("librarySettings")
        val output = Path.of(args[1])
        CompilerTestSupport.configure()
        listOf("one" to fixture["source"].asString, "two" to fixture["replacementSource"].asString).forEach { (name, text) ->
            val errors = ErrorList()
            val compilation = EmbeddingSupport.instance().compileModule(Source(text, fixture["sourceFile"].asString), null, errors)
            check(compilation.succeeded()) { errors.errors.toString() }
            val folder = Files.createDirectories(output.resolve(name))
            Files.write(folder.resolve(fixture["binaryFile"].asString), compilation.toDependency().bytes())
        }
        val sources = Files.createDirectories(output.resolve("sources"))
        Files.writeString(sources.resolve(fixture["sourceFile"].asString), fixture["source"].asString)
    }
}
