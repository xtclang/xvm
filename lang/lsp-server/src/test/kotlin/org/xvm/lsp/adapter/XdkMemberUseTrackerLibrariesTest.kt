package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.Heavy
import org.xvm.lsp.adapter.xdk.XdkCodeActionCache
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkProject
import org.xvm.lsp.adapter.xdk.XdkProjectQueries
import org.xvm.lsp.adapter.xdk.XdkWorkspaceDiscovery
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The member-use tracker ([org.xvm.lsp.adapter.xdk.XdkMemberUseTracker]) is sound over the XDK's own libraries, the 22
 * modules of all 21 compiled from copies of the repository's sources, ecstasy among them: after each edit, the next
 * revision's compile, which starts from the one before, gives each module that depends on an edited one, and keeps its
 * compile, the facts, artifact and errors that compiling every module from nothing gives it. The edits are a method
 * body's, a private method's, a public method's, lines that move declarations, and two renames, each proven by the
 * rename's own proof first: of a method of json, and of a method of ecstasy's String that json calls, which the
 * workspace adds. Each prints the modules it compiles, by count, never timed. Heavy: it compiles every library from
 * source several times, which needs the heap that a rename's proof over ecstasy does.
 */
@Heavy
class XdkMemberUseTrackerLibrariesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `over the XDK's libraries, every module that keeps its compile compiles alike from nothing`() {
        CompilerTestSupport.configure()
        val root = File(requireNotNull(System.getProperty("xtc.composite.root")) { "xtc.composite.root is not set" })
        val workspace = directory.toRealPath().toFile()
        root
            .listFiles { file -> file.isDirectory && file.name.startsWith("lib_") }!!
            .forEach { library -> library.resolve("src/main").copyRecursively(workspace.resolve("${library.name}/src/main")) }
        val string = workspace.resolve("lib_ecstasy/src/main/x/ecstasy/text/String.x")
        val parser = workspace.resolve("lib_json/src/main/x/json/Parser.x")
        // a method that only the workspace's String has, which json calls, for a rename of a String method
        string.writeText(withMember(string.readText(), "Int workspaceOnly() = 1;"))
        val input = workspace.resolve("lib_json/src/main/x/json/DocInput.x")
        input.writeText(withMember(input.readText(), "void probe() { Int n = \"x\".workspaceOnly(); }"))
        val modules = XdkWorkspaceDiscovery.scan(listOf(workspace), emptyMap(), emptyList()) { false }
        // the 21 libraries hold 22 modules: lib_runner holds runnerClient too
        assertThat(modules).hasSize(22)
        val project = XdkProject(modules)
        val compiled = CopyOnWriteArrayList<String>()
        val cache = XdkCodeActionCache()

        fun queries(
            overlays: Map<String, String> = emptyMap(),
            actions: XdkCodeActionCache = XdkCodeActionCache(),
        ) = XdkProjectQueries(
            project,
            overlays,
            XdkDependencies(emptyList()),
            { sources, repository, errors ->
                EmbeddingSupport.instance().compileModule(sources, repository, errors).also { compiled += sources.qualifiedModuleName }
            },
            { false },
            actionCache = actions,
        )
        val first = queries(actions = cache)
        first.prepareRename()
        val before = requireNotNull(cache.base()) { "the libraries compile" }
        assertThat(compiled).hasSize(22)
        assertThat(before.errors.values.flatten()).noneMatch { it.startsWith("ERROR") }

        /**
         * The next revision's compile after [edits], as editor buffers over the files, from [before], and the compile of
         * every module from nothing, which each module that depends on an edited one, and keeps its compile, must compile
         * as; it prints what it compiled. The files stay as they are, as a module's resources would take a file written
         * again for another.
         */
        fun next(
            case: String,
            edits: Map<File, String>,
        ) {
            val overlays = edits.mapKeys { it.key.toURI().toString() }
            compiled.clear()
            val tracked = requireNotNull(queries(overlays).revisionCompile(before))
            val tracking = compiled.toList()
            val edited = edits.keys.mapNotNullTo(hashSetOf()) { project.scope(it) }
            val downstream = edited.flatMapTo(linkedSetOf(), project::affected) - edited
            val kept = project.modules.values.filter { it.uri in downstream && it.name !in tracking }
            val keptFacts = kept.associate { it.name to factsText(tracked.facts.module(it.uri)) }
            val keptArtifacts = kept.associate { it.name to tracked.artifacts[it.name]?.revision }
            val keptErrors = kept.associate { it.name to tracked.errors[it.name] }
            println("tracker case=$case compiled=${tracking.size} (${tracking.joinToString()}) kept=${kept.size} of ${downstream.size}")
            compiled.clear()
            val scratch = requireNotNull(queries(overlays).revisionCompile())
            kept.forEach { module ->
                assertThat(factsText(scratch.facts.module(module.uri)))
                    .describedAs("$case: ${module.name}'s facts")
                    .isEqualTo(keptFacts[module.name])
                assertThat(scratch.artifacts[module.name]?.revision)
                    .describedAs("$case: ${module.name}'s artifact")
                    .isEqualTo(keptArtifacts[module.name])
                assertThat(scratch.errors[module.name]).describedAs("$case: ${module.name}'s errors").isEqualTo(keptErrors[module.name])
            }
        }

        /**
         * The edits of a rename of [name], declared on the first line of [file] that holds [line], to [renamed], which the
         * rename's proof proves, and which compiles after the edit only what the tracker does not keep.
         */
        fun renamed(
            case: String,
            file: File,
            line: String,
            name: String,
            renamed: String,
        ): Map<File, String> {
            val lines = file.readText().lines()
            val at = lines.indexOfFirst { line in it }
            compiled.clear()
            val edit = requireNotNull(first.rename(file.toURI().toString(), at, lines[at].indexOf(name), renamed)) { "$case is refused" }
            println("tracker case=$case proof compiled=${compiled.size} (${compiled.joinToString()})")
            return edit.changes.entries.associate { (uri, edits) ->
                val target = File(URI(uri))
                target to applied(target.readText(), edits)
            }
        }

        val length = string.readText().replace("Int estimateStringLength() = size;", "Int estimateStringLength() = size + 0;")
        next("body of a String method", mapOf(string to length))
        next("private method of String", mapOf(string to withMember(string.readText(), "private Int trackerProbe() = 0;")))
        next("public method of json's Parser", mapOf(parser to withMember(parser.readText(), "Int trackerProbe() = 0;")))
        next("comment that moves json's Parser's declarations", mapOf(parser to "// a comment\n" + parser.readText()))
        val expect = "rename of json's Parser.expectString"
        next(expect, renamed(expect, parser, "    String expectString() {", "expectString", "expectText"))
        val only = "rename of String.workspaceOnly"
        next(only, renamed(only, string, "    Int workspaceOnly() = 1;", "workspaceOnly", "workspaceRenamed"))
    }

    /** [text], a class's source, with [member] added at its end. */
    private fun withMember(
        text: String,
        member: String,
    ) = text.substring(0, text.trimEnd().lastIndexOf('}')) + "\n    $member\n}\n"

    /** [text] with [edits] applied, last first. */
    private fun applied(
        text: String,
        edits: List<TextEdit>,
    ): String {
        val starts = text.lines().runningFold(0) { offset, line -> offset + line.length + 1 }

        fun offset(position: Position) = starts[position.line] + position.column
        return edits.sortedByDescending { offset(it.range.start) }.fold(text) { result, edit ->
            result.substring(0, offset(edit.range.start)) + edit.newText + result.substring(offset(edit.range.end))
        }
    }
}
