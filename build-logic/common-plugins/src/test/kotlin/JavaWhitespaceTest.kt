import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Protects the whitespace rules' boundaries: literal contents, column groups and idempotence. */
class JavaWhitespaceTest {
    @Test
    fun `leaves Unicode escape tokenization to a Java parser`() {
        val source = "        String text = \\u0022example\\u0022;\n        Code                 code     = create();\n"
        assertEquals(source, JavaWhitespace.format(source))
    }

    @Test
    fun `removes orphaned padding but retains intentional columns`() {
        val source = """
            class Sample {
                int       field = 1;
                void run() {
                    Code                 code     = create();
                    use(code);
                    boolean       first = true;
                    boolean       complete = false;
                    use(first, complete);
                    TargetInfo target = find();
                    Register   register;
                    this.first    = first;
                    this.complete = complete;
                    use(register);
                    target   = null;
                }
            }
        """.trimIndent()
        val expected = source
            .replace("Code                 code     =", "Code code =")
            .replace("boolean       ", "boolean ")
            .replace("target   = null", "target = null")
        assertEquals(expected, JavaWhitespace.format(source))
        assertEquals(expected, JavaWhitespace.format(expected))
    }

    @Test
    fun `removes stray blank lines before bare jumps and else but keeps return paragraphs`() {
        val source = "        work();\n\n        continue;\n        work();\n\n        break;\n        work();\n\n        return;\n        work();\n\n    } else {\n        work();\n\n        return value;\n"
        val expected = source.replace(";\n\n        continue", ";\n        continue")
            .replace(";\n\n        break", ";\n        break")
            .replace(";\n\n        return;", ";\n        return;")
            .replace(";\n\n    } else", ";\n    } else")
        assertEquals(expected, JavaWhitespace.format(source))
        assertEquals(expected.replace("\n", "\r\n"), JavaWhitespace.format(source.replace("\n", "\r\n")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["break;", "continue;", "return;"])
    fun `removes blank lines between a closed block and a bare jump`(jump: String) {
        val source = "        if (ready) {\n            work();\n        }\n\n        $jump\n"
        val expected = source.replace("}\n\n", "}\n")
        assertEquals(expected, JavaWhitespace.format(source))
        assertEquals(expected, JavaWhitespace.format(expected))
        assertEquals(expected.replace("\n", "\r\n"), JavaWhitespace.format(source.replace("\n", "\r\n")))
    }

    @Test
    fun `keeps paragraphs before return values and jumps outside the closed block's scope`() {
        val source = "        }\n\n    break;\n        }\n\n        return value;\n"
        assertEquals(source, JavaWhitespace.format(source))
    }

    @Test
    fun `preserves comments strings characters and text blocks`() {
        val quote = "\"\"\""
        val source = """
            class Sample {
                void run() {
                    // Code                 code     = create();
                    /*
                    Code                 code     = create();
                    work();

                    return;
                    }

                    break;
                    */
                    String text = $quote
                    Code                 code     = create();
                    work();

                    return;
                    }

                    break;
                    $quote;
                    String escaped = "a\\\"b";
                    char escapedQuote = '\'';
                    char slash = '/';
                    /* keep this explanation */ Code                 local     = create();
                    java.util.Map</* key */ String, Object> values = create();
                    values["preserve  spaces"]    = value;
                    use(slash);
                    Code                 code     = create();
                }
            }
        """.trimIndent()
        val last = source.lastIndexOf("Code                 code     = create();")
        val expected = source.replaceRange(last, last + "Code                 code     =".length, "Code code =")
        assertEquals(expected, JavaWhitespace.format(source))
    }
}
