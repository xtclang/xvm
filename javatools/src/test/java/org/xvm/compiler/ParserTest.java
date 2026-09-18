package org.xvm.compiler;

import java.io.File;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener.ErrorInfo;

import org.xvm.compiler.ast.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test of the Ecstasy parser
 */
public class ParserTest {
    /**
     * Allow for command-line testing
     *
     * @param args  file name
     */
    public static void main(String[] args)
            throws Exception {
        if (args.length < 1 || args[0].isEmpty()) {
            out("file name required");
            return;
        }

        File file = new File(args[0]);
        if (!(file.exists() && file.canRead())) {
            out("cannot read file: " + args[0]);
            return;
        }

        Source source = new Source(file);
        parse(source);
    }

    @Test
    public void testSimpleModule() {
        parse("module Test {}");
    }

    @Test
    public void testSimpleInterface() {
        parse("interface SortedMap extends Map {}");
    }

    @Test
    public void testSimpleDelegates() {
        parse("class DependentFutureRef delegates Ref(value) {}");
    }

    @Test
    public void testMissingSemicolonInExpressionStatement() {
        ErrorList errlist = new ErrorList(5);
        Parser    parser  = new Parser(new Source("""
                module TestSimple {
                    @Inject Console console;

                    void run() {
                        console.print("no semicolon")
                    }
                }
                """), errlist);

        parser.parseSource();

        assertEquals(1, errlist.getSeriousErrorCount());
        assertEquals(1, errlist.getErrors().size());

        ErrorInfo error = errlist.getErrors().get(0);
        assertEquals(Parser.MISSING_SEMICOLON, error.getCode());
        assertEquals("Semicolon is missing.", error.getMessageText());
    }

    @Test
    public void testDeeplyNestedExpressionIsReportedNotOverflowed() {
        // deep enough that the recursive descent would exhaust the stack without a limit
        int       cNesting = Parser.MAX_EXPR_DEPTH * 64;
        ErrorList errlist  = new ErrorList(5);
        Parser    parser   = new Parser(new Source("""
                module TestSimple {
                    void run() {
                        Int x = %s1%s;
                    }
                }
                """.formatted("(".repeat(cNesting), ")".repeat(cNesting))), errlist);

        // the limit is not recoverable, so parseSource() abandons its progress, as documented
        assertThrows(CompilerException.class, parser::parseSource);

        assertEquals(1, errlist.getSeriousErrorCount());

        ErrorInfo error = errlist.getErrors().get(0);
        assertEquals(Parser.EXPR_TOO_DEEP, error.getCode());
        assertEquals("Expression is nested too deeply; the limit is "
                + Parser.MAX_EXPR_DEPTH + " levels.", error.getMessageText());
    }

    @Test
    public void testNestingJustUnderTheLimitStillParses() {
        ErrorList errlist  = new ErrorList(5);
        int       cNesting = Parser.MAX_EXPR_DEPTH - 8;
        Parser    parser   = new Parser(new Source("""
                module TestSimple {
                    void run() {
                        Int x = %s1%s;
                    }
                }
                """.formatted("(".repeat(cNesting), ")".repeat(cNesting))), errlist);

        parser.parseSource();

        assertEquals(0, errlist.getSeriousErrorCount());
    }

    static void parse(String value) {
            parse(new Source(value));
    }

    static void parse(Source source) {
        ErrorList errlist = new ErrorList(5);
        Parser parser = new Parser(source, errlist);

        Statement stmt = parser.parseSource();
        out(stmt);

        out("error list (" + errlist.getSeriousErrorCount()
                + " of " + errlist.getSeriousErrorMax() + ", sev="
                + errlist.getSeverity() + "):");

        errlist.getErrors().forEach(ParserTest::out);
    }

    /**
     * Debug output.
     *
     * @param o  something to print
     */
    static void out(Object o) {
        System.out.println(o);
    }
}
