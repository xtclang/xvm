package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.lsp.model.SymbolInfo

/**
 * Contextual declaration keywords remain legal variable names in expressions. In particular,
 * `return True, module;` exposed a grammar ambiguity with keyword types in repository lookup.
 * Compile the same source with javatools to check language validity, then exercise the native
 * parser through the adapter so grammar generation and loading are covered too.
 */
class TreeSitterContextKeywordTest : TreeSitterTestBase() {
    @ParameterizedTest(name = "{0} remains a variable name in expressions")
    @ValueSource(strings = ["module", "package", "class", "interface", "mixin", "service", "const", "enum", "struct", "val", "var"])
    fun contextualKeywordsRemainVariableNames(keyword: String) {
        val source =
            """
            module KeywordNames {
                import ecstasy.reflect.ModuleTemplate;

                conditional ModuleTemplate lookup(ModuleTemplate candidate) {
                    return True, candidate;
                }

                conditional ModuleTemplate find(ModuleTemplate candidate) {
                    if (ModuleTemplate $keyword := lookup(candidate)) {
                        return True, $keyword;
                    }
                    return False;
                }

                ModuleTemplate identity(ModuleTemplate $keyword) {
                    return $keyword;
                }

                void consume(ModuleTemplate $keyword) {
                    identity($keyword);
                    assert !$keyword.qualifiedName.empty;
                    assert $keyword == identity($keyword);
                }
            }
            """.trimIndent()

        CompilerTestSupport.configure()
        val errors = ErrorList(100)
        val compiled = EmbeddingSupport.instance().compile(source, null, errors)
        assertThat(errors.errors).isEmpty()
        assertThat(compiled).isNotNull()

        val result = ts.compile(freshUri(), source)

        assertThat(result.diagnostics).isEmpty()
        assertThat(result.success).isTrue()
        assertThat(result.symbols)
            .anyMatch { it.name == "KeywordNames" && it.kind == SymbolInfo.SymbolKind.MODULE }
    }
}
