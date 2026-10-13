import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Distinguishes real binary operators from similar punctuation in Java syntax and embedded text. */
class JavaBinaryOperatorSpacingTest {
    @ParameterizedTest
    @ValueSource(strings = ["*", "/", "%", "+", "-", "<<", ">>", ">>>", "<", ">", "<=", ">=", "==", "!=", "&", "^", "|", "&&", "||"])
    fun `spaces both sides of each binary operator`(operator: String) {
        val source = "class Sample { Object f(int x, int y) { return x${operator}y; } }"
        val expected = "class Sample { Object f(int x, int y) { return x $operator y; } }"
        assertFormat(expected, source)
    }

    @Test
    fun `distinguishes unary signs and generic brackets from binary operators`() {
        val source = """
            class Sample {
                java.util.Map<String, java.util.List<Integer>> values;
                int f(int x, int y) { return x-- - -y + -2 + ++x + (x>>2); }
                int g(int x) { return -x + ~x + +x; }
            }
        """.trimIndent()
        assertFormat(source.replace("x>>2", "x >> 2"), source)
    }

    @Test
    fun `keeps comments and literal contents verbatim`() {
        val source = "class Sample { String s = \"x-2\"; char c = '-'; int f(int x) { return x/* x-2 */-/* x+1 */2; } }"
        val expected = source.replace("*/-/*", "*/ - /*")
        assertFormat(expected, source)
        val block = "class Sample { String s = \"\"\"\n    x-2\n    \"\"\"; int f(int x) { return x-2; } }"
        assertFormat(block.replace("return x-2", "return x - 2"), block)
    }

    @Test
    fun `spaces concatenation even when javac folds adjacent string literals`() {
        val source = "class Sample { String text = \"x+2\"+/* keep + */\"y-2\"+\"z\"; }"
        val expected = "class Sample { String text = \"x+2\" + /* keep + */\"y-2\" + \"z\"; }"
        assertFormat(expected, source)
    }

    @Test
    fun `preserves extra padding and line breaks`() {
        val source = "class Sample { int f(int x) { return x  -\n    2; } }\r\n"
        assertFormat(source, source)
    }

    @Test
    fun `spaces nested binary expressions without changing lambda arrows or increments`() {
        val source = "class Sample { void f() { java.util.function.IntUnaryOperator f = x -> (x+2)*(x-2); for (int i=0; i<2; ++i) {} } }"
        val expected = source.replace("(x+2)*(x-2)", "(x + 2) * (x - 2)").replace("i<2", "i < 2")
        assertFormat(expected, source)
    }

    @Test
    fun `leaves malformed Java and Unicode escapes unchanged`() {
        val malformed = "class Sample { int f(int x) { return x-2 +; } }"
        assertFormat(malformed, malformed)
        val escaped = "class Sample { int f(int x) { return x\\u002d2; } }"
        assertFormat(escaped, escaped)
    }

    @Test
    fun `Unicode escapes elsewhere in a file do not disable operator spacing`() {
        val source = "class Sample { String s = \"\\u003d\"; int f(int x) { return x-2; } }"
        assertFormat(source.replace("return x-2", "return x - 2"), source)
    }

    private fun assertFormat(expected: String, source: String) {
        assertEquals(expected, JavaBinaryOperatorSpacing.format(source))
        assertEquals(expected, JavaBinaryOperatorSpacing.format(expected))
    }
}
