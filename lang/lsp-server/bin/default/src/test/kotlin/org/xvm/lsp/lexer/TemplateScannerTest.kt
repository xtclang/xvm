package org.xvm.lsp.lexer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("TemplateScanner")
class TemplateScannerTest {
    private val scanner = TemplateScanner()

    @Nested
    @DisplayName("Simple template strings")
    inner class SimpleTemplateStrings {
        @Test
        @DisplayName("should tokenize empty template string")
        fun emptyTemplateString() {
            val source = "\$\"\""
            val tokens = scanner.tokenize(source)

            assertThat(tokens)
                .describedAs("Source: '$source' (length=${source.length}), chars: ${source.map { it.code }}")
                .hasSize(2)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should tokenize template string without expressions")
        fun templateWithoutExpressions() {
            val source = """${'$'}"Hello World""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(3)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Hello World")
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should tokenize template with single expression")
        fun templateWithSingleExpression() {
            val source = """${'$'}"Hello {name}!""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(6)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Hello ")
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            assertThat(tokens[4].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[4].value).isEqualTo("!")
            assertThat(tokens[5].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should tokenize template with expression at start")
        fun templateWithExpressionAtStart() {
            val source = """${'$'}"{name} is here""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(5)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[3].value).isEqualTo(" is here")
            assertThat(tokens[4].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should tokenize template with expression at end")
        fun templateWithExpressionAtEnd() {
            val source = """${'$'}"Hello {name}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(5)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Hello ")
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            assertThat(tokens[4].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should tokenize template with only expression")
        fun templateWithOnlyExpression() {
            val source = """${'$'}"{value}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(4)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should tokenize template with multiple expressions")
        fun templateWithMultipleExpressions() {
            val source = """${'$'}"{a} + {b} = {c}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(10)
            // `$"` start
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            // {a}
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            // content: " + "
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[3].value).isEqualTo(" + ")
            // {b}
            assertThat(tokens[4].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[5].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            // content: " = "
            assertThat(tokens[6].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[6].value).isEqualTo(" = ")
            // {c}
            assertThat(tokens[7].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[8].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            // `"` end
            assertThat(tokens[9].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }
    }

    @Nested
    @DisplayName("Escape sequences")
    inner class EscapeSequences {
        @Test
        @DisplayName("should handle escaped brace")
        fun escapedBrace() {
            // Note: Only \{ is a valid escape in XTC templates (not \})
            val source = """${'$'}"Use \{braces}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(3)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Use {braces}")
        }

        @Test
        @DisplayName("should handle escaped backslash")
        fun escapedBackslash() {
            val source = """${'$'}"Path: C:\\Users""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(3)
            assertThat(tokens[1].value).isEqualTo("Path: C:\\Users")
        }

        @Test
        @DisplayName("should handle escaped quote")
        fun escapedQuote() {
            val source = """${'$'}"Say \"Hello\"""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(3)
            assertThat(tokens[1].value).isEqualTo("Say \"Hello\"")
        }

        @Test
        @DisplayName("should handle newline escape")
        fun newlineEscape() {
            val source = """${'$'}"Line1\nLine2""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(3)
            assertThat(tokens[1].value).isEqualTo("Line1\nLine2")
        }

        @Test
        @DisplayName("should handle tab escape")
        fun tabEscape() {
            val source = """${'$'}"Col1\tCol2""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(3)
            assertThat(tokens[1].value).isEqualTo("Col1\tCol2")
        }
    }

    @Nested
    @DisplayName("Nested braces in expressions")
    inner class NestedBraces {
        @Test
        @DisplayName("should handle nested braces in expression")
        fun nestedBraces() {
            val source = """${'$'}"Result: {map.get({key})}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(5)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Result: ")
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
            assertThat(tokens[4].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should handle lambda with braces in expression")
        fun lambdaInExpression() {
            val source = """${'$'}"Sum: {items.map(x -> { return x * 2; }).sum()}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(5)
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
        }

        @Test
        @DisplayName("should handle string with braces inside expression")
        fun stringWithBracesInExpression() {
            val source = """${'$'}"Value: {format("{0}")}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(5)
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_START)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
        }
    }

    @Nested
    @DisplayName("Comments in expressions")
    inner class CommentsInExpressions {
        @Test
        @DisplayName("should skip single-line comments in multiline expression")
        fun singleLineCommentInMultiline() {
            // Multiline template allows newlines in expressions
            val source = "\$|Value: {\n x // this is } not the end\n |}"
            val tokens = scanner.tokenize(source)

            // Should find the closing brace after the comment
            val exprEndTokens = tokens.filter { it.type == TemplateTokenType.TEMPLATE_EXPR_END }
            assertThat(exprEndTokens).hasSize(1)
        }

        @Test
        @DisplayName("should skip block comments in expression")
        fun blockComment() {
            val source = """${'$'}"Value: {x /* } */ + y}""""
            val tokens = scanner.tokenize(source)

            assertThat(tokens).hasSize(5)
            assertThat(tokens[3].type).isEqualTo(TemplateTokenType.TEMPLATE_EXPR_END)
        }
    }

    @Nested
    @DisplayName("Multiple template strings in source")
    inner class MultipleTemplates {
        @Test
        @DisplayName("should tokenize multiple template strings")
        fun multipleTemplates() {
            val source =
                """
                val a = ${'$'}"Hello {name}";
                val b = ${'$'}"World";
                """.trimIndent()
            val tokens = scanner.tokenize(source)

            val templateStarts = tokens.filter { it.type == TemplateTokenType.TEMPLATE_START }
            val templateEnds = tokens.filter { it.type == TemplateTokenType.TEMPLATE_END }

            assertThat(templateStarts).hasSize(2)
            assertThat(templateEnds).hasSize(2)
        }

        @Test
        @DisplayName("should handle regular strings between templates")
        fun regularStringsBetween() {
            val source = """val x = "not a template"; val y = ${'$'}"template {v}"; val z = "also not";"""
            val tokens = scanner.tokenize(source)

            // Should only find one template
            val templateStarts = tokens.filter { it.type == TemplateTokenType.TEMPLATE_START }
            assertThat(templateStarts).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Multiline templates")
    inner class MultilineTemplates {
        @Test
        @DisplayName("should tokenize multiline template start")
        fun multilineStart() {
            val source = "\$|Hello World"
            val tokens = scanner.tokenize(source)

            assertThat(tokens)
                .describedAs("Tokens for multiline template: $tokens")
                .hasSize(3)
            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_MULTILINE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Hello World")
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }

        @Test
        @DisplayName("should handle multiline continuation")
        fun multilineContinuation() {
            val source = "\$|Line 1\n |Line 2"
            val tokens = scanner.tokenize(source)

            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_MULTILINE_START)
            // Content should include both lines with the newline
            val contentTokens = tokens.filter { it.type == TemplateTokenType.TEMPLATE_CONTENT }
            assertThat(contentTokens).hasSize(1)
            assertThat(contentTokens[0].value).isEqualTo("Line 1\nLine 2")
        }

        @Test
        @DisplayName("should end multiline when no continuation")
        fun multilineEndWithoutContinuation() {
            val source = "\$|Line 1\nNo continuation"
            val tokens = scanner.tokenize(source)

            assertThat(tokens[0].type).isEqualTo(TemplateTokenType.TEMPLATE_MULTILINE_START)
            assertThat(tokens[1].type).isEqualTo(TemplateTokenType.TEMPLATE_CONTENT)
            assertThat(tokens[1].value).isEqualTo("Line 1")
            assertThat(tokens[2].type).isEqualTo(TemplateTokenType.TEMPLATE_END)
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should report error for unterminated template")
        fun unterminatedTemplate() {
            val source = """${'$'}"Hello {name"""
            val tokens = scanner.tokenize(source)

            val errorTokens = tokens.filter { it.type == TemplateTokenType.ERROR }
            assertThat(errorTokens).isNotEmpty()
        }

        @Test
        @DisplayName("should report error for unclosed expression")
        fun unclosedExpression() {
            val source = """${'$'}"Hello {name""""
            val tokens = scanner.tokenize(source)

            val errorTokens = tokens.filter { it.type == TemplateTokenType.ERROR }
            assertThat(errorTokens).isNotEmpty()
        }
    }

    @Nested
    @DisplayName("Token positions")
    inner class TokenPositions {
        @Test
        @DisplayName("should have correct positions for template start")
        fun templateStartPosition() {
            val source = """val x = ${'$'}"hello""""
            val tokens = scanner.tokenize(source)

            val start = tokens.first { it.type == TemplateTokenType.TEMPLATE_START }
            assertThat(start.startOffset).isEqualTo(8) // position of $
            assertThat(start.endOffset).isEqualTo(10) // after $"
        }

        @Test
        @DisplayName("should have correct positions for content")
        fun contentPosition() {
            val source = """${'$'}"hello""""
            val tokens = scanner.tokenize(source)

            val content = tokens.first { it.type == TemplateTokenType.TEMPLATE_CONTENT }
            assertThat(content.startOffset).isEqualTo(2) // after $"
            assertThat(content.endOffset).isEqualTo(7) // before "
            assertThat(content.length).isEqualTo(5)
        }

        @Test
        @DisplayName("should have correct positions for expression markers")
        fun expressionMarkerPositions() {
            val source = """${'$'}"{x}""""
            val tokens = scanner.tokenize(source)

            val exprStart = tokens.first { it.type == TemplateTokenType.TEMPLATE_EXPR_START }
            val exprEnd = tokens.first { it.type == TemplateTokenType.TEMPLATE_EXPR_END }

            assertThat(exprStart.startOffset).isEqualTo(2) // position of {
            assertThat(exprStart.endOffset).isEqualTo(3)
            assertThat(exprEnd.startOffset).isEqualTo(4) // position of }
            assertThat(exprEnd.endOffset).isEqualTo(5)
        }
    }

    @Nested
    @DisplayName("State immutability")
    inner class StateImmutability {
        @Test
        @DisplayName("scanner state should be immutable (sealed interface)")
        fun stateImmutability() {
            // States are data classes - copy creates new instances
            val state1: ScanState = ScanState.Normal(0)
            val state2: ScanState = ScanState.Normal(5)
            val state3: ScanState = ScanState.Template(0, multiline = false)

            assertThat(state1.pos).isEqualTo(0)
            assertThat(state2.pos).isEqualTo(5)
            assertThat(state1).isInstanceOf(ScanState.Normal::class.java)
            assertThat(state3).isInstanceOf(ScanState.Template::class.java)

            // Verify copy creates new instance
            val template = ScanState.Template(10, multiline = true)
            val copied = template.copy(pos = 20)
            assertThat(template.pos).isEqualTo(10)
            assertThat(copied.pos).isEqualTo(20)
        }

        @Test
        @DisplayName("scanner should be thread-safe")
        fun threadSafety() {
            val source = """${'$'}"Hello {name}!""""

            // Run multiple tokenizations concurrently
            val results =
                (1..10).map {
                    Thread {
                        val tokens = scanner.tokenize(source)
                        assertThat(tokens).hasSize(6)
                    }
                }

            results.forEach { it.start() }
            results.forEach { it.join() }
        }
    }
}
