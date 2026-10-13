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
                    use(register);
                    this.first    = first;
                    this.complete = complete;
                    use(this.complete);
                    target   = null;
                }
            }
        """.trimIndent()
        val expected = source
            .replace("Code                 code     =", "Code code =")
            .replace("boolean       ", "boolean ")
            .replace("first = true", "first    = true")
            .replace("target   = null", "target = null")
        assertEquals(expected, JavaWhitespace.format(source))
        assertEquals(expected, JavaWhitespace.format(expected))
    }

    @Test
    fun `keeps Unicode identifiers in one assignment group`() {
        val source = """
            void clear() {
                m_typeEnumeration = null;
                m_typeException   = null;
                m_typeException१    = null;
                m_typeStringBuffer = null;
                m_typeString१       = null;
                m_typeBit           = null;
            }
        """.trimIndent().prependIndent("    ")
        val expected = """
            void clear() {
                m_typeEnumeration  = null;
                m_typeException    = null;
                m_typeException१   = null;
                m_typeStringBuffer = null;
                m_typeString१      = null;
                m_typeBit          = null;
            }
        """.trimIndent().prependIndent("    ")
        assertFormat(expected, source)
    }

    @Test
    fun `preserves aligned array targets and pads the shorter assignment`() {
        val source = "        afDynamic[index] = false;\n        ahValue  [index--] = frame.popStack();\n"
        val expected = "        afDynamic[index]   = false;\n        ahValue  [index--] = frame.popStack();\n"
        assertFormat(expected, source)
    }

    @Test
    fun `repairs missing padding in an aligned declaration group`() {
        val source = """
            void init() {
                int cBucketsOld = buckets.length;
                int nModuloOld  = cBucketsOld >>> 1;
                int nModuloNew = INDEX_SIZE[index];
            }
        """.trimIndent().prependIndent("    ")
        assertFormat(source.replace("nModuloNew =", "nModuloNew  ="), source)
    }

    @Test
    fun `aligns mixed declarations ordinary assignments and compound assignments`() {
        val source = """
            void read() {
                lIntVal = unpackInt(bytes, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int idNext = (int) lIntVal;

                lIntVal = unpackInt(bytes, ofCur);
                ofCur     += (int) (lIntVal >>> 32);
                int cBytes = (int) lIntVal;
            }
        """.trimIndent().prependIndent("    ")
        val expected = source.replace("lIntVal =", "lIntVal    =")
        assertFormat(expected, source)
    }

    @ParameterizedTest
    @ValueSource(strings = ["+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>="])
    fun `aligns the equals sign of compound assignments`(operator: String) {
        val source = "        nExp $operator bits;\n        nSig  = bits;\n"
        val expected = "        nExp $operator bits;\n        nSig${" ".repeat(operator.length)}= bits;\n"
        assertFormat(expected, source)
    }

    @Test
    fun `keeps a wrapped initializer in its alignment group`() {
        val source = """
            void read() {
                body    = new StatementBlock(statements,
                        start, end);
                lEndPos = body.getEndPosition();
            }
        """.trimIndent().prependIndent("    ")
        assertFormat(source, source)
    }

    @Test
    fun `does not pull a separate long declaration into a short alignment group`() {
        val source = """
            void read() {
                MethodDeclarationStatement method = new MethodDeclarationStatement(
                        start, end);
                body    = new StatementBlock(statements,
                        start, end);
                lEndPos = body.getEndPosition();
                use(body);
                long  lPos    = expr.getStartPosition();
                      type    = new VariableTypeExpression();
                Token tokName = name();
                Token tokAsn  = assignment();
                VariableDeclarationStatement var = declaration();
            }
        """.trimIndent().prependIndent("    ")
        assertFormat(source, source)
    }

    @Test
    fun `keeps assignments indented under declaration names`() {
        val source = """
            void read() {
                long  lPos    = expr.getStartPosition();
                      type    = new VariableTypeExpression();
                Token tokName = name();
                Token tokAsn  = assignment();
            }
        """.trimIndent().prependIndent("    ")
        assertFormat(source, source)
    }

    @Test
    fun `leaves groups without alignment padding alone`() {
        val source = "        int first = 1;\n        int longerName = 2;\n        use(first, longerName);\n"
        assertFormat(source, source)
    }

    /** Verify exact layout, repeatability and line endings for each regression example. */
    private fun assertFormat(expected: String, source: String) {
        assertEquals(expected, JavaWhitespace.format(source))
        assertEquals(expected, JavaWhitespace.format(expected))
        assertEquals(expected.replace("\n", "\r\n"), JavaWhitespace.format(source.replace("\n", "\r\n")))
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
