package org.xvm.lsp.adapter

import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkAutoImports
import org.xvm.lsp.adapter.xdk.XdkBuildModel
import org.xvm.lsp.adapter.xdk.XdkLibraries
import org.xvm.lsp.adapter.xdk.XdkLibraryReplacement
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import org.xvm.lsp.model.Diagnostic
import org.xvm.testing.awaited
import java.nio.file.Path
import java.util.concurrent.ExecutionException

/**
 * A workspace whose own module has a bundled XDK library's name, which then replaces the bundled one, as the XVM
 * repository's replace them all ([org.xvm.lsp.adapter.xdk.XdkLibraryReplacement]): the server treats the library as
 * the workspace's sources, not as the bundle.
 */
class XdkReplacedLibraryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a rename of a workspace library's public declaration edits the modules that import it`() {
        val json = "module json.xtclang.org { class Answer {} }"
        val app = "module App { package json import json.xtclang.org; json.Answer? find() = Null; }"
        val jsonUri = directory.source("json", json)
        val appUri = directory.source("App", app)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule(JSON, jsonUri),
                    XdkSourceModule("App", appUri, setOf(JSON)),
                ),
            )
            assertThat(adapter.compile(jsonUri, json).diagnostics).isEmpty()
            // App compiles against the workspace's json, which has Answer, and not the bundled one
            assertThat(adapter.compile(appUri, app).diagnostics).isEmpty()
            val at = json.positionOf("class Answer", "Answer")
            val edit = requireNotNull(adapter.rename(jsonUri, at.line, at.column, "Reply")) { "No rename of the workspace's json" }
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(jsonUri, appUri)
            assertThat(apply(json, edit.changes.getValue(jsonUri))).isEqualTo(json.replace("Answer", "Reply"))
            assertThat(apply(app, edit.changes.getValue(appUri))).isEqualTo(app.replace("Answer", "Reply"))
        }
    }

    @Test
    fun `import candidates for a replaced library come from the workspace's compiled library, not the bundle`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport.instance().compileModule(Source("module json.xtclang.org { class WorkspaceOnly {} }", "json.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        // the server compiles a workspace module of a library's name from source, and indexes what it can import
        val artifact = compilation.toDependency()
        assertThat(artifact.imports.map { it.path }).containsExactly("WorkspaceOnly")
        val facts = CompilerRenameFacts(emptyList(), emptyMap())
        val replaced = mapOf(JSON to artifact.imports)
        assertThat(XdkAutoImports.targets("WorkspaceOnly", facts, replaced).map { it.module }).containsExactly(JSON)
        // the bundled json's Parser is not offered once the workspace replaces json, and the bundled xml's still is
        assertThat(XdkAutoImports.targets("Parser", facts, replaced).map { it.module })
            .doesNotContain(JSON)
            .contains("xml.xtclang.org")
        assertThat(XdkAutoImports.targets("Parser", facts).map { it.module }).contains(JSON)
    }

    @Test
    fun `a workspace library with errors says that the server's compiler may be older than the repository's`() {
        directory.source("json", "module json.xtclang.org { Missing value; }")
        directory.source("xml", "module xml.xtclang.org {}")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val reports = adapter.workspaceDiagnosticsAsync().awaited().flatMap { it.diagnostics }

            fun codes(module: String) = reports.filter { it.location.uri.endsWith("/$module.x") }.map { it.code }
            assertThat(codes("json")).contains(Diagnostic.Codes.REPLACED_LIBRARY)
            assertThat(codes("xml")).doesNotContain(Diagnostic.Codes.REPLACED_LIBRARY)
        }
    }

    @Test
    fun `a replaced library reaches only the modules of its own workspace folder`() {
        // the XVM repository's json, with a class the bundled one lacks, beside a project that uses the bundled json
        directory.source("xdk/json", "module json.xtclang.org { class WorkspaceOnly {} }")
        directory.source("xdk/App", "module App { package json import json.xtclang.org; json.WorkspaceOnly? find() = Null; }")
        directory.source("other/Tool", "module Tool { package json import json.xtclang.org; json.Parser? find() = Null; }")
        val xdk = folder("xdk")
        val other = folder("other")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(xdk, other).map { directory.resolve(it).toString() })
            val errors =
                adapter
                    .workspaceDiagnosticsAsync()
                    .awaited()
                    .flatMap { it.diagnostics }
                    .filter { it.severity == Diagnostic.Severity.ERROR }
            assertThat(errors).isEmpty()
            assertThat(adapter.replacedLibraries()).containsExactly(JSON)
            assertThat(adapter.replacedFolders()).containsExactly(uri(xdk))
            // the other folder builds with the embedded compiler, by module or as a folder; the XVM repository's does not
            assertThat(adapter.buildAsync("Tool", rebuild = false).awaited().succeeded).isTrue()
            assertThat(
                adapter
                    .buildAsync(null, rebuild = false, folder = uri(other))
                    .awaited()
                    .modules
                    .map { it.name },
            ).containsExactly("Tool")
            for (refused in listOf(
                { adapter.buildAsync("App", rebuild = false) },
                { adapter.buildAsync(null, false, folder = uri(xdk)) },
            )) {
                assertThatThrownBy { refused().get() }
                    .isInstanceOf(ExecutionException::class.java)
                    .hasMessageContaining(XdkLibraryReplacement.NO_EMBEDDED_RUNS)
            }
        }
    }

    @Test
    fun `in the XVM repository the replaced libraries are the folder's reason, and manualTests on its own XDK keeps it on Gradle`() {
        // the repository's json, which replaces the bundled one, and manualTests, whose xdk dependency is the XDK that the
        // repository's own project :xdk makes, which the export names without reading it
        val json = directory.source("lib_json/src/main/x/json", "module json.xtclang.org {}")
        val manual = directory.source("manualTests/src/main/x/ManualTest", "module ManualTest {}")

        fun entry(
            project: String,
            module: String,
            more: Map<String, Any> = emptyMap(),
        ) = mapOf(
            "projectId" to project,
            "sourceSet" to "main",
            "sourceFiles" to listOf(module),
            "moduleRoots" to listOf(module),
            "resourceRoots" to emptyList<String>(),
            "projectDependencies" to emptyList<String>(),
            "modulePath" to emptyList<String>(),
        ) + more
        val xdk =
            mapOf(
                "version" to XdkLibraries.version.orEmpty(),
                "libraries" to "${uri("manualTests")}build/xtc/xdk/lib/",
                "project" to ":xdk",
            )
        val model =
            XdkBuildModel.read(
                listOf(
                    Gson()
                        .toJsonTree(
                            mapOf(
                                "schemaVersion" to 1,
                                "sourceSets" to listOf(entry(":lib_json", json), entry(":manualTests", manual, mapOf("xdk" to xdk))),
                            ),
                        ).asJsonObject,
                ),
            )
        val root = uri(".")
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            adapter.replaceBuildInputs(model.resolve())
            // the folder's reason, and that of a build of it, is the replaced library, as the editors and the manual plan say
            assertThat(adapter.gradleFolders()).isEqualTo(mapOf(root to "replaces the bundled libraries $JSON with its own modules"))
            assertThatThrownBy { adapter.buildAsync(null, false, folder = root).get() }
                .hasMessageContaining(XdkLibraryReplacement.NO_EMBEDDED_RUNS)
            val own = "builds with XDK ${XdkLibraries.version} that the build's own project :xdk makes"
            assertThatThrownBy { adapter.buildAsync("ManualTest", false).get() }.hasMessageContaining("The module ManualTest $own")
            // with every library bundled, manualTests alone keeps the folder on Gradle, for the repository's own XDK
            System.setProperty(XdkLibraryReplacement.PROPERTY, "false")
            try {
                adapter.replaceBuildInputs(model.resolve())
                assertThat(adapter.replacedLibraries()).isEmpty()
                assertThat(adapter.gradleFolders().getValue(root)).startsWith(own)
            } finally {
                System.clearProperty(XdkLibraryReplacement.PROPERTY)
            }
        }
    }

    private fun folder(name: String): String = name.also { directory.resolve(it).toFile().mkdirs() }

    private fun uri(folder: String): String =
        directory
            .resolve(folder)
            .toFile()
            .canonicalFile
            .toURI()
            .toString()

    private companion object {
        const val JSON = "json.xtclang.org"
    }
}
