package org.xvm.lsp.adapter

import com.google.gson.JsonParser
import java.lang.ref.WeakReference
import java.nio.file.Path
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkWorkspaceDiscovery

/**
 * The actual teaching sources, including companion files and all automatically discovered roots.
 */
class XdkTeachingWorkspaceTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `delegate property names use the compiler composition binding`() {
        val text =
            "module Delegate { interface Api {} class Engine implements Api {} " +
                "class Forward(Engine target) delegates Api(target) {} }"
        val uri = directory.resolve("Delegate.x").toUri().toString()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val use = text.lastIndexOf("target")
            assertThat(adapter.findDefinition(uri, 0, use)?.startColumn)
                .isEqualTo(text.indexOf("target"))
        }
    }

    @Test
    fun `unproven bindings still reject rename when they occur in a transitive consumer`() {
        val library = "module Library { class Box { Int number = 1; } }"
        val consumer =
            "module Consumer { package lib import Library; " +
                "annotation Tracked<T> into Var<T> { @Override T get() = super(); } " +
                "class State { @Tracked Int value = 1; } Int use(lib.Box box) = box.number; }"
        directory.resolve("Library.x").toFile().writeText(library)
        directory.resolve("Consumer.x").toFile().writeText(consumer)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val uri = directory.resolve("Library.x").toFile().canonicalFile.toURI().toString()
            assertThat(adapter.compile(uri, library).diagnostics).isEmpty()
            assertThat(
                    adapter
                        .compile(directory.resolve("Consumer.x").toUri().toString(), consumer)
                        .diagnostics
                )
                .isEmpty()
            assertThat(adapter.rename(uri, 0, library.indexOf("number"), "amount")).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `teaching workspace property proof preserves live buffers and releases compiler state`(
        multipleBuffers: Boolean
    ) {
        CompilerTestSupport.configure()
        val fixtures = teachingSources()
        fixtures.forEach { (name, text) ->
            directory.resolve(name).toFile().apply {
                parentFile.mkdirs()
                writeText(text)
            }
        }
        val roots =
            XdkWorkspaceDiscovery.scan(listOf(directory.toFile()), emptyMap(), emptyList()) {
                false
            }
        // The original 24-root workspace now also includes 7a.9's initially empty Broken module.
        assertThat(roots).hasSize(25)
        val observed = mutableListOf<WeakReference<Any>>()
        XdkAdapter(
                { source, repository, errors ->
                    EmbeddingSupport.instance().compileModule(source, repository, errors)
                },
                { sources, repository, errors ->
                    EmbeddingSupport.instance().compileModule(sources, repository, errors).also {
                        compilation ->
                        observed += WeakReference(compilation)
                        compilation.pool()?.let { observed += WeakReference(it) }
                        compilation.sourceTrees().forEach { observed += WeakReference(it) }
                    }
                },
                { _, _, _, _, _ -> error("A complete rename proof must not use cursor recovery") },
            )
            .use { adapter ->
                adapter.initializeWorkspace(listOf(directory.toString()))
                val target = "X102/PropertyRename.x"
                val text = fixtures.getValue(target)

                fun uri(file: String) =
                    directory.resolve(file).toFile().canonicalFile.toURI().toString()
                val buffers = buildMap {
                    put(uri(target), text + "\n// Unsaved target buffer\n")
                    if (multipleBuffers) {
                        listOf("Navigation.x", "Consumer.x", "Project/Child.x", "Properties.x")
                            .forEach { file ->
                                put(
                                    uri(file),
                                    "// Unsaved neighboring buffer\n" + fixtures.getValue(file),
                                )
                            }
                    }
                }
                buffers.forEach { (uri, source) ->
                    assertThat(adapter.compile(uri, source).success).describedAs(uri).isTrue()
                }
                val diagnostics =
                    buffers.keys.associateWith { adapter.getCachedResult(it)?.diagnostics }
                observed.clear()
                val edit =
                    requireNotNull(adapter.rename(uri(target), 0, text.indexOf("value"), "amount"))
                assertThat(edit.changes.keys).containsExactly(uri(target))
                assertThat(edit.changes.getValue(uri(target))).hasSize(4)
                assertThat(edit.renames).isEmpty()
                assertThat(buffers.keys.associateWith { adapter.getCachedResult(it)?.diagnostics })
                    .isEqualTo(diagnostics)
                released(observed)

                val collision =
                    buffers
                        .getValue(uri(target))
                        .replace("class Base {", "class Base { Int amount = 3;")
                assertThat(adapter.compile(uri(target), collision).success).isTrue()
                val beforeRefusal = adapter.getCachedResult(uri(target))
                observed.clear()
                assertThat(adapter.rename(uri(target), 0, collision.indexOf("value"), "amount"))
                    .isNull()
                assertThat(adapter.getCachedResult(uri(target))).isEqualTo(beforeRefusal)
                released(observed)
                fixtures.forEach { (file, source) ->
                    assertThat(directory.resolve(file).toFile().readText()).isEqualTo(source)
                }
                println(
                    "Teaching workspace: roots=${roots.size}, open buffers=${buffers.size}, heap=${Runtime.getRuntime().maxMemory()} bytes"
                )
            }
    }

    private fun released(observed: List<WeakReference<Any>>) {
        assertThat(observed).isNotEmpty()
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted {
            System.gc()
            assertThat(observed.count { it.get() != null })
                .describedAs("query-owned compiler objects")
                .isZero()
        }
    }

    /** Mirror the native workspace inputs, reading the shared catalog instead of copying XTC. */
    private fun teachingSources(): Map<String, String> {
        val lang = Path.of(System.getProperty("xtc.composite.root")).resolve("lang")
        val catalog =
            JsonParser.parseString(
                    lang
                        .resolve("test-fixtures/compiler-playbook/scenarios.json")
                        .toFile()
                        .readText()
                )
                .asJsonObject
        val manual = lang.resolve("doc/manual-test-plan.md").toFile().readText()
        val blocks =
            Regex("```xtc\\n([\\s\\S]*?)\\n```").findAll(manual).map { it.groupValues[1] }.toList()
        val cases = catalog.getAsJsonObject("cases")

        fun values(id: String) = cases.getAsJsonObject(id).getAsJsonObject("values")
        return buildMap {
            catalog.getAsJsonObject("common").getAsJsonArray("fixtures").forEach { element ->
                val fixture = element.asJsonObject
                val pattern = Regex(fixture["pattern"].asString, RegexOption.MULTILINE)
                put(fixture["file"].asString, blocks.single(pattern::containsMatchIn) + "\n")
            }
            listOf("X101", "X102", "X103", "X104").forEach { id ->
                val data = values(id)
                put(
                    (if (id == "X101") "" else "$id/") + data["file"].asString,
                    data["source"].asString,
                )
            }
            values("X99").let { data ->
                put("X99/" + data["file"].asString, data["original"].asString)
                put("X99/" + data["library"].asString, data["libraryText"].asString)
            }
            values("X100").let { data ->
                put("X100/" + data["file"].asString, data["source"].asString)
                put("X100/" + data["neighbor"].asString, data["neighborText"].asString)
            }
            values("X103").let { data ->
                put("X103/" + data["member"].asString, data["memberSource"].asString)
            }
            values("X103").let { data ->
                put("X103/" + data["companion"].asString, data["companionSource"].asString)
            }
            values("X105").let { data ->
                put("X105/" + data["file"].asString, "module AutoImports {}")
                put("X105/" + data["library"].asString, data["libraryText"].asString)
            }
            values("7a.9").let { data ->
                put(
                    data["file"].asString,
                    data["moduleStart"].asString + data["moduleEnd"].asString,
                )
            }
        }
    }
}
