package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS

class XdkTypeMoveTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `moving a type between packages rewrites constructors and closed consumers`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box { static Int number() = 1; }")
        write("App/util/Marker.x", "class Marker {}")
        write(
            "Consumer.x",
            "module Consumer { package app import App; app.tools.Box make() = new app.tools.Box(); Int read() = app.tools.Box.number(); }",
        )
        session { adapter ->
            move(adapter)
            assertThat(read("App.x")).contains("util.Box").doesNotContain("tools.Box")
            assertThat(read("Consumer.x")).contains("app.util.Box", "app.util.Box.number()").doesNotContain("tools.Box")
        }
    }

    @Test
    fun `moving a type preserves explicit import aliases and qualifies bare names`() {
        write("App.x", "module App { import tools.Box as Crate; Crate make() = new Crate(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/tools/Factory.x", "class Factory { Box make() = new Box(); }")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            move(adapter)
            assertThat(read("App.x")).contains("import util.Box as Crate", "Crate make() = new Crate()")
            assertThat(read("App/tools/Factory.x")).contains("util.Box make() = new util.Box()")
        }
    }

    @Test
    fun `moving companion sources preserves resources and old package sibling bindings`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box { Helper make() = new Helper(); }")
        write("App/tools/Helper.x", "class Helper {}")
        write("App/tools/Box/Part.x", "class Part {}")
        write("App/tools/Box/data.txt", "keep this resource")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            val edit = move(adapter)
            assertThat(edit.renames).containsEntry(uri("App/tools/Box"), uri("App/util/Box"))
            assertThat(read("App/util/Box/data.txt")).isEqualTo("keep this resource")
            assertThat(read("App/util/Box/Part.x")).isEqualTo("class Part {}")
            assertThat(read("App/util/Box.x")).contains("tools.Helper")
        }
    }

    @Test
    fun `move leaves unrelated same named types and their calls unchanged`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); other.Box second() = new other.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/other/Box.x", "class Box {}")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            move(adapter)
            assertThat(read("App.x")).contains("util.Box make() = new util.Box()", "other.Box second() = new other.Box()")
        }
    }

    @Test
    fun `target collisions or a different module refuse the entire move`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/util/Box.x", "class Box {}")
        write("Other.x", "module Other {}")
        write("Other/Marker.x", "class Marker {}")
        session { adapter ->
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(adapter.renameFilesAsync(mapOf(uri("App/tools/Box.x") to uri("Other/Box.x"))).get(30, SECONDS)).isNull()
            assertThat(read("App.x")).contains("tools.Box")
            assertThat(directory.resolve("App/tools/Box.x")).exists()
        }
    }

    @Test
    fun `moving back to the module namespace removes package prefixes and preserves nested types`() {
        write("App.x", "module App { package other import Other; tools.Box.Part make() = new tools.Box.Part(); }")
        write("Other.x", "module Other {}")
        write("App/tools/Box.x", "class Box {}")
        write("App/tools/Box/Part.x", "static class Part {}")
        session { adapter ->
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            val requested = mapOf(uri("App/tools/Box.x") to uri("App/Box.x"))
            val edit = requireNotNull(adapter.renameFilesAsync(requested).get(30, SECONDS))
            apply(edit, requested)
            assertThat(read("App.x")).isEqualTo("module App { package other import Other; Box.Part make() = new Box.Part(); }")
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `a compiling move that would capture an unrelated bare method is refused`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools.x", "package tools { static Int number() = 1; }")
        write("App/tools/Box.x", "class Box { Int read() = number(); }")
        write("App/util.x", "package util { static Int number() = 2; }")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(read("App/tools/Box.x")).isEqualTo("class Box { Int read() = number(); }")
            // Compilation alone accepts the wrong package's number(). The refusal above must
            // come from comparing the original call target, not from a syntax/access failure.
            write("App.x", read("App.x").replace("tools.Box", "util.Box"))
            Files.move(directory.resolve("App/tools/Box.x"), directory.resolve("App/util/Box.x"))
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `occupied companion directory refuses a move before touching files`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/util/Marker.x", "class Marker {}")
        write("App/util/Box/data.txt", "unrelated existing resource")
        session { adapter ->
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(read("App/util/Box/data.txt")).isEqualTo("unrelated existing resource")
            assertThat(directory.resolve("App/tools/Box.x")).exists()
        }
    }

    @Test
    fun `configured source graph includes closed consumers without replacing settings`() {
        write("App.x", "module App {}")
        write("App/tools/Box.x", "class Box {}")
        write("App/util/Marker.x", "class Marker {}")
        write("Consumer.x", "module Consumer { package app import App; import app.tools.Box; Box make() = new Box(); }")
        session { adapter ->
            adapter.replaceSourceModules(
                listOf(
                    XdkSourceModule("App", uri("App.x")),
                    XdkSourceModule("Consumer", uri("Consumer.x"), setOf("App")),
                ),
            )
            val edit = requireNotNull(adapter.renameFilesAsync(request()).get(30, SECONDS))
            apply(edit, request())
            assertThat(read("Consumer.x")).contains("import app.util.Box", "Box make() = new Box()")
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `same package file rename preserves explicit aliases and updates implicit imports`() {
        write(
            "App.x",
            """
            module App {
                import tools.Box as Kept;
                Kept make() = new Kept();
            }
            """.trimIndent(),
        )
        write("App/tools/Box.x", "class Box {}")
        write(
            "Consumer.x",
            """
            module Consumer {
                package app import App;
                import app.tools.Box;
                Box make() = new Box();
            }
            """.trimIndent(),
        )
        session { adapter ->
            move(adapter, mapOf(uri("App/tools/Box.x") to uri("App/tools/Crate.x")))
            assertThat(read("App.x")).contains("import tools.Crate as Kept", "Kept make() = new Kept()")
            assertThat(read("Consumer.x")).contains("import app.tools.Crate", "Crate make() = new Crate()")
        }
    }

    @Test
    fun `rename and package move preserve aliases bare names companions and closed consumers together`() {
        write(
            "App.x",
            """
            module App {
                import tools.Box as Kept;
                Kept make() = new Kept();
                tools.Box.Part part() = new tools.Box.Part();
            }
            """.trimIndent(),
        )
        write(
            "App/tools/Box.x",
            """
            class Box {
                static Int number() = 1;
                Helper make() = new Helper();
            }
            """.trimIndent(),
        )
        write("App/tools/Box/Part.x", "static class Part {}")
        write("App/tools/Box/data.txt", "preserved resource")
        write("App/tools/Helper.x", "class Helper {}")
        write("App/tools/Factory.x", "class Factory { Box make() = new Box(); }")
        write("App/tools/Crate.x", "class Crate {}")
        write("App/util/Marker.x", "class Marker {}")
        write(
            "Consumer.x",
            """
            module Consumer {
                package app import App;
                import app.tools.Box;
                Box make() = new Box();
                Int value() = app.tools.Box.number();
            }
            """.trimIndent(),
        )
        val original = sourceTexts()
        session { adapter ->
            val requested = mapOf(uri("App/tools/Box.x") to uri("App/util/Crate.x"))
            val edit = move(adapter, requested)
            assertThat(edit.renames).containsEntry(uri("App/tools/Box"), uri("App/util/Crate"))
            assertThat(read("App.x")).contains("import util.Crate as Kept", "Kept make() = new Kept()", "util.Crate.Part")
            assertThat(read("App/tools/Factory.x")).contains("util.Crate make() = new util.Crate()")
            assertThat(read("App/util/Crate.x")).contains("class Crate", "tools.Helper make() = new tools.Helper()")
            assertThat(read("Consumer.x")).contains("import app.util.Crate", "Crate make() = new Crate()", "app.util.Crate.number()")
            assertThat(read("App/tools/Crate.x")).isEqualTo("class Crate {}")
            assertThat(read("App/util/Crate/data.txt")).isEqualTo("preserved resource")
            assertThat(read("App/util/Crate/Part.x")).isEqualTo("static class Part {}")
            move(adapter, requested.entries.associate { (from, to) -> to to from })
            // Reverse refactoring need not restore the original shortest spelling (Undo does).
            assertThat(sourceTexts().keys).containsExactlyInAnyOrderElementsOf(original.keys)
            assertThat(read("App/tools/Box.x")).contains("class Box")
            assertThat(read("Consumer.x")).contains("app.tools.Box")
        }
    }

    @Test
    fun `combined move refuses collisions and invalid names without partial edits`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/util.x", "package util { class Taken {} }")
        write("App/util/Marker.x", "class Marker {}")
        val original = sourceTexts()
        session { adapter ->
            listOf("Taken", "not-a-name").forEach { name ->
                val requested = mapOf(uri("App/tools/Box.x") to uri("App/util/$name.x"))
                assertThat(adapter.renameFilesAsync(requested).get(30, SECONDS)).isNull()
            }
            assertThat(sourceTexts()).isEqualTo(original)
        }
    }

    @Test
    fun `combined move refuses a changed unqualified method target even when final sources compile`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools.x", "package tools { static Int number() = 1; }")
        write("App/tools/Box.x", "class Box { Int read() = number(); }")
        write("App/util.x", "package util { static Int number() = 2; }")
        write("App/util/Marker.x", "class Marker {}")
        session { adapter ->
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            val requested = mapOf(uri("App/tools/Box.x") to uri("App/util/Crate.x"))
            assertThat(adapter.renameFilesAsync(requested).get(30, SECONDS)).isNull()
            write("App.x", read("App.x").replace("tools.Box", "util.Crate"))
            write("App/tools/Box.x", read("App/tools/Box.x").replace("class Box", "class Crate"))
            Files.move(directory.resolve("App/tools/Box.x"), directory.resolve("App/util/Crate.x"))
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        }
    }

    @Test
    fun `interacting type moves are planned together independently of request order`() {
        write(
            "App.x",
            """
            module App {
                left.First first() = new left.First();
                right.Second second() = new right.Second();
            }
            """.trimIndent(),
        )
        write("App/left/First.x", "class First { right.Second next() = new right.Second(); }")
        write("App/right/Second.x", "class Second { left.First next() = new left.First(); }")
        write("App/util/Marker.x", "class Marker {}")
        write(
            "Consumer.x",
            """
            module Consumer {
                package app import App;
                app.left.First first() = new app.left.First();
                app.right.Second second() = new app.right.Second();
            }
            """.trimIndent(),
        )
        session { adapter ->
            val requested = mapOf(uri("App/left/First.x") to uri("App/util/Alpha.x"), uri("App/right/Second.x") to uri("App/util/Beta.x"))
            val first = requireNotNull(adapter.renameFilesAsync(requested).get(30, SECONDS))
            val reversed = requireNotNull(adapter.renameFilesAsync(requested.entries.reversed().associate { it.toPair() }).get(30, SECONDS))
            assertThat(first.changes).isEqualTo(reversed.changes)
            assertThat(first.renames).isEqualTo(reversed.renames)
            apply(first, requested)
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
            assertThat(read("App/util/Alpha.x")).contains("class Alpha", "util.Beta next() = new util.Beta()")
            assertThat(read("App/util/Beta.x")).contains("class Beta", "util.Alpha next() = new util.Alpha()")
            assertThat(read("Consumer.x")).contains("app.util.Alpha", "app.util.Beta").doesNotContain("app.left", "app.right")
        }
    }

    @Test
    fun `one invalid type destination refuses the entire interacting batch`() {
        write("App.x", "module App { left.First first() = new left.First(); right.Second second() = new right.Second(); }")
        write("App/left/First.x", "class First {}")
        write("App/right/Second.x", "class Second {}")
        write("App/util.x", "package util { class Taken {} }")
        write("App/util/Marker.x", "class Marker {}")
        val original = sourceTexts()
        session { adapter ->
            val requested = mapOf(uri("App/left/First.x") to uri("App/util/Alpha.x"), uri("App/right/Second.x") to uri("App/util/Taken.x"))
            assertThat(adapter.renameFilesAsync(requested).get(30, SECONDS)).isNull()
            assertThat(sourceTexts()).isEqualTo(original)
            assertThat(directory.resolve("App/util/Alpha.x")).doesNotExist()
        }
    }

    @Test
    fun `empty nested destination acquires compiler package ownership without marker sources`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("Consumer.x", "module Consumer { package app import App; app.tools.Box make() = new app.tools.Box(); }")
        Files.createDirectories(directory.resolve("App/util/nested"))
        session { adapter ->
            move(adapter, mapOf(uri("App/tools/Box.x") to uri("App/util/nested/Parcel.x")))
            assertThat(read("App.x")).contains("util.nested.Parcel")
            assertThat(read("Consumer.x")).contains("app.util.nested.Parcel")
            assertThat(read("App/util/nested/Parcel.x")).isEqualTo("class Parcel {}")
        }
    }

    @Test
    fun `unused type moves into an explicit empty package`() {
        write("App.x", "module App {}")
        write("App/tools/Box.x", "class Box {}")
        write("App/util.x", "package util {}")
        Files.createDirectories(directory.resolve("App/util"))
        session { adapter ->
            move(adapter)
            assertThat(read("App/util/Box.x")).isEqualTo("class Box {}")
        }
    }

    @Test
    fun `empty destinations outside the module or below a class remain refused`() {
        write("App.x", "module App { tools.Box make() = new tools.Box(); }")
        write("App/tools/Box.x", "class Box {}")
        write("App/Owner.x", "class Owner {}")
        write("Other.x", "module Other {}")
        listOf("App/Owner/nested", "Other/empty", "unowned").forEach { Files.createDirectories(directory.resolve(it)) }
        session { adapter ->
            val original = sourceTexts()
            listOf("App/Owner/nested", "Other/empty", "unowned").forEach { destination ->
                assertThat(adapter.renameFilesAsync(mapOf(uri("App/tools/Box.x") to uri("$destination/Box.x"))).get(30, SECONDS))
                    .describedAs(destination).isNull()
            }
            assertThat(sourceTexts()).isEqualTo(original)
        }
    }

    @Test
    fun `qualified imports and calls retain comments whitespace and explicit aliases during combined moves`() {
        write("App.x", """
            module App {
                import tools /* namespace */ . Box as Crate;
                Crate make() = new Crate();
                tools /* type */ . Box direct() = new tools . /* constructor */ Box();
            }
        """.trimIndent())
        write("App/tools/Box.x", "class Box { static Int number() = 1; }")
        write("Consumer.x", """
            module Consumer {
                package app import App;
                app /* alias */ . tools /* package */ . Box make() = new app . tools . Box();
                Int read() = app.tools /* call */ . Box.number();
            }
        """.trimIndent())
        Files.createDirectories(directory.resolve("App/util/nested"))
        session { adapter ->
            move(adapter, mapOf(uri("App/tools/Box.x") to uri("App/util/nested/Parcel.x")))
            assertThat(read("App.x")).contains(
                "import util /* namespace */ . nested.Parcel as Crate;",
                "Crate make() = new Crate();",
                "util /* type */ . nested.Parcel direct() = new util . /* constructor */ nested.Parcel();",
            )
            assertThat(read("Consumer.x")).contains(
                "app /* alias */ . util /* package */ . nested.Parcel make() = new app . util . nested.Parcel();",
                "app.util /* call */ . nested.Parcel.number()",
            )
        }
    }

    @Test
    fun `removing a namespace preserves line comments unicode and CRLF in nested types and calls`() {
        val text = """
            module App {
                tools /* α */ . Box.Part make() = new tools // keep namespace note
                    . Box.Part();
            }
        """.trimIndent().replace("\n", "\r\n")
        write("App.x", text)
        write("App/tools/Box.x", "class Box {}")
        write("App/tools/Box/Part.x", "static class Part {}")
        session { adapter ->
            move(adapter, mapOf(uri("App/tools/Box.x") to uri("App/Box.x")))
            assertThat(read("App.x")).isEqualTo(text.replace("tools /* α */ .", " /* α */ ").replace("tools //", " //").replace(". Box.Part();", " Box.Part();"))
        }
    }

    @Test
    fun `commented names do not weaken refusal when a move changes an unqualified call binding`() {
        write("App.x", "module App { tools /* target */ . Box make() = new tools . Box(); }")
        write("App/tools.x", "package tools { static Int number() = 1; }")
        write("App/tools/Box.x", "class Box { Int read() = number(); }")
        write("App/util.x", "package util { static Int number() = 2; }")
        Files.createDirectories(directory.resolve("App/util"))
        session { adapter ->
            val original = sourceTexts()
            assertThat(adapter.renameFilesAsync(request()).get(30, SECONDS)).isNull()
            assertThat(sourceTexts()).isEqualTo(original)
        }
    }

    private fun sourceTexts(): Map<String, String> =
        directory
            .toFile()
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(directory.toFile()).path to it.readText() }

    private fun request() = mapOf(uri("App/tools/Box.x") to uri("App/util/Box.x"))

    private fun read(file: String) = Files.readString(directory.resolve(file))

    private fun move(
        adapter: XdkAdapter,
        requested: Map<String, String> = request(),
    ): WorkspaceEdit {
        assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        val edit = requireNotNull(adapter.renameFilesAsync(requested).get(30, SECONDS))
        assertThat(edit.versioned).isTrue()
        apply(edit, requested)
        adapter.initializeWorkspace(listOf(directory.toString()))
        assertThat(adapter.workspaceDiagnosticsAsync().get(30, SECONDS)).allMatch { it.success }
        return edit
    }

    private fun session(body: (XdkAdapter) -> Unit) {
        CompilerTestSupport.configure()
        directory = directory.toRealPath()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            body(adapter)
        }
    }

    private fun write(
        file: String,
        source: String,
    ) {
        directory.resolve(file).toFile().apply {
            parentFile.mkdirs()
            writeText(source)
        }
    }

    private fun uri(file: String) =
        directory
            .resolve(file)
            .toFile()
            .toURI()
            .toString()
            .removeSuffix("/")

    private fun apply(
        edit: WorkspaceEdit,
        requested: Map<String, String>,
    ) {
        edit.changes.forEach { (uri, changes) ->
            val path = Path.of(URI(uri))
            val text = Files.readString(path)

            fun offset(position: Position): Int =
                if (position.line == 0) position.column
                else Regex("\\r\\n|\\r|\\n").findAll(text).elementAt(position.line - 1).range.last + 1 + position.column
            Files.writeString(
                path,
                changes
                    .sortedByDescending { offset(it.range.start) }
                    .fold(text) { value, change ->
                        value.replaceRange(
                            offset(change.range.start),
                            offset(change.range.end),
                            change.newText,
                        )
                    },
            )
        }
        (edit.renames + requested).forEach { (from, to) ->
            Files.move(Path.of(URI(from)), Path.of(URI(to)))
        }
    }
}
