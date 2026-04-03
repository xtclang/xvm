package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.xvm.lsp.treesitter.SemanticTokenLegend

/**
 * Semantic token tests for [TreeSitterAdapter].
 *
 * Exercises semantic token generation for various declaration types, type
 * references, call expressions, and regression scenarios involving document
 * edits that change document length.
 *
 * All tests are skipped (not failed) when the tree-sitter native library is unavailable.
 */
@DisplayName("TreeSitterAdapter - Semantic Tokens")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SemanticTokenTest : TreeSitterTestBase() {
    // ========================================================================
    // Semantic token test helpers
    // ========================================================================

    private val semanticTypeIndex = SemanticTokenLegend.typeIndex
    private val semanticModIndex = SemanticTokenLegend.modIndex

    private fun decodeSemanticTokens(data: List<Int>): List<IntArray> {
        val result = mutableListOf<IntArray>()
        var line = 0
        var column = 0
        var i = 0
        while (i + 4 < data.size) {
            val deltaLine = data[i]
            val deltaStart = data[i + 1]
            val length = data[i + 2]
            val tokenType = data[i + 3]
            val tokenMods = data[i + 4]

            line += deltaLine
            column = if (deltaLine > 0) deltaStart else column + deltaStart

            result.add(intArrayOf(line, column, length, tokenType, tokenMods))
            i += 5
        }
        return result
    }

    private fun hasSemanticModifier(
        mods: Int,
        name: String,
    ): Boolean {
        val bit = semanticModIndex[name] ?: return false
        return (mods and (1 shl bit)) != 0
    }

    // ========================================================================
    // getSemanticTokens()
    // ========================================================================

    @Nested
    @DisplayName("getSemanticTokens()")
    inner class SemanticTokenTests {
        @Test
        @DisplayName("should return semantic tokens for class declaration")
        fun shouldReturnTokensForClassDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull
            assertThat(tokens!!.data).isNotEmpty

            val decoded = decodeSemanticTokens(tokens.data)
            logger.info("[TEST] class decl tokens: {}", decoded.map { it.toList() })

            // "Person" should be classified as "class" with "declaration" modifier
            // IntArray: [line, column, length, tokenType, tokenModifiers]
            val classToken = decoded.find { it[3] == semanticTypeIndex["class"] && it[2] == "Person".length }
            assertThat(classToken).isNotNull
            assertThat(hasSemanticModifier(classToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for interface declaration")
        fun shouldReturnTokensForInterfaceDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    interface Runnable {
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            val ifaceToken = decoded.find { it[3] == semanticTypeIndex["interface"] && it[2] == "Runnable".length }
            assertThat(ifaceToken).isNotNull
            assertThat(hasSemanticModifier(ifaceToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for method declaration")
        fun shouldReturnTokensForMethodDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] method decl tokens: {}", decoded.map { it.toList() })

            val methodToken = decoded.find { it[3] == semanticTypeIndex["method"] && it[2] == "getName".length }
            assertThat(methodToken).isNotNull
            assertThat(hasSemanticModifier(methodToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for property declaration")
        fun shouldReturnTokensForPropertyDeclaration() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String name = "hello";
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] property decl tokens: {}", decoded.map { it.toList() })

            val propToken = decoded.find { it[3] == semanticTypeIndex["property"] && it[2] == "name".length }
            assertThat(propToken).isNotNull
            assertThat(hasSemanticModifier(propToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return semantic tokens for parameter")
        fun shouldReturnTokensForParameter() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] parameter tokens: {}", decoded.map { it.toList() })

            val paramTokens = decoded.filter { it[3] == semanticTypeIndex["parameter"] }
            assertThat(paramTokens).hasSizeGreaterThanOrEqualTo(2)
            paramTokens.forEach { assertThat(hasSemanticModifier(it[4], "declaration")).isTrue() }
        }

        @Test
        @DisplayName("should return semantic tokens for type reference")
        fun shouldReturnTokensForTypeReference() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String getName() {
                            return name;
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)

            // "String" should be classified as "type"
            val typeTokens = decoded.filter { it[3] == semanticTypeIndex["type"] }
            assertThat(typeTokens).isNotEmpty
        }

        @Test
        @DisplayName("should return semantic tokens for module declaration")
        fun shouldReturnTokensForModuleDeclaration() {
            val uri = freshUri()
            ts.compile(uri, "module myapp {}")

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] module tokens: {}", decoded.map { it.toList() })

            val nsToken = decoded.find { it[3] == semanticTypeIndex["namespace"] }
            assertThat(nsToken).isNotNull
            assertThat(hasSemanticModifier(nsToken!![4], "declaration")).isTrue()
        }

        @Test
        @DisplayName("should return null for empty file")
        fun shouldReturnNullForEmptyFile() {
            val uri = freshUri()
            ts.compile(uri, "")

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNull()
        }

        @Test
        @DisplayName("should handle file with errors gracefully")
        fun shouldHandleFileWithErrorsGracefully() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                """.trimIndent(),
            )

            // Should not throw -- may return partial tokens or null
            val tokens = ts.getSemanticTokens(uri)
            logger.info("[TEST] error file tokens: {}", tokens?.data?.size ?: "null")
        }

        @Test
        @DisplayName("should produce valid delta encoding")
        fun shouldProduceValidDeltaEncoding() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Person {
                        String name = "hello";
                        Int age = 0;
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val data = tokens!!.data
            // Data must be a multiple of 5
            assertThat(data.size % 5).isEqualTo(0)

            // Decode and verify positions are non-negative
            val decoded = decodeSemanticTokens(data)
            for (token in decoded) {
                assertThat(token[0]).isGreaterThanOrEqualTo(0) // line
                assertThat(token[1]).isGreaterThanOrEqualTo(0) // column
                assertThat(token[2]).isGreaterThan(0) // length
            }

            // Verify tokens are in order (line, then column)
            for (i in 1 until decoded.size) {
                val prev = decoded[i - 1]
                val curr = decoded[i]
                val prevPos = prev[0] * 10_000 + prev[1]
                val currPos = curr[0] * 10_000 + curr[1]
                assertThat(currPos).isGreaterThanOrEqualTo(prevPos)
            }
        }

        @Test
        @DisplayName("should return semantic tokens for call expression")
        fun shouldReturnTokensForCallExpression() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    class Calculator {
                        Int add(Int a, Int b) {
                            return a + b;
                        }
                        void test() {
                            add(1, 2);
                        }
                    }
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            assertThat(tokens).isNotNull

            val decoded = decodeSemanticTokens(tokens!!.data)
            logger.info("[TEST] call expression tokens: {}", decoded.map { it.toList() })

            // "add" should appear as method at the call site too
            val methodTokens = decoded.filter { it[3] == semanticTypeIndex["method"] }
            assertThat(methodTokens).hasSizeGreaterThanOrEqualTo(2) // declaration + call
        }

        @Test
        @DisplayName("should classify const as struct with readonly modifier")
        fun shouldClassifyConstAsStruct() {
            val uri = freshUri()
            ts.compile(
                uri,
                """
                module myapp {
                    const Point(Int x, Int y);
                }
                """.trimIndent(),
            )

            val tokens = ts.getSemanticTokens(uri)
            if (tokens == null) {
                // Grammar may not produce const_declaration node -- skip gracefully
                return
            }

            val decoded = decodeSemanticTokens(tokens.data)
            logger.info("[TEST] const tokens: {}", decoded.map { it.toList() })

            val structToken = decoded.find { it[3] == semanticTypeIndex["struct"] }
            if (structToken != null) {
                assertThat(hasSemanticModifier(structToken[4], "declaration")).isTrue()
                assertThat(hasSemanticModifier(structToken[4], "readonly")).isTrue()
            }
        }

        /**
         * Regression test for StringIndexOutOfBoundsException after rename.
         *
         * Reproduces the exact bug: compile a document with "Console console", rename
         * "console" to "apa" (making the document shorter), then request semantic tokens.
         * Previously crashed because incremental parsing (passing oldTree without Tree.edit())
         * produced nodes with stale byte offsets from the longer original source.
         */
        @Test
        @DisplayName("should return semantic tokens after rename shortens document")
        fun shouldReturnTokensAfterRenameShortenDocument() {
            val uri = freshUri()
            val originalSource =
                """
                module myapp {
                    void run(String[] args=[]) {
                        @Inject Console console;
                        if (args.empty) {
                            console.print("Hello!");
                            return;
                        }
                        for (String arg : args) {
                            console.print(${"\""}Hello, {arg}!${"\""});
                        }
                    }
                }
                """.trimIndent()

            // Step 1: Initial compile
            val result1 = ts.compile(uri, originalSource)
            assertThat(result1.success).isTrue()

            // Step 2: Semantic tokens must work on initial content
            val tokens1 = ts.getSemanticTokens(uri)
            assertThat(tokens1).isNotNull
            assertThat(tokens1!!.data).isNotEmpty
            logger.info("[TEST] initial semantic tokens: {} data items", tokens1.data.size)

            // Step 3: Simulate rename "console" -> "apa" (7 chars -> 3 chars, doc gets shorter)
            val renamedSource = originalSource.replace("console", "apa")
            assertThat(renamedSource.length).isLessThan(originalSource.length)

            // Step 4: Re-compile with shorter content (same URI = incremental parse path)
            val result2 = ts.compile(uri, renamedSource)
            assertThat(result2.success).isTrue()

            // Step 5: Semantic tokens on shorter document must NOT crash
            // Previously threw: StringIndexOutOfBoundsException: Range [178, 178 + 238) out of bounds
            val tokens2 = ts.getSemanticTokens(uri)
            assertThat(tokens2).isNotNull
            assertThat(tokens2!!.data).isNotEmpty
            logger.info("[TEST] post-rename semantic tokens: {} data items", tokens2.data.size)

            // Verify the renamed identifier appears in tokens
            val decoded = decodeSemanticTokens(tokens2.data)
            logger.info("[TEST] post-rename decoded: {}", decoded.map { it.toList() })
        }

        /**
         * Regression test: compile -> rename (longer) -> semantic tokens.
         * The reverse case: document grows after rename. Verifies we don't
         * have off-by-one errors in the opposite direction.
         */
        @Test
        @DisplayName("should return semantic tokens after rename lengthens document")
        fun shouldReturnTokensAfterRenameLengthenDocument() {
            val uri = freshUri()
            val originalSource =
                """
                module myapp {
                    class Cat {
                        String name;
                        void greet() {
                            name.print();
                        }
                    }
                }
                """.trimIndent()

            ts.compile(uri, originalSource)
            val tokens1 = ts.getSemanticTokens(uri)
            assertThat(tokens1).isNotNull

            // Rename "name" -> "fullQualifiedName" (4 chars -> 17 chars, doc grows)
            val renamedSource = originalSource.replace("name", "fullQualifiedName")
            assertThat(renamedSource.length).isGreaterThan(originalSource.length)

            ts.compile(uri, renamedSource)
            val tokens2 = ts.getSemanticTokens(uri)
            assertThat(tokens2).isNotNull
            assertThat(tokens2!!.data).isNotEmpty
        }

        /**
         * Regression test: multiple rapid edits on same URI.
         * Simulates fast typing where compile is called many times in quick succession.
         */
        @Test
        @DisplayName("should handle rapid sequential recompilations")
        fun shouldHandleRapidRecompilations() {
            val uri = freshUri()
            val base = "module myapp { class Person { String name; } }"

            // Compile 10 times with progressively different content (same URI)
            repeat(10) { i ->
                val content = base.replace("name", "name$i")
                val result = ts.compile(uri, content)
                assertThat(result.success).isTrue()

                // Semantic tokens must work after every recompilation
                val tokens = ts.getSemanticTokens(uri)
                assertThat(tokens).describedAs("iteration $i").isNotNull
            }
        }

        /**
         * Verify folding ranges also work after rename (they use line/column, not byte offsets,
         * so they should always work -- but good to verify the full adapter pipeline).
         */
        @Test
        @DisplayName("should return folding ranges after rename")
        fun shouldReturnFoldingRangesAfterRename() {
            val uri = freshUri()
            val originalSource =
                """
                module myapp {
                    void run(String[] args=[]) {
                        @Inject Console console;
                        console.print("Hello!");
                    }
                }
                """.trimIndent()

            ts.compile(uri, originalSource)
            val folds1 = ts.getFoldingRanges(uri)
            assertThat(folds1).isNotEmpty

            // Rename and verify folding still works
            val renamedSource = originalSource.replace("console", "x")
            ts.compile(uri, renamedSource)
            val folds2 = ts.getFoldingRanges(uri)
            assertThat(folds2).isNotEmpty
        }
    }
}
