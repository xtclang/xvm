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
    public void testDeeplyNestedExpressionIsReported() {
        // deep enough that the recursive descent would exhaust the stack without a limit
        int       cNesting = Parser.MAX_NESTING_DEPTH * 64;
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
        assertEquals(Parser.NESTING_TOO_DEEP, error.getCode());
        assertEquals("Nested too deeply; the limit is "
                + Parser.MAX_NESTING_DEPTH + " levels.", error.getMessageText());
    }

    @Test
    public void testNestingUnderTheLimitStillParses() {
        // a quarter of the budget: a parenthesised expression spends more than one level, since
        // it passes the expression guard and the prefix guard on the way down
        ErrorList errlist  = new ErrorList(5);
        int       cNesting = Parser.MAX_NESTING_DEPTH / 4;
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

    @Test
    public void testDeeplyNestedTypeIsReported() {
        // types recurse through their own chain, not through the expression chain. The code is
        // not asserted here: a type is parsed inside a SafeLookAhead, which discards the errors
        // of an attempt that fails, so what survives is the token error the caller reports
        // instead. What matters is that the parse ends in a CompilerException rather than a
        // StackOverflowError, which assertThrows already pins, since an Error is not a
        // CompilerException
        int       cNesting = Parser.MAX_NESTING_DEPTH * 64;
        ErrorList errlist  = new ErrorList(5);
        Parser    parser   = new Parser(new Source("""
                module TestSimple {
                    void run() {
                        %sInt%s x;
                    }
                }
                """.formatted("List<".repeat(cNesting), ">".repeat(cNesting))), errlist);

        assertThrows(CompilerException.class, parser::parseSource);

        assertEquals(1, errlist.getSeriousErrorCount());
    }

    @Test
    public void testDeeplyNestedStatementIsReported() {
        // statements recurse through a third chain again; a switch body is the cheapest to nest
        int       cNesting = Parser.MAX_NESTING_DEPTH * 64;
        ErrorList errlist  = new ErrorList(5);
        Parser    parser   = new Parser(new Source("""
                module TestSimple {
                    void run() {
                        %s break;%s
                    }
                }
                """.formatted("switch (1) { case 1:".repeat(cNesting), "}".repeat(cNesting))), errlist);

        assertThrows(CompilerException.class, parser::parseSource);

        assertEquals(Parser.NESTING_TOO_DEEP, errlist.getErrors().get(0).getCode());
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
