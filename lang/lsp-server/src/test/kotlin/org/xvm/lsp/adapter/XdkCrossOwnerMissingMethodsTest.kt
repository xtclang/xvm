package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class XdkCrossOwnerMissingMethodsTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["peer", "Other", "Missing.Other"])
    fun `source receiver selects the destination and explicit public dispatch`(receiver: String) {
        query(
            """
            module Missing {
                class Other {}
                class Caller {
                    Int read(Other peer, Int value) {
                        return $receiver.§missing(value);
                    }
                }
            }
            """.trimIndent(),
            signature = "public ${if (receiver == "peer") "" else "static "}Int64 missing(Int64 arg1)",
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int", "List<Int>", "(Int | String)", "Value"])
    fun `signature retains compiler type identities in a closed companion`(type: String) {
        query(
            """
            module Missing {
                class Value {}
                $type read(Other peer, $type value) {
                    return peer.§missing(value);
                }
            }
            """.trimIndent(),
            companion = "class Other {}",
            signature = "public ${type.replace("Int", "Int64")} missing(${type.replace("Int", "Int64")} arg1)",
        )
    }

    @Test
    fun `static void repair edits only the companion and leaves caller untouched`() {
        query(
            """
            module Missing {
                void run() {
                    Other.§missing("text");
                }
            }
            """.trimIndent(),
            companion = "class Other {}",
            signature = "public static void missing(String arg1)",
        )
    }

    @Test
    fun `destination type shadowing cannot silently change the signature`() {
        query(
            """
            module Missing {
                Object read(Other peer) {
                    return peer.§missing();
                }
            }
            """.trimIndent(),
            companion = "class Other { class Object {} }",
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["interface Other {}", "class Other<Element> {}", "const Other {}", "class Other { Int missing(String value) = 1; }"],
    )
    fun `unsupported destination or existing member never acquires another method`(target: String) {
        query(
            """
            module Missing {
                Int read(Other peer, Int value) {
                    return peer.§missing(value);
                }
            }
            """.trimIndent(),
            companion = target,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["String", "Type<Other>", "Class<Other>"])
    fun `binary and runtime type receivers are not source destinations`(type: String) {
        query(
            """
            module Missing {
                class Other {}
                Int read($type peer, Int value) {
                    return peer.§missing(value);
                }
            }
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int local = value;", "var local = value;", "val local = value;", "Int local; local = value;"])
    fun `compiler established local arguments retain their type in another owner`(local: String) {
        query(
            """
            module Missing {
                class Other {}
                Int read(Other peer, Int value) {
                    $local
                    return peer.§missing(local);
                }
            }
            """.trimIndent(),
            signature = "public Int64 missing(Int64 arg1)",
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int", "List<Int>", "(Int | String)", "Value"])
    fun `closed companion receives exact local argument and initializer result types`(type: String) {
        query(
            """
            module Missing {
                class Value {}
                $type read($type value) {
                    val local = value;
                    $type result = Other.§missing(local);
                    return result;
                }
            }
            """.trimIndent(),
            companion = "class Other {}",
            signature = "public static ${type.replace("Int", "Int64")} missing(${type.replace("Int", "Int64")} arg1)",
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["peer", "Other", "Missing.Other"])
    fun `initializer repair combines local parameter and literal evidence`(receiver: String) {
        query(
            """
            module Missing {
                class Other {}
                Int read(Other peer, Int value) {
                    var local = value;
                    Int result = $receiver.§missing(local, value, "text");
                    return result;
                }
            }
            """.trimIndent(),
            signature = "public ${if (receiver == "peer") "" else "static "}Int64 missing(Int64 arg1, Int64 arg2, String arg3)",
        )
    }

    @Test
    fun `local receiver and zero argument typed initializer retain instance dispatch`() {
        query(
            """
            module Missing {
                Int read(Other peer) {
                    var other = peer;
                    Int result = other.§missing();
                    return result;
                }
            }
            """.trimIndent(),
            companion = "class Other {}",
            signature = "public Int64 missing()",
        )
    }

    @Test
    fun `void call accepts an inferred local argument`() {
        query(
            """
            module Missing {
                void run(String value) {
                    var local = value;
                    Other.§missing(local);
                }
            }
            """.trimIndent(),
            companion = "class Other {}",
            signature = "public static void missing(String arg1)",
        )
    }

    @Test
    fun `initializer destination shadowing refuses a different but assignable return type`() {
        query(
            """
            module Missing {
                Object read(Other peer) {
                    Object result = peer.§missing();
                    return result;
                }
            }
            """.trimIndent(),
            companion = "class Other { class Object {} }",
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "var result = peer.§missing(value); return result;",
            "Int local; return peer.§missing(local);",
            "var local = unknown; return peer.§missing(local);",
            "return peer.§missing(local); Int local = value;",
            "if (value > 0) { Int local = value; } return peer.§missing(local);",
            "var local = value; return peer.§missing(local + 1);",
        ],
    )
    fun `unproven local contexts do not acquire public signatures`(body: String) {
        query(
            """
            module Missing {
                class Other {}
                Int read(Other peer, Int value) {
                    $body
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `broken neighbor prevents a repair from being published`() {
        query(
            """
            module Missing {
                class Other {}
                Int read(Other peer, Int value) {
                    return peer.§missing(value);
                }
                Int broken() = "wrong";
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `read only companion is never offered as an edit destination`() {
        val target = directory.resolve("Missing/Other.x")
        Files.createDirectories(target.parent)
        Files.writeString(target, "class Other {}")
        val permissions = Files.getPosixFilePermissions(target)
        try {
            Files.setPosixFilePermissions(
                target,
                permissions - setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE),
            )
            query(
                """
                module Missing {
                    Int read(Other peer, Int value) {
                        return peer.§missing(value);
                    }
                }
                """.trimIndent(),
            )
        } finally {
            Files.setPosixFilePermissions(target, permissions)
        }
    }

    private fun query(
        marked: String,
        companion: String? = null,
        signature: String? = null,
    ) {
        CompilerTestSupport.configure()
        val source = directory.resolve("Missing.x").toFile().canonicalFile
        val original = marked.replace("§", "")
        source.writeText(original)
        val target =
            if (companion == null) {
                source
            } else {
                directory.resolve("Missing/Other.x").toFile().canonicalFile.apply {
                    parentFile.mkdirs()
                    writeText(companion)
                }
            }
        val targetOriginal = target.readText()
        val uri = source.toURI().toString()
        val targetUri = target.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            val diagnostics = adapter.compile(uri, original).diagnostics
            assertThat(diagnostics).isNotEmpty().noneMatch { it.code == "EMB-5" }
            val at = XdkRename.position(original, marked.indexOf('§'))
            val actions = adapter.getCodeActions(uri, Range(at, at), diagnostics).filter { it.title.contains("method 'missing'") }
            if (signature == null) {
                assertThat(actions).isEmpty()
            } else {
                assertThat(actions).describedAs("Diagnostics: %s", diagnostics).hasSize(1)
                val action = actions.single()
                assertThat(action.title).isEqualTo("Create public method 'missing' in 'Other'")
                val edit = requireNotNull(action.edit)
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
                assertThat(changed).contains(signature, "TODO();")
                assertThat(adapter.compile(targetUri, changed).diagnostics).isEmpty()
                if (target != source) assertThat(adapter.compile(uri, original).diagnostics).isEmpty()
            }
            assertThat(source.readText()).isEqualTo(original)
            assertThat(target.readText()).isEqualTo(targetOriginal)
        }
    }
}
