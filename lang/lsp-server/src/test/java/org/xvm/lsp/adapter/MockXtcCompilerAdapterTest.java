package org.xvm.lsp.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xvm.lsp.model.CompilationResult;
import org.xvm.lsp.model.Diagnostic;
import org.xvm.lsp.model.SymbolInfo;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockXtcCompilerAdapter")
class MockXtcCompilerAdapterTest {

    private MockXtcCompilerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MockXtcCompilerAdapter();
    }

    @Nested
    @DisplayName("compile()")
    class CompileTests {

        @Test
        @DisplayName("should parse module declaration")
        void shouldParseModuleDeclaration() {
            final String source = """
                    module myapp {
                    }
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.success()).isTrue();
            assertThat(result.diagnostics()).isEmpty();
            assertThat(result.symbols())
                    .anyMatch(s -> s.name().equals("myapp") &&
                            s.kind() == SymbolInfo.SymbolKind.MODULE);
        }

        @Test
        @DisplayName("should parse class declaration")
        void shouldParseClassDeclaration() {
            final String source = """
                    module myapp {
                        class Person {
                        }
                    }
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.success()).isTrue();
            assertThat(result.symbols())
                    .anyMatch(s -> s.name().equals("Person") &&
                            s.kind() == SymbolInfo.SymbolKind.CLASS);
        }

        @Test
        @DisplayName("should parse interface declaration")
        void shouldParseInterfaceDeclaration() {
            final String source = """
                    module myapp {
                        interface Runnable {
                        }
                    }
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.symbols())
                    .anyMatch(s -> s.name().equals("Runnable") &&
                            s.kind() == SymbolInfo.SymbolKind.INTERFACE);
        }

        @Test
        @DisplayName("should parse service declaration")
        void shouldParseServiceDeclaration() {
            final String source = """
                    module myapp {
                        service UserService {
                        }
                    }
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.symbols())
                    .anyMatch(s -> s.name().equals("UserService") &&
                            s.kind() == SymbolInfo.SymbolKind.SERVICE);
        }

        @Test
        @DisplayName("should parse method declaration")
        void shouldParseMethodDeclaration() {
            final String source = """
                    module myapp {
                        class Person {
                            String getName() {
                                return name;
                            }
                        }
                    }
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.symbols())
                    .anyMatch(s -> s.name().equals("getName") &&
                            s.kind() == SymbolInfo.SymbolKind.METHOD);
        }

        @Test
        @DisplayName("should detect ERROR markers")
        void shouldDetectErrorMarkers() {
            final String source = """
                    module myapp {
                        // ERROR: undefined variable 'x'
                    }
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.success()).isFalse();
            assertThat(result.diagnostics())
                    .hasSize(1)
                    .allMatch(d -> d.severity() == Diagnostic.Severity.ERROR)
                    .anyMatch(d -> d.message().contains("undefined variable"));
        }

        @Test
        @DisplayName("should detect unmatched braces")
        void shouldDetectUnmatchedBraces() {
            final String source = """
                    module myapp {
                        class Person {
                    """;

            final CompilationResult result = adapter.compile("file:///test.x", source);

            assertThat(result.diagnostics())
                    .anyMatch(d -> d.message().contains("Unmatched"));
        }
    }

    @Nested
    @DisplayName("getHoverInfo()")
    class HoverTests {

        @Test
        @DisplayName("should return hover info for class")
        void shouldReturnHoverInfoForClass() {
            final String source = """
                    module myapp {
                        class Person {
                        }
                    }
                    """;

            adapter.compile("file:///test.x", source);
            final var hover = adapter.getHoverInfo("file:///test.x", 1, 10);

            assertThat(hover).isPresent();
            assertThat(hover.get()).contains("class Person");
        }

        @Test
        @DisplayName("should return empty for unknown position")
        void shouldReturnEmptyForUnknownPosition() {
            final String source = """
                    module myapp {
                    }
                    """;

            adapter.compile("file:///test.x", source);
            final var hover = adapter.getHoverInfo("file:///test.x", 100, 0);

            assertThat(hover).isEmpty();
        }
    }

    @Nested
    @DisplayName("getCompletions()")
    class CompletionTests {

        @Test
        @DisplayName("should return keywords")
        void shouldReturnKeywords() {
            final var completions = adapter.getCompletions("file:///test.x", 0, 0);

            assertThat(completions)
                    .anyMatch(c -> c.label().equals("class"))
                    .anyMatch(c -> c.label().equals("interface"))
                    .anyMatch(c -> c.label().equals("module"));
        }

        @Test
        @DisplayName("should return built-in types")
        void shouldReturnBuiltInTypes() {
            final var completions = adapter.getCompletions("file:///test.x", 0, 0);

            assertThat(completions)
                    .anyMatch(c -> c.label().equals("String"))
                    .anyMatch(c -> c.label().equals("Int"))
                    .anyMatch(c -> c.label().equals("Boolean"));
        }

        @Test
        @DisplayName("should include document symbols")
        void shouldIncludeDocumentSymbols() {
            final String source = """
                    module myapp {
                        class Person {
                        }
                    }
                    """;

            adapter.compile("file:///test.x", source);
            final var completions = adapter.getCompletions("file:///test.x", 3, 0);
            assertThat(completions).anyMatch(c -> c.label().equals("Person"));
        }
    }

    @Nested
    @DisplayName("findDefinition()")
    class DefinitionTests {

        @Test
        @DisplayName("should find definition of class")
        void shouldFindDefinitionOfClass() {
            final String source = """
                    module myapp {
                        class Person {
                        }
                    }
                    """;

            adapter.compile("file:///test.x", source);
            final var definition = adapter.findDefinition("file:///test.x", 1, 10);

            assertThat(definition).isPresent();
            assertThat(definition.get().startLine()).isEqualTo(1);
        }
    }
}
