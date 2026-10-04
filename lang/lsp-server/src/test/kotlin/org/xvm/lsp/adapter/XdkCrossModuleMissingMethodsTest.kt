package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class XdkCrossModuleMissingMethodsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["peer", "lib.Other"])
    fun `configured dependency receives the public method with exact dispatch`(receiver: String) {
        query(
            caller("Int", "return $receiver.§missing(value);"),
            signature = "public ${if (receiver == "peer") "" else "static "}Int64 missing(Int64 arg1)",
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int", "List<Int>", "(Int | String)", "lib.Value"])
    fun `closed companion renders inferred local and initializer types in the destination module`(type: String) {
        query(
            caller(
                type,
                """
                val local = value;
                $type result = lib.Other.§missing(local);
                return result;
                """.trimIndent(),
            ),
            library = "module Library { class Value {} }",
            companion = "class Other {}",
            signature = "public static ${type.replace(
                "Int",
                "Int64",
            ).removePrefix("lib.")} missing(${type.replace("Int", "Int64").removePrefix("lib.")} arg1)",
        )
    }

    @Test
    fun `third module signature uses the destination import alias rather than the caller alias`() {
        query(
            caller("shared.Value", "return peer.§missing(value);", "package shared import Types;"),
            library =
                """
                module Library {
                    package types import Types;
                    class Other {}
                }
                """.trimIndent(),
            extra = mapOf("Types" to "module Types { class Value {} }"),
            libraryDependencies = setOf("Types"),
            signature = "public types.Value missing(types.Value arg1)",
        )
    }

    @Test
    fun `inferred third module local uses the destination alias`() {
        query(
            caller(
                "shared.Value",
                """
                val local = value;
                shared.Value result = peer.§missing(local);
                return result;
                """.trimIndent(),
                "package shared import Types;",
            ),
            library = "module Library { package types import Types; class Other {} }",
            extra = mapOf("Types" to "module Types { class Value {} }"),
            libraryDependencies = setOf("Types"),
            signature = "public types.Value missing(types.Value arg1)",
        )
    }

    @Test
    fun `caller owned type would introduce a reverse dependency and is refused`() {
        query(caller("Value", "return peer.§missing(value);", "class Value {}"))
    }

    @Test
    fun `repair cannot invent a destination import even when the dependency edge exists`() {
        query(
            caller("shared.Value", "return peer.§missing(value);", "package shared import Types;"),
            extra = mapOf("Types" to "module Types { class Value {} }"),
            libraryDependencies = setOf("Types"),
        )
    }

    @Test
    fun `unrelated source root is not an implicit dependency`() {
        query(caller("Int", "return peer.§missing(value);"), callerDependencies = emptySet())
    }

    @Test
    fun `whole graph failure in a closed consumer prevents publication`() {
        query(caller("Int", "return peer.§missing(value);"), brokenConsumer = true)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "interface Other {}",
            "class Other<Element> {}",
            "class Other { Int missing(String value) = 1; }",
            "class Other { class Object {} }",
        ],
    )
    fun `unsupported or ambiguous destination remains a refusal`(target: String) {
        query(caller("Object", "return peer.§missing(value);"), library = "module Library { $target }")
    }

    @Test
    fun `read only dependency source cannot become an edit target`() {
        query(caller("Int", "return peer.§missing(value);"), readOnly = true)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `binary source indexes do not grant source ownership`(indexed: Boolean) {
        CompilerTestSupport.configure()
        val library = directory.resolve("Library.x").toFile().canonicalFile
        val text = "module Library { class Other {} }"
        library.writeText(text)
        val errors = ErrorList()
        val compiled = EmbeddingSupport.instance().compileModule(Source(text, library.path), null, errors)
        assertThat(compiled.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val dependency = compiled.toDependency().let { if (indexed) it else XdkDependency.fromBinary(it.bytes()) }
        query(caller("Int", "return peer.§missing(value);"), binary = dependency)
    }

    private fun caller(
        type: String,
        body: String,
        declarations: String = "",
    ): String =
        """
        module App {
            package lib import Library;
            $declarations
            $type read(lib.Other peer, $type value) {
        ${body.prependIndent("        ")}
            }
        }
        """.trimIndent()

    private fun query(
        marked: String,
        library: String = "module Library { class Other {} }",
        companion: String? = null,
        signature: String? = null,
        extra: Map<String, String> = emptyMap(),
        libraryDependencies: Set<String> = emptySet(),
        callerDependencies: Set<String> = setOf("Library") + extra.keys,
        brokenConsumer: Boolean = false,
        readOnly: Boolean = false,
        binary: XdkDependency? = null,
    ) {
        CompilerTestSupport.configure()

        fun write(
            name: String,
            text: String,
        ) = directory.resolve(name).toFile().canonicalFile.apply {
            parentFile.mkdirs()
            writeText(text)
        }
        val original = marked.replace("§", "")
        val source = write("App.x", original)
        val root = write("Library.x", library)
        val target = companion?.let { write("Library/Other.x", it) } ?: root
        val targetOriginal = target.readText()
        val modules =
            buildList {
                extra.forEach { (name, text) -> add(XdkSourceModule(name, write("$name.x", text).toURI().toString())) }
                if (binary == null) add(XdkSourceModule("Library", root.toURI().toString(), libraryDependencies))
                add(XdkSourceModule("App", source.toURI().toString(), callerDependencies))
                if (brokenConsumer) {
                    add(
                        XdkSourceModule(
                            "Consumer",
                            write("Consumer.x", "module Consumer { Missing value; }").toURI().toString(),
                            setOf("Library"),
                        ),
                    )
                }
            }
        val permissions = Files.getPosixFilePermissions(target.toPath())
        try {
            if (readOnly) {
                Files.setPosixFilePermissions(
                    target.toPath(),
                    permissions - setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE),
                )
            }
            XdkAdapter().use { adapter ->
                adapter.initializeWorkspace(listOf(directory.toString()))
                adapter.replaceSourceModules(modules)
                if (binary != null) adapter.replaceDependencies(listOf(binary))
                val uri = source.toURI().toString()
                val targetUri = target.toURI().toString()
                val diagnostics = adapter.compile(uri, original).diagnostics
                assertThat(diagnostics).isNotEmpty().noneMatch { it.code == "EMB-5" }
                val at = XdkRename.position(original, marked.indexOf('§'))
                val actions = adapter.getCodeActions(uri, Range(at, at), diagnostics).filter { it.title.contains("method 'missing'") }
                if (signature == null) {
                    assertThat(actions).isEmpty()
                } else {
                    assertThat(actions).describedAs("Diagnostics: %s", diagnostics).hasSize(1)
                    val edit = requireNotNull(actions.single().edit)
                    assertThat(edit.versioned).isTrue()
                    assertThat(edit.changes.keys).containsExactly(targetUri)
                    val changed =
                        edit.changes
                            .getValue(targetUri)
                            .sortedByDescending {
                                it.range.start.line * targetOriginal.length +
                                    it.range.start.column
                            }.fold(targetOriginal) { text, change ->
                                fun offset(position: Position) =
                                    requireNotNull(XdkRename.offset(targetOriginal, SemanticModel.Position(position.line, position.column)))
                                text.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                            }
                    assertThat(changed).contains(signature)
                    assertThat(adapter.compile(targetUri, changed).diagnostics).isEmpty()
                    assertThat(adapter.compile(uri, original).diagnostics).isEmpty()
                }
                assertThat(source.readText()).isEqualTo(original)
                assertThat(target.readText()).isEqualTo(targetOriginal)
            }
        } finally {
            if (readOnly) Files.setPosixFilePermissions(target.toPath(), permissions)
        }
    }
}
