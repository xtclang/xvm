package org.xvm.compiler;


import java.io.File;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorList;

import org.xvm.compiler.ast.Statement;

/**
 * Test of the Ecstasy parser
 */
public class ParserTest
    {
    /**
     * Allow for command-line testing
     *
     * @param args  file name
     */
    public static void main(String[] args)
            throws Exception
        {
        if (args.length < 1 || args[0].isEmpty())
            {
            out("file name required");
            return;
            }

        File file = new File(args[0]);
        if (!(file.exists() && file.canRead()))
            {
            out("cannot read file: " + args[0]);
            return;
            }

        Source source = new Source(file);
        parse(source);
        }

    @Test
    public void testSimpleModule()
        {
        parse("module Test {}");
        }

    @Test
    public void testSimpleInterface()
        {
        parse("interface SortedMap extends Map {}");
        }

    @Test
    public void testSimpleDelegates()
        {
        parse("class DependentFutureRef delegates Ref(value) {}");
        }

    static void parse(String value)
        {
            parse(new Source(value));
        }

    static void parse(Source source)
        {
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
    static void out(Object o)
        {
        System.out.println(o);
        }
    }
