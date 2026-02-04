package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.SymbolInfo

@DisplayName("MockXtcCompilerAdapter")
class MockXtcCompilerAdapterTest {
    private lateinit var adapter: MockXtcCompilerAdapter

    @BeforeEach
    fun setUp() {
        adapter = MockXtcCompilerAdapter()
    }

    @Nested
    @DisplayName("compile()")
    inner class CompileTests {
        @Test
        @DisplayName("should parse module declaration")
        fun shouldParseModuleDeclaration() {
            val source =
                """
                module myapp {
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.success).isTrue()
            assertThat(result.diagnostics).isEmpty()
            assertThat(result.symbols)
                .anyMatch { it.name == "myapp" && it.kind == SymbolInfo.SymbolKind.MODULE }
        }

        @Test
        @DisplayName("should parse class declaration")
        fun shouldParseClassDeclaration() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.success).isTrue()
            assertThat(result.symbols)
                .anyMatch { it.name == "Person" && it.kind == SymbolInfo.SymbolKind.CLASS }
        }

        @Test
        @DisplayName("should parse interface declaration")
        fun shouldParseInterfaceDeclaration() {
            val source =
                """
                module myapp {
                    interface Runnable {
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.symbols)
                .anyMatch { it.name == "Runnable" && it.kind == SymbolInfo.SymbolKind.INTERFACE }
        }

        @Test
        @DisplayName("should parse service declaration")
        fun shouldParseServiceDeclaration() {
            val source =
                """
                module myapp {
                    service UserService {
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.symbols)
                .anyMatch { it.name == "UserService" && it.kind == SymbolInfo.SymbolKind.SERVICE }
        }

        @Test
        @DisplayName("should parse method declaration")
        fun shouldParseMethodDeclaration() {
            val source =
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.symbols)
                .anyMatch { it.name == "getName" && it.kind == SymbolInfo.SymbolKind.METHOD }
        }

        @Test
        @DisplayName("should detect ERROR markers")
        fun shouldDetectErrorMarkers() {
            val source =
                """
                module myapp {
                    // ERROR: undefined variable 'x'
                }
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.success).isFalse()
            assertThat(result.diagnostics)
                .hasSize(1)
                .allMatch { it.severity == Diagnostic.Severity.ERROR }
                .anyMatch { it.message.contains("undefined variable") }
        }

        @Test
        @DisplayName("should detect unmatched braces")
        fun shouldDetectUnmatchedBraces() {
            val source =
                """
                module myapp {
                    class Person {
                """.trimIndent()

            val result = adapter.compile("file:///test.x", source)

            assertThat(result.diagnostics)
                .anyMatch { it.message.contains("Unmatched") }
        }
    }

    @Nested
    @DisplayName("getHoverInfo()")
    inner class HoverTests {
        @Test
        @DisplayName("should return hover info for class")
        fun shouldReturnHoverInfoForClass() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val hover = adapter.getHoverInfo("file:///test.x", 1, 10)

            assertThat(hover).isNotNull()
            assertThat(hover).contains("class Person")
        }

        @Test
        @DisplayName("should return null for unknown position")
        fun shouldReturnNullForUnknownPosition() {
            val source =
                """
                module myapp {
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val hover = adapter.getHoverInfo("file:///test.x", 100, 0)

            assertThat(hover).isNull()
        }
    }

    @Nested
    @DisplayName("getCompletions()")
    inner class CompletionTests {
        @Test
        @DisplayName("should return keywords")
        fun shouldReturnKeywords() {
            val completions = adapter.getCompletions("file:///test.x", 0, 0)

            assertThat(completions)
                .anyMatch { it.label == "class" }
                .anyMatch { it.label == "interface" }
                .anyMatch { it.label == "module" }
        }

        @Test
        @DisplayName("should return built-in types")
        fun shouldReturnBuiltInTypes() {
            val completions = adapter.getCompletions("file:///test.x", 0, 0)

            assertThat(completions)
                .anyMatch { it.label == "String" }
                .anyMatch { it.label == "Int" }
                .anyMatch { it.label == "Boolean" }
        }

        @Test
        @DisplayName("should include document symbols")
        fun shouldIncludeDocumentSymbols() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val completions = adapter.getCompletions("file:///test.x", 3, 0)

            assertThat(completions).anyMatch { it.label == "Person" }
        }
    }

    @Nested
    @DisplayName("findDefinition()")
    inner class DefinitionTests {
        @Test
        @DisplayName("should find definition of class")
        fun shouldFindDefinitionOfClass() {
            val source =
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent()

            adapter.compile("file:///test.x", source)
            val definition = adapter.findDefinition("file:///test.x", 1, 10)

            assertThat(definition).isNotNull()
            assertThat(definition!!.startLine).isEqualTo(1)
        }
    }
}
